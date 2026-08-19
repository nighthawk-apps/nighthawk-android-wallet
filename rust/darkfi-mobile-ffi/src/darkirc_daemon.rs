//! In-process darkirc daemon for mobile.
//!
//! The darkirc daemon runs as a background thread inside the FFI library.
//! It connects to the DarkFi P2P network and syncs the event graph (DAG).
//! Messages are passed directly to the UI via UniFFI callbacks.

use std::path::PathBuf;
use std::sync::atomic::{AtomicU8, Ordering};
use std::sync::Arc;

use darkfi_serial::{deserialize_async_partial, serialize_async};
use smol::Executor;

use crate::{DarkfiWalletNativeError, DarkircEventCallback};

/// Daemon lifecycle states exposed to Swift/Kotlin via UniFFI.
const STATUS_NOT_RUNNING: u8 = 0;
const STATUS_STARTING: u8 = 1;
const STATUS_RUNNING: u8 = 2;
const STATUS_STOPPING: u8 = 3;
const STATUS_FAILED: u8 = 4;

/// Global daemon state (atomically updated).
static DAEMON_STATUS: AtomicU8 = AtomicU8::new(STATUS_NOT_RUNNING);

/// Fine-grained connection phase for UI (independent of coarse DAEMON_STATUS).
const PHASE_STOPPED: u8 = 0;
const PHASE_STARTING: u8 = 1;
const PHASE_WAITING_FOR_PEERS: u8 = 2;
const PHASE_STATIC_SYNC: u8 = 3;
const PHASE_SYNCING_DAG: u8 = 4;
const PHASE_LOADING_HISTORY: u8 = 5;
const PHASE_CONNECTED: u8 = 6;
const PHASE_STOPPING: u8 = 7;
const PHASE_FAILED: u8 = 8;

static CONNECTION_PHASE: AtomicU8 = AtomicU8::new(PHASE_STOPPED);

fn set_phase(phase: u8) {
    CONNECTION_PHASE.store(phase, Ordering::Relaxed);
}

/// Per-lifecycle stop channel. Each `start_darkirc` installs a fresh
/// `(Sender, Receiver)` pair so a leftover stop token cannot wake the
/// next daemon. The rest of DarkIRC is still a process-wide singleton.
static STOP_CHANNEL: std::sync::LazyLock<
    smol::lock::RwLock<Option<(smol::channel::Sender<()>, smol::channel::Receiver<()>)>>,
> = std::sync::LazyLock::new(|| smol::lock::RwLock::new(None));

/// UI callback registered at `start_darkirc`. Kept globally so
/// `send_chat_message` can self-echo even if `event_pub` delivery races
/// across executors (common mobile failure: Connected but own "hi" missing).
static CALLBACK: std::sync::LazyLock<smol::lock::RwLock<Option<Arc<dyn DarkircEventCallback>>>> =
    std::sync::LazyLock::new(|| smol::lock::RwLock::new(None));

/// Set after static_sync + sync_selected + history replay finish.
static DAG_SYNCED: AtomicU8 = AtomicU8::new(0);

/// Global handles for sending messages
static EVENT_GRAPH: std::sync::LazyLock<
    smol::lock::RwLock<Option<darkfi::event_graph::EventGraphPtr>>,
> = std::sync::LazyLock::new(|| smol::lock::RwLock::new(None));
static P2P: std::sync::LazyLock<smol::lock::RwLock<Option<darkfi::net::P2pPtr>>> =
    std::sync::LazyLock::new(|| smol::lock::RwLock::new(None));

/// On-wire IRC PRIVMSG — imported directly from the upstream `darkirc` crate
/// (lib name `irc2`) so the struct is always byte-for-byte compatible with the
/// live network. The fields are: `version`, `msg_type`, `channel`, `nick`, `msg`.
pub use irc2::Privmsg;

/// Current upstream wire version emitted by `darkirc` clients.
const PRIVMSG_VERSION: u8 = 0;
/// Plaintext/standard message type emitted by `darkirc` clients.
const PRIVMSG_MSG_TYPE: u8 = 0;

// =====================================================================
// DarkIRC consensus parameters (must match bin/darkirc).
// Changing any of these is a hard fork vs the live network.
// =====================================================================

/// Epoch origin for DAG rotation (UTC midnight, 1 March 2025).
const DARKIRC_INITIAL_GENESIS: u64 = 1_740_787_200_000;
/// DAG rotation period, in hours.
const DARKIRC_HOURS_ROTATION: u64 = 1;
/// Genesis payload embedded in genesis events.
const DARKIRC_GENESIS_CONTENTS: &[u8] = b"darkirc-v1";
/// How many rotating DAGs to retain locally (darkirc default).
const DARKIRC_MAX_DAGS: usize = 24;

/// Convenience constructor for mobile send — upstream doesn't export one.
fn new_privmsg(channel: String, nick: String, msg: String) -> Privmsg {
    Privmsg {
        version: PRIVMSG_VERSION,
        msg_type: PRIVMSG_MSG_TYPE,
        channel,
        nick,
        msg,
    }
}

/// Forwards `tracing` events into Android logcat.
///
/// darkfi's `net`/`event_graph` modules log through the `tracing` facade, while
/// `android_logger` only backs the `log` facade. Without this bridge none of
/// the P2P/DAG-sync diagnostics reach logcat, which makes connection failures
/// effectively invisible on-device.
struct LogcatWriter;

impl std::io::Write for LogcatWriter {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        let line = String::from_utf8_lossy(buf);
        let trimmed = line.trim_end();
        if !trimmed.is_empty() {
            log::info!(target: "darkfi-net", "{trimmed}");
        }
        Ok(buf.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl<'a> tracing_subscriber::fmt::MakeWriter<'a> for LogcatWriter {
    type Writer = LogcatWriter;
    fn make_writer(&'a self) -> Self::Writer {
        LogcatWriter
    }
}

static LOGGING_INIT: std::sync::Once = std::sync::Once::new();

fn init_logging() {
    LOGGING_INIT.call_once(|| {
        // `log` backend (rustls/sled use it).
        // Release: WARN+ only to prevent P2P metadata leaking to logcat.
        // Debug: INFO for development diagnostics.
        let (max_level, filter_spec) = if cfg!(debug_assertions) {
            (log::LevelFilter::Debug, "info,rustls=off,sled=off,sled_overlay=off,mio=off,polling=off,async_io=off,want=off")
        } else {
            (log::LevelFilter::Warn, "warn,rustls=off,sled=off,sled_overlay=off,mio=off,polling=off,async_io=off,want=off")
        };
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(max_level)
                .with_tag("darkfi-mobile-ffi")
                .with_filter(
                    android_logger::FilterBuilder::new()
                        .parse(filter_spec)
                        .build(),
                ),
        );

        // `tracing` backend (darkfi net/event_graph).
        // Release: WARN+ only. Debug: INFO for P2P/DAG diagnostics.
        let level = if cfg!(debug_assertions) { "info" } else { "warn" };
        let filter = tracing_subscriber::EnvFilter::new(level);
        let _ = tracing_subscriber::fmt()
            .with_env_filter(filter)
            .with_writer(LogcatWriter)
            .with_ansi(false)
            .with_target(true)
            .without_time()
            .try_init();
    });
}

/// Returns the current daemon status as a string.
pub fn darkirc_status() -> String {
    match DAEMON_STATUS.load(Ordering::Relaxed) {
        STATUS_NOT_RUNNING => "not_running",
        STATUS_STARTING => "starting",
        STATUS_RUNNING => "running",
        STATUS_STOPPING => "stopping",
        STATUS_FAILED => "failed",
        _ => "unknown",
    }
    .to_string()
}

/// Fine-grained connection / DAG-sync phase for UI progress text.
///
/// Values: `stopped` | `starting` | `waiting_for_peers` | `static_sync` |
/// `syncing_dag` | `loading_history` | `connected` | `stopping` | `failed`.
pub fn darkirc_connection_phase() -> String {
    match DAEMON_STATUS.load(Ordering::Relaxed) {
        STATUS_FAILED => return "failed".to_string(),
        STATUS_STOPPING => return "stopping".to_string(),
        STATUS_NOT_RUNNING if CONNECTION_PHASE.load(Ordering::Relaxed) != PHASE_FAILED => {
            return "stopped".to_string();
        }
        _ => {}
    }
    match CONNECTION_PHASE.load(Ordering::Relaxed) {
        PHASE_STARTING => "starting",
        PHASE_WAITING_FOR_PEERS => "waiting_for_peers",
        PHASE_STATIC_SYNC => "static_sync",
        PHASE_SYNCING_DAG => "syncing_dag",
        PHASE_LOADING_HISTORY => "loading_history",
        PHASE_CONNECTED => "connected",
        PHASE_STOPPING => "stopping",
        PHASE_FAILED => "failed",
        _ => "stopped",
    }
    .to_string()
}

/// Start the darkirc daemon on a background thread.
///
/// When `use_tor` is set, all P2P traffic is routed over Tor via the Guardian
/// `tor-android` SOCKS5 proxy listening on `127.0.0.1:tor_socks_port`: the
/// daemon dials the `socks5://…/<onion>:9601` darkirc seeds, so every
/// connection (seed + discovered peers) stays inside Tor. When false the daemon
/// connects over clearnet `tcp+tls` seeds. This mirrors the wallet's
/// `DarkfidP2pTransport.TorViaSocks5` and keeps the "Connected (Tor)" indicator
/// honest — it is only reached after a real onion handshake + DAG sync.
///
/// `tor_socks_port` is ignored when `use_tor` is false. The embedded Tor SOCKS
/// listener must already be reachable (the Kotlin layer waits on it via
/// `TorSocksReadiness`) before this is called with `use_tor = true`.
pub fn start_darkirc(
    datastore_path: String,
    use_tor: bool,
    tor_socks_port: u16,
    callback: Option<Box<dyn DarkircEventCallback>>,
) -> Result<(), DarkfiWalletNativeError> {
    let mut current = DAEMON_STATUS.load(Ordering::SeqCst);
    loop {
        if current == STATUS_RUNNING || current == STATUS_STARTING || current == STATUS_STOPPING {
            return Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "darkirc cannot start, current status: {}",
                darkirc_status()
            )));
        }
        match DAEMON_STATUS.compare_exchange_weak(
            current,
            STATUS_STARTING,
            Ordering::SeqCst,
            Ordering::SeqCst,
        ) {
            Ok(_) => break,
            Err(x) => current = x,
        }
    }
    init_logging();

    let db_path = PathBuf::from(&datastore_path);
    let cb: Option<Arc<dyn DarkircEventCallback>> = callback.map(Arc::from);
    DAG_SYNCED.store(0, Ordering::Relaxed);
    set_phase(PHASE_STARTING);

    // Fresh stop channel for this daemon lifecycle so a leftover token
    // from the previous run cannot wake the new wait loops.
    let (stop_tx, stop_rx) = smol::channel::bounded(1);
    crate::block_on(async {
        *STOP_CHANNEL.write().await = Some((stop_tx, stop_rx.clone()));
    });

    // Register callback before spawning so early sends (once RUNNING) can echo.
    crate::block_on(async {
        *CALLBACK.write().await = cb.clone();
    });

    std::thread::Builder::new()
        .name("darkirc".to_string())
        .spawn({
            let daemon_stop_rx = stop_rx;
            move || {
                // Wrap the entire daemon body in catch_unwind so DAEMON_STATUS
                // is always reset, even on panics. Without this, a panic leaves
                // the status stuck at STARTING/RUNNING and all subsequent
                // start_darkirc calls are permanently rejected.
                let outcome = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                    let ex = Arc::new(Executor::new());
                    let ex_clone = ex.clone();

                    // M4: Use a stop signal so worker threads exit cleanly when the
                    // main daemon finishes, instead of blocking on pending::<()>()
                    // which can deadlock if executor tasks are stuck on I/O.
                    let (worker_stop_tx, worker_stop_rx) = smol::channel::bounded::<()>(1);

                    let result = easy_parallel::Parallel::new()
                        .each(0..4, |_| {
                            let stop = worker_stop_rx.clone();
                            smol::block_on(ex_clone.run(async move {
                                let _ = stop.recv().await;
                            }))
                        })
                        .finish(|| {
                            smol::block_on(async {
                                let result = run_darkirc_daemon(
                                    db_path,
                                    use_tor,
                                    tor_socks_port,
                                    ex.clone(),
                                    cb,
                                    daemon_stop_rx,
                                )
                                .await;
                                // Signal workers to stop
                                drop(worker_stop_tx);
                                result
                            })
                        })
                        .1;

                    match result {
                        Ok(()) => {
                            DAEMON_STATUS.store(STATUS_NOT_RUNNING, Ordering::Relaxed);
                            set_phase(PHASE_STOPPED);
                        }
                        Err(e) => {
                            log::error!("darkirc daemon failed: {e}");
                            DAEMON_STATUS.store(STATUS_FAILED, Ordering::Relaxed);
                            set_phase(PHASE_FAILED);
                        }
                    }
                }));

                if outcome.is_err() {
                    log::error!("darkirc daemon thread panicked — forcing status to FAILED");
                    DAEMON_STATUS.store(STATUS_FAILED, Ordering::Relaxed);
                    set_phase(PHASE_FAILED);
                }

                // Always clean up global state, panic or not.
                DAG_SYNCED.store(0, Ordering::Relaxed);
                if DAEMON_STATUS.load(Ordering::Relaxed) != STATUS_FAILED {
                    set_phase(PHASE_STOPPED);
                }
                smol::block_on(async {
                    *CALLBACK.write().await = None;
                    *STOP_CHANNEL.write().await = None;
                });
            }
        })
        .map_err(|e| {
            DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "failed to spawn darkirc thread: {e}"
            ))
        })?;

    Ok(())
}

/// Stop the darkirc daemon gracefully.
pub fn stop_darkirc() -> Result<(), DarkfiWalletNativeError> {
    let mut current = DAEMON_STATUS.load(Ordering::SeqCst);
    loop {
        if current == STATUS_NOT_RUNNING || current == STATUS_STOPPING {
            return Ok(());
        }
        match DAEMON_STATUS.compare_exchange_weak(
            current,
            STATUS_STOPPING,
            Ordering::SeqCst,
            Ordering::SeqCst,
        ) {
            Ok(_) => break,
            Err(x) => current = x,
        }
    }

    set_phase(PHASE_STOPPING);
    crate::block_on(async {
        if let Some((ref tx, _)) = *STOP_CHANNEL.read().await {
            let _ = tx.try_send(());
        }
    });
    Ok(())
}

/// Send a chat message natively
pub fn send_chat_message(
    channel: String,
    nick: String,
    message: String,
) -> Result<(), DarkfiWalletNativeError> {
    let current = DAEMON_STATUS.load(Ordering::Relaxed);
    if current != STATUS_RUNNING {
        return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
            "darkirc daemon is not running".to_string(),
        ));
    }
    if DAG_SYNCED.load(Ordering::Relaxed) != 1 {
        return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
            "darkirc DAG is not synced yet; wait until connected".to_string(),
        ));
    }

    crate::block_on(async move {
        let eg_lock = EVENT_GRAPH.read().await;
        let p2p_lock = P2P.read().await;

        if let (Some(eg), Some(p2p)) = (&*eg_lock, &*p2p_lock) {
            let msg = new_privmsg(channel.clone(), nick.clone(), message.clone());

            let event = match darkfi::event_graph::Event::new(serialize_async(&msg).await, eg).await
            {
                Ok(ev) => ev,
                Err(e) => {
                    log::error!("Failed building Event: {}", e);
                    return Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                        "Event::new failed: {}",
                        e
                    )));
                }
            };
            let event_id = event.id().to_hex().to_string();
            let event_ts = event.header.timestamp;

            // The DAG is keyed by the current genesis timestamp, NOT the channel.
            let dag_name = eg.current_genesis.read().await.header.timestamp.to_string();

            // Public insert path used by upstream darkirc (`insert_signal_with_blob`):
            // header insert + optional RLN blob verify + body commit.
            // `dag_insert` is crate-private; with RLN disabled the blob is empty.
            let blob: Vec<u8> = Vec::new();
            let inserted = match eg.insert_signal_with_blob(&event, &blob, &dag_name).await {
                Ok(ids) => ids,
                Err(e) => {
                    log::error!("Failed inserting signal event to DAG: {}", e);
                    return Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                        "insert_signal_with_blob failed: {}",
                        e
                    )));
                }
            };

            // Empty insert = silent skip (missing header / NULL parents / dup).
            // Treat as failure so the UI does not clear the compose box thinking
            // the send succeeded while nothing was relayed.
            if inserted.is_empty() {
                log::error!(
                    "insert_signal_with_blob returned no ids for channel={} nick={} (event skipped)",
                    channel,
                    nick
                );
                return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
                    "insert_signal_with_blob skipped event (empty result)".to_string(),
                ));
            }

            if let Err(e) = p2p
                .broadcast(&darkfi::event_graph::proto::EventPut(event, blob))
                .await
            {
                log::error!("Event broadcast was not admitted: {}", e);
                return Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                    "EventPut broadcast failed: {}",
                    e
                )));
            }

            // Direct self-echo to the UI callback. `event_pub` also notifies the
            // relay task, but that path can miss on mobile when notify races
            // across the send `block_on` executor and the daemon executor —
            // leaving Connected UI with no visible message. Dedup is by event id.
            if let Some(cb) = CALLBACK.read().await.as_ref() {
                cb.on_message(
                    event_id.clone(),
                    channel.clone(),
                    nick.clone(),
                    message,
                    event_ts,
                );
            }

            log::info!(
                "send_chat_message ok channel={} nick={} event={}",
                channel,
                nick,
                event_id
            );
            Ok(())
        } else {
            Err(DarkfiWalletNativeError::NativeDrkUnavailable(
                "EventGraph or P2P not initialized".to_string(),
            ))
        }
    })
}

/// Upstream darkirc Tor onion seeds (`bin/darkirc/darkirc_config.toml` at tip
/// 57397a9e0, `host:port`), dialled through a local SOCKS5 proxy.
const DARKIRC_ONION_SEEDS: [&str; 2] = [
    "wgxxaifz5gv4iggcflyl67lgmsihffs6bbwobqah4np52t3y3olrnpid.onion:9601",
    "inx5s3pdzddvgb5ii3oydutmbvw6fvor3oqu65wtxl3pyevtvrdn4had.onion:9601",
];

/// Builds the darkirc seed URLs that dial the onion seeds through a local
/// SOCKS5 proxy on `127.0.0.1:tor_socks_port` (Guardian tor-android on Android).
///
/// The resulting `socks5://127.0.0.1:<port>/<onion>:9601` form is exactly what
/// darkfi's `net::transport::socks5` dialer expects (proxy in the authority,
/// destination in the path).
fn tor_socks5_seeds(tor_socks_port: u16) -> Vec<url::Url> {
    DARKIRC_ONION_SEEDS
        .iter()
        .map(|dest| {
            url::Url::parse(&format!("socks5://127.0.0.1:{tor_socks_port}/{dest}")).unwrap()
        })
        .collect()
}

/// The actual darkirc daemon loop. Runs until stopped or error.
async fn run_darkirc_daemon(
    datastore_path: PathBuf,
    use_tor: bool,
    tor_socks_port: u16,
    ex: Arc<Executor<'static>>,
    callback: Option<Arc<dyn DarkircEventCallback>>,
    stop_rx: smol::channel::Receiver<()>,
) -> Result<(), String> {
    use darkfi::{
        event_graph::{proto::ProtocolEventGraph, EventGraph, EventGraphConfig},
        net::{
            session::SESSION_DEFAULT,
            settings::{NetworkProfile, Settings},
            P2p,
        },
    };
    use sled_overlay::sled;
    use url::Url;

    // Create datastore (with retry for rapid restart lock release)
    std::fs::create_dir_all(&datastore_path).map_err(|e| format!("create datastore: {e}"))?;
    let mut sled_db_result = sled::open(&datastore_path);
    if sled_db_result.is_err() {
        for _ in 0..5 {
            smol::Timer::after(std::time::Duration::from_millis(200)).await;
            sled_db_result = sled::open(&datastore_path);
            if sled_db_result.is_ok() {
                break;
            }
        }
    }
    let sled_db = sled_db_result.map_err(|e| format!("open sled: {e}"))?;

    // Seeds and the outbound transport profile are chosen by `use_tor`. Both
    // sets come straight from upstream `bin/darkirc/darkirc_config.toml` at
    // tip 57397a9e0 (network moved from :25551/:25552 to :9600/:9601 with new
    // onion addresses in July 2026):
    //   - clearnet: the `tcp+tls` lilith seeds on :9600.
    //   - tor: the `socks5://127.0.0.1:<port>/<onion>:9601` seeds, dialled
    //     through the Guardian `tor-android` SOCKS5 proxy (no in-process arti
    //     on Android; the `p2p-socks5` darkfi feature has no extra deps).
    // Seeds are used for peer discovery (fetch the address book, then
    // disconnect); real outbound connections are then opened to discovered
    // peers over the same active profile, so with the `socks5` profile every
    // connection stays inside Tor.
    let (profile_name, seeds): (&str, Vec<Url>) = if use_tor {
        ("socks5", tor_socks5_seeds(tor_socks_port))
    } else {
        (
            "tcp+tls",
            vec![
                Url::parse("tcp+tls://lilith0.dark.fi:9600").unwrap(),
                Url::parse("tcp+tls://lilith1.dark.fi:9600").unwrap(),
            ],
        )
    };

    let mut p2p_settings = Settings {
        app_name: "darkirc".to_string(),
        app_version: semver::Version::parse("0.5.1").unwrap(),
        // The live darkirc network now uses the per-network magic bytes from
        // `darkirc_config.toml` ([251, 229, 199, 181]). Verified 2026-07-22:
        // seeds on lilith0/1.dark.fi:9600 complete the handshake and full
        // 24-DAG evgr2 sync with these bytes (the pre-Dec-2025 network used
        // the darkfi DEFAULT magic bytes on :25551, which no longer works).
        magic_bytes: darkfi::net::settings::MagicBytes([251, 229, 199, 181]),
        seeds,
        peers: vec![],
        outbound_connections: 5,
        inbound_connections: 2,
        // We are a leaf client connecting to public seeds. Strict banning makes
        // a single odd frame from a seed permanently blacklist it (and the
        // blacklist is sticky for the process), which starved us of the only
        // reachable seed. Relaxed matches how seed-facing nodes behave.
        ban_policy: darkfi::net::settings::BanPolicy::Relaxed,
        ..Settings::default()
    };

    // Tor circuits are slow to build, so use the longer upstream tor timeouts
    // for the SOCKS profile; clearnet keeps the snappier values.
    let mut profile = if use_tor {
        NetworkProfile::tor_default()
    } else {
        NetworkProfile {
            outbound_connect_timeout: 40,
            channel_handshake_timeout: 30,
            ..Default::default()
        }
    };
    if use_tor {
        // Onion handshakes (circuit build + version exchange) can exceed the
        // 65s tor default on mobile networks; give them headroom.
        profile.outbound_connect_timeout = 90;
        profile.channel_handshake_timeout = 75;
    }
    p2p_settings
        .profiles
        .insert(profile_name.to_string(), profile);
    p2p_settings.active_profiles = vec![profile_name.to_string()];

    // Tor path: peers advertise `tor://…onion` addresses. Without mixing,
    // those hosts are darklisted (socks5 != tor) and `is_connected()` never
    // becomes true, so DAG sync never starts. Enable transport mixing through
    // the local SOCKS5 proxy (matches upstream darkirc socks5+tor mixing).
    if use_tor {
        p2p_settings.mixed_profiles = vec!["tor".to_string()];
        p2p_settings.tor_socks5_proxy = Some(
            Url::parse(&format!("socks5://127.0.0.1:{tor_socks_port}"))
                .map_err(|e| format!("tor socks5 proxy url: {e}"))?,
        );
    }

    let p2p = P2p::new(p2p_settings, ex.clone())
        .await
        .map_err(|e| format!("P2P init: {e}"))?;

    let replay_path = datastore_path.join("replay");
    std::fs::create_dir_all(&replay_path).map_err(|e| format!("create replay dir: {e}"))?;

    // Match live darkirc consensus params; RLN stays off on mobile (no
    // proving keys / identity). Sync mode is chosen per-call below.
    let eg_config = EventGraphConfig {
        initial_genesis: DARKIRC_INITIAL_GENESIS,
        hours_rotation: DARKIRC_HOURS_ROTATION,
        genesis_contents: DARKIRC_GENESIS_CONTENTS.to_vec(),
        rln_enabled: false,
        pregenerated_identity_commitments: Vec::new(),
        max_dags: Some(DARKIRC_MAX_DAGS),
    };

    let event_graph = EventGraph::new(
        p2p.clone(),
        sled_db.clone(),
        replay_path.clone(),
        false, // replay_mode
        eg_config,
        ex.clone(),
    )
    .await
    .map_err(|e| format!("EventGraph init: {e}"))?;

    let prune_task = event_graph.prune_task.get().unwrap();

    let event_graph_ = Arc::clone(&event_graph);
    let registry = p2p.protocol_registry();
    registry
        .register(SESSION_DEFAULT, move |channel, _| {
            let eg = event_graph_.clone();
            async move { ProtocolEventGraph::init(eg, channel).await.unwrap() }
        })
        .await;

    // Save globals
    *EVENT_GRAPH.write().await = Some(event_graph.clone());
    *P2P.write().await = Some(p2p.clone());

    p2p.clone()
        .start()
        .await
        .map_err(|e| format!("P2P start: {e}"))?;

    DAEMON_STATUS.store(STATUS_RUNNING, Ordering::Relaxed);
    set_phase(PHASE_WAITING_FOR_PEERS);
    log::info!(
        "darkirc daemon started over {} transport, syncing DAG...",
        if use_tor { "tor (socks5)" } else { "tcp+tls" }
    );

    let dags_count = DARKIRC_MAX_DAGS;
    let comms_timeout = 5u64;
    const DAG_REFRESH_SECS: u64 = 3600;

    // Relay events — track relayed IDs so the history replay can skip them.
    let relayed_ids: Arc<smol::lock::Mutex<std::collections::HashSet<String>>> =
        Arc::new(smol::lock::Mutex::new(std::collections::HashSet::new()));
    let ev_sub = event_graph.event_pub.clone().subscribe().await;
    let cb_clone = callback.clone();
    let relayed_ids_clone = Arc::clone(&relayed_ids);
    let relay_task = ex.spawn(async move {
        loop {
            let ev = ev_sub.receive().await;
            if let Some(cb) = &cb_clone {
                if let Ok((privmsg, _)) = deserialize_async_partial::<Privmsg>(ev.content()).await {
                    let eid = ev.id().to_hex().to_string();
                    relayed_ids_clone.lock().await.insert(eid.clone());
                    cb.on_message(
                        eid,
                        privmsg.channel,
                        privmsg.nick,
                        privmsg.msg,
                        ev.header.timestamp,
                    );
                }
            }
        }
    });

    // Sync while connected; on peer loss, wait briefly and retry so background
    // suspensions that drop sockets can recover without a full daemon restart.
    // Failed sync steps always retry (do not park in the monitor loop).
    loop {
        if DAEMON_STATUS.load(Ordering::Relaxed) == STATUS_STOPPING {
            break;
        }

        if !p2p.is_connected() {
            set_phase(PHASE_WAITING_FOR_PEERS);
            log::info!("darkirc daemon waiting for P2P peers...");
            smol::future::race(
                async {
                    let _ = stop_rx.recv().await;
                },
                async {
                    smol::Timer::after(std::time::Duration::from_secs(comms_timeout)).await;
                },
            )
            .await;
            continue;
        }

        set_phase(PHASE_STATIC_SYNC);
        log::info!("darkirc daemon connected, waiting for static sync...");
        if event_graph.static_sync().await.is_err() {
            log::warn!("darkirc daemon static_sync failed — retrying");
            set_phase(PHASE_WAITING_FOR_PEERS);
            smol::future::race(
                async {
                    let _ = stop_rx.recv().await;
                },
                async {
                    smol::Timer::after(std::time::Duration::from_secs(comms_timeout)).await;
                },
            )
            .await;
            continue;
        }

        set_phase(PHASE_SYNCING_DAG);
        log::info!("darkirc daemon static sync complete. Starting sync_selected...");
        // Full sync (bodies + headers). Header-only clients use
        // `sync_selected_headers` instead (darkirc `--fast-mode`).
        if event_graph.sync_selected(dags_count).await.is_err() {
            log::warn!("darkirc daemon sync_selected failed — retrying");
            set_phase(PHASE_WAITING_FOR_PEERS);
            smol::future::race(
                async {
                    let _ = stop_rx.recv().await;
                },
                async {
                    smol::Timer::after(std::time::Duration::from_secs(comms_timeout)).await;
                },
            )
            .await;
            continue;
        }

        set_phase(PHASE_LOADING_HISTORY);
        log::info!("darkirc daemon sync_selected complete. Fetching historical events...");
        let history = match event_graph.order_events().await {
            Ok(events) => events,
            Err(e) => {
                log::error!("darkirc daemon order_events failed: {e}");
                set_phase(PHASE_WAITING_FOR_PEERS);
                smol::future::race(
                    async {
                        let _ = stop_rx.recv().await;
                    },
                    async {
                        smol::Timer::after(std::time::Duration::from_secs(comms_timeout)).await;
                    },
                )
                .await;
                continue;
            }
        };
        log::info!("darkirc daemon fetched {} historical events", history.len());
        if let Some(cb) = &callback {
            let mut seen = relayed_ids.lock().await;
            for ev in history {
                let eid = ev.id().to_hex().to_string();
                // Skip events already delivered by the live relay task (or a prior replay).
                if !seen.insert(eid.clone()) {
                    continue;
                }
                if let Ok((privmsg, _)) = deserialize_async_partial::<Privmsg>(ev.content()).await {
                    cb.on_message(
                        eid,
                        privmsg.channel,
                        privmsg.nick,
                        privmsg.msg,
                        ev.header.timestamp,
                    );
                }
            }
        }
        DAG_SYNCED.store(1, Ordering::Relaxed);
        set_phase(PHASE_CONNECTED);
        log::info!("darkirc daemon DAG synced and history replayed");

        // Monitor until stop or loss of all peers, then outer loop resyncs.
        // Periodic sync_selected covers missed live relays / DAG rotation.
        let net_sub = p2p.hosts().subscribe_disconnect().await;
        let mut last_dag_refresh = std::time::Instant::now();
        loop {
            if DAEMON_STATUS.load(Ordering::Relaxed) == STATUS_STOPPING {
                break;
            }
            if !p2p.is_connected() {
                log::info!("darkirc daemon disconnected (0 peers), preparing resync...");
                DAG_SYNCED.store(0, Ordering::Relaxed);
                set_phase(PHASE_WAITING_FOR_PEERS);
                break;
            }

            let stop_or_disc = smol::future::race(
                async {
                    let _ = net_sub.receive().await;
                    "disconnect"
                },
                smol::future::race(
                    async {
                        let _ = stop_rx.recv().await;
                        "stop"
                    },
                    async {
                        smol::Timer::after(std::time::Duration::from_secs(30)).await;
                        "timer"
                    },
                ),
            )
            .await;

            if stop_or_disc == "stop" || DAEMON_STATUS.load(Ordering::Relaxed) == STATUS_STOPPING {
                break;
            }

            if stop_or_disc == "disconnect" && !p2p.is_connected() {
                log::info!("darkirc daemon detected disconnection, preparing resync...");
                DAG_SYNCED.store(0, Ordering::Relaxed);
                set_phase(PHASE_WAITING_FOR_PEERS);
                break;
            }

            if stop_or_disc == "timer"
                && last_dag_refresh.elapsed() >= std::time::Duration::from_secs(DAG_REFRESH_SECS)
            {
                match event_graph.sync_selected(dags_count).await {
                    Ok(()) => {
                        last_dag_refresh = std::time::Instant::now();
                        log::info!("darkirc daemon periodic sync_selected ok");
                    }
                    Err(e) => {
                        log::warn!("darkirc daemon periodic sync_selected failed: {e}");
                    }
                }
            }
        }

        if DAEMON_STATUS.load(Ordering::Relaxed) == STATUS_STOPPING {
            break;
        }

        if !p2p.is_connected() {
            smol::Timer::after(std::time::Duration::from_secs(comms_timeout)).await;
        }
    }

    p2p.stop().await;
    prune_task.stop().await;
    relay_task.cancel().await;

    // Clear globals
    *EVENT_GRAPH.write().await = None;
    *P2P.write().await = None;

    let _ = sled_db.flush_async().await;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use darkfi_serial::serialize_async;

    /// A public-channel PRIVMSG produced by upstream `darkirc` is serialized as
    /// `version | msg_type | channel | nick | msg`. This buffer is hand-encoded
    /// in that exact layout (u8 + VarInt-prefixed UTF-8 strings) so the test
    /// fails if the struct field order/count ever drifts from upstream.
    fn upstream_public_privmsg_bytes() -> Vec<u8> {
        let mut bytes = vec![0x00u8, 0x00u8]; // version = 0, msg_type = 0
        for s in ["#dev", "alice", "hello world"] {
            bytes.push(s.len() as u8); // VarInt length (single byte for <0xFD)
            bytes.extend_from_slice(s.as_bytes());
        }
        bytes
    }

    #[test]
    fn deserializes_upstream_public_channel_wire_format() {
        let bytes = upstream_public_privmsg_bytes();
        let (msg, consumed): (Privmsg, usize) =
            smol::block_on(deserialize_async_partial(&bytes)).expect("deserialize");

        assert_eq!(msg.version, 0);
        assert_eq!(msg.msg_type, 0);
        assert_eq!(msg.channel, "#dev");
        assert_eq!(msg.nick, "alice");
        assert_eq!(msg.msg, "hello world");
        assert_eq!(consumed, bytes.len());
    }

    #[test]
    fn serialized_output_matches_upstream_layout() {
        let msg = new_privmsg("#dev".into(), "alice".into(), "hello world".into());
        let encoded = smol::block_on(serialize_async(&msg));
        // The first two bytes MUST be the version/msg_type prefix.
        assert_eq!(&encoded[..2], &[0x00, 0x00]);
        assert_eq!(encoded, upstream_public_privmsg_bytes());
    }

    #[test]
    fn round_trips_through_serialize_deserialize() {
        let original = new_privmsg("#math".into(), "bob".into(), "2+2=4".into());
        let encoded = smol::block_on(serialize_async(&original));
        let (decoded, _): (Privmsg, usize) =
            smol::block_on(deserialize_async_partial(&encoded)).expect("deserialize");
        assert_eq!(decoded.version, original.version);
        assert_eq!(decoded.msg_type, original.msg_type);
        assert_eq!(decoded.channel, original.channel);
        assert_eq!(decoded.nick, original.nick);
        assert_eq!(decoded.msg, original.msg);
    }

    #[test]
    fn tor_socks5_seeds_use_proxy_authority_and_onion_path() {
        let seeds = tor_socks5_seeds(9050);
        assert_eq!(seeds.len(), DARKIRC_ONION_SEEDS.len());

        for (seed, onion) in seeds.iter().zip(DARKIRC_ONION_SEEDS.iter()) {
            // Scheme + proxy authority must point at the local Tor SOCKS proxy
            // so darkfi's socks5 dialer connects through Tor, not clearnet.
            assert_eq!(seed.scheme(), "socks5");
            assert_eq!(seed.host_str(), Some("127.0.0.1"));
            assert_eq!(seed.port(), Some(9050));
            // The onion destination must live in the path (proxy semantics).
            assert_eq!(seed.path(), format!("/{onion}"));
            assert!(seed.path().ends_with(":9601"));
        }
    }

    #[test]
    fn tor_socks5_seeds_honor_custom_proxy_port() {
        let seeds = tor_socks5_seeds(9150);
        assert!(seeds.iter().all(|s| s.port() == Some(9150)));
    }
}
