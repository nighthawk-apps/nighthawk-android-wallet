//! Nighthawk Mesh protocol (sans-I/O). Radios live in Kotlin / Swift.

#[allow(dead_code)]
mod abi;
mod allowlist;
mod bulk_session;
mod engine;
mod fragment;
mod gcs;
mod identity;
mod lwd_ctrl;
mod packet;
mod session;
mod splice;
mod types;

pub use allowlist::{gateway_eligible, validate_lwd_ctrl, LwdCtrlError};
pub use bulk_session::{
    may_start_bulk, BulkOffer as MeshBulkOffer, KIND_AWARE, KIND_MULTIPEER, KIND_SOFTAP,
    MAX_BULK_BYTES, MAX_BULK_SECS,
};
pub use engine::{EngineEvent, IngestError, LwdRequest, MeshEngine, OsPowerState};
pub use fragment::{split as fragment_split, FragError, FragmentAssembler};
pub use identity::MeshIdentity;
pub use lwd_ctrl::{decode_mesh_event, encode_mesh_event};
pub use packet::{decode, encode, CodecError};
pub use splice::{set_lwd_splice, LwdSpliceFn};
pub use types::{
    MeshPacket, PacketType, BLE_ALLOWED_LWD, BLE_FORBIDDEN_LWD, DAG_SYNC_REPLY_MAX, DEFAULT_TTL,
    FRAGMENT_CHUNK, LOCAL_TTL, LWD_CTRL_MAX, MAX_PAYLOAD, NH_ANNOUNCE, NH_DAG_EVENT, NH_DAG_SYNC,
    NH_LWD_CTRL, SENDER_LEN, EVENT_INNER_MAX,
};

use std::collections::VecDeque;
use std::sync::{Mutex, OnceLock};

use bulk_session::BulkOffer;
use engine::IngestError as EngErr;

fn engine() -> &'static Mutex<MeshEngine> {
    static ENGINE: OnceLock<Mutex<MeshEngine>> = OnceLock::new();
    ENGINE.get_or_init(|| Mutex::new(MeshEngine::new()))
}

fn hold() -> &'static Mutex<VecDeque<Vec<u8>>> {
    static HOLD: OnceLock<Mutex<VecDeque<Vec<u8>>>> = OnceLock::new();
    HOLD.get_or_init(|| Mutex::new(VecDeque::new()))
}

pub fn start_mesh() -> Result<(), String> {
    crate::panic_fence::catch_string("start_mesh", || {
        let mut g = engine().lock().map_err(|e| e.to_string())?;
        g.set_mesh_on(true);
        Ok(())
    })
}

pub fn stop_mesh() -> Result<(), String> {
    crate::panic_fence::catch_string("stop_mesh", || {
        let mut g = engine().lock().map_err(|e| e.to_string())?;
        g.set_mesh_on(false);
        Ok(())
    })
}

pub fn mesh_status() -> String {
    engine()
        .lock()
        .map(|g| g.status_json())
        .unwrap_or_else(|_| "{\"mesh_on\":false,\"error\":\"lock\"}".into())
}

pub fn mesh_peer_id() -> [u8; SENDER_LEN] {
    engine()
        .lock()
        .map(|g| g.peer_id())
        .unwrap_or([0u8; SENDER_LEN])
}

pub fn mesh_last_gateway_peer() -> Option<[u8; SENDER_LEN]> {
    engine().lock().ok().and_then(|g| g.last_gateway_peer())
}

pub fn mesh_set_gateway_eligible(opt_in: bool) -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.set_gateway_opt_in(opt_in);
    Ok(())
}

pub fn mesh_wipe() -> Result<(), String> {
    crate::panic_fence::catch_string("mesh_wipe", || {
        if let Ok(mut h) = hold().lock() {
            h.clear();
        }
        clear_bulk_tcp_override();
        let mut g = engine().lock().map_err(|e| e.to_string())?;
        g.wipe();
        Ok(())
    })
}

fn bulk_tcp() -> &'static Mutex<Option<(String, u16)>> {
    static BULK: OnceLock<Mutex<Option<(String, u16)>>> = OnceLock::new();
    BULK.get_or_init(|| Mutex::new(None))
}

/// Client UnifOMR dials this TCP instead of the LWD host. TLS name + pin
/// still use the real lightwalletd URL. Gateway must not set this.
pub fn set_bulk_tcp_override(host: Option<String>, port: u16) -> Result<(), String> {
    let mut g = bulk_tcp().lock().map_err(|e| e.to_string())?;
    *g = host.map(|h| (h, port));
    Ok(())
}

pub fn bulk_tcp_override() -> Option<(String, u16)> {
    bulk_tcp().lock().ok().and_then(|g| g.clone())
}

pub fn clear_bulk_tcp_override() {
    if let Ok(mut g) = bulk_tcp().lock() {
        *g = None;
    }
}

pub fn mesh_submit_bulk_join(dest: [u8; SENDER_LEN]) -> Result<[u8; 16], String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.submit_bulk_join(dest).map_err(|e| format!("{e:?}"))
}

pub fn mesh_submit_bulk_offer(
    dest: [u8; SENDER_LEN],
    kind: u8,
    port: u16,
    ssid: &str,
    psk: &[u8],
    session_id: [u8; 16],
) -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.submit_bulk_offer(
        dest,
        BulkOffer {
            session_id,
            kind,
            port,
            ssid: ssid.to_string(),
            psk: psk.to_vec(),
        },
    )
    .map_err(|e| format!("{e:?}"))
}

pub fn mesh_record_bulk_bytes(n: u64) -> bool {
    engine()
        .lock()
        .map(|mut g| g.record_bulk_bytes(n))
        .unwrap_or(false)
}

pub fn mesh_set_os_power_state(
    foreground: bool,
    charging: bool,
    unmetered: bool,
) -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.set_power(OsPowerState {
        foreground,
        charging,
        unmetered,
    });
    Ok(())
}

pub fn mesh_ingest_link_bytes(bytes: Vec<u8>) -> Result<(), String> {
    crate::panic_fence::catch_string("mesh_ingest_link_bytes", || {
        let mut g = engine().lock().map_err(|e| e.to_string())?;
        match g.ingest_bytes(&bytes) {
            Ok(()) | Err(EngErr::DroppedDuplicate) => Ok(()),
            Err(e) => Err(format!("{e:?}")),
        }
    })
}

pub fn mesh_pop_outbound() -> Vec<Vec<u8>> {
    engine()
        .lock()
        .map(|mut g| g.pop_outbound())
        .unwrap_or_default()
}

/// One outbound frame for the C ABI. Does not drop siblings.
pub fn mesh_pop_one_outbound() -> Option<Vec<u8>> {
    let mut h = hold().lock().ok()?;
    if h.is_empty() {
        for f in mesh_pop_outbound() {
            h.push_back(f);
        }
    }
    h.pop_front()
}

pub fn mesh_submit_lwd_ctrl(
    dest: [u8; SENDER_LEN],
    method: &str,
    body: &[u8],
) -> Result<[u8; 16], String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.submit_lwd_ctrl(dest, method, body)
        .map_err(|e| format!("{e:?}"))
}

pub fn mesh_publish_dag(body: Vec<u8>) -> Result<(), String> {
    mesh_publish_event(event_id_from_body(&body), body)
}

pub fn mesh_publish_event(id: [u8; 16], body: Vec<u8>) -> Result<(), String> {
    crate::panic_fence::catch_string("mesh_publish_event", || {
        let mut g = engine().lock().map_err(|e| e.to_string())?;
        if !g.is_on() {
            return Err("mesh_off".into());
        }
        g.publish_event(id, body).map_err(|e| format!("{e:?}"))
    })
}

pub fn mesh_neighbor_up(dest: [u8; SENDER_LEN]) -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.neighbor_up(dest).map_err(|e| format!("{e:?}"))
}

pub fn mesh_neighbor_down(dest: [u8; SENDER_LEN]) -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    g.neighbor_down(dest);
    Ok(())
}

pub fn mesh_pop_inbound_event() -> Option<([u8; 16], Vec<u8>)> {
    engine().lock().ok().and_then(|mut g| g.pop_inbound_event())
}

fn event_id_from_body(body: &[u8]) -> [u8; 16] {
    let h = blake3::hash(body);
    let mut id = [0u8; 16];
    id.copy_from_slice(&h.as_bytes()[..16]);
    id
}

pub fn mesh_request_dag_sync() -> Result<(), String> {
    let mut g = engine().lock().map_err(|e| e.to_string())?;
    if !g.is_on() {
        return Err("mesh_off".into());
    }
    g.request_dag_sync();
    Ok(())
}

/// Offline wallet path: small allowlisted RPCs toward the last gateway.
/// UnifOMR methods are rejected here — use [`mesh_submit_bulk_join`].
pub fn mesh_try_offline_ctrl(_method: &str, _body: &[u8]) -> Result<[u8; 16], String> {
    Err("mesh_lwd_disabled".into())
}

/// LWD splice is disabled this pass (EventGraph mesh only).
pub fn mesh_pump_gateway() {}

pub fn mesh_pop_events_json() -> String {
    let Ok(mut g) = engine().lock() else {
        return "[]".into();
    };
    let ev = g.pop_events();
    let mut parts = Vec::new();
    for e in ev {
        match e {
            EngineEvent::EventPut { from, payload, .. } => {
                parts.push(format!(
                    "{{\"t\":\"event\",\"from\":\"{}\",\"n\":{}}}",
                    hex8(from),
                    payload.len()
                ));
            }
            EngineEvent::CacheFull { dropped } => {
                parts.push(format!("{{\"t\":\"cache_full\",\"dropped\":{dropped}}}"));
            }
            EngineEvent::DagSync { missing } => {
                let remaining = missing.len().saturating_sub(DAG_SYNC_REPLY_MAX);
                parts.push(format!(
                    "{{\"t\":\"dagsync\",\"missing\":{},\"remaining\":{}}}",
                    missing.len(),
                    remaining
                ));
            }
            EngineEvent::Ping { .. } => {}
        }
    }
    format!("[{}]", parts.join(","))
}

fn hex8(id: [u8; SENDER_LEN]) -> String {
    hex_bytes(&id)
}

fn hex_bytes(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn json_escape(_s: &str) -> String {
    String::new()
}

#[cfg(test)]
mod tests;
