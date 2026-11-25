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

use std::collections::{HashMap, VecDeque};
use std::net::{IpAddr, SocketAddr};
use std::sync::atomic::{AtomicBool, AtomicU8, Ordering};
use std::sync::Mutex as StdMutex;
use std::time::{Duration, Instant};

use hickory_proto::op::{Message, Query, ResponseCode};
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
/// Maximum cached DNS host entries before oldest eviction.
const MAX_DNS_CACHE_ENTRIES: usize = 256;
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
/// Set by a network change so the next dial drops the cached tunnel.
static DROP_TUNNEL: AtomicBool = AtomicBool::new(false);
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

struct DnsCache {
    map: HashMap<String, (IpAddr, Instant)>,
    order: VecDeque<String>,
}

impl DnsCache {
    fn new() -> Self {
        Self {
            map: HashMap::new(),
            order: VecDeque::new(),
        }
    }
}

static DNS_CACHE: StdMutex<Option<DnsCache>> = StdMutex::new(None);
static NYM_THREAD: StdMutex<Option<std::thread::JoinHandle<()>>> = StdMutex::new(None);

fn take_finished_nym_thread() -> Option<std::thread::JoinHandle<()>> {
    let mut slot = match NYM_THREAD.lock() {
        Ok(g) => g,
        Err(e) => e.into_inner(),
    };
    match slot.as_ref().map(|h| h.is_finished()) {
        Some(true) => slot.take(),
        _ => None,
    }
}

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

    if let Some(h) = take_finished_nym_thread() {
        let _ = h.join();
    }

    if NYM_STATE
        .compare_exchange(
            NYM_STOPPED,
            NYM_BOOTSTRAPPING,
            Ordering::SeqCst,
            Ordering::SeqCst,
        )
        .is_err()
        && NYM_STATE
            .compare_exchange(
                NYM_FAILED,
                NYM_BOOTSTRAPPING,
                Ordering::SeqCst,
                Ordering::SeqCst,
            )
            .is_err()
    {
        return Ok(false);
    }

    NYM_STOP_REQUESTED.store(false, Ordering::SeqCst);

    let handle = std::thread::Builder::new()
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

    if let Ok(mut slot) = NYM_THREAD.lock() {
        *slot = Some(handle);
    }

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

/// Drop the cached mixnet tunnel on the next dial and forget DNS answers.
pub(crate) fn on_network_changed() {
    DROP_TUNNEL.store(true, Ordering::SeqCst);
    clear_dns_cache();
    if let Ok(mut last) = LAST_HEALTHY.lock() {
        *last = None;
    }
}

/// Forget DNS answers while the process is backgrounded. The tunnel stays up
/// so a quick resume does not pay for another bootstrap.
pub(crate) fn on_suspend() {
    clear_dns_cache();
}

/// Block until the Nym tunnel is up (or `timeout` elapses / bootstrap failed).
#[cfg(test)]
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
    // The directory fetch times out often on mobile; one failure used to kill
    // the proxy for the whole session, so DarkIRC never got a tunnel.
    let mut bootstrap_err = String::new();
    for attempt in 0..3 {
        if NYM_STOP_REQUESTED.load(Ordering::SeqCst) {
            tracing::info!("nym bootstrap cancelled by stop request");
            return Ok(());
        }
        let tunnel_res = tokio::select! {
            res = get_tunnel() => res,
            _ = wait_for_stop() => {
                tracing::info!("nym bootstrap cancelled by stop request");
                return Ok(());
            }
        };
        match tunnel_res {
            Ok(_) => {
                bootstrap_err.clear();
                break;
            }
            Err(e) => {
                bootstrap_err = e;
                tracing::warn!("nym tunnel bootstrap failed (attempt {attempt}): {bootstrap_err}");
                tokio::select! {
                    _ = tokio::time::sleep(Duration::from_secs(2)) => {}
                    _ = wait_for_stop() => {
                        tracing::info!("nym bootstrap cancelled by stop request");
                        return Ok(());
                    }
                }
            }
        }
    }
    if !bootstrap_err.is_empty() {
        return Err(bootstrap_err);
    }
    NYM_STATE.store(NYM_CONNECTED, Ordering::SeqCst);
    tracing::info!("nym SOCKS proxy ready on 127.0.0.1:{socks_port}");

    while !NYM_STOP_REQUESTED.load(Ordering::SeqCst) {
        if NYM_STATE.load(Ordering::SeqCst) == NYM_FAILED {
            let mut slot = TUNNEL.lock().await;
            if let Some((_, tunnel)) = slot.tunnel.take() {
                tunnel.shutdown().await;
            }
            clear_dns_cache();
            return Err("Nym tunnel rebuild failed".into());
        }
        let accepted = tokio::select! {
            r = listener.accept() => r,
            _ = tokio::time::sleep(Duration::from_secs(1)) => continue,
        };
        match accepted {
            Ok((stream, _)) => {
                tokio::spawn(async move {
                    if let Err(e) = handle_socks_conn(stream).await {
                        // Info, not debug: a failed dial is why DarkIRC stays on
                        // "waiting for peers" after the tunnel itself is up.
                        // Redact any host/IP details before logging.
                        tracing::info!(
                            "nym SOCKS connection closed: {}",
                            crate::sync::redact_sync_error(&e)
                        );
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

/// Minimal SOCKS5 CONNECT handler. Prefer RFC 1929 username/password when the
/// client offers it, and still accept method 0x00 so existing callers work.
async fn handle_socks_conn(mut inbound: TcpStream) -> Result<(), String> {
    accept_socks_greeting(&mut inbound).await?;

    let mut req = [0u8; 4];
    inbound
        .read_exact(&mut req)
        .await
        .map_err(|e| e.to_string())?;
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
            inbound
                .read_exact(&mut addr)
                .await
                .map_err(|e| e.to_string())?;
            std::net::Ipv4Addr::from(addr).to_string()
        }
        0x03 => {
            let mut len = [0u8; 1];
            inbound
                .read_exact(&mut len)
                .await
                .map_err(|e| e.to_string())?;
            let mut domain = vec![0u8; len[0] as usize];
            inbound
                .read_exact(&mut domain)
                .await
                .map_err(|e| e.to_string())?;
            String::from_utf8(domain).map_err(|e| e.to_string())?
        }
        0x04 => {
            let mut addr = [0u8; 16];
            inbound
                .read_exact(&mut addr)
                .await
                .map_err(|e| e.to_string())?;
            std::net::Ipv6Addr::from(addr).to_string()
        }
        other => {
            let _ = inbound.write_all(&socks_reply(0x08)).await;
            return Err(format!("unsupported SOCKS atyp {other}"));
        }
    };
    let mut port_bytes = [0u8; 2];
    inbound
        .read_exact(&mut port_bytes)
        .await
        .map_err(|e| e.to_string())?;
    let port = u16::from_be_bytes(port_bytes);

    // Onion services are only reachable over Tor; never fall back to clearnet.
    // Strip trailing DNS root dot(s) so `foo.onion.` is blocked identically to `foo.onion`.
    let normalized_host = host.trim().trim_end_matches('.').to_ascii_lowercase();
    if normalized_host.ends_with(".onion") || normalized_host == "onion" {
        let _ = inbound.write_all(&socks_reply(0x04)).await;
        return Err("Tor onion address rejected; Nym cannot reach .onion hosts".into());
    }

    let mut outbound = match nym_connect(&host, port).await {
        Ok(s) => s,
        Err(e) => {
            let _ = inbound.write_all(&socks_reply(0x04)).await;
            return Err(format!("Nym connect failed: {e}"));
        }
    };
    inbound
        .write_all(&socks_reply(0x00))
        .await
        .map_err(|e| e.to_string())?;

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
    if DROP_TUNNEL.swap(false, Ordering::SeqCst) {
        if let Some((_, tunnel)) = slot.tunnel.take() {
            tracing::info!("dropping Nym tunnel after a network change");
            tokio::spawn(async move { tunnel.shutdown().await });
        }
        clear_dns_cache();
    }
    if let Some((gen, tunnel)) = &slot.tunnel {
        return Ok((*gen, tunnel.clone()));
    }
    tracing::info!("bootstrapping Nym mixnet tunnel");
    let start = Instant::now();
    let rebuilding = slot.next_gen > 0;
    NYM_REBUILDING.store(rebuilding, Ordering::SeqCst);
    let built = tokio::time::timeout(BOOTSTRAP_TIMEOUT, Tunnel::builder().build()).await;
    NYM_REBUILDING.store(false, Ordering::SeqCst);
    let tunnel = match built {
        Ok(Ok(tunnel)) => tunnel,
        Ok(Err(e)) => {
            if rebuilding {
                NYM_STATE.store(NYM_FAILED, Ordering::SeqCst);
            }
            return Err(format!("failed to bootstrap Nym mixnet tunnel: {e}"));
        }
        Err(_) => {
            if rebuilding {
                NYM_STATE.store(NYM_FAILED, Ordering::SeqCst);
            }
            return Err("Nym mixnet tunnel bootstrap timed out".to_string());
        }
    };
    tracing::info!("Nym mixnet tunnel ready in {:?}", start.elapsed());
    let gen = slot.next_gen;
    slot.next_gen += 1;
    slot.tunnel = Some((gen, tunnel.clone()));
    NYM_STATE.store(NYM_CONNECTED, Ordering::SeqCst);
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
        Ok(None) => return Err("no A/AAAA record from mixnet DNS".into()),
        Err(e) => {
            // One dead or slow name is normal (old hostlist entries). Drop the
            // shared tunnel only when a health probe says it is not carrying
            // traffic, matching the TCP-connect path below. Invalidating on
            // every DNS miss tears down in-flight peer dials and leaves
            // DarkIRC on "waiting for peers" forever.
            if !tunnel_is_healthy(gen, &tunnel).await {
                invalidate_tunnel(gen).await;
            }
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
        Ok(Err(e)) => format!("mixnet TCP connect failed: {e}"),
        Err(_) => "mixnet TCP connect timed out".to_string(),
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

/// First successful DNS answer. A fast UDP failure does not cancel TCP, and
/// a blackholed UDP query does not delay the TCP attempt.
async fn race_udp_tcp_dns<U, T>(udp: U, tcp: T) -> Result<Option<IpAddr>, String>
where
    U: std::future::Future<Output = Result<Option<IpAddr>, String>>,
    T: std::future::Future<Output = Result<Option<IpAddr>, String>>,
{
    tokio::pin!(udp);
    tokio::pin!(tcp);
    let mut udp_err: Option<String> = None;
    let mut tcp_err: Option<String> = None;
    loop {
        tokio::select! {
            res = &mut udp, if udp_err.is_none() => {
                match res {
                    Ok(answer) => return Ok(answer),
                    Err(e) => udp_err = Some(e),
                }
            }
            res = &mut tcp, if tcp_err.is_none() => {
                match res {
                    Ok(answer) => return Ok(answer),
                    Err(e) => tcp_err = Some(e),
                }
            }
        }
        if udp_err.is_some() && tcp_err.is_some() {
            return Err(format!(
                "udp: {}; tcp: {}",
                udp_err.unwrap_or_default(),
                tcp_err.unwrap_or_default()
            ));
        }
    }
}

/// Whether tunnel `gen` still carries traffic: recently proven, or answers a
/// fresh DNS query (UDP or TCP) through the mixnet.
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
        let probe = tokio::time::timeout(
            DNS_TIMEOUT,
            race_udp_tcp_dns(
                dns_query(tunnel, HEALTH_PROBE_HOST, *resolver, RecordType::A),
                dns_query_tcp(tunnel, HEALTH_PROBE_HOST, *resolver, RecordType::A),
            ),
        );
        if matches!(probe.await, Ok(Ok(_))) {
            mark_healthy(gen);
            return true;
        }
    }
    false
}

/// Resolve `host` through the mixnet unless it is an IP literal or freshly
/// cached. Queries `A` first, then falls back cleanly to `AAAA` for IPv6-only
/// hosts. `Ok(None)` means no `A`/`AAAA` record exists (`Err` is a transport/DNS error).
async fn resolve_over_nym(tunnel: &Tunnel, host: &str) -> Result<Option<IpAddr>, String> {
    let trimmed = host.trim().trim_matches(['[', ']']);
    if let Ok(ip) = trimmed.parse::<IpAddr>() {
        return Ok(Some(ip));
    }
    if let Some(ip) = dns_cache_get(host) {
        return Ok(Some(ip));
    }

    match query_resolvers_for_type(tunnel, host, RecordType::A).await {
        Ok(Some(ip)) => {
            dns_cache_put(host, ip);
            return Ok(Some(ip));
        }
        Ok(None) => {}
        Err(a_err) => {
            // Fall back to AAAA before failing hard in case A returned an error or host is IPv6-only.
            if let Ok(Some(ip)) = query_resolvers_for_type(tunnel, host, RecordType::AAAA).await {
                dns_cache_put(host, ip);
                return Ok(Some(ip));
            }
            return Err(a_err);
        }
    }

    match query_resolvers_for_type(tunnel, host, RecordType::AAAA).await {
        Ok(Some(ip)) => {
            dns_cache_put(host, ip);
            Ok(Some(ip))
        }
        Ok(None) => Ok(None),
        Err(e) => {
            // Do not fail hard if A already succeeded with NXDomain/NODATA and AAAA had a resolver hiccup.
            tracing::debug!("mixnet AAAA fallback for {host} failed: {e}");
            Ok(None)
        }
    }
}

async fn query_resolvers_for_type(
    tunnel: &Tunnel,
    host: &str,
    record_type: RecordType,
) -> Result<Option<IpAddr>, String> {
    let mut last_err = String::new();
    // Race UDP and TCP per resolver. One 8s budget covers both transports so a
    // blackholed UDP path cannot burn 3×8s before TCP is tried.
    for (attempt, resolver) in DNS_RESOLVERS.iter().enumerate() {
        let query = tokio::time::timeout(
            DNS_TIMEOUT,
            race_udp_tcp_dns(
                dns_query(tunnel, host, *resolver, record_type),
                dns_query_tcp(tunnel, host, *resolver, record_type),
            ),
        )
        .await
        .map_err(|_| "mixnet DNS lookup timed out".to_string())
        .and_then(|r| r);
        match query {
            Ok(answer) => return Ok(answer),
            Err(e) => {
                tracing::warn!(
                    "mixnet DNS {:?} lookup failed (attempt {attempt}): {}",
                    record_type,
                    crate::sync::redact_sync_error(&e)
                );
                last_err = e;
            }
        }
    }
    Err(last_err)
}

/// One DNS query to `resolver` over a fresh tunnel UDP socket.
async fn dns_query(
    tunnel: &Tunnel,
    host: &str,
    resolver: [u8; 4],
    record_type: RecordType,
) -> Result<Option<IpAddr>, String> {
    let udp = tunnel.udp_socket().await.map_err(|e| e.to_string())?;
    let (txid, bytes) = dns_query_bytes_for(host, record_type)?;
    let resolver_addr = SocketAddr::from((resolver, 53));
    udp.send_to(&bytes, resolver_addr)
        .await
        .map_err(|e| e.to_string())?;
    let mut buf = [0u8; 1500];
    for _ in 0..4 {
        let (len, src) = udp.recv_from(&mut buf).await.map_err(|e| e.to_string())?;
        if src != resolver_addr {
            tracing::warn!(
                "ignoring unexpected DNS UDP responder {src} (expected {resolver_addr})"
            );
            continue;
        }
        let response = Message::from_vec(&buf[..len]).map_err(|e| e.to_string())?;
        return parse_dns_response(&response, txid, true);
    }
    Err(format!(
        "DNS UDP responses did not match expected resolver {resolver_addr}"
    ))
}

/// DNS over TCP (RFC 1035 length prefix) through the mixnet. Used when UDP
/// to the resolver does not return or is truncated.
async fn dns_query_tcp(
    tunnel: &Tunnel,
    host: &str,
    resolver: [u8; 4],
    record_type: RecordType,
) -> Result<Option<IpAddr>, String> {
    let mut tcp = tunnel
        .tcp_connect(SocketAddr::from((resolver, 53)))
        .await
        .map_err(|e| e.to_string())?;
    let (txid, bytes) = dns_query_bytes_for(host, record_type)?;
    if bytes.len() > u16::MAX as usize {
        return Err("DNS query too large".into());
    }
    tcp.write_all(&(bytes.len() as u16).to_be_bytes())
        .await
        .map_err(|e| e.to_string())?;
    tcp.write_all(&bytes).await.map_err(|e| e.to_string())?;
    let mut len_buf = [0u8; 2];
    tcp.read_exact(&mut len_buf)
        .await
        .map_err(|e| e.to_string())?;
    let n = u16::from_be_bytes(len_buf) as usize;
    if n == 0 || n > 4096 {
        return Err(format!("DNS TCP response length {n}"));
    }
    let mut buf = vec![0u8; n];
    tcp.read_exact(&mut buf).await.map_err(|e| e.to_string())?;
    let response = Message::from_vec(&buf).map_err(|e| e.to_string())?;
    parse_dns_response(&response, txid, false)
}

fn dns_query_bytes_for(host: &str, record_type: RecordType) -> Result<(u16, Vec<u8>), String> {
    let mut query = Message::query();
    let txid: u16 = rand::random();
    query.metadata.id = txid;
    query.metadata.recursion_desired = true;
    let name = Name::from_ascii(host).map_err(|e| e.to_string())?;
    query.add_query(Query::query(name, record_type));
    let bytes = query.to_vec().map_err(|e| e.to_string())?;
    Ok((txid, bytes))
}

#[cfg(test)]
fn dns_query_bytes(host: &str) -> Result<Vec<u8>, String> {
    dns_query_bytes_for(host, RecordType::A).map(|(_, bytes)| bytes)
}

fn parse_dns_response(
    response: &Message,
    expected_txid: u16,
    reject_truncated: bool,
) -> Result<Option<IpAddr>, String> {
    if response.metadata.id != expected_txid {
        return Err(format!(
            "DNS TXID mismatch: expected {expected_txid}, got {}",
            response.metadata.id
        ));
    }
    if reject_truncated && response.metadata.truncation {
        return Err("DNS UDP response truncated".into());
    }
    match response.metadata.response_code {
        ResponseCode::NoError => Ok(first_ip_record(response)),
        ResponseCode::NXDomain => Ok(None),
        other => Err(format!("DNS response error: {other:?}")),
    }
}

fn first_ip_record(msg: &Message) -> Option<IpAddr> {
    msg.answers
        .iter()
        .find_map(|record| match &record.data {
            RData::A(a) => Some(IpAddr::V4(a.0)),
            _ => None,
        })
        .or_else(|| {
            msg.answers.iter().find_map(|record| match &record.data {
                RData::AAAA(aaaa) => Some(IpAddr::V6(aaaa.0)),
                _ => None,
            })
        })
}

#[cfg(test)]
fn first_a_record(msg: &Message) -> Option<IpAddr> {
    first_ip_record(msg)
}

fn dns_cache_get(host: &str) -> Option<IpAddr> {
    let key = host.trim().to_ascii_lowercase();
    let cache = DNS_CACHE.lock().ok()?;
    let (ip, at) = cache.as_ref()?.map.get(&key)?;
    (at.elapsed() < DNS_TTL).then_some(*ip)
}

fn dns_cache_put(host: &str, ip: IpAddr) {
    let key = host.trim().to_ascii_lowercase();
    if let Ok(mut guard) = DNS_CACHE.lock() {
        let cache = guard.get_or_insert_with(DnsCache::new);
        let now = Instant::now();
        // Prune expired entries first.
        cache.map.retain(|_, (_, at)| at.elapsed() < DNS_TTL);
        cache.order.retain(|k| cache.map.contains_key(k));
        while cache.map.len() >= MAX_DNS_CACHE_ENTRIES && !cache.map.contains_key(&key) {
            if let Some(oldest) = cache.order.pop_front() {
                cache.map.remove(&oldest);
            } else {
                break;
            }
        }
        if !cache.map.contains_key(&key) {
            cache.order.push_back(key.clone());
        }
        cache.map.insert(key, (ip, now));
    }
}

fn clear_dns_cache() {
    if let Ok(mut cache) = DNS_CACHE.lock() {
        *cache = None;
    }
}

async fn accept_socks_greeting(inbound: &mut TcpStream) -> Result<(), String> {
    let mut head = [0u8; 2];
    inbound
        .read_exact(&mut head)
        .await
        .map_err(|e| e.to_string())?;
    if head[0] != 0x05 {
        return Err("not a SOCKS5 greeting".into());
    }
    let mut methods = vec![0u8; head[1] as usize];
    inbound
        .read_exact(&mut methods)
        .await
        .map_err(|e| e.to_string())?;
    let method = crate::transport_manager::socks_method(&methods);
    inbound
        .write_all(&[0x05, method])
        .await
        .map_err(|e| e.to_string())?;
    if method == 0xff {
        return Err("no acceptable SOCKS5 method".into());
    }
    if method == 0x02 {
        let mut ver_ulen = [0u8; 2];
        inbound
            .read_exact(&mut ver_ulen)
            .await
            .map_err(|e| e.to_string())?;
        if ver_ulen[0] != 0x01 {
            return Err("bad SOCKS5 username/password version".into());
        }
        let mut user = vec![0u8; ver_ulen[1] as usize];
        inbound
            .read_exact(&mut user)
            .await
            .map_err(|e| e.to_string())?;
        let mut plen = [0u8; 1];
        inbound
            .read_exact(&mut plen)
            .await
            .map_err(|e| e.to_string())?;
        let mut pass = vec![0u8; plen[0] as usize];
        inbound
            .read_exact(&mut pass)
            .await
            .map_err(|e| e.to_string())?;
        let username = String::from_utf8(user).map_err(|e| e.to_string())?;
        let password = String::from_utf8(pass).map_err(|e| e.to_string())?;
        let ok = crate::transport_manager::socks_password_ok(&username, &password);
        inbound
            .write_all(&[0x01, if ok { 0x00 } else { 0x01 }])
            .await
            .map_err(|e| e.to_string())?;
        if !ok {
            return Err("SOCKS5 username/password rejected".into());
        }
    }
    Ok(())
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
        assert_eq!(dns_cache_get("CACHE-TEST.EXAMPLE"), Some(ip));
        assert_eq!(dns_cache_get("cache-miss.example"), None);
    }

    #[test]
    fn dns_cache_is_bounded() {
        clear_dns_cache();
        for i in 0..(MAX_DNS_CACHE_ENTRIES + 32) {
            let ip = IpAddr::V4(Ipv4Addr::new(10, 0, (i / 256) as u8, (i % 256) as u8));
            dns_cache_put(&format!("host-{i}.example"), ip);
        }
        let len = DNS_CACHE
            .lock()
            .unwrap()
            .as_ref()
            .map_or(0, |c| c.map.len());
        assert_eq!(len, MAX_DNS_CACHE_ENTRIES);
        assert!(dns_cache_get("host-0.example").is_none());
        assert!(dns_cache_get(&format!("host-{}.example", MAX_DNS_CACHE_ENTRIES + 31)).is_some());
        clear_dns_cache();
    }

    #[test]
    fn parse_dns_response_validates_txid_rcode_tc_and_aaaa() {
        use std::net::Ipv6Addr;
        let name = Name::from_str("example.com.").unwrap();
        let mut msg = Message::new(42, MessageType::Response, OpCode::Query);
        msg.add_query(Query::query(name.clone(), RecordType::AAAA));
        msg.add_answer(Record::from_rdata(
            name,
            300,
            RData::AAAA(rdata::AAAA(Ipv6Addr::LOCALHOST)),
        ));

        // TXID mismatch rejected
        assert!(parse_dns_response(&msg, 99, true).is_err());

        // Matching TXID extracts AAAA cleanly
        assert_eq!(
            parse_dns_response(&msg, 42, true).unwrap(),
            Some(IpAddr::V6(Ipv6Addr::LOCALHOST))
        );

        // UDP truncation fails so TCP race can win
        msg.metadata.truncation = true;
        assert!(parse_dns_response(&msg, 42, true).is_err());
        assert!(parse_dns_response(&msg, 42, false).is_ok());
        msg.metadata.truncation = false;

        // SERVFAIL returns Err (failover to next resolver), NXDomain returns Ok(None)
        msg.metadata.response_code = ResponseCode::ServFail;
        assert!(parse_dns_response(&msg, 42, true).is_err());
        msg.metadata.response_code = ResponseCode::NXDomain;
        assert_eq!(parse_dns_response(&msg, 42, true).unwrap(), None);
    }

    #[test]
    fn status_reports_stopped_before_start() {
        assert_eq!(nym_status(), "stopped");
    }

    #[test]
    fn dns_query_bytes_is_a_recursive_a_query() {
        let bytes = dns_query_bytes("lilith0.dark.fi").unwrap();
        let msg = Message::from_vec(&bytes).unwrap();
        assert!(msg.metadata.recursion_desired);
        assert_eq!(msg.queries.len(), 1);
        assert_eq!(msg.queries[0].query_type(), RecordType::A);
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
            let mut s =
                tokio_socks::tcp::Socks5Stream::connect(("127.0.0.1", port), ("example.com", 80))
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

    #[tokio::test]
    async fn socks_rejects_trailing_dot_onion_without_dialling() {
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
        let host = b"abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefab.onion.";
        let mut req = vec![0x05, 0x01, 0x00, 0x03, host.len() as u8];
        req.extend_from_slice(host);
        req.extend_from_slice(&9067u16.to_be_bytes());
        c.write_all(&req).await.unwrap();
        let mut reply = [0u8; 10];
        c.read_exact(&mut reply).await.unwrap();
        assert_eq!(reply[1], 0x04);
        let err = server.await.unwrap().unwrap_err();
        assert!(err.contains("onion"));
        assert!(!err.contains("abcdefabcdef"));
    }
}
