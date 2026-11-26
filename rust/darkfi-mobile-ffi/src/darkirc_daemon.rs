//! In-process darkirc daemon for mobile.
//!
//! The darkirc daemon runs as a background thread inside the FFI library.
//! It connects to the DarkFi P2P network and syncs the event graph (DAG).
//! Messages are passed directly to the UI via UniFFI callbacks.

use std::collections::{HashSet, VecDeque};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, AtomicU8, Ordering};
use std::sync::{Arc, Mutex};

use darkfi_serial::{deserialize_async, deserialize_async_partial, serialize_async};
use smol::Executor;
use zeroize::{Zeroize, Zeroizing};

use crate::{DarkfiWalletNativeError, DarkircEventCallback};

const MAX_RELAYED_IDS: usize = 10_000;

struct BoundedIdSet {
    set: HashSet<String>,
    order: VecDeque<String>,
    cap: usize,
}

impl BoundedIdSet {
    fn new(cap: usize) -> Self {
        Self {
            set: HashSet::with_capacity(cap.min(1024)),
            order: VecDeque::with_capacity(cap.min(1024)),
            cap: cap.max(1),
        }
    }

    fn insert(&mut self, id: String) -> bool {
        if self.set.contains(&id) {
            return false;
        }
        while self.set.len() >= self.cap {
            if let Some(oldest) = self.order.pop_front() {
                self.set.remove(&oldest);
            } else {
                break;
            }
        }
        self.order.push_back(id.clone());
        self.set.insert(id)
    }
}

fn dispatch_darkirc_message(
    cb: &dyn DarkircEventCallback,
    event_id: String,
    channel: String,
    nick: String,
    message: String,
    timestamp: u64,
    is_outgoing: bool,
) {
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        cb.on_message(event_id, channel, nick, message, timestamp, is_outgoing);
    }));
}

/// Deliver DAG history to the UI, skipping ids already sent by the live relay or
/// an earlier replay. Undecryptable foreign DMs are dropped cleanly.
async fn replay_history(
    history: Vec<darkfi::event_graph::Event>,
    callback: &Option<Arc<dyn DarkircEventCallback>>,
    seen: &smol::lock::Mutex<BoundedIdSet>,
) {
    let Some(cb) = callback else { return };
    let mut seen = seen.lock().await;
    for ev in history {
        let eid = ev.id().to_hex().to_string();
        if !seen.insert(eid.clone()) {
            continue;
        }
        if let Ok((privmsg, _)) = deserialize_async_partial::<Privmsg>(ev.content()).await {
            if let Some(shown) = present_privmsg(privmsg) {
                dispatch_darkirc_message(
                    cb.as_ref(),
                    eid,
                    shown.channel,
                    shown.nick,
                    shown.msg,
                    ev.header.timestamp,
                    shown.is_outgoing,
                );
            }
        }
    }
}

/// Daemon lifecycle states exposed to Swift/Kotlin via UniFFI.
const STATUS_NOT_RUNNING: u8 = 0;
const STATUS_STARTING: u8 = 1;
const STATUS_RUNNING: u8 = 2;
const STATUS_STOPPING: u8 = 3;
const STATUS_FAILED: u8 = 4;

/// Global daemon state (atomically updated).
static DAEMON_STATUS: AtomicU8 = AtomicU8::new(STATUS_NOT_RUNNING);

/// Bumped on every accepted start. A thread may publish status or clear the
/// shared session only while this still equals the generation it started with.
/// A hung `p2p.stop` otherwise stores `not_running` over the daemon that
/// replaced it.
static DAEMON_GENERATION: AtomicU64 = AtomicU64::new(0);

/// Monotonic milliseconds when the current stop was requested. Zero means no
/// stop is in progress. A start may replace a stop that has been pending for
/// [`STUCK_STOP_MS`] so chat cannot stay on `stopping` forever.
static STOP_REQUESTED_MS: AtomicU64 = AtomicU64::new(0);

/// How long a stop may sit in `stopping` before the next start replaces it.
/// Upstream darkfi `48e024246` fixed the `outbound_session` retry spin on
/// `P2p::stop()`, so stops normally finish in <100ms; this remains a defensive
/// guard against stalled OS sockets / I/O and sits below the 5s Kotlin wait.
const STUCK_STOP_MS: u64 = 3_000;

fn monotonic_ms() -> u64 {
    static START: std::sync::OnceLock<std::time::Instant> = std::sync::OnceLock::new();
    let start = START.get_or_init(std::time::Instant::now);
    start.elapsed().as_millis() as u64
}

fn daemon_generation_owns(owner: u64, current: u64) -> bool {
    owner == current
}

fn this_daemon_should_exit(generation: u64) -> bool {
    if !daemon_generation_owns(generation, DAEMON_GENERATION.load(Ordering::SeqCst)) {
        return true;
    }
    DAEMON_STATUS.load(Ordering::SeqCst) == STATUS_STOPPING
}

/// A live daemon is refused. A stop that has not finished is refused until it
/// has been pending for [`STUCK_STOP_MS`]. `None` means the stop age is
/// unknown, which is treated as already stuck.
pub(crate) fn may_replace_daemon(status: u8, stop_pending_ms: Option<u64>) -> bool {
    match status {
        STATUS_RUNNING | STATUS_STARTING => false,
        STATUS_STOPPING => stop_pending_ms.unwrap_or(u64::MAX) >= STUCK_STOP_MS,
        _ => true,
    }
}

fn stop_pending_ms() -> Option<u64> {
    let requested = STOP_REQUESTED_MS.load(Ordering::SeqCst);
    if requested == 0 {
        None
    } else {
        Some(monotonic_ms().saturating_sub(requested))
    }
}

fn publish_daemon_exit(generation: u64, failed: bool) {
    if !daemon_generation_owns(generation, DAEMON_GENERATION.load(Ordering::SeqCst)) {
        return;
    }
    if failed {
        DAEMON_STATUS.store(STATUS_FAILED, Ordering::Relaxed);
        set_phase(PHASE_FAILED);
    } else {
        DAEMON_STATUS.store(STATUS_NOT_RUNNING, Ordering::Relaxed);
        set_phase(PHASE_STOPPED);
    }
}

fn signal_stop_channel() {
    crate::block_on(async {
        if let Some((ref tx, _)) = *STOP_CHANNEL.read().await {
            let _ = tx.try_send(());
            tx.close();
        }
    });
}

async fn clear_session_if_owner(generation: u64) {
    release_shared_session(generation).await;
}

/// Drop the process-wide EventGraph and P2P handles.
///
/// Those handles own the datastore lock. A daemon thread that lost its
/// generation is not allowed to clear them, so a replacement start used to
/// leave the previous database open forever. Every later `open` then failed
/// and chat stayed on the error screen after the first disconnect.
async fn release_shared_session(generation: u64) {
    let mut event_graph = EVENT_GRAPH.write().await;
    let mut p2p = P2P.write().await;
    if !daemon_generation_owns(generation, DAEMON_GENERATION.load(Ordering::SeqCst)) {
        return;
    }
    *event_graph = None;
    *p2p = None;
}

async fn install_shared_session(
    generation: u64,
    event_graph: darkfi::event_graph::EventGraphPtr,
    p2p: darkfi::net::P2pPtr,
) {
    let mut event_graph_slot = EVENT_GRAPH.write().await;
    let mut p2p_slot = P2P.write().await;
    if !daemon_generation_owns(generation, DAEMON_GENERATION.load(Ordering::SeqCst)) {
        return;
    }
    *event_graph_slot = Some(event_graph);
    *p2p_slot = Some(p2p);
}

/// Defensive timeout guard around async teardown calls (`p2p.stop`, `prune.stop`,
/// task cancels). Upstream darkfi `48e024246` fixed the `outbound_session` spin
/// loop during `P2p::stop()`, so teardown normally completes promptly; 2s bounds
/// worst-case OS socket stalls during mobile network switches.
async fn abandon_after<T>(label: &'static str, fut: impl std::future::Future<Output = T>) {
    smol::future::race(
        async move {
            let _ = fut.await;
        },
        async move {
            smol::Timer::after(std::time::Duration::from_secs(2)).await;
            tracing::warn!("darkirc {label} did not finish within 2s");
        },
    )
    .await;
}

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

type StopChannelPair = (smol::channel::Sender<()>, smol::channel::Receiver<()>);

/// Per-lifecycle stop channel. Each `start_darkirc` installs a fresh
/// `(Sender, Receiver)` pair so a leftover stop token cannot wake the
/// next daemon. The rest of DarkIRC is still a process-wide singleton.
static STOP_CHANNEL: std::sync::LazyLock<smol::lock::RwLock<Option<StopChannelPair>>> =
    std::sync::LazyLock::new(|| smol::lock::RwLock::new(None));

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

/// Event ids ingested from BLE so the relay task does not mesh-gossip them
/// again (originator already sealed to neighbors).
static MESH_SOURCED: std::sync::LazyLock<Mutex<HashSet<[u8; 16]>>> =
    std::sync::LazyLock::new(|| Mutex::new(HashSet::new()));

/// DarkFi-app HUD uses three outbound slots. Same density here (not the video overlay).
const OUTBOUND_HUD_SLOTS: usize = 3;
const OUTBOUND_SLOTS_FILE: &str = "outbound_slots.json";

static DATASTORE_PATH: std::sync::LazyLock<std::sync::RwLock<Option<PathBuf>>> =
    std::sync::LazyLock::new(|| std::sync::RwLock::new(None));

/// Set once the legacy on-disk `darkirc_crypto_bundle.json` has been checked/migrated
/// for the current datastore path so foreign DAG messages never hit the filesystem.
static DM_LEGACY_BUNDLE_LOADED: AtomicBool = AtomicBool::new(false);

fn set_datastore_path(path: PathBuf) {
    if let Ok(mut g) = DATASTORE_PATH.write() {
        *g = Some(path);
    }
    DM_LEGACY_BUNDLE_LOADED.store(false, Ordering::Release);
}

fn current_datastore_path() -> Option<PathBuf> {
    DATASTORE_PATH.read().ok().and_then(|g| g.clone())
}

#[allow(dead_code)]
fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if c.is_control() => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
}

fn format_outbound_slots_json(connected: usize, handshake: bool) -> String {
    let mut parts = Vec::with_capacity(OUTBOUND_HUD_SLOTS);
    for i in 0..OUTBOUND_HUD_SLOTS {
        let state = if i < connected {
            "connected"
        } else if handshake {
            "handshake"
        } else {
            "sleeping"
        };
        parts.push(format!("{{\"slot\":{i},\"state\":\"{state}\"}}"));
    }
    format!("[{}]", parts.join(","))
}

fn snapshot_connected_peer_count() -> usize {
    let Some(guard) = P2P.try_read() else {
        return 0;
    };
    let Some(p2p) = guard.as_ref() else {
        return 0;
    };
    p2p.hosts()
        .peers()
        .into_iter()
        .take(OUTBOUND_HUD_SLOTS)
        .count()
}

fn connecting_for_hud() -> bool {
    matches!(
        CONNECTION_PHASE.load(Ordering::Relaxed),
        PHASE_STARTING
            | PHASE_WAITING_FOR_PEERS
            | PHASE_STATIC_SYNC
            | PHASE_SYNCING_DAG
            | PHASE_LOADING_HISTORY
    )
}

/// Live outbound HUD slots as JSON (three slots: connected / connecting / sleeping).
pub fn darkirc_outbound_slots() -> String {
    let connected = snapshot_connected_peer_count();
    let handshake = connected == 0 && connecting_for_hud();
    format_outbound_slots_json(connected, handshake)
}

fn publish_outbound_slots() {
    let json = darkirc_outbound_slots();
    let Some(dir) = current_datastore_path() else {
        return;
    };
    let dest = dir.join(OUTBOUND_SLOTS_FILE);
    let tmp = dir.join("outbound_slots.json.tmp");
    if std::fs::write(&tmp, json.as_bytes()).is_ok() {
        let _ = std::fs::rename(&tmp, dest);
    }
}

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

const MAX_PRIVMSG_FIELD_LEN: usize = 512;
const MAX_NICK_LEN: usize = 24;

static DARKIRC_DAGS_COUNT: AtomicU32 = AtomicU32::new(DARKIRC_MAX_DAGS as u32);
static DARKIRC_FAST_MODE: AtomicBool = AtomicBool::new(false);

/// Configure DarkIRC DAG retention/sync parameters without breaking `start_darkirc`.
pub fn configure_darkirc(dags_count: u32, fast_mode: bool) {
    let clamped = if dags_count == 0 {
        DARKIRC_MAX_DAGS as u32
    } else {
        dags_count.min(DARKIRC_MAX_DAGS as u32)
    };
    DARKIRC_DAGS_COUNT.store(clamped, Ordering::Relaxed);
    DARKIRC_FAST_MODE.store(fast_mode, Ordering::Relaxed);
}

/// Contact saltboxes for DM encryption (channel/nick/msg), keyed by contact nick.
static DM_CONTACTS: std::sync::LazyLock<
    std::sync::RwLock<std::collections::HashMap<String, DmContactBoxes>>,
> = std::sync::LazyLock::new(|| std::sync::RwLock::new(std::collections::HashMap::new()));

static LAST_SELF_NICK: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());

struct DmContactBoxes {
    my_secret: Zeroizing<[u8; 32]>,
    saltbox: crypto_box::ChaChaBox,
    self_saltbox: crypto_box::ChaChaBox,
}

impl Drop for DmContactBoxes {
    fn drop(&mut self) {
        self.my_secret.zeroize();
    }
}

/// Validate and register a DM contact in memory.
pub(crate) fn register_dm_contact_checked(
    nick: &str,
    my_secret: &[u8],
    their_public: &[u8],
) -> Result<(), String> {
    let clean_nick = nick.trim();
    if clean_nick.is_empty() {
        return Err("DM contact nick cannot be empty".into());
    }
    if clean_nick.starts_with('#') {
        return Err("DM contact nick cannot start with '#'".into());
    }
    let secret_arr: [u8; 32] = my_secret
        .try_into()
        .map_err(|_| format!("invalid DM secret key length: {}", my_secret.len()))?;
    let mut secret_zero = Zeroizing::new(secret_arr);
    let my_sk = crypto_box::SecretKey::from(*secret_zero);
    let their_pk = crypto_box::PublicKey::from_slice(their_public).map_err(|e| e.to_string())?;
    let my_pk = my_sk.public_key();
    let boxes = DmContactBoxes {
        my_secret: Zeroizing::new(*secret_zero),
        saltbox: crypto_box::ChaChaBox::new(&their_pk, &my_sk),
        self_saltbox: crypto_box::ChaChaBox::new(&my_pk, &my_sk),
    };
    secret_zero.zeroize();
    DM_CONTACTS
        .write()
        .map_err(|e| e.to_string())?
        .insert(clean_nick.to_string(), boxes);
    Ok(())
}

/// Register a DM contact in memory for UniFFI callers (never touches disk).
/// The error names the failure (empty nick, bad key length) and does not include key bytes.
pub fn register_dm_contact(
    nick: String,
    mut my_secret: Vec<u8>,
    their_public: Vec<u8>,
) -> Result<(), crate::DarkfiWalletNativeError> {
    let result = register_dm_contact_checked(&nick, &my_secret, &their_public);
    my_secret.zeroize();
    result.map_err(|e| {
        tracing::warn!(target: "darkirc", "register_dm_contact skipped: {e}");
        crate::DarkfiWalletNativeError::CryptoError(e)
    })
}

/// Unregister a DM contact from memory (zeroizing stored secret on drop).
pub fn unregister_dm_contact(nick: String) {
    if let Ok(mut contacts) = DM_CONTACTS.write() {
        contacts.remove(&nick);
    }
}

/// Clear all registered DM contacts from memory (zeroizing secrets on drop).
pub fn clear_dm_contacts() {
    if let Ok(mut contacts) = DM_CONTACTS.write() {
        contacts.clear();
    }
    if let Ok(mut self_nick) = LAST_SELF_NICK.lock() {
        self_nick.zeroize();
    }
}

fn load_dm_contacts_from_datastore(dir: &std::path::Path) {
    DM_LEGACY_BUNDLE_LOADED.store(true, Ordering::Release);
    let path = dir.join("darkirc_crypto_bundle.json");
    let Ok(mut raw) = std::fs::read_to_string(&path) else {
        return;
    };
    for (nick, mut sk, pk) in parse_dm_contacts_json(&raw) {
        let _ = register_dm_contact_checked(&nick, &sk, &pk);
        sk.zeroize();
    }
    // The legacy bundle holds DM private keys. Overwrite, unlink, and keep in memory only.
    let _ = std::fs::write(&path, vec![0u8; raw.len()]);
    let _ = std::fs::remove_file(&path);
    raw.zeroize();
}

fn parse_dm_contacts_json(raw: &str) -> Vec<(String, Vec<u8>, Vec<u8>)> {
    // Minimal parse of DarkircCryptoBundle.contacts without a serde_json dep.
    let mut out = Vec::new();
    let Some(arr) = raw.split("\"contacts\"").nth(1) else {
        return out;
    };
    for chunk in arr.split('{').skip(1) {
        let Some(end) = chunk.find('}') else {
            continue;
        };
        let obj = &chunk[..end];
        let nick = json_string_field(obj, "nick");
        let secret = json_string_field(obj, "myDmChachaSecretBase58")
            .or_else(|| json_string_field(obj, "my_dm_chacha_secret_base58"));
        let public = json_string_field(obj, "dmChachaPublicBase58")
            .or_else(|| json_string_field(obj, "dm_chacha_public_base58"));
        if let (Some(nick), Some(secret), Some(public)) = (nick, secret, public) {
            if let (Ok(sk), Ok(pk)) = (
                bs58::decode(secret).into_vec(),
                bs58::decode(public).into_vec(),
            ) {
                if sk.len() == 32 && pk.len() == 32 {
                    out.push((nick.to_string(), sk, pk));
                }
            }
        }
    }
    out
}

fn json_string_field<'a>(obj: &'a str, key: &str) -> Option<&'a str> {
    let pat = format!("\"{key}\"");
    let rest = obj.split(&pat).nth(1)?;
    let rest = rest.trim_start_matches([' ', ':', '\t']);
    let rest = rest.strip_prefix('"')?;
    let end = rest.find('"')?;
    Some(&rest[..end])
}

fn ensure_legacy_dm_contacts_loaded() {
    if DM_LEGACY_BUNDLE_LOADED.load(Ordering::Acquire) {
        return;
    }
    if let Some(dir) = current_datastore_path() {
        load_dm_contacts_from_datastore(&dir);
    }
}

struct PresentedPrivmsg {
    channel: String,
    nick: String,
    msg: String,
    is_outgoing: bool,
}

/// Attempt to decrypt a DM against in-memory `DM_CONTACTS`.
/// Returns `None` if no registered contact decrypts the payload or if the
/// decrypted channel sentinel does not match `[0x00; MAX_NICK_LEN]`.
fn try_decrypt_privmsg(privmsg: &Privmsg) -> Option<PresentedPrivmsg> {
    ensure_legacy_dm_contacts_loaded();
    let contacts = DM_CONTACTS.read().ok()?;
    let channel_ct = bs58::decode(&privmsg.channel).into_vec().ok()?;
    let nick_ct = bs58::decode(&privmsg.nick).into_vec().ok()?;
    let msg_ct = bs58::decode(&privmsg.msg).into_vec().ok()?;
    let self_nick = LAST_SELF_NICK.lock().map(|g| g.clone()).unwrap_or_default();
    for (name, contact) in contacts.iter() {
        let Some(channel_dec) = irc2::crypto::saltbox::try_decrypt(&contact.saltbox, &channel_ct)
        else {
            continue;
        };
        if channel_dec.as_slice() != [0x00; MAX_NICK_LEN] {
            continue;
        }
        let outgoing = irc2::crypto::saltbox::try_decrypt(&contact.self_saltbox, &nick_ct)
            .is_some_and(|n| n.as_slice() == [0x00; MAX_NICK_LEN]);
        let Some(msg_dec) = irc2::crypto::saltbox::try_decrypt(&contact.saltbox, &msg_ct) else {
            continue;
        };
        return Some(PresentedPrivmsg {
            channel: name.clone(),
            nick: if outgoing && !self_nick.is_empty() {
                self_nick
            } else {
                name.clone()
            },
            msg: String::from_utf8_lossy(&msg_dec).into_owned(),
            is_outgoing: outgoing,
        });
    }
    None
}

/// Decrypt a DM using in-memory `DM_CONTACTS`, or pass a public `#channel` through.
/// Undecryptable foreign DMs return `None` and are dropped cleanly.
fn present_privmsg(privmsg: Privmsg) -> Option<PresentedPrivmsg> {
    if privmsg.channel.starts_with('#') {
        let self_nick = LAST_SELF_NICK
            .lock()
            .map(|guard| guard.clone())
            .unwrap_or_default();
        let is_outgoing = !self_nick.is_empty() && privmsg.nick == self_nick;
        return Some(PresentedPrivmsg {
            channel: privmsg.channel,
            nick: privmsg.nick,
            msg: privmsg.msg,
            is_outgoing,
        });
    }
    try_decrypt_privmsg(&privmsg)
}

/// Encrypt channel, nick, and message for DMs exactly as upstream `try_encrypt`.
///
/// Protocol note (upstream `irc2::crypto::saltbox` wire format): `channel`, `nick`,
/// and `msg` are encrypted as three independent XChaCha20-Poly1305 ciphertexts with
/// no cross-field AAD binding (preserving exact compatibility with upstream `darkirc`
/// nodes and desktop/mobile peers). Do not alter this wire format without an upstream
/// protocol bump.
fn try_encrypt_privmsg(privmsg: &mut Privmsg) -> Result<(), String> {
    if privmsg.channel.starts_with('#') {
        return Ok(());
    }
    ensure_legacy_dm_contacts_loaded();
    let contacts = DM_CONTACTS.read().map_err(|e| e.to_string())?;
    let Some(contact) = contacts.get(&privmsg.channel) else {
        return Err(
            "DM contact is not registered; refusing plaintext channel/nick on the wire".into(),
        );
    };
    privmsg.channel = irc2::crypto::saltbox::encrypt(&contact.saltbox, &[0x00; MAX_NICK_LEN]);
    privmsg.nick = irc2::crypto::saltbox::encrypt(&contact.self_saltbox, &[0x00; MAX_NICK_LEN]);
    privmsg.msg = irc2::crypto::saltbox::encrypt(&contact.saltbox, privmsg.msg.as_bytes());
    Ok(())
}

fn mesh_event_id16(event: &darkfi::event_graph::Event) -> [u8; 16] {
    let mut id = [0u8; 16];
    id.copy_from_slice(&event.id().as_bytes()[..16]);
    id
}

async fn mesh_gossip_event(event: &darkfi::event_graph::Event, blob: &[u8]) {
    let ev_bytes = serialize_async(event).await;
    let Ok(payload) = crate::mesh::encode_mesh_event(&ev_bytes, blob) else {
        return;
    };
    let _ = crate::mesh::mesh_publish_event(mesh_event_id16(event), payload);
}

async fn drain_mesh_inbound(eg: &darkfi::event_graph::EventGraph) {
    while let Some((_id, payload)) = crate::mesh::mesh_pop_inbound_event() {
        let Ok((ev_bytes, blob)) = crate::mesh::decode_mesh_event(&payload) else {
            continue;
        };
        let Ok(event) = deserialize_async::<darkfi::event_graph::Event>(&ev_bytes).await else {
            continue;
        };
        if blob.len() > darkfi::event_graph::EventGraph::MAX_MESH_EVENT_BLOB {
            continue;
        }
        if !event.validate_new() {
            continue;
        }
        let id16 = mesh_event_id16(&event);
        let dag_name = eg.current_genesis.read().await.header.timestamp.to_string();
        let _ = eg
            .header_dag_insert(vec![event.header.clone()], &dag_name)
            .await;
        match eg.ingest_mesh_event(event, blob, &dag_name).await {
            Ok(ids) => {
                if !ids.is_empty() {
                    mark_mesh_sourced(id16);
                }
            }
            Err(e) => tracing::debug!("mesh ingest skipped: {e}"),
        }
    }
}

fn mark_mesh_sourced(id: [u8; 16]) {
    if let Ok(mut g) = MESH_SOURCED.lock() {
        g.insert(id);
        if g.len() > 4096 {
            g.clear();
        }
    }
}

fn is_mesh_sourced(id: [u8; 16]) -> bool {
    MESH_SOURCED
        .lock()
        .ok()
        .map(|g| g.contains(&id))
        .unwrap_or(false)
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
            write_logcat(trimmed);
        }
        Ok(buf.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

/// `log` macros are compiled out of release builds (darkfi's drk enables
/// `log/release_max_level_off`). Debug builds write to liblog directly.
/// Release builds write nothing: logcat is readable by the OS, bugreports,
/// and `adb`, and upstream P2P lines include peer addresses.
#[cfg(target_os = "android")]
fn write_logcat(line: &str) {
    if !cfg!(debug_assertions) {
        return;
    }
    let Ok(text) = std::ffi::CString::new(line.replace('\0', " ")) else {
        return;
    };
    // SAFETY: both pointers are valid NUL-terminated strings for the duration of the call.
    unsafe {
        android_log_sys::__android_log_write(
            android_log_sys::LogPriority::INFO as std::os::raw::c_int,
            c"darkfi-net".as_ptr(),
            text.as_ptr(),
        );
    }
}

#[cfg(target_os = "ios")]
fn write_logcat(line: &str) {
    // stderr is captured into the iOS unified log and sysdiagnose. Release
    // builds stay silent so peer and wallet diagnostics are not collected.
    if !cfg!(debug_assertions) {
        return;
    }
    eprintln!("[darkfi-net] {line}");
}

#[cfg(all(not(target_os = "android"), not(target_os = "ios")))]
fn write_logcat(line: &str) {
    if !cfg!(debug_assertions) {
        return;
    }
    eprintln!("[darkfi-net] {line}");
}

impl<'a> tracing_subscriber::fmt::MakeWriter<'a> for LogcatWriter {
    type Writer = LogcatWriter;
    fn make_writer(&'a self) -> Self::Writer {
        LogcatWriter
    }
}

static LOGGING_INIT: std::sync::Once = std::sync::Once::new();

pub(crate) fn init_logging() {
    LOGGING_INIT.call_once(|| {
        // `log` backend (rustls/kvdb use it).
        // Release: off. Debug: INFO for development diagnostics.
        // Shipping clients must not write peer or wallet lines to the OS log.
        let (max_level, filter_spec) = if cfg!(debug_assertions) {
            (log::LevelFilter::Debug, "info,rustls=off,kvdb=off,kvdb_overlay=off,mio=off,polling=off,async_io=off,want=off")
        } else {
            (log::LevelFilter::Off, "off")
        };
        #[cfg(target_os = "android")]
        {
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
        }
        #[cfg(not(target_os = "android"))]
        let _ = (max_level, filter_spec);

        // `tracing` backend (darkfi net/event_graph).
        // Debug: INFO for P2P/DAG diagnostics. Release: off, including Nym.
        let level = if cfg!(debug_assertions) { "info" } else { "off" };
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
/// `transport` follows the app's network choice:
/// - `Nym`: clearnet `tcp+tls` seeds and discovered peers are dialled through
///   the in-process Nym SOCKS5 proxy on `127.0.0.1:socks_port` (`socks5+tls`
///   mixing). Onion-only peers are unreachable over Nym and are skipped.
/// - `Tor`: the onion seeds through the in-process Arti SOCKS5 proxy, with
///   `tor` peers mixed through it, so every connection stays inside Tor.
/// - `Direct`: clearnet `tcp+tls` seeds; `socks_port` is ignored.
///
/// For `Nym`/`Tor` the proxy must already carry traffic (the app layer waits
/// on it) before this is called.
pub fn start_darkirc(
    datastore_path: String,
    transport: crate::DarkircTransport,
    socks_port: u16,
    callback: Option<Box<dyn DarkircEventCallback>>,
) -> Result<(), DarkfiWalletNativeError> {
    crate::panic_fence::catch_wallet("start_darkirc", || {
        start_darkirc_inner(datastore_path, transport, socks_port, callback)
    })
}

fn start_darkirc_inner(
    datastore_path: String,
    transport: crate::DarkircTransport,
    socks_port: u16,
    callback: Option<Box<dyn DarkircEventCallback>>,
) -> Result<(), DarkfiWalletNativeError> {
    let mut current = DAEMON_STATUS.load(Ordering::SeqCst);
    loop {
        let pending = stop_pending_ms();
        if !may_replace_daemon(current, pending) {
            if current == STATUS_STOPPING {
                // The first stop signal can be lost if it is sent before the
                // daemon installs its channel. Send it again, then let the
                // caller retry once the stop has been pending long enough.
                signal_stop_channel();
            }
            return Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "darkirc cannot start, current status: {}",
                darkirc_status()
            )));
        }
        if current == STATUS_STOPPING {
            signal_stop_channel();
            tracing::warn!(
                "darkirc stop still pending after {}ms; replacing the daemon",
                pending.unwrap_or(STUCK_STOP_MS)
            );
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
    let generation = DAEMON_GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    STOP_REQUESTED_MS.store(0, Ordering::SeqCst);
    init_logging();

    let db_path = PathBuf::from(&datastore_path);
    set_datastore_path(db_path.clone());
    load_dm_contacts_from_datastore(&db_path);
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
                // start_darkirc calls are permanently rejected. A thread that
                // no longer owns `generation` must not write that status: the
                // start that replaced a hung stop already published its own.
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
                                    transport,
                                    socks_port,
                                    ex.clone(),
                                    cb,
                                    daemon_stop_rx,
                                    generation,
                                )
                                .await;
                                // Signal workers to stop
                                drop(worker_stop_tx);
                                result
                            })
                        })
                        .1;

                    match result {
                        Ok(()) => publish_daemon_exit(generation, false),
                        Err(e) => {
                            tracing::error!("darkirc daemon failed: {e}");
                            publish_daemon_exit(generation, true);
                        }
                    }
                }));

                if outcome.is_err() {
                    tracing::error!("darkirc daemon thread panicked — forcing status to FAILED");
                    publish_daemon_exit(generation, true);
                    smol::block_on(clear_session_if_owner(generation));
                }

                if !daemon_generation_owns(generation, DAEMON_GENERATION.load(Ordering::SeqCst)) {
                    return;
                }
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
            DAEMON_STATUS.store(STATUS_FAILED, Ordering::Relaxed);
            set_phase(PHASE_FAILED);
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
        if current == STATUS_NOT_RUNNING {
            return Ok(());
        }
        if current == STATUS_FAILED {
            // The daemon thread has already exited. `stopping` with no thread
            // never publishes `not_running`, so the next start waits out the
            // stuck-stop timeout and then fails to reopen the datastore.
            match DAEMON_STATUS.compare_exchange_weak(
                STATUS_FAILED,
                STATUS_NOT_RUNNING,
                Ordering::SeqCst,
                Ordering::SeqCst,
            ) {
                Ok(_) => {
                    set_phase(PHASE_STOPPED);
                    STOP_REQUESTED_MS.store(0, Ordering::SeqCst);
                    return Ok(());
                }
                Err(observed) => {
                    current = observed;
                    continue;
                }
            }
        }
        if current == STATUS_STOPPING {
            // Already stopping is not a reason to drop the signal. The thread
            // may not have installed the channel yet, or the first send lost
            // the race. A second stop has to reach it.
            signal_stop_channel();
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

    STOP_REQUESTED_MS.store(monotonic_ms().max(1), Ordering::SeqCst);
    set_phase(PHASE_STOPPING);
    signal_stop_channel();
    crate::mesh::notify_mesh_wake();
    Ok(())
}

/// Send a chat message natively
pub fn send_chat_message(
    channel: String,
    nick: String,
    message: String,
) -> Result<(), DarkfiWalletNativeError> {
    crate::panic_fence::catch_wallet("send_chat_message", || {
        send_chat_message_inner(channel, nick, message)
    })
}

fn send_chat_message_inner(
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

    if channel.len() > MAX_PRIVMSG_FIELD_LEN
        || nick.len() > MAX_PRIVMSG_FIELD_LEN
        || message.len() > MAX_PRIVMSG_FIELD_LEN
    {
        return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
            "channel, nick, and message must be at most 512 bytes".into(),
        ));
    }

    crate::block_on(async move {
        let eg_lock = EVENT_GRAPH.read().await;
        let p2p_lock = P2P.read().await;

        if let (Some(eg), Some(p2p)) = (&*eg_lock, &*p2p_lock) {
            if let Ok(mut slot) = LAST_SELF_NICK.lock() {
                *slot = nick.clone();
            }
            let mut msg = new_privmsg(channel.clone(), nick.clone(), message.clone());
            if let Err(e) = try_encrypt_privmsg(&mut msg) {
                return Err(DarkfiWalletNativeError::NativeDrkUnavailable(e));
            }

            let event = match darkfi::event_graph::Event::new(serialize_async(&msg).await, eg).await
            {
                Ok(ev) => ev,
                Err(e) => {
                    tracing::error!("Failed building Event: {}", e);
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
                    tracing::error!("Failed inserting signal event to DAG: {}", e);
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
                tracing::error!("insert_signal_with_blob returned no ids (event skipped)");
                return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
                    "insert_signal_with_blob skipped event (empty result)".to_string(),
                ));
            }

            mesh_gossip_event(&event, &blob).await;

            if let Err(e) = p2p
                .broadcast(&darkfi::event_graph::proto::EventPut(event, blob))
                .await
            {
                tracing::error!("Event broadcast was not admitted: {}", e);
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
                dispatch_darkirc_message(
                    cb.as_ref(),
                    event_id.clone(),
                    channel.clone(),
                    nick.clone(),
                    message,
                    event_ts,
                    true,
                );
            }

            tracing::info!("send_chat_message ok event={}", event_id);
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
/// SOCKS5 proxy on `127.0.0.1:tor_socks_port` (in-process Arti), embedding the
/// process-local RFC 1929 credentials when authenticating to the local proxy.
///
/// The resulting `socks5://user:pass@127.0.0.1:<port>/<onion>:9601` form is
/// accepted by darkfi's `net::transport::socks5` dialer (proxy + optional auth
/// in the authority, destination in the path).
fn tor_socks5_seeds(tor_socks_port: u16) -> Vec<url::Url> {
    let auth = crate::transport_manager::current_socks_auth();
    DARKIRC_ONION_SEEDS
        .iter()
        .map(|dest| {
            url::Url::parse(&format!(
                "socks5://{}:{}@127.0.0.1:{tor_socks_port}/{dest}",
                auth.username, auth.password
            ))
            .unwrap()
        })
        .collect()
}

/// Clearnet darkirc seeds (upstream `bin/darkirc/darkirc_config.toml`).
const DARKIRC_CLEARNET_SEEDS: [&str; 2] = ["lilith0.dark.fi:9600", "lilith1.dark.fi:9600"];

/// Seeds, active profile, and transport mixing for one [`crate::DarkircTransport`].
struct TransportPlan {
    profile_name: &'static str,
    seeds: Vec<url::Url>,
    mixed_profiles: Vec<String>,
    tor_socks5_proxy: Option<url::Url>,
    nym_socks5_proxy: Option<url::Url>,
    /// Proxied links (Tor circuits, mixnet hops) need the longer timeouts.
    slow_links: bool,
}

fn transport_plan(transport: crate::DarkircTransport, socks_port: u16) -> TransportPlan {
    let auth = crate::transport_manager::current_socks_auth();
    let proxy = || {
        url::Url::parse(&format!(
            "socks5://{}:{}@127.0.0.1:{socks_port}",
            auth.username, auth.password
        ))
        .unwrap()
    };
    match transport {
        crate::DarkircTransport::Direct => TransportPlan {
            profile_name: "tcp+tls",
            seeds: DARKIRC_CLEARNET_SEEDS
                .iter()
                .map(|seed| url::Url::parse(&format!("tcp+tls://{seed}")).unwrap())
                .collect(),
            mixed_profiles: vec![],
            tor_socks5_proxy: None,
            nym_socks5_proxy: None,
            slow_links: false,
        },
        // Peers advertise `tor://…onion` addresses. Without mixing those hosts
        // are darklisted (socks5 != tor), `is_connected()` never becomes true,
        // and DAG sync never starts.
        crate::DarkircTransport::Tor => TransportPlan {
            profile_name: "socks5",
            seeds: tor_socks5_seeds(socks_port),
            mixed_profiles: vec!["tor".to_string()],
            tor_socks5_proxy: Some(proxy()),
            nym_socks5_proxy: None,
            slow_links: true,
        },
        // Nym exits reach clearnet only, so dial the clearnet seeds through the
        // Nym SOCKS proxy and mix discovered `tcp+tls` peers through it as well.
        crate::DarkircTransport::Nym => TransportPlan {
            profile_name: "socks5+tls",
            seeds: DARKIRC_CLEARNET_SEEDS
                .iter()
                .map(|seed| {
                    url::Url::parse(&format!(
                        "socks5+tls://{}:{}@127.0.0.1:{socks_port}/{seed}",
                        auth.username, auth.password
                    ))
                    .unwrap()
                })
                .collect(),
            mixed_profiles: vec!["tcp+tls".to_string()],
            tor_socks5_proxy: None,
            nym_socks5_proxy: Some(proxy()),
            slow_links: true,
        },
    }
}

async fn wait_for_stop(stop_rx: &smol::channel::Receiver<()>) {
    if DAEMON_STATUS.load(Ordering::SeqCst) == STATUS_STOPPING {
        return;
    }
    let _ = stop_rx.recv().await;
}

async fn sleep_or_stop(stop_rx: &smol::channel::Receiver<()>, dur: std::time::Duration) {
    smol::future::race(wait_for_stop(stop_rx), async {
        smol::Timer::after(dur).await;
    })
    .await;
}

struct FailedProtocol(String);

impl darkfi::net::protocol::protocol_base::ProtocolBase for FailedProtocol {
    fn start<'life0, 'async_trait>(
        self: Arc<Self>,
        _executor: Arc<Executor<'life0>>,
    ) -> std::pin::Pin<
        Box<dyn std::future::Future<Output = darkfi::Result<()>> + Send + 'async_trait>,
    >
    where
        'life0: 'async_trait,
        Self: 'async_trait,
    {
        Box::pin(async move { Err(darkfi::Error::Custom(self.0.clone())) })
    }

    fn name(&self) -> &'static str {
        "ProtocolEventGraphInitFailed"
    }
}

/// The actual darkirc daemon loop. Runs until stopped or error.
async fn run_darkirc_daemon(
    datastore_path: PathBuf,
    transport: crate::DarkircTransport,
    socks_port: u16,
    ex: Arc<Executor<'static>>,
    callback: Option<Arc<dyn DarkircEventCallback>>,
    stop_rx: smol::channel::Receiver<()>,
    generation: u64,
) -> Result<(), String> {
    use darkfi::{
        event_graph::{proto::ProtocolEventGraph, EventGraph, EventGraphConfig},
        net::{
            session::SESSION_DEFAULT,
            settings::{NetworkProfile, Settings},
            P2p,
        },
    };
    use kvdb_overlay::Database;

    // The previous generation's EventGraph keeps the datastore lock. Drop it
    // before open, and keep retrying while that thread is still tearing down.
    std::fs::create_dir_all(&datastore_path).map_err(|e| format!("create datastore: {e}"))?;
    release_shared_session(generation).await;
    let mut kvdb_result = Database::open_default(&datastore_path);
    if kvdb_result.is_err() {
        for _ in 0..40 {
            if this_daemon_should_exit(generation) {
                return Ok(());
            }
            release_shared_session(generation).await;
            sleep_or_stop(&stop_rx, std::time::Duration::from_millis(500)).await;
            if this_daemon_should_exit(generation) {
                return Ok(());
            }
            kvdb_result = Database::open_default(&datastore_path);
            if kvdb_result.is_ok() {
                break;
            }
        }
    }
    let kvdb = kvdb_result.map_err(|e| format!("open kvdb: {e}"))?;

    // Seeds and the outbound transport profile are chosen by `transport`. Both
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
    let plan = transport_plan(transport, socks_port);
    let profile_name = plan.profile_name;
    let seeds = plan.seeds.clone();

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
    let mut profile = if plan.slow_links {
        NetworkProfile::tor_default()
    } else {
        NetworkProfile {
            outbound_connect_timeout: 40,
            channel_handshake_timeout: 30,
            ..Default::default()
        }
    };
    if plan.slow_links {
        // Onion and mixnet handshakes (circuit build + version exchange) can exceed the
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
    p2p_settings.mixed_profiles = plan.mixed_profiles;
    p2p_settings.tor_socks5_proxy = plan.tor_socks5_proxy;
    p2p_settings.nym_socks5_proxy = plan.nym_socks5_proxy;

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

    let event_graph = match smol::future::race(
        async {
            wait_for_stop(&stop_rx).await;
            None
        },
        async {
            Some(
                EventGraph::new(
                    p2p.clone(),
                    kvdb.clone(),
                    replay_path.clone(),
                    false, // replay_mode
                    eg_config,
                    ex.clone(),
                )
                .await,
            )
        },
    )
    .await
    {
        None => {
            abandon_after("p2p.stop", p2p.stop()).await;
            clear_session_if_owner(generation).await;
            let _ = kvdb.flush_default_mode_async().await;
            return Ok(());
        }
        Some(Ok(graph)) => graph,
        Some(Err(e)) => {
            abandon_after("p2p.stop", p2p.stop()).await;
            clear_session_if_owner(generation).await;
            let _ = kvdb.flush_default_mode_async().await;
            return Err(format!("EventGraph init: {e}"));
        }
    };
    if this_daemon_should_exit(generation) {
        abandon_after("p2p.stop", p2p.stop()).await;
        clear_session_if_owner(generation).await;
        let _ = kvdb.flush_default_mode_async().await;
        return Ok(());
    }

    let prune_task = event_graph.prune_task.get();

    let event_graph_ = Arc::clone(&event_graph);
    let registry = p2p.protocol_registry();
    registry
        .register(SESSION_DEFAULT, move |channel, _| {
            let eg = event_graph_.clone();
            async move {
                match ProtocolEventGraph::init(eg, channel.clone()).await {
                    Ok(proto) => proto,
                    Err(e) => {
                        tracing::error!("ProtocolEventGraph::init failed: {e}");
                        channel.stop().await;
                        Arc::new(FailedProtocol(format!("ProtocolEventGraph::init: {e}")))
                    }
                }
            }
        })
        .await;

    // Save globals only while this generation still owns the daemon. A start
    // that replaced a hung stop must keep the session it just installed.
    install_shared_session(generation, event_graph.clone(), p2p.clone()).await;

    let p2p_started = smol::future::race(
        async {
            wait_for_stop(&stop_rx).await;
            None
        },
        async { Some(p2p.clone().start().await) },
    )
    .await;
    match p2p_started {
        None => {
            abandon_after("p2p.stop", p2p.stop()).await;
            if let Some(prune) = prune_task {
                abandon_after("prune.stop", prune.stop()).await;
            }
            clear_session_if_owner(generation).await;
            let _ = kvdb.flush_default_mode_async().await;
            return Ok(());
        }
        Some(Err(e)) => {
            abandon_after("p2p.stop", p2p.stop()).await;
            if let Some(prune) = prune_task {
                abandon_after("prune.stop", prune.stop()).await;
            }
            clear_session_if_owner(generation).await;
            let _ = kvdb.flush_default_mode_async().await;
            return Err(format!("P2P start: {e}"));
        }
        Some(Ok(())) => {}
    }

    DAEMON_STATUS.store(STATUS_RUNNING, Ordering::Relaxed);
    set_phase(PHASE_WAITING_FOR_PEERS);
    tracing::info!(
        "darkirc daemon started over {:?} ({}) transport, syncing DAG...",
        transport,
        profile_name
    );

    let dags_count =
        (DARKIRC_DAGS_COUNT.load(Ordering::Relaxed) as usize).clamp(1, DARKIRC_MAX_DAGS);
    let fast_mode = DARKIRC_FAST_MODE.load(Ordering::Relaxed);
    let comms_timeout = 5u64;
    const DAG_REFRESH_SECS: u64 = 3600;

    // Relay events — track bounded relayed IDs so history replay skips them.
    let relayed_ids: Arc<smol::lock::Mutex<BoundedIdSet>> =
        Arc::new(smol::lock::Mutex::new(BoundedIdSet::new(MAX_RELAYED_IDS)));
    let ev_sub = event_graph.event_pub.clone().subscribe().await;
    let cb_clone = callback.clone();
    let relayed_ids_clone = Arc::clone(&relayed_ids);
    let relay_task = ex.spawn(async move {
        loop {
            let ev = ev_sub.receive().await;
            if !is_mesh_sourced(mesh_event_id16(&ev)) {
                mesh_gossip_event(&ev, &[]).await;
            }
            if let Some(cb) = &cb_clone {
                if let Ok((privmsg, _)) = deserialize_async_partial::<Privmsg>(ev.content()).await {
                    let eid = ev.id().to_hex().to_string();
                    relayed_ids_clone.lock().await.insert(eid.clone());
                    if let Some(shown) = present_privmsg(privmsg) {
                        dispatch_darkirc_message(
                            cb.as_ref(),
                            eid,
                            shown.channel,
                            shown.nick,
                            shown.msg,
                            ev.header.timestamp,
                            shown.is_outgoing,
                        );
                    }
                }
            }
        }
    });

    let mesh_eg = event_graph.clone();
    let mesh_stop_rx = stop_rx.clone();
    let mesh_generation = generation;
    let mesh_task = ex.spawn(async move {
        loop {
            if this_daemon_should_exit(mesh_generation) {
                break;
            }
            if !crate::transport_manager::mesh_enabled() {
                smol::future::race(
                    wait_for_stop(&mesh_stop_rx),
                    crate::mesh::wait_for_mesh_wake(),
                )
                .await;
                continue;
            }
            drain_mesh_inbound(&mesh_eg).await;
            smol::future::race(
                wait_for_stop(&mesh_stop_rx),
                smol::future::race(crate::mesh::wait_for_mesh_wake(), async {
                    smol::Timer::after(std::time::Duration::from_millis(250)).await;
                }),
            )
            .await;
        }
    });

    // Show the locally stored DAG right away; peer connect over Nym/Tor takes
    // tens of seconds. The post-sync replay below skips these ids.
    match event_graph.order_events().await {
        Ok(cached) => {
            tracing::info!(
                "darkirc daemon replaying {} cached events before peer sync",
                cached.len()
            );
            replay_history(cached, &callback, &relayed_ids).await;
        }
        Err(e) => tracing::warn!("darkirc daemon cached history unavailable: {e}"),
    }

    // Sync while connected; on peer loss, wait briefly and retry so background
    // suspensions that drop sockets can recover without a full daemon restart.
    // Failed sync steps always retry (do not park in the monitor loop).
    let mut logged_waiting = false;
    loop {
        if this_daemon_should_exit(generation) {
            break;
        }

        if !p2p.is_connected() {
            set_phase(PHASE_WAITING_FOR_PEERS);
            if !logged_waiting {
                tracing::info!("darkirc daemon waiting for P2P peers...");
                logged_waiting = true;
            }
            // `is_connected` is a cheap local check; poll it often so sync
            // starts as soon as the first channel is up.
            sleep_or_stop(&stop_rx, std::time::Duration::from_secs(1)).await;
            continue;
        }
        logged_waiting = false;

        set_phase(PHASE_STATIC_SYNC);
        tracing::info!("darkirc daemon connected, waiting for static sync...");
        let static_res = smol::future::race(
            async {
                wait_for_stop(&stop_rx).await;
                None
            },
            async { Some(event_graph.static_sync().await) },
        )
        .await;
        match static_res {
            None => break,
            Some(Err(_)) => {
                tracing::warn!("darkirc daemon static_sync failed — retrying");
                set_phase(PHASE_WAITING_FOR_PEERS);
                sleep_or_stop(&stop_rx, std::time::Duration::from_secs(comms_timeout)).await;
                continue;
            }
            Some(Ok(())) => {}
        }

        set_phase(PHASE_SYNCING_DAG);
        tracing::info!("darkirc daemon static sync complete. Starting sync_selected...");
        // Full sync (bodies + headers), or header-only when fast_mode is enabled.
        let sync_res = smol::future::race(
            async {
                wait_for_stop(&stop_rx).await;
                None
            },
            async {
                let res = if fast_mode {
                    event_graph.sync_selected_headers(dags_count).await
                } else {
                    event_graph.sync_selected(dags_count).await
                };
                Some(res)
            },
        )
        .await;
        match sync_res {
            None => break,
            Some(Err(_)) => {
                tracing::warn!("darkirc daemon sync_selected failed — retrying");
                set_phase(PHASE_WAITING_FOR_PEERS);
                sleep_or_stop(&stop_rx, std::time::Duration::from_secs(comms_timeout)).await;
                continue;
            }
            Some(Ok(())) => {}
        }

        set_phase(PHASE_LOADING_HISTORY);
        tracing::info!("darkirc daemon sync_selected complete. Fetching historical events...");
        let order_res = smol::future::race(
            async {
                wait_for_stop(&stop_rx).await;
                None
            },
            async { Some(event_graph.order_events().await) },
        )
        .await;
        let history = match order_res {
            None => break,
            Some(Ok(events)) => events,
            Some(Err(e)) => {
                tracing::error!("darkirc daemon order_events failed: {e}");
                set_phase(PHASE_WAITING_FOR_PEERS);
                sleep_or_stop(&stop_rx, std::time::Duration::from_secs(comms_timeout)).await;
                continue;
            }
        };
        tracing::info!("darkirc daemon fetched {} historical events", history.len());
        replay_history(history, &callback, &relayed_ids).await;
        if this_daemon_should_exit(generation) {
            break;
        }
        DAG_SYNCED.store(1, Ordering::Relaxed);
        set_phase(PHASE_CONNECTED);
        publish_outbound_slots();
        tracing::info!("darkirc daemon DAG synced and history replayed");

        // Monitor until stop or loss of all peers, then outer loop resyncs.
        // Periodic sync_selected covers missed live relays / DAG rotation.
        // Track both monotonic Instant and wall-clock SystemTime: on Android,
        // Instant pauses during deep sleep (Doze), so wall-clock skew > 1h
        // also triggers a DAG rotation refresh on wake.
        let net_sub = p2p.hosts().subscribe_disconnect().await;
        let mut last_dag_refresh = std::time::Instant::now();
        let mut last_dag_refresh_wall = std::time::SystemTime::now();
        loop {
            if this_daemon_should_exit(generation) {
                break;
            }
            if !p2p.is_connected() {
                tracing::info!("darkirc daemon disconnected (0 peers), preparing resync...");
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
                        wait_for_stop(&stop_rx).await;
                        "stop"
                    },
                    async {
                        smol::Timer::after(std::time::Duration::from_secs(30)).await;
                        "timer"
                    },
                ),
            )
            .await;

            if stop_or_disc == "stop" || this_daemon_should_exit(generation) {
                break;
            }

            if stop_or_disc == "disconnect" && !p2p.is_connected() {
                tracing::info!("darkirc daemon detected disconnection, preparing resync...");
                DAG_SYNCED.store(0, Ordering::Relaxed);
                set_phase(PHASE_WAITING_FOR_PEERS);
                break;
            }

            if stop_or_disc == "timer" {
                publish_outbound_slots();
            }

            let mono_elapsed = last_dag_refresh.elapsed();
            let wall_elapsed = std::time::SystemTime::now()
                .duration_since(last_dag_refresh_wall)
                .ok();
            let refresh_due = needs_dag_refresh(
                mono_elapsed,
                wall_elapsed,
                std::time::Duration::from_secs(DAG_REFRESH_SECS),
            );
            if stop_or_disc == "timer" && refresh_due {
                let refresh_res = smol::future::race(
                    async {
                        wait_for_stop(&stop_rx).await;
                        None
                    },
                    async {
                        let res = if fast_mode {
                            event_graph.sync_selected_headers(dags_count).await
                        } else {
                            event_graph.sync_selected(dags_count).await
                        };
                        Some(res)
                    },
                )
                .await;
                match refresh_res {
                    None => break,
                    Some(Ok(())) => {
                        last_dag_refresh = std::time::Instant::now();
                        last_dag_refresh_wall = std::time::SystemTime::now();
                        tracing::info!("darkirc daemon periodic sync_selected ok");
                    }
                    Some(Err(e)) => {
                        tracing::warn!("darkirc daemon periodic sync_selected failed: {e}");
                    }
                }
            }
        }

        if this_daemon_should_exit(generation) {
            break;
        }

        if !p2p.is_connected() {
            sleep_or_stop(&stop_rx, std::time::Duration::from_secs(comms_timeout)).await;
        }
    }

    abandon_after("p2p.stop", p2p.stop()).await;
    if let Some(prune) = prune_task {
        abandon_after("prune.stop", prune.stop()).await;
    }
    abandon_after("mesh.cancel", mesh_task.cancel()).await;
    abandon_after("relay.cancel", relay_task.cancel()).await;

    clear_session_if_owner(generation).await;

    let _ = kvdb.flush_default_mode_async().await;
    Ok(())
}

fn needs_dag_refresh(
    mono_elapsed: std::time::Duration,
    wall_elapsed: Option<std::time::Duration>,
    threshold: std::time::Duration,
) -> bool {
    if mono_elapsed >= threshold {
        return true;
    }
    wall_elapsed.is_some_and(|w| w >= threshold || w.saturating_sub(mono_elapsed) >= threshold)
}

#[cfg(test)]
mod tests {
    use super::*;
    use darkfi_serial::serialize_async;

    #[test]
    fn datastore_reopens_after_the_previous_handle_is_dropped() {
        let dir = std::env::temp_dir().join(format!(
            "darkirc-reopen-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_nanos()
        ));
        std::fs::create_dir_all(&dir).unwrap();
        let held = kvdb_overlay::Database::open_default(&dir).expect("first open");
        assert!(
            kvdb_overlay::Database::open_default(&dir).is_err(),
            "a second open must fail while the first handle still holds the lock"
        );
        drop(held);
        kvdb_overlay::Database::open_default(&dir).expect("reopen after drop");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_fresh_stop_is_kept_and_a_stuck_stop_can_be_replaced() {
        assert!(!may_replace_daemon(STATUS_RUNNING, None));
        assert!(!may_replace_daemon(STATUS_STARTING, Some(60_000)));
        assert!(!may_replace_daemon(STATUS_STOPPING, Some(0)));
        assert!(!may_replace_daemon(
            STATUS_STOPPING,
            Some(STUCK_STOP_MS - 1)
        ));
        assert!(may_replace_daemon(STATUS_STOPPING, Some(STUCK_STOP_MS)));
        assert!(may_replace_daemon(STATUS_STOPPING, None));
        assert!(may_replace_daemon(STATUS_FAILED, None));
        assert!(may_replace_daemon(STATUS_NOT_RUNNING, None));
    }

    #[test]
    fn a_late_thread_does_not_own_a_newer_generation() {
        assert!(daemon_generation_owns(4, 4));
        assert!(!daemon_generation_owns(4, 5));
    }

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
        let auth = crate::transport_manager::current_socks_auth();
        let seeds = tor_socks5_seeds(9050);
        assert_eq!(seeds.len(), DARKIRC_ONION_SEEDS.len());

        for (seed, onion) in seeds.iter().zip(DARKIRC_ONION_SEEDS.iter()) {
            // Scheme + proxy authority must point at the local Tor SOCKS proxy
            // with RFC 1929 credentials so darkfi's socks5 dialer authenticates.
            assert_eq!(seed.scheme(), "socks5");
            assert_eq!(seed.username(), auth.username);
            assert_eq!(seed.password(), Some(auth.password.as_str()));
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

    #[test]
    fn nym_plan_proxies_clearnet_seeds_and_peers_through_nym() {
        let plan = transport_plan(crate::DarkircTransport::Nym, 1080);
        assert_eq!(plan.profile_name, "socks5+tls");
        assert_eq!(plan.seeds.len(), DARKIRC_CLEARNET_SEEDS.len());
        for seed in &plan.seeds {
            assert_eq!(seed.scheme(), "socks5+tls");
            assert_eq!(seed.host_str(), Some("127.0.0.1"));
            assert_eq!(seed.port(), Some(1080));
            assert!(seed.path().ends_with(".dark.fi:9600"), "{seed}");
        }
        assert_eq!(plan.mixed_profiles, vec!["tcp+tls".to_string()]);
        let proxy = plan.nym_socks5_proxy.unwrap();
        assert_eq!(
            (proxy.scheme(), proxy.host_str(), proxy.port()),
            ("socks5", Some("127.0.0.1"), Some(1080))
        );
        assert!(plan.tor_socks5_proxy.is_none());
        assert!(plan.slow_links);
    }

    #[test]
    fn nym_plan_mixes_discovered_tcp_tls_peers_via_nym_proxy() {
        let plan = transport_plan(crate::DarkircTransport::Nym, 1080);
        let peer = url::Url::parse("tcp+tls://203.0.113.7:9600").unwrap();
        let routes = darkfi::net::hosts::HostContainer::mix_host(
            &peer,
            &[plan.profile_name.to_string()],
            &plan.mixed_profiles,
            &plan.tor_socks5_proxy,
            &plan.nym_socks5_proxy,
        );
        assert_eq!(routes.len(), 1);
        assert_eq!(routes[0].scheme(), "socks5+tls");
        assert_eq!(routes[0].port(), Some(1080));
        assert_eq!(routes[0].path(), "/203.0.113.7:9600");
        let onion = url::Url::parse(
            "tor://abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefab.onion:9601",
        )
        .unwrap();
        assert!(darkfi::net::hosts::HostContainer::mix_host(
            &onion,
            &[plan.profile_name.to_string()],
            &plan.mixed_profiles,
            &plan.tor_socks5_proxy,
            &plan.nym_socks5_proxy,
        )
        .is_empty());
    }

    #[test]
    fn tor_and_direct_plans_are_unchanged() {
        let tor = transport_plan(crate::DarkircTransport::Tor, 9050);
        assert_eq!(tor.profile_name, "socks5");
        assert_eq!(tor.mixed_profiles, vec!["tor".to_string()]);
        assert!(tor.nym_socks5_proxy.is_none());
        let direct = transport_plan(crate::DarkircTransport::Direct, 0);
        assert_eq!(direct.profile_name, "tcp+tls");
        assert!(direct.seeds.iter().all(|s| s.scheme() == "tcp+tls"));
        assert!(direct.mixed_profiles.is_empty() && !direct.slow_links);
    }

    #[test]
    fn outbound_hud_json_pads_three_sleeping_slots() {
        let json = format_outbound_slots_json(0, false);
        assert_eq!(
            json,
            r#"[{"slot":0,"state":"sleeping"},{"slot":1,"state":"sleeping"},{"slot":2,"state":"sleeping"}]"#
        );
    }

    #[test]
    fn outbound_hud_json_never_includes_peer_urls() {
        let json = format_outbound_slots_json(1, true);
        assert!(!json.contains("url"));
        assert!(json.contains(r#""state":"connected""#));
        assert!(json.contains(r#""state":"handshake""#));
    }

    #[test]
    fn outbound_slots_api_when_daemon_stopped_is_sleeping_hud() {
        let json = darkirc_outbound_slots();
        assert!(json.contains("sleeping"));
        assert!(json.contains("\"slot\":2"));
    }

    #[test]
    fn public_channel_privmsg_stays_plaintext() {
        let mut msg = new_privmsg("#dev".into(), "alice".into(), "hello".into());
        try_encrypt_privmsg(&mut msg).unwrap();
        assert_eq!(msg.channel, "#dev");
        assert_eq!(msg.msg, "hello");
    }

    static DM_TEST_MUTEX: Mutex<()> = Mutex::new(());

    #[test]
    fn dm_encrypts_channel_nick_and_message() {
        let _guard = DM_TEST_MUTEX.lock().unwrap();
        clear_dm_contacts();
        let kp = crate::generate_dm_keypair();
        let sk = bs58::decode(&kp.secret_b58).into_vec().unwrap();
        let pk = bs58::decode(&kp.public_b58).into_vec().unwrap();
        register_dm_contact("bob".into(), sk, pk).unwrap();
        let mut msg = new_privmsg("bob".into(), "alice".into(), "hello".into());
        try_encrypt_privmsg(&mut msg).unwrap();
        assert_ne!(msg.channel, "bob");
        assert_ne!(msg.nick, "alice");
        assert_ne!(msg.msg, "hello");
        clear_dm_contacts();
    }

    #[test]
    fn dm_register_encrypt_decrypt_unregister_clear_in_memory_only() {
        let _guard = DM_TEST_MUTEX.lock().unwrap();
        clear_dm_contacts();
        // Ensure zero disk datastore path is needed or touched.
        assert!(current_datastore_path().is_none());

        let alice = crate::generate_dm_keypair();
        let bob = crate::generate_dm_keypair();
        let alice_sk = bs58::decode(&alice.secret_b58).into_vec().unwrap();
        let alice_pk = bs58::decode(&alice.public_b58).into_vec().unwrap();
        let bob_sk = bs58::decode(&bob.secret_b58).into_vec().unwrap();
        let bob_pk = bs58::decode(&bob.public_b58).into_vec().unwrap();

        // Alice registers Bob, encrypts a DM in memory.
        register_dm_contact("bob".into(), alice_sk, bob_pk).unwrap();
        let mut wire = new_privmsg("bob".into(), "alice".into(), "secret hello".into());
        try_encrypt_privmsg(&mut wire).unwrap();

        // Now unregister Bob on Alice's side -> cannot send or decrypt.
        unregister_dm_contact("bob".into());
        let mut blocked = new_privmsg("bob".into(), "alice".into(), "blocked".into());
        assert!(try_encrypt_privmsg(&mut blocked).is_err());
        assert!(present_privmsg(wire.clone()).is_none());

        // Bob registers Alice in memory -> decrypts Alice's wire message cleanly.
        register_dm_contact("alice".into(), bob_sk, alice_pk).unwrap();
        let decrypted = present_privmsg(wire.clone()).expect("bob decrypts alice DM");
        assert_eq!(decrypted.channel, "alice");
        assert_eq!(decrypted.nick, "alice");
        assert_eq!(decrypted.msg, "secret hello");
        assert!(!decrypted.is_outgoing);

        // clear_dm_contacts wipes everything from memory.
        clear_dm_contacts();
        assert!(present_privmsg(wire).is_none());
    }

    #[test]
    fn undecryptable_foreign_dm_is_dropped_not_forwarded_raw() {
        let _guard = DM_TEST_MUTEX.lock().unwrap();
        clear_dm_contacts();

        let alice = crate::generate_dm_keypair();
        let bob = crate::generate_dm_keypair();
        let carol = crate::generate_dm_keypair();
        let alice_sk = bs58::decode(&alice.secret_b58).into_vec().unwrap();
        let bob_pk = bs58::decode(&bob.public_b58).into_vec().unwrap();
        let carol_sk = bs58::decode(&carol.secret_b58).into_vec().unwrap();
        let carol_pk = bs58::decode(&carol.public_b58).into_vec().unwrap();

        // Alice encrypts to Bob.
        register_dm_contact("bob".into(), alice_sk, bob_pk).unwrap();
        let mut wire = new_privmsg("bob".into(), "alice".into(), "for bob only".into());
        try_encrypt_privmsg(&mut wire).unwrap();
        clear_dm_contacts();

        // Carol only has a contact with herself; foreign DM for Bob MUST drop cleanly.
        register_dm_contact("dave".into(), carol_sk, carol_pk).unwrap();
        assert!(present_privmsg(wire).is_none());
        clear_dm_contacts();
    }

    #[test]
    fn dm_register_rejects_empty_nick_and_short_key() {
        let empty = register_dm_contact(String::new(), vec![0; 32], vec![0; 32]);
        assert!(empty.is_err());
        let hash_prefix = register_dm_contact("#bob".into(), vec![1; 32], vec![2; 32]);
        assert!(hash_prefix.is_err());
        let short = register_dm_contact("bob".into(), vec![1; 16], vec![0; 32]);
        assert!(short.is_err());
    }

    #[test]
    fn bounded_id_set_caps_size_and_evicts_oldest() {
        let mut set = BoundedIdSet::new(3);
        assert!(set.insert("e1".into()));
        assert!(!set.insert("e1".into()));
        assert!(set.insert("e2".into()));
        assert!(set.insert("e3".into()));
        assert!(set.insert("e4".into()));
        assert_eq!(set.set.len(), 3);
        assert!(!set.set.contains("e1"));
        assert!(set.set.contains("e2"));
        assert!(set.set.contains("e4"));
    }

    #[test]
    fn configure_darkirc_clamps_dags_and_sets_fast_mode() {
        configure_darkirc(0, true);
        assert_eq!(
            DARKIRC_DAGS_COUNT.load(Ordering::Relaxed),
            DARKIRC_MAX_DAGS as u32
        );
        assert!(DARKIRC_FAST_MODE.load(Ordering::Relaxed));

        configure_darkirc(6, false);
        assert_eq!(DARKIRC_DAGS_COUNT.load(Ordering::Relaxed), 6);
        assert!(!DARKIRC_FAST_MODE.load(Ordering::Relaxed));

        configure_darkirc(999, false);
        assert_eq!(
            DARKIRC_DAGS_COUNT.load(Ordering::Relaxed),
            DARKIRC_MAX_DAGS as u32
        );
    }

    #[test]
    fn dm_decrypt_rejects_non_zero_channel_sentinel() {
        let _guard = DM_TEST_MUTEX.lock().unwrap();
        clear_dm_contacts();
        let alice = crate::generate_dm_keypair();
        let bob = crate::generate_dm_keypair();
        let alice_sk = bs58::decode(&alice.secret_b58).into_vec().unwrap();
        let alice_pk = bs58::decode(&alice.public_b58).into_vec().unwrap();
        let bob_sk = bs58::decode(&bob.secret_b58).into_vec().unwrap();
        let bob_pk = bs58::decode(&bob.public_b58).into_vec().unwrap();

        let a_sk = crypto_box::SecretKey::from_slice(&alice_sk).unwrap();
        let b_pk = crypto_box::PublicKey::from_slice(&bob_pk).unwrap();
        let saltbox = crypto_box::ChaChaBox::new(&b_pk, &a_sk);
        let self_box = crypto_box::ChaChaBox::new(&a_sk.public_key(), &a_sk);
        let bad_channel = Privmsg {
            version: PRIVMSG_VERSION,
            msg_type: PRIVMSG_MSG_TYPE,
            channel: irc2::crypto::saltbox::encrypt(&saltbox, b"not-zero-sentinel"),
            nick: irc2::crypto::saltbox::encrypt(&self_box, &[0x00; MAX_NICK_LEN]),
            msg: irc2::crypto::saltbox::encrypt(&saltbox, b"hello"),
        };

        register_dm_contact("alice".into(), bob_sk, alice_pk).unwrap();
        assert!(present_privmsg(bad_channel).is_none());
        clear_dm_contacts();
    }

    #[test]
    fn needs_dag_refresh_detects_wall_clock_suspend_skew() {
        let threshold = std::time::Duration::from_secs(3600);
        // Short monotonic and short wall -> no refresh
        assert!(!needs_dag_refresh(
            std::time::Duration::from_secs(30),
            Some(std::time::Duration::from_secs(30)),
            threshold,
        ));
        // Suspended > 1h: monotonic barely moved, wall jumped > 1h -> refresh
        assert!(needs_dag_refresh(
            std::time::Duration::from_secs(30),
            Some(std::time::Duration::from_secs(3700)),
            threshold,
        ));
        // Normal uptime >= 1h with backwards wall clock -> still refreshes on monotonic
        assert!(needs_dag_refresh(
            std::time::Duration::from_secs(3600),
            None,
            threshold,
        ));
    }
}
