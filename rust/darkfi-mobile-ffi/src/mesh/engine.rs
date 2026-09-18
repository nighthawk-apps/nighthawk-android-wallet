//! Serial mesh engine: neighbor crypto_box sessions, encrypted EventPut / GCS.

use std::collections::{HashMap, VecDeque};
use std::sync::Arc;

use super::allowlist::{gateway_eligible, validate_lwd_ctrl, LwdCtrlError};
use super::fragment::{split, FragError, FragmentAssembler};
use super::gcs::{encode_gcs, gcs_contains};
use super::identity::MeshIdentity;
use super::bulk_session::{BulkOffer, BulkQuota, ID_ROTATE_MS};
use super::lwd_ctrl::{
    decode_inner, encode_inner, LwdInner, KIND_DAG_SYNC, KIND_EVENT_PUT, KIND_REQ,
};
use super::packet::{decode, encode, CodecError};
use super::session::{HsOutcome, SessionError, SessionTable};
use super::types::{
    unix_ms, MeshPacket, PacketType, DAG_CACHE_MAX_BYTES, DAG_CACHE_MAX_EVENTS, DAG_SYNC_REPLY_MAX,
    DEDUP_CAP, DEDUP_TTL_MS, EVENT_INNER_MAX, FRAGMENT_CHUNK, HANDSHAKE_TIMEOUT_MS,
    INBOUND_RATE_MAX, INBOUND_RATE_WINDOW_MS, SENDER_LEN,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct OsPowerState {
    pub foreground: bool,
    pub charging: bool,
    pub unmetered: bool,
}

impl Default for OsPowerState {
    fn default() -> Self {
        Self {
            foreground: true,
            charging: false,
            unmetered: false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum IngestError {
    Codec(CodecError),
    Frag(FragError),
    Lwd(LwdCtrlError),
    Session(SessionError),
    DroppedDuplicate,
    PlaintextCtrl,
    NotDirected,
    NotForeground,
    RateLimited,
    BulkDenied,
    TooLarge,
}

impl From<CodecError> for IngestError {
    fn from(e: CodecError) -> Self {
        Self::Codec(e)
    }
}

impl From<FragError> for IngestError {
    fn from(e: FragError) -> Self {
        Self::Frag(e)
    }
}

impl From<SessionError> for IngestError {
    fn from(e: SessionError) -> Self {
        Self::Session(e)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EngineEvent {
    /// Decrypted EventGraph payload (`event_len || event || blob_len || blob`).
    EventPut {
        from: [u8; SENDER_LEN],
        id: [u8; 16],
        payload: Vec<u8>,
    },
    DagSync {
        missing: Vec<[u8; 16]>,
    },
    CacheFull {
        dropped: usize,
    },
    Ping {
        sender: [u8; SENDER_LEN],
    },
}

#[derive(Clone)]
pub struct LwdRequest {
    pub from: [u8; SENDER_LEN],
    pub corr_id: [u8; 16],
    pub method: String,
    pub body: Vec<u8>,
}

pub type TestSplice = Arc<dyn Fn(&str, &[u8]) -> Result<Vec<u8>, String> + Send + Sync>;

pub struct MeshEngine {
    identity: MeshIdentity,
    assembler: FragmentAssembler,
    seen: HashMap<[u8; 16], u64>,
    seen_order: VecDeque<[u8; 16]>,
    dag_ids: Vec<[u8; 16]>,
    dag_payloads: HashMap<[u8; 16], Vec<u8>>,
    dag_bytes: usize,
    outbound: Vec<Vec<u8>>,
    events: Vec<EngineEvent>,
    inbound_events: Vec<( [u8; 16], Vec<u8> )>,
    sessions: SessionTable,
    pending_lwd: Vec<LwdRequest>,
    last_gateway: Option<[u8; SENDER_LEN]>,
    mesh_on: bool,
    gateway_opt_in: bool,
    wifi_share_opt_in: bool,
    power: OsPowerState,
    degree: u8,
    inbound_stamps: HashMap<[u8; SENDER_LEN], VecDeque<u64>>,
    sync_seen: HashMap<[u8; 16], u64>,
    sync_sent: HashMap<[u8; SENDER_LEN], std::collections::HashSet<[u8; 16]>>,
    last_rotate_ms: u64,
    bulk_quota: Option<BulkQuota>,
    pub test_splice: Option<TestSplice>,
}

impl MeshEngine {
    pub fn new() -> Self {
        Self {
            identity: MeshIdentity::generate(),
            assembler: FragmentAssembler::default(),
            seen: HashMap::new(),
            seen_order: VecDeque::new(),
            dag_ids: Vec::new(),
            dag_payloads: HashMap::new(),
            dag_bytes: 0,
            outbound: Vec::new(),
            events: Vec::new(),
            inbound_events: Vec::new(),
            sessions: SessionTable::default(),
            pending_lwd: Vec::new(),
            last_gateway: None,
            mesh_on: false,
            gateway_opt_in: false,
            wifi_share_opt_in: true,
            power: OsPowerState::default(),
            degree: 1,
            inbound_stamps: HashMap::new(),
            sync_seen: HashMap::new(),
            sync_sent: HashMap::new(),
            last_rotate_ms: unix_ms(),
            bulk_quota: None,
            test_splice: None,
        }
    }

    pub fn peer_id(&self) -> [u8; SENDER_LEN] {
        self.identity.peer_id()
    }

    pub fn last_gateway_peer(&self) -> Option<[u8; SENDER_LEN]> {
        self.last_gateway
    }

    pub fn set_mesh_on(&mut self, on: bool) {
        self.mesh_on = on;
    }

    pub fn is_on(&self) -> bool {
        self.mesh_on
    }

    pub fn set_gateway_opt_in(&mut self, on: bool) {
        self.gateway_opt_in = on;
        self.wifi_share_opt_in = on;
    }

    pub fn set_wifi_share_opt_in(&mut self, on: bool) {
        self.wifi_share_opt_in = on;
        self.gateway_opt_in = on;
    }

    pub fn set_power(&mut self, power: OsPowerState) {
        self.power = power;
    }

    pub fn set_degree(&mut self, degree: u8) {
        self.degree = degree;
    }

    pub fn rotate_identity(&mut self) {
        self.identity.rotate();
        self.sessions.wipe();
    }

    pub fn wipe(&mut self) {
        self.identity.wipe();
        self.seen.clear();
        self.seen_order.clear();
        self.dag_ids.clear();
        self.dag_payloads.clear();
        self.dag_bytes = 0;
        self.outbound.clear();
        self.events.clear();
        self.inbound_events.clear();
        self.sessions.wipe();
        self.pending_lwd.clear();
        self.last_gateway = None;
        self.mesh_on = false;
        self.gateway_opt_in = false;
        self.wifi_share_opt_in = true;
        self.inbound_stamps.clear();
        self.sync_seen.clear();
        self.sync_sent.clear();
        self.bulk_quota = None;
        self.last_rotate_ms = unix_ms();
    }

    pub fn maybe_rotate(&mut self, now_ms: u64) {
        if now_ms.saturating_sub(self.last_rotate_ms) < ID_ROTATE_MS {
            return;
        }
        self.rotate_identity();
        self.last_rotate_ms = now_ms;
    }

    pub fn gateway_ready(&self) -> bool {
        gateway_eligible(
            self.mesh_on,
            self.gateway_opt_in,
            self.power.charging,
            self.power.unmetered,
        )
    }

    pub fn may_originate_ctrl(&self) -> bool {
        self.mesh_on && self.power.foreground
    }

    pub fn bulk_available(&self) -> bool {
        false
    }

    pub fn pop_outbound(&mut self) -> Vec<Vec<u8>> {
        std::mem::take(&mut self.outbound)
    }

    pub fn pop_events(&mut self) -> Vec<EngineEvent> {
        std::mem::take(&mut self.events)
    }

    pub fn pop_inbound_event(&mut self) -> Option<([u8; 16], Vec<u8>)> {
        if self.inbound_events.is_empty() {
            None
        } else {
            Some(self.inbound_events.remove(0))
        }
    }

    pub fn pop_lwd_requests(&mut self) -> Vec<LwdRequest> {
        std::mem::take(&mut self.pending_lwd)
    }

    pub fn status_json(&self) -> String {
        format!(
            "{{\"mesh_on\":{},\"peer_id\":\"{}\",\"peers_hint\":{},\"gateway_ready\":false,\"bulk_available\":false,\"dag_ids\":{},\"foreground\":{}}}",
            self.mesh_on,
            hex8(self.peer_id()),
            self.sessions.ready_peers().len(),
            self.dag_ids.len(),
            self.power.foreground
        )
    }

    /// Presence is handshake-only. Caps are never advertised.
    pub fn announce(&mut self) {}

    /// Kick a 1-hop handshake toward `dest` (GATT neighbor).
    pub fn neighbor_up(&mut self, dest: [u8; SENDER_LEN]) -> Result<(), IngestError> {
        if !self.mesh_on {
            return Ok(());
        }
        if dest == self.peer_id() {
            return Ok(());
        }
        if self.sessions.is_ready(&dest) || self.sessions.is_init_sent(&dest) {
            return Ok(());
        }
        let hs = self.sessions.begin_initiator(dest, Vec::new())?;
        let pkt = MeshPacket::directed(PacketType::NoiseHs, self.peer_id(), dest, hs);
        self.queue_maybe_fragment(pkt);
        self.degree = self.sessions.ready_peers().len() as u8;
        Ok(())
    }

    pub fn neighbor_down(&mut self, dest: [u8; SENDER_LEN]) {
        self.sessions.drop_peer(&dest);
        self.sync_sent.remove(&dest);
        self.degree = self.sessions.ready_peers().len() as u8;
    }

    /// Opaque EventGraph payload. `id` is the first 16 bytes of `Event.id()`.
    pub fn publish_event(&mut self, id: [u8; 16], body: Vec<u8>) -> Result<(), IngestError> {
        if !self.mesh_on {
            return Err(IngestError::NotForeground);
        }
        if body.len() > EVENT_INNER_MAX {
            return Err(IngestError::TooLarge);
        }
        if self.dag_payloads.contains_key(&id) {
            return Ok(());
        }
        self.remember_dag(id, body.clone());
        self.fanout_event_put(id, body, None)
    }

    /// Test / C ABI helper: hash the body into an id.
    pub fn publish_dag_event(&mut self, body: Vec<u8>) {
        let id = blake3_16(&body);
        let _ = self.publish_event(id, body);
    }

    pub fn request_dag_sync(&mut self) {
        if !self.mesh_on {
            return;
        }
        let (p, m, data) = encode_gcs(&self.dag_ids);
        let mut payload = Vec::with_capacity(1 + 4 + 2 + data.len());
        payload.push(p);
        payload.extend_from_slice(&m.to_be_bytes());
        payload.extend_from_slice(&(data.len() as u16).to_be_bytes());
        payload.extend_from_slice(&data);
        let inner = encode_inner(&LwdInner {
            kind: KIND_DAG_SYNC,
            corr_id: [0u8; 16],
            method: "dagsync".into(),
            body: payload,
        });
        let Ok(inner) = inner else {
            return;
        };
        let dests = self.sessions.ready_peers();
        for dest in dests {
            let _ = self.send_directed_inner(dest, inner.clone());
        }
    }

    pub fn submit_lwd_ctrl(
        &mut self,
        _dest: [u8; SENDER_LEN],
        method: &str,
        body: &[u8],
    ) -> Result<[u8; 16], IngestError> {
        let mut allow = method.as_bytes().to_vec();
        allow.push(b'\n');
        allow.extend_from_slice(body);
        validate_lwd_ctrl(&allow).map_err(IngestError::Lwd)?;
        Err(IngestError::BulkDenied)
    }

    pub fn complete_lwd_ctrl(
        &mut self,
        _dest: [u8; SENDER_LEN],
        _corr_id: [u8; 16],
        _result: Result<Vec<u8>, String>,
    ) -> Result<(), IngestError> {
        Err(IngestError::BulkDenied)
    }

    pub fn submit_bulk_offer(
        &mut self,
        _dest: [u8; SENDER_LEN],
        _offer: BulkOffer,
    ) -> Result<(), IngestError> {
        Err(IngestError::BulkDenied)
    }

    pub fn submit_bulk_join(&mut self, _dest: [u8; SENDER_LEN]) -> Result<[u8; 16], IngestError> {
        Err(IngestError::BulkDenied)
    }

    pub fn record_bulk_bytes(&mut self, _n: u64) -> bool {
        false
    }

    pub fn drain_gateway_splice(&mut self) {
        self.pending_lwd.clear();
    }

    pub fn ingest_bytes(&mut self, bytes: &[u8]) -> Result<(), IngestError> {
        if !self.mesh_on {
            return Ok(());
        }
        let now = unix_ms();
        self.sessions.expire_handshakes(now, HANDSHAKE_TIMEOUT_MS);
        let pkt = decode(bytes)?;
        self.maybe_rotate(now);
        if self.is_duplicate(&pkt, now) {
            return Err(IngestError::DroppedDuplicate);
        }
        let Some(pkt) = self.assembler.ingest(&pkt, now)? else {
            return Ok(());
        };
        if let Some(rx) = pkt.recipient {
            if rx != self.peer_id() {
                return Ok(());
            }
        }
        self.handle_complete(pkt)
    }

    fn send_directed_inner(
        &mut self,
        dest: [u8; SENDER_LEN],
        inner: Vec<u8>,
    ) -> Result<(), IngestError> {
        if self.sessions.is_ready(&dest) {
            let sealed = self.sessions.seal(&self.identity, &dest, &inner)?;
            let pkt = MeshPacket::directed(
                PacketType::NoiseEnc,
                self.peer_id(),
                dest,
                sealed,
            );
            self.queue_maybe_fragment(pkt);
            return Ok(());
        }
        match self.sessions.queue_until_ready(dest, inner.clone())? {
            Some(_) => {
                let hs = self.sessions.begin_initiator(dest, inner)?;
                let pkt = MeshPacket::directed(PacketType::NoiseHs, self.peer_id(), dest, hs);
                self.queue_maybe_fragment(pkt);
            }
            None => {}
        }
        Ok(())
    }

    fn fanout_event_put(
        &mut self,
        id: [u8; 16],
        body: Vec<u8>,
        except: Option<[u8; SENDER_LEN]>,
    ) -> Result<(), IngestError> {
        let inner = encode_inner(&LwdInner {
            kind: KIND_EVENT_PUT,
            corr_id: id,
            method: "event".into(),
            body,
        })
        .map_err(IngestError::Lwd)?;
        let dests = self.sessions.ready_peers();
        for dest in dests {
            if Some(dest) == except {
                continue;
            }
            self.send_directed_inner(dest, inner.clone())?;
        }
        Ok(())
    }

    fn handle_complete(&mut self, pkt: MeshPacket) -> Result<(), IngestError> {
        match pkt.typ {
            PacketType::Announce | PacketType::DagEvent | PacketType::DagSync => Ok(()),
            PacketType::LwdCtrl => Err(IngestError::PlaintextCtrl),
            PacketType::NoiseHs => {
                if pkt.recipient.is_none() {
                    return Err(IngestError::NotDirected);
                }
                self.on_noise_hs(pkt)
            }
            PacketType::NoiseEnc => {
                if pkt.recipient.is_none() {
                    return Err(IngestError::NotDirected);
                }
                self.on_noise_enc(pkt)
            }
            PacketType::Ping => {
                self.events.push(EngineEvent::Ping { sender: pkt.sender });
                let pong = MeshPacket::directed(
                    PacketType::Pong,
                    self.peer_id(),
                    pkt.sender,
                    Vec::new(),
                );
                self.queue_maybe_fragment(pong);
                Ok(())
            }
            PacketType::Pong => Ok(()),
            PacketType::BulkOffer | PacketType::BulkJoin => Ok(()),
            PacketType::Fragment => Ok(()),
        }
    }

    fn on_noise_hs(&mut self, pkt: MeshPacket) -> Result<(), IngestError> {
        let from = pkt.sender;
        match self.sessions.ingest_hs(&self.identity, from, &pkt.payload)? {
            HsOutcome::Reply(payload) => {
                let reply =
                    MeshPacket::directed(PacketType::NoiseHs, self.peer_id(), from, payload);
                self.queue_maybe_fragment(reply);
            }
            HsOutcome::Established { reply, flush } => {
                if let Some(payload) = reply {
                    let reply =
                        MeshPacket::directed(PacketType::NoiseHs, self.peer_id(), from, payload);
                    self.queue_maybe_fragment(reply);
                }
                for inner in flush {
                    let sealed = self.sessions.seal(&self.identity, &from, &inner)?;
                    let enc = MeshPacket::directed(
                        PacketType::NoiseEnc,
                        self.peer_id(),
                        from,
                        sealed,
                    );
                    self.queue_maybe_fragment(enc);
                }
                self.degree = self.sessions.ready_peers().len() as u8;
            }
        }
        Ok(())
    }

    fn on_noise_enc(&mut self, pkt: MeshPacket) -> Result<(), IngestError> {
        let from = pkt.sender;
        if !self.inbound_ok(from) {
            return Err(IngestError::RateLimited);
        }
        let inner_bytes = self.sessions.open(&self.identity, &from, &pkt.payload)?;
        let inner = decode_inner(&inner_bytes).map_err(IngestError::Lwd)?;
        match inner.kind {
            KIND_EVENT_PUT => {
                let id = inner.corr_id;
                let body = inner.body;
                if body.len() > EVENT_INNER_MAX {
                    return Err(IngestError::TooLarge);
                }
                let is_new = !self.dag_payloads.contains_key(&id);
                self.remember_dag(id, body.clone());
                if is_new {
                    self.events.push(EngineEvent::EventPut {
                        from,
                        id,
                        payload: body.clone(),
                    });
                    self.inbound_events.push((id, body.clone()));
                    let _ = self.fanout_event_put(id, body, Some(from));
                }
            }
            KIND_DAG_SYNC => {
                let sync_id = blake3_16(&inner.body);
                let now = unix_ms();
                let seen_recently = self
                    .sync_seen
                    .get(&sync_id)
                    .map(|t| now.saturating_sub(*t) < DEDUP_TTL_MS)
                    .unwrap_or(false);
                self.sync_seen.insert(sync_id, now);
                let missing = self.diff_gcs(&inner.body);
                self.events.push(EngineEvent::DagSync {
                    missing: missing.clone(),
                });
                let already = self
                    .sync_sent
                    .get(&from)
                    .cloned()
                    .unwrap_or_default();
                let mut to_send = Vec::new();
                for id in missing {
                    if already.contains(&id) {
                        continue;
                    }
                    if to_send.len() >= DAG_SYNC_REPLY_MAX {
                        break;
                    }
                    if self.dag_payloads.contains_key(&id) {
                        to_send.push(id);
                    }
                }
                for id in &to_send {
                    if let Some(body) = self.dag_payloads.get(id).cloned() {
                        let inner = encode_inner(&LwdInner {
                            kind: KIND_EVENT_PUT,
                            corr_id: *id,
                            method: "event".into(),
                            body,
                        })
                        .map_err(IngestError::Lwd)?;
                        self.send_directed_inner(from, inner)?;
                    }
                }
                self.sync_sent.entry(from).or_default().extend(to_send);
                if !seen_recently {
                    let fwd = encode_inner(&LwdInner {
                        kind: KIND_DAG_SYNC,
                        corr_id: inner.corr_id,
                        method: "dagsync".into(),
                        body: inner.body,
                    })
                    .map_err(IngestError::Lwd)?;
                    let dests = self.sessions.ready_peers();
                    for dest in dests {
                        if dest == from {
                            continue;
                        }
                        let _ = self.send_directed_inner(dest, fwd.clone());
                    }
                }
            }
            KIND_REQ | super::lwd_ctrl::KIND_BULK_OFFER | super::lwd_ctrl::KIND_BULK_JOIN => {
                return Err(IngestError::BulkDenied);
            }
            _ => return Err(IngestError::Lwd(LwdCtrlError::UnknownMethod)),
        }
        Ok(())
    }

    fn inbound_ok(&mut self, from: [u8; SENDER_LEN]) -> bool {
        let now = unix_ms();
        let q = self.inbound_stamps.entry(from).or_default();
        while let Some(&t) = q.front() {
            if now.saturating_sub(t) > INBOUND_RATE_WINDOW_MS {
                q.pop_front();
            } else {
                break;
            }
        }
        if q.len() >= INBOUND_RATE_MAX {
            return false;
        }
        q.push_back(now);
        true
    }

    fn diff_gcs(&self, payload: &[u8]) -> Vec<[u8; 16]> {
        if payload.len() < 7 {
            return Vec::new();
        }
        let p = payload[0];
        let m = u32::from_be_bytes(payload[1..5].try_into().unwrap());
        let n = u16::from_be_bytes(payload[5..7].try_into().unwrap()) as usize;
        if payload.len() < 7 + n {
            return Vec::new();
        }
        let data = &payload[7..7 + n];
        self.dag_ids
            .iter()
            .copied()
            .filter(|id| !gcs_contains(p, m, data, id))
            .collect()
    }

    fn remember_dag(&mut self, id: [u8; 16], body: Vec<u8>) {
        if self.dag_payloads.contains_key(&id) {
            return;
        }
        let mut dropped = 0usize;
        while self.dag_ids.len() >= DAG_CACHE_MAX_EVENTS
            || self.dag_bytes.saturating_add(body.len()) > DAG_CACHE_MAX_BYTES
        {
            if let Some(old) = self.dag_ids.first().copied() {
                self.dag_ids.remove(0);
                if let Some(prev) = self.dag_payloads.remove(&old) {
                    self.dag_bytes = self.dag_bytes.saturating_sub(prev.len());
                }
                dropped += 1;
            } else {
                break;
            }
        }
        if dropped > 0 {
            self.events.push(EngineEvent::CacheFull { dropped });
        }
        self.dag_bytes = self.dag_bytes.saturating_add(body.len());
        self.dag_ids.push(id);
        self.dag_payloads.insert(id, body);
    }

    fn queue_maybe_fragment(&mut self, pkt: MeshPacket) {
        match split(&pkt, FRAGMENT_CHUNK) {
            Ok(parts) => {
                for p in parts {
                    if let Ok(bytes) = encode(&p) {
                        if bytes.len() <= EVENT_INNER_MAX {
                            self.outbound.push(bytes);
                        }
                    }
                }
            }
            Err(_) => {}
        }
    }

    fn is_duplicate(&mut self, pkt: &MeshPacket, now: u64) -> bool {
        let id = pkt.packet_id();
        if let Some(ts) = self.seen.get(&id) {
            if now.saturating_sub(*ts) < DEDUP_TTL_MS {
                return true;
            }
        }
        self.seen.insert(id, now);
        self.seen_order.push_back(id);
        while self.seen_order.len() > DEDUP_CAP {
            if let Some(old) = self.seen_order.pop_front() {
                self.seen.remove(&old);
            }
        }
        false
    }
}

impl Default for MeshEngine {
    fn default() -> Self {
        Self::new()
    }
}

fn hex8(id: [u8; SENDER_LEN]) -> String {
    id.iter().map(|b| format!("{b:02x}")).collect()
}

fn blake3_16(body: &[u8]) -> [u8; 16] {
    let h = blake3::hash(body);
    let mut id = [0u8; 16];
    id.copy_from_slice(&h.as_bytes()[..16]);
    id
}
