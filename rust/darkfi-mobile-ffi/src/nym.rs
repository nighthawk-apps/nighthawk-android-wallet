//! In-process Nym mixnet SOCKS5 proxy for iOS and Android.
//!
//! Same transport as zkool2: a shared `nym_smolmix::Tunnel` sends TCP through
//! the mixnet and exits at an IPR gateway. Hostnames resolve over the tunnel
//! (DNS to 1.1.1.1 through the mixnet UDP socket), so server names never reach
//! the local resolver. The mixnet client is ephemeral and needs no bandwidth
//! credential.
//!
//! The proxy speaks SOCKS5 on localhost like the Arti proxy in `tor.rs`, so
//! the lightwalletd client routes through it unchanged.
//!
//! Status is derived from the actual tunnel bootstrap: it never reports
//! `Connected` unless a tunnel came up. Transport failures discard the tunnel
//! and the next connection bootstraps a fresh one.

use std::collections::HashMap;
use std::net::{IpAddr, SocketAddr};
use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};
use std::sync::Mutex as StdMutex;
use std::time::{Duration, Instant};

use hickory_proto::op::{Message, Query};
use hickory_proto::rr::{Name, RData, RecordType};
use nym_smolmix::{TcpStream as MixTcpStream, Tunnel};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::Mutex;

/// Default localhost SOCKS5 port for the Nym proxy.
pub const DEFAULT_NYM_SOCKS_PORT: u16 = 1080;

/// Per-attempt DNS query timeout (a fresh socket is used for each attempt).
const DNS_TIMEOUT: Duration = Duration::from_secs(8);
/// Public resolvers tried in turn through the mixnet, so one slow resolver or
/// exit path does not fail the lookup (and tear down the tunnel).
const DNS_RESOLVERS: [[u8; 4]; 3] = [[1, 1, 1, 1], [9, 9, 9, 9], [8, 8, 8, 8]];
/// Looked up to tell a dead tunnel from an unreachable destination.
const HEALTH_PROBE_HOST: &str = "nymtech.net";
/// A tunnel that recently proved healthy is not probed again for this long.
const HEALTH_PROBE_INTERVAL: Duration = Duration::from_secs(30);
/// How long a resolved address may be reused without a new lookup.
const DNS_TTL: Duration = Duration::from_secs(300);
/// Give up on a TCP connect through the mixnet after this long.
const TCP_CONNECT_TIMEOUT: Duration = Duration::from_secs(30);
/// Give up on tunnel bootstrap after this long. The bootstrap holds the
/// tunnel lock, so a hang here would otherwise block every caller forever.
const BOOTSTRAP_TIMEOUT: Duration = Duration::from_secs(90);

const NYM_STOPPED: u8 = 0;
const NYM_BOOTSTRAPPING: u8 = 1;
const NYM_CONNECTED: u8 = 2;
const NYM_FAILED: u8 = 3;

static NYM_STATE: AtomicU8 = AtomicU8::new(NYM_STOPPED);
static NYM_STOP_REQUESTED: AtomicBool = AtomicBool::new(false);
/// Set while a replacement tunnel bootstraps after the previous one failed.
static NYM_REBUILDING: AtomicBool = AtomicBool::new(false);
/// Generation and time of the last proof that the tunnel carries traffic.
static LAST_HEALTHY: StdMutex<Option<(u64, Instant)>> = StdMutex::new(None);

struct TunnelSlot {
    tunnel: Option<(u64, Tunnel)>,
    next_gen: u64,
}

static TUNNEL: Mutex<TunnelSlot> = Mutex::const_new(TunnelSlot {
    tunnel: None,
    next_gen: 0,
});

static DNS_CACHE: StdMutex<Option<HashMap<String, (IpAddr, Instant)>>> = StdMutex::new(None);

/// Start the in-process Nym SOCKS proxy on the given port.
///
/// Returns `Ok(true)` when a new bootstrap was kicked off, `Ok(false)` when a
/// proxy is already running or bootstrapping. Bootstrap proceeds on a
/// background thread; poll [`is_nym_running`] to observe it.
pub fn start_nym_proxy(socks_port: u16) -> Result<bool, crate::DarkfiWalletNativeError> {
    #[cfg(feature = "darkirc")]
    crate::darkirc_daemon::init_logging();
    let _ = rustls::crypto::ring::default_provider().install_default();

    // A pending stop is still winding down the previous proxy; let it exit
    // so this start is not mistaken for a duplicate.
    let deadline = Instant::now() + Duration::from_secs(3);
    while NYM_STOP_REQUESTED.load(Ordering::SeqCst)
        && !matches!(NYM_STATE.load(Ordering::SeqCst), NYM_STOPPED | NYM_FAILED)
        && Instant::now() < deadline
    {
        std::thread::sleep(Duration::from_millis(50));
    }

    if NYM_STATE
        .compare_exchange(
            NYM_STOPPED,
            NYM_BOOTSTRAPPING,
            Ordering::SeqCst,
            Ordering::SeqCst,
        )
        .is_err()
    {
        if NYM_STATE.load(Ordering::SeqCst) == NYM_FAILED {
            NYM_STATE.store(NYM_BOOTSTRAPPING, Ordering::SeqCst);
        } else {
            return Ok(false);
        }
    }

    NYM_STOP_REQUESTED.store(false, Ordering::SeqCst);

    std::thread::Builder::new()
        .name("nym-proxy".into())
        .spawn(move || {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                tokio::runtime::Builder::new_multi_thread()
                    .worker_threads(2)
                    .thread_name("nym-proxy-rt")
                    .enable_all()
                    .build()
                    .map_err(|e| format!("nym runtime: {e}"))?
                    .block_on(run_socks_proxy(socks_port))
            }));
            match result {
                Ok(Ok(())) => NYM_STATE.store(NYM_STOPPED, Ordering::SeqCst),
                Ok(Err(e)) => {
                    tracing::error!("nym proxy exited with error: {e}");
                    NYM_STATE.store(NYM_FAILED, Ordering::SeqCst);
                }
                Err(payload) => {
                    let msg = payload
                        .downcast_ref::<&str>()
                        .map(|s| s.to_string())
                        .or_else(|| payload.downcast_ref::<String>().cloned())
                        .unwrap_or_else(|| "unknown panic".into());
                    tracing::error!("nym proxy thread panicked: {msg}");
                    NYM_STATE.store(NYM_FAILED, Ordering::SeqCst);
                }
            }
        })
        .map_err(|e| {
            NYM_STATE.store(NYM_FAILED, Ordering::SeqCst);
            crate::DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "failed to spawn nym thread: {e}"
            ))
        })?;

    Ok(true)
}

/// Request the Nym SOCKS proxy to stop. The accept loop exits on its next
/// iteration, shuts the tunnel down, and resets the state to `Stopped`.
pub fn stop_nym_proxy() {
    NYM_STOP_REQUESTED.store(true, Ordering::SeqCst);
}

/// Whether the mixnet tunnel genuinely came up and the proxy is accepting.
pub fn is_nym_running() -> bool {
    NYM_STATE.load(Ordering::SeqCst) == NYM_CONNECTED
}

/// Proxy lifecycle for UI progress: `stopped` | `bootstrapping` | `connected` |
/// `reconnecting` (a failed tunnel is being replaced) | `failed`.
pub fn nym_status() -> String {
    match NYM_STATE.load(Ordering::SeqCst) {
        NYM_BOOTSTRAPPING => "bootstrapping",
        NYM_CONNECTED if NYM_REBUILDING.load(Ordering::SeqCst) => "reconnecting",
        NYM_CONNECTED => "connected",
        NYM_FAILED => "failed",
        _ => "stopped",
    }
    .to_string()
}

/// Block until the Nym tunnel is up (or `timeout` elapses / bootstrap failed).
pub fn wait_until_running(timeout: Duration) -> Result<(), crate::DarkfiWalletNativeError> {
    let deadline = Instant::now() + timeout;
    loop {
        if NYM_STOP_REQUESTED.load(Ordering::SeqCst) {
            return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                "Nym mixnet proxy was stopped".into(),
            ));
        }
        match NYM_STATE.load(Ordering::SeqCst) {
            NYM_CONNECTED => return Ok(()),
            NYM_FAILED => {
                return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                    "Nym mixnet bootstrap failed".into(),
                ));
            }
            NYM_STOPPED => {
                return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                    "Nym mixnet proxy is not running".into(),
                ));
            }
            _ => {
                if Instant::now() >= deadline {
                    return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                        "Timed out waiting for Nym mixnet bootstrap".into(),
                    ));
                }
                std::thread::sleep(Duration::from_millis(250));
            }
        }
    }
}

/// Bootstrap the tunnel and serve SOCKS5 CONNECT requests until stopped.
async fn run_socks_proxy(socks_port: u16) -> Result<(), String> {
    // Bind before bootstrap so readiness probes see a listening socket while
    // the mixnet client comes up (same reason as the Arti proxy).
    let listener = TcpListener::bind(("127.0.0.1", socks_port))
        .await
        .map_err(|e| format!("SOCKS bind on 127.0.0.1:{socks_port} failed: {e}"))?;
    tracing::info!("nym SOCKS bound on 127.0.0.1:{socks_port}; bootstrapping mixnet tunnel...");

    // A stop (e.g. the user continuing without Nym) abandons a slow bootstrap.
    tokio::select! {
        r = get_tunnel() => { r.map_err(|e| e.to_string())?; }
        _ = wait_for_stop() => {
            tracing::info!("nym bootstrap cancelled by stop request");
            return Ok(());
        }
    }
    NYM_STATE.store(NYM_CONNECTED, Ordering::SeqCst);
    tracing::info!("nym SOCKS proxy ready on 127.0.0.1:{socks_port}");

    while !NYM_STOP_REQUESTED.load(Ordering::SeqCst) {
        let accepted = tokio::select! {
            r = listener.accept() => r,
            _ = tokio::time::sleep(Duration::from_secs(1)) => continue,
        };
        match accepted {
            Ok((stream, _)) => {
                tokio::spawn(async move {
                    if let Err(e) = handle_socks_conn(stream).await {
                        tracing::debug!("nym SOCKS connection closed: {e}");
                    }
                });
            }
            Err(e) => tracing::warn!("nym SOCKS accept error: {e}"),
        }
    }

    let mut slot = TUNNEL.lock().await;
    if let Some((_, tunnel)) = slot.tunnel.take() {
        tunnel.shutdown().await;
    }
    clear_dns_cache();
    Ok(())
}

async fn wait_for_stop() {
    while !NYM_STOP_REQUESTED.load(Ordering::SeqCst) {
        tokio::time::sleep(Duration::from_millis(250)).await;
    }
}

/// Minimal SOCKS5 CONNECT handler: "no authentication", read the target,
/// dial it through the mixnet, and pipe bytes both ways.
async fn handle_socks_conn(mut inbound: TcpStream) -> Result<(), String> {
    let mut head = [0u8; 2];
    inbound.read_exact(&mut head).await.map_err(|e| e.to_string())?;
    if head[0] != 0x05 {
        return Err("not a SOCKS5 greeting".into());
    }
    let mut methods = vec![0u8; head[1] as usize];
    inbound.read_exact(&mut methods).await.map_err(|e| e.to_string())?;
    inbound.write_all(&[0x05, 0x00]).await.map_err(|e| e.to_string())?;

    let mut req = [0u8; 4];
    inbound.read_exact(&mut req).await.map_err(|e| e.to_string())?;
    if req[0] != 0x05 {
        return Err("bad SOCKS5 request version".into());
    }
    if req[1] != 0x01 {
        let _ = inbound.write_all(&socks_reply(0x07)).await;
        return Err("unsupported SOCKS command".into());
    }

    let host: String = match req[3] {
        0x01 => {
            let mut addr = [0u8; 4];
            inbound.read_exact(&mut addr).await.map_err(|e| e.to_string())?;
            std::net::Ipv4Addr::from(addr).to_string()
        }
        0x03 => {
            let mut len = [0u8; 1];
            inbound.read_exact(&mut len).await.map_err(|e| e.to_string())?;
            let mut domain = vec![0u8; len[0] as usize];
            inbound.read_exact(&mut domain).await.map_err(|e| e.to_string())?;
            String::from_utf8(domain).map_err(|e| e.to_string())?
        }
        0x04 => {
            let mut addr = [0u8; 16];
            inbound.read_exact(&mut addr).await.map_err(|e| e.to_string())?;
            std::net::Ipv6Addr::from(addr).to_string()
        }
        other => {
            let _ = inbound.write_all(&socks_reply(0x08)).await;
            return Err(format!("unsupported SOCKS atyp {other}"));
        }
    };
    let mut port_bytes = [0u8; 2];
    inbound.read_exact(&mut port_bytes).await.map_err(|e| e.to_string())?;
    let port = u16::from_be_bytes(port_bytes);

    // Onion services are only reachable over Tor; never fall back to clearnet.
    if host.to_ascii_lowercase().ends_with(".onion") {
        let _ = inbound.write_all(&socks_reply(0x04)).await;
        return Err(format!("{host} is a Tor onion address; Nym cannot reach it"));
    }

    let mut outbound = match nym_connect(&host, port).await {
        Ok(s) => s,
        Err(e) => {
            let _ = inbound.write_all(&socks_reply(0x04)).await;
            return Err(format!("Nym connect to {host}:{port} failed: {e}"));
        }
    };
    inbound.write_all(&socks_reply(0x00)).await.map_err(|e| e.to_string())?;

    tokio::io::copy_bidirectional(&mut inbound, &mut outbound)
        .await
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// SOCKS5 reply with the given status and a dummy bound address (0.0.0.0:0).
fn socks_reply(status: u8) -> [u8; 10] {
    [0x05, status, 0x00, 0x01, 0, 0, 0, 0, 0, 0]
}

/// The shared tunnel, bootstrapping one if none is cached. The lock is held
/// during bootstrap so concurrent callers wait for the same tunnel.
async fn get_tunnel() -> Result<(u64, Tunnel), String> {
    let mut slot = TUNNEL.lock().await;
    if let Some((gen, tunnel)) = &slot.tunnel {
        return Ok((*gen, tunnel.clone()));
    }
    tracing::info!("bootstrapping Nym mixnet tunnel");
    let start = Instant::now();
    NYM_REBUILDING.store(slot.next_gen > 0, Ordering::SeqCst);
    let built = tokio::time::timeout(BOOTSTRAP_TIMEOUT, Tunnel::builder().build()).await;
    NYM_REBUILDING.store(false, Ordering::SeqCst);
    let tunnel = built
        .map_err(|_| "Nym mixnet tunnel bootstrap timed out".to_string())?
        .map_err(|e| format!("failed to bootstrap Nym mixnet tunnel: {e}"))?;
    tracing::info!("Nym mixnet tunnel ready in {:?}", start.elapsed());
    let gen = slot.next_gen;
    slot.next_gen += 1;
    slot.tunnel = Some((gen, tunnel.clone()));
    mark_healthy(gen);
    Ok((gen, tunnel))
}

/// Discard the cached tunnel if it is still generation `gen`, so the next
/// call bootstraps a fresh one. The stale tunnel shuts down in the background.
async fn invalidate_tunnel(gen: u64) {
    let mut slot = TUNNEL.lock().await;
    if matches!(&slot.tunnel, Some((g, _)) if *g == gen) {
        tracing::warn!("Nym tunnel failed; discarding it and clearing the DNS cache");
        if let Some((_, tunnel)) = slot.tunnel.take() {
            tokio::spawn(async move { tunnel.shutdown().await });
        }
        clear_dns_cache();
    }
}

/// Open a TCP stream to (host, port) through the mixnet.
///
/// A failed connect only discards the shared tunnel when a health probe shows
/// the tunnel itself is dead: P2P callers routinely dial offline peers, and
/// rebuilding on each of those would drop every other live connection.
async fn nym_connect(host: &str, port: u16) -> Result<MixTcpStream, String> {
    let (gen, tunnel) = get_tunnel().await?;

    let ip = match resolve_over_nym(&tunnel, host).await {
        Ok(Some(ip)) => ip,
        Ok(None) => return Err(format!("no A record for {host} from mixnet DNS")),
        Err(e) => {
            // Every resolver timed out: the tunnel is not carrying traffic.
            invalidate_tunnel(gen).await;
            return Err(e);
        }
    };

    let err = match tokio::time::timeout(
        TCP_CONNECT_TIMEOUT,
        tunnel.tcp_connect(SocketAddr::new(ip, port)),
    )
    .await
    {
        Ok(Ok(stream)) => {
            mark_healthy(gen);
            return Ok(stream);
        }
        Ok(Err(e)) => format!("mixnet TCP connect to {host}:{port} failed: {e}"),
        Err(_) => format!("mixnet TCP connect to {host}:{port} timed out"),
    };
    if !tunnel_is_healthy(gen, &tunnel).await {
        invalidate_tunnel(gen).await;
    }
    Err(err)
}

fn mark_healthy(gen: u64) {
    if let Ok(mut last) = LAST_HEALTHY.lock() {
        *last = Some((gen, Instant::now()));
    }
}

/// Whether tunnel `gen` still carries traffic: recently proven, or answers a
/// fresh DNS query through the mixnet.
async fn tunnel_is_healthy(gen: u64, tunnel: &Tunnel) -> bool {
    let recent = LAST_HEALTHY
        .lock()
        .ok()
        .and_then(|l| *l)
        .is_some_and(|(g, at)| g == gen && at.elapsed() < HEALTH_PROBE_INTERVAL);
    if recent {
        return true;
    }
    for resolver in DNS_RESOLVERS.iter().take(2) {
        let probe = tokio::time::timeout(DNS_TIMEOUT, dns_query(tunnel, HEALTH_PROBE_HOST, *resolver));
        if matches!(probe.await, Ok(Ok(_))) {
            mark_healthy(gen);
            return true;
        }
    }
    false
}

/// Resolve `host` through the mixnet unless it is an IP literal or freshly
/// cached. `Ok(None)` means the DNS server answered without an A record
/// (tunnel healthy, name unresolvable); `Err` is a transport failure.
async fn resolve_over_nym(tunnel: &Tunnel, host: &str) -> Result<Option<IpAddr>, String> {
    if let Ok(ip) = host.parse::<IpAddr>() {
        return Ok(Some(ip));
    }
    if let Some(ip) = dns_cache_get(host) {
        return Ok(Some(ip));
    }

    let mut last_err = String::new();
    for (attempt, resolver) in DNS_RESOLVERS.iter().enumerate() {
        // The timeout covers socket creation and send too: both can stall
        // when the tunnel's bridge is unhealthy.
        let query = tokio::time::timeout(DNS_TIMEOUT, dns_query(tunnel, host, *resolver))
            .await
            .map_err(|_| format!("mixnet DNS lookup for {host} timed out"))
            .and_then(|r| r);
        match query {
            Ok(answer) => {
                if let Some(ip) = answer {
                    dns_cache_put(host, ip);
                }
                return Ok(answer);
            }
            Err(e) => {
                tracing::warn!("mixnet DNS lookup for {host} failed (attempt {attempt}): {e}");
                last_err = e;
            }
        }
    }
    Err(last_err)
}

/// One DNS A query to `resolver` over a fresh tunnel UDP socket.
async fn dns_query(
    tunnel: &Tunnel,
    host: &str,
    resolver: [u8; 4],
) -> Result<Option<IpAddr>, String> {
    let udp = tunnel.udp_socket().await.map_err(|e| e.to_string())?;
    let mut query = Message::query();
    query.metadata.recursion_desired = true;
    let name = Name::from_ascii(host).map_err(|e| e.to_string())?;
    query.add_query(Query::query(name, RecordType::A));
    let bytes = query.to_vec().map_err(|e| e.to_string())?;
    udp.send_to(&bytes, SocketAddr::from((resolver, 53)))
        .await
        .map_err(|e| e.to_string())?;
    let mut buf = [0u8; 1500];
    let (len, _src) = udp.recv_from(&mut buf).await.map_err(|e| e.to_string())?;
    let response = Message::from_vec(&buf[..len]).map_err(|e| e.to_string())?;
    Ok(first_a_record(&response))
}

fn first_a_record(msg: &Message) -> Option<IpAddr> {
    msg.answers.iter().find_map(|record| match &record.data {
        RData::A(a) => Some(IpAddr::V4(a.0)),
        _ => None,
    })
}

fn dns_cache_get(host: &str) -> Option<IpAddr> {
    let cache = DNS_CACHE.lock().ok()?;
    let (ip, at) = cache.as_ref()?.get(host)?;
    (at.elapsed() < DNS_TTL).then_some(*ip)
}

fn dns_cache_put(host: &str, ip: IpAddr) {
    if let Ok(mut cache) = DNS_CACHE.lock() {
        cache
            .get_or_insert_with(HashMap::new)
            .insert(host.to_string(), (ip, Instant::now()));
    }
}

fn clear_dns_cache() {
    if let Ok(mut cache) = DNS_CACHE.lock() {
        *cache = None;
    }
}

#[cfg(test)]
mod tests {
    use std::net::Ipv4Addr;
    use std::str::FromStr;

    use hickory_proto::op::{Message, MessageType, OpCode, Query};
    use hickory_proto::rr::{rdata, Name, RData, Record, RecordType};

    use super::*;

    #[test]
    fn first_a_record_skips_cname_and_picks_a() {
        let name = Name::from_str("example.com.").unwrap();
        let mut msg = Message::new(1, MessageType::Response, OpCode::Query);
        msg.add_query(Query::query(name.clone(), RecordType::A));
        msg.add_answer(Record::from_rdata(
            name.clone(),
            300,
            RData::CNAME(rdata::CNAME(Name::from_str("alias.example.com.").unwrap())),
        ));
        msg.add_answer(Record::from_rdata(
            name,
            300,
            RData::A(rdata::A(Ipv4Addr::new(93, 184, 216, 34))),
        ));
        assert_eq!(
            first_a_record(&msg),
            Some(IpAddr::V4(Ipv4Addr::new(93, 184, 216, 34)))
        );
    }

    #[test]
    fn first_a_record_none_when_no_a_answer() {
        assert_eq!(first_a_record(&Message::query()), None);
    }

    #[test]
    fn dns_cache_round_trip() {
        let ip = IpAddr::V4(Ipv4Addr::new(1, 2, 3, 4));
        dns_cache_put("cache-test.example", ip);
        assert_eq!(dns_cache_get("cache-test.example"), Some(ip));
        assert_eq!(dns_cache_get("cache-miss.example"), None);
    }

    #[test]
    fn status_reports_stopped_before_start() {
        assert_eq!(nym_status(), "stopped");
    }

    #[test]
    fn resolvers_are_distinct_public_servers() {
        let mut seen = DNS_RESOLVERS.to_vec();
        seen.dedup();
        assert_eq!(seen.len(), DNS_RESOLVERS.len());
        assert!(DNS_RESOLVERS.iter().all(|r| r[0] != 127 && r[0] != 10));
    }

    #[test]
    fn wait_fails_fast_when_never_started() {
        assert!(wait_until_running(Duration::from_millis(10)).is_err());
    }

    /// Live mainnet round trip: `cargo test -p darkfi-mobile-ffi nym_live -- --ignored`.
    #[test]
    #[ignore]
    fn nym_live_http_through_socks() {
        let _ = tracing_subscriber::fmt()
            .with_env_filter(tracing_subscriber::EnvFilter::new("warn,nym_smolmix=info"))
            .with_writer(std::io::stderr)
            .try_init();
        let port = 19080;
        assert!(start_nym_proxy(port).unwrap());
        wait_until_running(Duration::from_secs(150)).unwrap();
        let body = tokio::runtime::Runtime::new().unwrap().block_on(async {
            let mut s = tokio_socks::tcp::Socks5Stream::connect(("127.0.0.1", port), ("example.com", 80))
                .await
                .unwrap();
            s.write_all(b"GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n")
                .await
                .unwrap();
            let mut buf = Vec::new();
            s.read_to_end(&mut buf).await.unwrap();
            String::from_utf8_lossy(&buf).into_owned()
        });
        stop_nym_proxy();
        assert!(body.starts_with("HTTP/1.1 200"), "{body}");
    }

    #[tokio::test]
    async fn socks_rejects_onion_without_dialling() {
        let listener = TcpListener::bind(("127.0.0.1", 0)).await.unwrap();
        let addr = listener.local_addr().unwrap();
        let server = tokio::spawn(async move {
            let (s, _) = listener.accept().await.unwrap();
            handle_socks_conn(s).await
        });
        let mut c = TcpStream::connect(addr).await.unwrap();
        c.write_all(&[0x05, 0x01, 0x00]).await.unwrap();
        let mut greet = [0u8; 2];
        c.read_exact(&mut greet).await.unwrap();
        assert_eq!(greet, [0x05, 0x00]);
        let host = b"abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefab.onion";
        let mut req = vec![0x05, 0x01, 0x00, 0x03, host.len() as u8];
        req.extend_from_slice(host);
        req.extend_from_slice(&9067u16.to_be_bytes());
        c.write_all(&req).await.unwrap();
        let mut reply = [0u8; 10];
        c.read_exact(&mut reply).await.unwrap();
        assert_eq!(reply[1], 0x04);
        assert!(server.await.unwrap().unwrap_err().contains("onion"));
    }
}
