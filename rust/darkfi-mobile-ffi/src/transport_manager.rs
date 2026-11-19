//! One privacy route for the wallet, DarkIRC, and fiat.
//!
//! `Nym`, `Tor`, and `Direct` switch together. An optional BLE mesh flag is
//! independent. Startup does not wait for the mixnet: proxies bootstrap on a
//! background thread and the sync loop waits on [`crate::sync::RouteGate`].
//! "Continue without Nym" is a process-local override and is not persisted.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};

use rand::RngCore;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TransportKind {
    Nym,
    Tor,
    Direct,
}

impl TransportKind {
    pub fn as_str(self) -> &'static str {
        match self {
            TransportKind::Nym => "nym",
            TransportKind::Tor => "tor",
            TransportKind::Direct => "direct",
        }
    }

    pub fn from_i32(kind: i32) -> Self {
        match kind {
            1 => TransportKind::Tor,
            2 => TransportKind::Direct,
            _ => TransportKind::Nym,
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum TransportPhase {
    Idle,
    Bootstrapping,
    Connecting,
    Ready,
    Degraded,
}

impl TransportPhase {
    pub fn as_str(self) -> &'static str {
        match self {
            TransportPhase::Idle => "idle",
            TransportPhase::Bootstrapping => "bootstrapping",
            TransportPhase::Connecting => "connecting",
            TransportPhase::Ready => "ready",
            TransportPhase::Degraded => "degraded",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SocksAuth {
    pub username: String,
    pub password: String,
}

#[derive(Clone, Debug)]
struct State {
    kind: TransportKind,
    session_direct: bool,
    mesh_enabled: bool,
    nym_port: u16,
    tor_port: u16,
    socks_auth: SocksAuth,
}

fn state() -> &'static Mutex<State> {
    static STATE: OnceLock<Mutex<State>> = OnceLock::new();
    STATE.get_or_init(|| {
        Mutex::new(State {
            kind: TransportKind::Nym,
            session_direct: false,
            mesh_enabled: false,
            nym_port: 1080,
            tor_port: 9050,
            socks_auth: fresh_socks_auth(),
        })
    })
}

fn lock() -> std::sync::MutexGuard<'static, State> {
    match state().lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    }
}

fn fresh_socks_auth() -> SocksAuth {
    let mut user = [0u8; 8];
    let mut pass = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut user);
    rand::thread_rng().fill_bytes(&mut pass);
    SocksAuth {
        username: hex_encode(&user),
        password: hex_encode(&pass),
    }
}

fn hex_encode(bytes: &[u8]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push(HEX[(byte >> 4) as usize] as char);
        out.push(HEX[(byte & 0x0f) as usize] as char);
    }
    out
}

/// Effective route after the session-only Direct override.
pub fn effective_kind() -> TransportKind {
    let guard = lock();
    if guard.session_direct {
        TransportKind::Direct
    } else {
        guard.kind
    }
}

pub fn session_direct() -> bool {
    lock().session_direct
}

pub fn mesh_enabled() -> bool {
    lock().mesh_enabled
}

/// RFC 1929 credentials for the local SOCKS5 proxies. Clients that only offer
/// method `0x00` are still accepted so DarkIRC and unit tests keep working.
pub fn current_socks_auth() -> SocksAuth {
    lock().socks_auth.clone()
}

/// Pick a SOCKS5 method. Prefer username/password when the client offers it.
pub fn socks_method(offered: &[u8]) -> u8 {
    if offered.contains(&0x02) {
        0x02
    } else if offered.contains(&0x00) {
        0x00
    } else {
        0xff
    }
}

pub fn socks_password_ok(username: &str, password: &str) -> bool {
    let auth = current_socks_auth();
    auth.username == username && auth.password == password
}

pub fn snapshot_json() -> String {
    let guard = lock();
    let kind = if guard.session_direct {
        TransportKind::Direct
    } else {
        guard.kind
    };
    let phase = phase_for(kind);
    let port = match kind {
        TransportKind::Nym => Some(guard.nym_port),
        TransportKind::Tor => Some(guard.tor_port),
        TransportKind::Direct => None,
    };
    let port_json = match port {
        Some(port) => port.to_string(),
        None => "null".to_string(),
    };
    format!(
        "{{\"kind\":\"{}\",\"phase\":\"{}\",\"mesh\":{},\"sessionDirect\":{},\"socksPort\":{}}}",
        kind.as_str(),
        phase.as_str(),
        guard.mesh_enabled,
        guard.session_direct,
        port_json,
    )
}

fn phase_for(kind: TransportKind) -> TransportPhase {
    match kind {
        TransportKind::Direct => TransportPhase::Ready,
        TransportKind::Nym => nym_phase(),
        TransportKind::Tor => match crate::tor::arti_phase() {
            "connected" => TransportPhase::Ready,
            "bootstrapping" => TransportPhase::Bootstrapping,
            "failed" => TransportPhase::Degraded,
            _ => TransportPhase::Idle,
        },
    }
}

#[cfg(feature = "nym")]
fn nym_phase() -> TransportPhase {
    match crate::nym::nym_status().as_str() {
        "connected" => TransportPhase::Ready,
        "reconnecting" => TransportPhase::Connecting,
        "bootstrapping" => TransportPhase::Bootstrapping,
        "failed" => TransportPhase::Degraded,
        _ => TransportPhase::Idle,
    }
}

#[cfg(not(feature = "nym"))]
fn nym_phase() -> TransportPhase {
    TransportPhase::Idle
}

/// Record the route chosen when a wallet handle opens. Does not block.
pub fn note_wallet_route(route: crate::sync::RouteGate, nym_port: u16, tor_port: u16) {
    let mut guard = lock();
    guard.kind = match route {
        crate::sync::RouteGate::Nym => TransportKind::Nym,
        crate::sync::RouteGate::Tor => TransportKind::Tor,
        crate::sync::RouteGate::Direct => TransportKind::Direct,
    };
    if nym_port > 0 {
        guard.nym_port = nym_port;
    }
    if tor_port > 0 {
        guard.tor_port = tor_port;
    }
}

pub fn note_proxy_started(kind: TransportKind, port: u16) {
    let mut guard = lock();
    match kind {
        TransportKind::Nym => {
            guard.kind = TransportKind::Nym;
            if port > 0 {
                guard.nym_port = port;
            }
        }
        TransportKind::Tor => {
            guard.kind = TransportKind::Tor;
            if port > 0 {
                guard.tor_port = port;
            }
        }
        TransportKind::Direct => guard.kind = TransportKind::Direct,
    }
}

pub fn note_proxy_stopped(kind: TransportKind) {
    let mut guard = lock();
    // A session-only Direct override stops Nym without forgetting the stored route.
    if guard.session_direct {
        return;
    }
    if guard.kind == kind {
        guard.kind = TransportKind::Direct;
    }
}

/// Switch wallet SOCKS, and the recorded DarkIRC/fiat route, without waiting
/// for Nym or Tor bootstrap. Callers reopen the wallet handle and DarkIRC.
pub fn apply(kind: TransportKind, nym_port: u16, tor_port: u16) -> Result<(), String> {
    {
        let mut guard = lock();
        guard.session_direct = false;
        guard.kind = kind;
        if nym_port > 0 {
            guard.nym_port = nym_port;
        }
        if tor_port > 0 {
            guard.tor_port = tor_port;
        }
        guard.socks_auth = fresh_socks_auth();
    }
    let (nym_port, tor_port) = {
        let guard = lock();
        (guard.nym_port, guard.tor_port)
    };
    crate::lightwallet_client::invalidate_lightwallet_client_pool();
    match kind {
        TransportKind::Nym => {
            crate::tor::stop_arti_proxy();
            #[cfg(feature = "nym")]
            {
                crate::nym::start_nym_proxy(nym_port).map_err(|e| format!("{e}"))?;
            }
            #[cfg(not(feature = "nym"))]
            {
                let _ = nym_port;
                return Err("nym feature not compiled".into());
            }
            crate::lightwallet_client::set_default_socks5_proxy(Some((
                "127.0.0.1".into(),
                nym_port,
            )));
        }
        TransportKind::Tor => {
            #[cfg(feature = "nym")]
            crate::nym::stop_nym_proxy();
            crate::tor::start_arti_proxy(tor_port).map_err(|e| format!("{e}"))?;
            crate::lightwallet_client::set_default_socks5_proxy(Some((
                "127.0.0.1".into(),
                tor_port,
            )));
        }
        TransportKind::Direct => {
            #[cfg(feature = "nym")]
            crate::nym::stop_nym_proxy();
            crate::tor::stop_arti_proxy();
            crate::lightwallet_client::set_default_socks5_proxy(None);
        }
    }
    Ok(())
}

/// Session-only clearnet. Does not rewrite the stored Nym/Tor preference.
pub fn continue_without_nym() {
    {
        let mut guard = lock();
        guard.session_direct = true;
        guard.socks_auth = fresh_socks_auth();
    }
    #[cfg(feature = "nym")]
    crate::nym::stop_nym_proxy();
    crate::lightwallet_client::set_default_socks5_proxy(None);
    crate::lightwallet_client::invalidate_lightwallet_client_pool();
}

pub fn clear_session_override() {
    lock().session_direct = false;
}

pub fn set_mesh_enabled(on: bool) {
    lock().mesh_enabled = on;
    if on {
        let _ = crate::mesh::start_mesh();
    } else {
        let _ = crate::mesh::stop_mesh();
    }
}

static SUSPENDED: AtomicBool = AtomicBool::new(false);

pub fn on_network_changed() {
    #[cfg(feature = "nym")]
    crate::nym::on_network_changed();
    crate::tor::on_network_changed();
    crate::lightwallet_client::invalidate_lightwallet_client_pool();
    let mut guard = lock();
    guard.socks_auth = fresh_socks_auth();
}

pub fn on_background() {
    SUSPENDED.store(true, Ordering::SeqCst);
    #[cfg(feature = "nym")]
    crate::nym::on_suspend();
    crate::tor::on_suspend();
}

pub fn on_foreground() {
    let was = SUSPENDED.swap(false, Ordering::SeqCst);
    if was {
        on_network_changed();
    }
}

pub fn suspended() -> bool {
    SUSPENDED.load(Ordering::SeqCst)
}

#[no_mangle]
pub extern "C" fn nh_transport_on_network_changed() -> i32 {
    on_network_changed();
    0
}

#[no_mangle]
pub extern "C" fn nh_transport_on_background() -> i32 {
    on_background();
    0
}

#[no_mangle]
pub extern "C" fn nh_transport_on_foreground() -> i32 {
    on_foreground();
    0
}

#[no_mangle]
pub extern "C" fn nh_transport_set_mesh(on: i32) -> i32 {
    set_mesh_enabled(on != 0);
    0
}

#[no_mangle]
pub extern "C" fn nh_transport_continue_without_nym() -> i32 {
    continue_without_nym();
    0
}

#[no_mangle]
pub extern "C" fn nh_transport_clear_session_override() -> i32 {
    clear_session_override();
    0
}

/// `kind`: 0 Nym, 1 Tor, 2 Direct. Ports `0` keep the last value.
#[no_mangle]
pub extern "C" fn nh_transport_apply(kind: i32, nym_port: i32, tor_port: i32) -> i32 {
    let nym_port = u16::try_from(nym_port).unwrap_or(0);
    let tor_port = u16::try_from(tor_port).unwrap_or(0);
    match apply(TransportKind::from_i32(kind), nym_port, tor_port) {
        Ok(()) => 0,
        Err(_) => 1,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn socks_method_prefers_userpass_and_allows_none() {
        assert_eq!(socks_method(&[0x00]), 0x00);
        assert_eq!(socks_method(&[0x00, 0x02]), 0x02);
        assert_eq!(socks_method(&[0x01]), 0xff);
    }

    #[test]
    fn socks_password_matches_the_session_credential() {
        let auth = current_socks_auth();
        assert!(socks_password_ok(&auth.username, &auth.password));
        assert!(!socks_password_ok(&auth.username, "nope"));
    }

    #[test]
    fn snapshot_json_names_the_route() {
        let json = snapshot_json();
        assert!(json.contains("\"kind\":"));
        assert!(json.contains("\"phase\":"));
        assert!(json.contains("\"sessionDirect\":"));
        assert!(json.contains("\"mesh\":"));
    }
}
