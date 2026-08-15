//! In-process Arti Tor integration for iOS.
//!
//! Runs a real, in-process Arti SOCKS5 proxy on localhost. Outbound
//! connections accepted on the SOCKS port are dialled through a bootstrapped
//! [`arti_client::TorClient`] (the same arti-client build the vendored darkfi
//! `net` layer links), giving Tor-routed connectivity for wallet RPC and the
//! DarkIRC P2P transport without an external Tor app.
//!
//! On Android, Guardian tor-android is used (bundled native binary).
//!
//! NOTE: status is derived from the *actual* bootstrap result — it never
//! reports `Connected` unless the Tor client genuinely bootstrapped. A failed
//! bootstrap surfaces as `Failed`, so the UI cannot misrepresent Tor coverage.

use std::sync::{
    atomic::{AtomicU8, Ordering},
    Arc,
};

use arti_client::{TorClient, TorClientConfig};
use futures::{AsyncReadExt, AsyncWriteExt};
use smol::net::{TcpListener, TcpStream};

/// Proxy lifecycle, exposed as a single atomic for cheap status polling.
const ARTI_STOPPED: u8 = 0;
const ARTI_BOOTSTRAPPING: u8 = 1;
const ARTI_CONNECTED: u8 = 2;
const ARTI_FAILED: u8 = 3;

static ARTI_STATE: AtomicU8 = AtomicU8::new(ARTI_STOPPED);

/// Set when a stop has been requested so the accept loop can wind down.
static ARTI_STOP_REQUESTED: std::sync::atomic::AtomicBool =
    std::sync::atomic::AtomicBool::new(false);

/// Start the in-process Arti SOCKS proxy on the given port.
///
/// Returns `Ok(true)` when a new proxy bootstrap was kicked off, `Ok(false)`
/// when one is already running/bootstrapping. The actual bootstrap proceeds on
/// a background thread; poll [`is_arti_running`] / the FFI status to observe
/// real progress.
pub fn start_arti_proxy(socks_port: u16) -> Result<bool, crate::DarkfiWalletNativeError> {
    // rustls 0.23 requires an explicit process-level CryptoProvider when both
    // `ring` and `aws-lc-rs` may be pulled in transitively (Arti + tonic).
    let _ = rustls::crypto::ring::default_provider().install_default();

    // Only one proxy at a time.
    if ARTI_STATE
        .compare_exchange(
            ARTI_STOPPED,
            ARTI_BOOTSTRAPPING,
            Ordering::SeqCst,
            Ordering::SeqCst,
        )
        .is_err()
    {
        // Already bootstrapping/connected (or previously failed and not reset).
        let cur = ARTI_STATE.load(Ordering::SeqCst);
        if cur == ARTI_FAILED {
            // Allow a retry after a prior failure.
            ARTI_STATE.store(ARTI_BOOTSTRAPPING, Ordering::SeqCst);
        } else {
            return Ok(false);
        }
    }

    ARTI_STOP_REQUESTED.store(false, Ordering::SeqCst);

    std::thread::Builder::new()
        .name("arti-proxy".into())
        .spawn(move || {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                smol::block_on(run_socks_proxy(socks_port))
            }));
            match result {
                Ok(Ok(())) => {
                    ARTI_STATE.store(ARTI_STOPPED, Ordering::SeqCst);
                }
                Ok(Err(e)) => {
                    tracing::error!("arti proxy exited with error: {e}");
                    ARTI_STATE.store(ARTI_FAILED, Ordering::SeqCst);
                }
                Err(_) => {
                    tracing::error!("arti proxy thread panicked");
                    ARTI_STATE.store(ARTI_FAILED, Ordering::SeqCst);
                }
            }
        })
        .map_err(|e| {
            ARTI_STATE.store(ARTI_FAILED, Ordering::SeqCst);
            crate::DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "failed to spawn arti thread: {e}"
            ))
        })?;

    Ok(true)
}

/// Request the Arti SOCKS proxy to stop. The accept loop exits on its next
/// iteration and the background thread resets the state to `Stopped`.
pub fn stop_arti_proxy() {
    ARTI_STOP_REQUESTED.store(true, Ordering::SeqCst);
}

/// Whether the proxy has *genuinely* bootstrapped and is accepting connections.
pub fn is_arti_running() -> bool {
    ARTI_STATE.load(Ordering::SeqCst) == ARTI_CONNECTED
}

/// Block until Arti has bootstrapped (or [timeout] elapses / bootstrap failed).
///
/// Callers that install the process-wide SOCKS route must wait here before the
/// first remote dial, otherwise lightwalletd / darkirc hit a listening-but-idle
/// socket and fail.
pub fn wait_until_running(
    timeout: std::time::Duration,
) -> Result<(), crate::DarkfiWalletNativeError> {
    let deadline = std::time::Instant::now() + timeout;
    loop {
        match ARTI_STATE.load(Ordering::SeqCst) {
            ARTI_CONNECTED => return Ok(()),
            ARTI_FAILED => {
                return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                    "Arti Tor bootstrap failed".into(),
                ));
            }
            ARTI_STOPPED => {
                // start_arti_proxy not called, or already stopped.
                return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                    "Arti Tor proxy is not running".into(),
                ));
            }
            _ => {
                if std::time::Instant::now() >= deadline {
                    return Err(crate::DarkfiWalletNativeError::NativeDrkUnavailable(
                        "Timed out waiting for Arti Tor bootstrap".into(),
                    ));
                }
                std::thread::sleep(std::time::Duration::from_millis(250));
            }
        }
    }
}

/// Bootstrap the Tor client and serve SOCKS5 CONNECT requests until stopped.
async fn run_socks_proxy(socks_port: u16) -> Result<(), String> {
    // Bind the SOCKS port *before* Tor bootstrap. Bootstrap can take 10–60s on
    // first launch; if we only listen afterwards, Android readiness probes
    // (30s) time out and darkirc/lightwalletd fail closed with "no SOCKS".
    // A listening socket accepts TCP probes while we bootstrap; CONNECT
    // handling starts only once the client is ready below.
    let listener = TcpListener::bind(("127.0.0.1", socks_port))
        .await
        .map_err(|e| format!("SOCKS bind on 127.0.0.1:{socks_port} failed: {e}"))?;
    tracing::info!("arti SOCKS bound on 127.0.0.1:{socks_port}; bootstrapping Tor client...");

    let config = TorClientConfig::default();
    // Arti ≥0.45 returns Arc<TorClient<_>>; isolated_client() also yields Arc.
    let tor_client: Arc<TorClient<tor_rtcompat::PreferredRuntime>> =
        TorClient::create_bootstrapped(config)
            .await
            .map_err(|e| format!("Tor bootstrap failed: {e}"))?;

    ARTI_STATE.store(ARTI_CONNECTED, Ordering::SeqCst);
    tracing::info!("arti SOCKS proxy bootstrapped and ready on 127.0.0.1:{socks_port}");

    loop {
        if ARTI_STOP_REQUESTED.load(Ordering::SeqCst) {
            break;
        }

        // Bounded accept so we periodically re-check the stop flag.
        let accept = listener.accept();
        let timeout = smol::Timer::after(std::time::Duration::from_secs(1));
        let stream = match smol::future::race(async { Some(accept.await) }, async {
            timeout.await;
            None
        })
        .await
        {
            Some(Ok((stream, _addr))) => stream,
            Some(Err(e)) => {
                tracing::warn!("arti SOCKS accept error: {e}");
                continue;
            }
            None => continue,
        };

        let client = tor_client.isolated_client();
        smol::spawn(async move {
            if let Err(e) = handle_socks_conn(stream, client).await {
                tracing::debug!("arti SOCKS connection closed: {e}");
            }
        })
        .detach();
    }

    Ok(())
}

/// Minimal SOCKS5 CONNECT handler: negotiates "no authentication", reads the
/// CONNECT target, dials it through Tor, and pipes bytes both ways.
async fn handle_socks_conn(
    mut inbound: TcpStream,
    tor_client: Arc<TorClient<tor_rtcompat::PreferredRuntime>>,
) -> Result<(), String> {
    // --- Greeting: VER, NMETHODS, METHODS... ---
    let mut head = [0u8; 2];
    inbound
        .read_exact(&mut head)
        .await
        .map_err(|e| e.to_string())?;
    if head[0] != 0x05 {
        return Err("not a SOCKS5 greeting".into());
    }
    let nmethods = head[1] as usize;
    let mut methods = vec![0u8; nmethods];
    inbound
        .read_exact(&mut methods)
        .await
        .map_err(|e| e.to_string())?;
    // Reply: VER=5, METHOD=0 (no authentication).
    inbound
        .write_all(&[0x05, 0x00])
        .await
        .map_err(|e| e.to_string())?;

    // --- Request: VER, CMD, RSV, ATYP, DST.ADDR, DST.PORT ---
    let mut req = [0u8; 4];
    inbound
        .read_exact(&mut req)
        .await
        .map_err(|e| e.to_string())?;
    if req[0] != 0x05 {
        return Err("bad SOCKS5 request version".into());
    }
    if req[1] != 0x01 {
        // Only CONNECT is supported; reply "command not supported".
        let _ = inbound
            .write_all(&[0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0])
            .await;
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
            let _ = inbound
                .write_all(&[0x05, 0x08, 0x00, 0x01, 0, 0, 0, 0, 0, 0])
                .await;
            return Err(format!("unsupported SOCKS atyp {other}"));
        }
    };

    let mut port_bytes = [0u8; 2];
    inbound
        .read_exact(&mut port_bytes)
        .await
        .map_err(|e| e.to_string())?;
    let port = u16::from_be_bytes(port_bytes);

    // --- Dial the target through Tor ---
    let tor_stream = match tor_client.connect((host.as_str(), port)).await {
        Ok(s) => s,
        Err(e) => {
            // Reply "host unreachable".
            let _ = inbound
                .write_all(&[0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0])
                .await;
            return Err(format!("Tor connect to {host}:{port} failed: {e}"));
        }
    };

    // Success reply with a dummy bound address (0.0.0.0:0).
    inbound
        .write_all(&[0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0])
        .await
        .map_err(|e| e.to_string())?;

    // --- Pipe bytes both ways until either side closes ---
    let (mut tor_read, mut tor_write) = tor_stream.split();
    let (mut in_read, mut in_write) = inbound.split();

    let client_to_tor = async {
        let _ = futures::io::copy(&mut in_read, &mut tor_write).await;
        let _ = tor_write.close().await;
    };
    let tor_to_client = async {
        let _ = futures::io::copy(&mut tor_read, &mut in_write).await;
        let _ = in_write.close().await;
    };

    futures::future::join(client_to_tor, tor_to_client).await;
    Ok(())
}
