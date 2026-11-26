//! Directed sessions for an opportunistic X25519 handshake without peer
//! authentication (BLAKE3 transcript, XChaCha20-Poly1305), with directional
//! transport keys and a 64-packet replay window.
//!
//! The handshake gives forward secrecy against a passive listener. It does not
//! bind the peer static to a known contact, so a relay of wallet control frames
//! has no MITM resistance. Static public keys travel only inside handshake
//! ciphertexts checked against the running BLAKE3 transcript. That peer static
//! is mixed into the DH (`es`, `se`, `ss`) before the ephemeral secrets are
//! dropped. Ready sessions derive directional `send_key` / `recv_key` from
//! `ee || es || se || ss || transcript_hash`.
//!
//! Transport frames bind protocol version, direction, and outer `seq` as AEAD
//! additional data.
//!
//! The initiator marks Ready and returns queued frames in the same outcome as
//! HS3, before the responder accepts it. HS3 has no reply. Android BLE sends
//! client writes with `WRITE_TYPE_NO_RESPONSE` and server notifications with
//! confirmation off, so the link does not ack HS3 either. Holding Ready until
//! an ack would desynchronize current peers: the responder becomes Ready on
//! HS3 and sends nothing back. If HS3 is dropped, the initiator can emit
//! transport frames the responder cannot open until the handshake times out.
//! That residual drop risk is unchanged.

use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{XChaCha20Poly1305, XNonce};
use crypto_box::{ChaChaBox, PublicKey, SecretKey};
use rand::RngCore;
use zeroize::Zeroizing;

use super::identity::MeshIdentity;
use super::lwd_ctrl::{pad_frame, unpad_frame};
use super::types::{HANDSHAKE_TIMEOUT_MS, SENDER_LEN};

/// Mesh handshake protocol version (bumped from legacy v1 so old peers fail cleanly).
pub(crate) const HS_VERSION: u8 = 2;
const HS1: u8 = 0x11;
const HS2: u8 = 0x12;
const HS3: u8 = 0x13;

/// Direction tags bound into transport AEAD AAD.
const DIR_I2R: u8 = 0x01;
const DIR_R2I: u8 = 0x02;

const MAX_HANDSHAKES: usize = 8;
const MAX_READY: usize = 8;
/// HS1 timestamps kept past the session caps so losing a slot does not let
/// that same id start another handshake immediately.
const MAX_HS1_TRACKED: usize = 64;
const REPLAY_WINDOW_SIZE: u64 = 64;

const DH_DOMAIN: &[u8; 32] = b"nighthawk-mesh-x25519-dh-v2\0\0\0\0\0";

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SessionError {
    Key,
    Encrypt,
    Decrypt,
    Truncated,
    BadState,
    Pad,
    Replay,
    UnsupportedVersion,
}

/// 64-packet sliding replay window.
///
/// Tolerates mild BLE frame reordering within 64 sequence numbers while
/// strictly rejecting duplicates and packets older than the window.
#[derive(Debug, Default, Clone)]
struct ReplayWindow {
    highest: u64,
    bitmap: u64,
}

impl ReplayWindow {
    fn check(&self, seq: u64) -> bool {
        if seq == 0 {
            return false;
        }
        if seq > self.highest {
            return true;
        }
        let diff = self.highest - seq;
        if diff >= REPLAY_WINDOW_SIZE {
            return false;
        }
        (self.bitmap & (1u64 << diff)) == 0
    }

    fn accept(&mut self, seq: u64) {
        if seq == 0 {
            return;
        }
        if seq > self.highest {
            let shift = seq - self.highest;
            if shift >= REPLAY_WINDOW_SIZE {
                self.bitmap = 1;
            } else {
                self.bitmap = (self.bitmap << shift) | 1;
            }
            self.highest = seq;
        } else {
            let diff = self.highest - seq;
            if diff < REPLAY_WINDOW_SIZE {
                self.bitmap |= 1u64 << diff;
            }
        }
    }
}

#[derive(Default)]
pub struct SessionTable {
    map: std::collections::HashMap<[u8; SENDER_LEN], Session>,
    lru: std::collections::VecDeque<[u8; SENDER_LEN]>,
    /// Last accepted HS1 time per peer id. Survives slot eviction.
    hs1_recent: std::collections::HashMap<[u8; SENDER_LEN], u64>,
    hs1_order: std::collections::VecDeque<[u8; SENDER_LEN]>,
}

struct Session {
    state: SessState,
}

enum SessState {
    InitSent {
        eph: SecretKey,
        queued: Vec<Vec<u8>>,
        started_ms: u64,
    },
    WaitHs3 {
        their_eph: [u8; 32],
        our_eph: SecretKey,
        ee: Zeroizing<[u8; 32]>,
        h_hs2: [u8; 32],
        started_ms: u64,
    },
    Ready {
        /// Peer static mixed into `es` / `se` / `ss`. Not a known contact.
        #[allow(dead_code)]
        their_static: [u8; 32],
        send_key: Zeroizing<[u8; 32]>,
        recv_key: Zeroizing<[u8; 32]>,
        send_dir: u8,
        recv_dir: u8,
        send_seq: u64,
        recv_window: ReplayWindow,
        ready_since_ms: u64,
    },
}

impl SessionTable {
    pub fn wipe(&mut self) {
        self.map.clear();
        self.lru.clear();
        self.hs1_recent.clear();
        self.hs1_order.clear();
    }

    pub fn drop_peer(&mut self, peer: &[u8; SENDER_LEN]) {
        self.map.remove(peer);
        self.lru.retain(|k| k != peer);
    }

    /// Drop InitSent / WaitHs3 sessions that never finished (simultaneous-open deadlock).
    pub fn expire_handshakes(&mut self, now_ms: u64, timeout_ms: u64) -> usize {
        let stale: Vec<[u8; SENDER_LEN]> = self
            .map
            .iter()
            .filter_map(|(k, s)| match &s.state {
                SessState::InitSent { started_ms, .. } | SessState::WaitHs3 { started_ms, .. }
                    if now_ms.saturating_sub(*started_ms) > timeout_ms =>
                {
                    Some(*k)
                }
                _ => None,
            })
            .collect();
        let n = stale.len();
        for k in stale {
            self.drop_peer(&k);
        }
        n
    }

    /// Drop Ready sessions older than `max_age_ms`. The next send starts a new handshake.
    pub fn expire_aged(&mut self, now_ms: u64, max_age_ms: u64) -> usize {
        let stale: Vec<[u8; SENDER_LEN]> = self
            .map
            .iter()
            .filter_map(|(k, s)| match &s.state {
                SessState::Ready { ready_since_ms, .. }
                    if now_ms.saturating_sub(*ready_since_ms) > max_age_ms =>
                {
                    Some(*k)
                }
                _ => None,
            })
            .collect();
        let n = stale.len();
        for k in stale {
            self.drop_peer(&k);
        }
        n
    }

    pub fn ready_peers(&self) -> Vec<[u8; SENDER_LEN]> {
        self.map
            .iter()
            .filter_map(|(k, s)| match s.state {
                SessState::Ready { .. } => Some(*k),
                _ => None,
            })
            .collect()
    }

    pub fn is_ready(&self, peer: &[u8; SENDER_LEN]) -> bool {
        matches!(
            self.map.get(peer),
            Some(Session {
                state: SessState::Ready { .. }
            })
        )
    }

    pub fn is_init_sent(&self, peer: &[u8; SENDER_LEN]) -> bool {
        matches!(
            self.map.get(peer),
            Some(Session {
                state: SessState::InitSent { .. }
            })
        )
    }

    pub fn begin_initiator(
        &mut self,
        peer: [u8; SENDER_LEN],
        queued: Vec<u8>,
    ) -> Result<Vec<u8>, SessionError> {
        self.evict_if_needed();
        let eph = SecretKey::generate(&mut rand::rngs::OsRng);
        let mut payload = Vec::with_capacity(2 + 32);
        payload.push(HS_VERSION);
        payload.push(HS1);
        payload.extend_from_slice(&eph.public_key().to_bytes());
        self.map.insert(
            peer,
            Session {
                state: SessState::InitSent {
                    eph,
                    queued: if queued.is_empty() {
                        Vec::new()
                    } else {
                        vec![queued]
                    },
                    started_ms: super::types::unix_ms(),
                },
            },
        );
        self.touch(peer);
        Ok(payload)
    }

    pub fn queue_until_ready(
        &mut self,
        peer: [u8; SENDER_LEN],
        inner: Vec<u8>,
    ) -> Result<Option<Vec<u8>>, SessionError> {
        match self.map.get_mut(&peer) {
            Some(Session {
                state: SessState::InitSent { queued, .. },
            }) => {
                queued.push(inner);
                Ok(None)
            }
            Some(Session {
                state: SessState::Ready { .. },
            }) => Ok(Some(inner)),
            Some(_) => Err(SessionError::BadState),
            None => Ok(Some(inner)),
        }
    }

    pub fn ingest_hs(
        &mut self,
        id: &MeshIdentity,
        from: [u8; SENDER_LEN],
        payload: &[u8],
    ) -> Result<HsOutcome, SessionError> {
        if payload.len() < 2 {
            return Err(SessionError::Truncated);
        }
        if payload[0] != HS_VERSION {
            return Err(SessionError::UnsupportedVersion);
        }
        match payload[1] {
            HS1 => self.on_hs1(id, from, &payload[2..]),
            HS2 => self.on_hs2(id, from, &payload[2..]),
            HS3 => self.on_hs3(id, from, &payload[2..]),
            _ => Err(SessionError::UnsupportedVersion),
        }
    }

    pub fn seal(
        &mut self,
        _id: &MeshIdentity,
        peer: &[u8; SENDER_LEN],
        inner: &[u8],
    ) -> Result<Vec<u8>, SessionError> {
        let (send_key, send_dir, seq) = match self.map.get_mut(peer) {
            Some(Session {
                state:
                    SessState::Ready {
                        send_key,
                        send_dir,
                        send_seq,
                        ..
                    },
            }) => {
                let Some(next_seq) = send_seq.checked_add(1) else {
                    return Err(SessionError::BadState);
                };
                *send_seq = next_seq;
                (send_key.clone(), *send_dir, next_seq)
            }
            _ => return Err(SessionError::BadState),
        };
        self.touch(*peer);
        let padded = pad_frame(inner).map_err(|_| SessionError::Pad)?;
        box_seal(&send_key, send_dir, &padded, seq)
    }

    pub fn open(
        &mut self,
        _id: &MeshIdentity,
        peer: &[u8; SENDER_LEN],
        payload: &[u8],
    ) -> Result<Vec<u8>, SessionError> {
        if payload.len() < 24 + 8 + 16 {
            return Err(SessionError::Truncated);
        }
        let seq = u64::from_be_bytes(payload[24..32].try_into().unwrap());
        let (recv_key, recv_dir) = match self.map.get(peer) {
            Some(Session {
                state:
                    SessState::Ready {
                        recv_key,
                        recv_dir,
                        recv_window,
                        ..
                    },
            }) => {
                if !recv_window.check(seq) {
                    return Err(SessionError::Replay);
                }
                (recv_key.clone(), *recv_dir)
            }
            _ => return Err(SessionError::BadState),
        };
        let padded = box_open(&recv_key, recv_dir, payload)?;
        if let Some(Session {
            state: SessState::Ready { recv_window, .. },
        }) = self.map.get_mut(peer)
        {
            recv_window.accept(seq);
        }
        unpad_frame(&padded).map_err(|_| SessionError::Pad)
    }

    fn on_hs1(
        &mut self,
        id: &MeshIdentity,
        from: [u8; SENDER_LEN],
        rest: &[u8],
    ) -> Result<HsOutcome, SessionError> {
        if rest.len() != 32 {
            return Err(SessionError::Truncated);
        }
        // Ready sessions stay bound. HS1 must not reset or evict them.
        if self.is_ready(&from) {
            return Err(SessionError::BadState);
        }
        // Simultaneous open: the lexicographically smaller id stays initiator.
        // If `from` is larger than us, ignore their HS1 and wait for HS2.
        if self.is_init_sent(&from) && from > id.peer_id() {
            return Err(SessionError::BadState);
        }
        let now = super::types::unix_ms();
        // One accepted HS1 per peer id per handshake timeout. A repeat must
        // not restart DH or replace WaitHs3.
        if self.hs1_too_soon(&from, now) || self.is_wait_hs3(&from) {
            return Err(SessionError::BadState);
        }
        let mut their_eph = [0u8; 32];
        their_eph.copy_from_slice(rest);

        let our_eph = SecretKey::generate(&mut rand::rngs::OsRng);
        let our_eph_pub = our_eph.public_key().to_bytes();
        let ee = dh_shared(&our_eph.to_bytes(), &their_eph)?;
        let h_hs2 = transcript_hs2(&from, &id.peer_id(), &their_eph, &our_eph_pub);
        let k_hs2 = derive_hs2_key(&ee, &h_hs2);
        let our_static_pub = id.static_public().ok_or(SessionError::Key)?;
        let sealed = aead_seal_with_aad(&k_hs2, &our_static_pub, &h_hs2)?;

        let mut payload = Vec::with_capacity(2 + 32 + sealed.len());
        payload.push(HS_VERSION);
        payload.push(HS2);
        payload.extend_from_slice(&our_eph_pub);
        payload.extend_from_slice(&sealed);

        if self.is_init_sent(&from) {
            self.map.remove(&from);
        }
        self.evict_if_needed();
        self.note_hs1(from, now);
        self.map.insert(
            from,
            Session {
                state: SessState::WaitHs3 {
                    their_eph,
                    our_eph,
                    ee,
                    h_hs2,
                    started_ms: now,
                },
            },
        );
        self.touch(from);
        Ok(HsOutcome::Reply(payload))
    }

    fn on_hs2(
        &mut self,
        id: &MeshIdentity,
        from: [u8; SENDER_LEN],
        rest: &[u8],
    ) -> Result<HsOutcome, SessionError> {
        if rest.len() < 32 + 24 + 16 {
            return Err(SessionError::Truncated);
        }
        let (our_eph_bytes, our_eph_pub) = match self.map.get(&from) {
            Some(Session {
                state: SessState::InitSent { eph, .. },
            }) => (Zeroizing::new(eph.to_bytes()), eph.public_key().to_bytes()),
            _ => return Err(SessionError::BadState),
        };
        let mut their_eph = [0u8; 32];
        their_eph.copy_from_slice(&rest[..32]);

        let ee = dh_shared(&our_eph_bytes, &their_eph)?;
        let h_hs2 = transcript_hs2(&id.peer_id(), &from, &our_eph_pub, &their_eph);
        let k_hs2 = derive_hs2_key(&ee, &h_hs2);

        let their_static_bytes = aead_open_with_aad(&k_hs2, &rest[32..], &h_hs2)?;
        if their_static_bytes.len() != 32 {
            return Err(SessionError::Decrypt);
        }
        let mut their_static = [0u8; 32];
        their_static.copy_from_slice(&their_static_bytes);

        let es = dh_shared(&our_eph_bytes, &their_static)?;
        let our_static_sk = id.secret_bytes();
        let our_static_pub = id.static_public().ok_or(SessionError::Key)?;
        let se = dh_shared(&our_static_sk, &their_eph)?;
        let ss = dh_shared(&our_static_sk, &their_static)?;

        let h_hs3 = transcript_hs3(&h_hs2, &their_static);
        let k_hs3 = derive_hs3_key(&ee, &es, &h_hs3);
        let hs3_body = aead_seal_with_aad(&k_hs3, &our_static_pub, &h_hs3)?;

        let h_final = transcript_final(&h_hs3, &our_static_pub);
        let (k_i2r, k_r2i) = derive_transport_keys(&ee, &es, &se, &ss, &h_final);

        let queued = match self.map.remove(&from) {
            Some(Session {
                state: SessState::InitSent { queued, .. },
            }) => queued,
            _ => Vec::new(),
        };

        let mut payload = Vec::with_capacity(2 + hs3_body.len());
        payload.push(HS_VERSION);
        payload.push(HS3);
        payload.extend_from_slice(&hs3_body);

        self.map.insert(
            from,
            Session {
                state: SessState::Ready {
                    their_static,
                    send_key: k_i2r,
                    recv_key: k_r2i,
                    send_dir: DIR_I2R,
                    recv_dir: DIR_R2I,
                    send_seq: 0,
                    recv_window: ReplayWindow::default(),
                    ready_since_ms: super::types::unix_ms(),
                },
            },
        );
        self.touch(from);
        self.evict_ready_if_over_cap();
        Ok(HsOutcome::Established {
            reply: Some(payload),
            flush: queued,
        })
    }

    fn on_hs3(
        &mut self,
        id: &MeshIdentity,
        from: [u8; SENDER_LEN],
        rest: &[u8],
    ) -> Result<HsOutcome, SessionError> {
        if rest.len() < 24 + 16 {
            return Err(SessionError::Truncated);
        }
        let (their_eph, our_eph_bytes, ee, h_hs2) = match self.map.get(&from) {
            Some(Session {
                state:
                    SessState::WaitHs3 {
                        their_eph,
                        our_eph,
                        ee,
                        h_hs2,
                        ..
                    },
            }) => (
                *their_eph,
                Zeroizing::new(our_eph.to_bytes()),
                ee.clone(),
                *h_hs2,
            ),
            _ => return Err(SessionError::BadState),
        };

        let our_static_sk = id.secret_bytes();
        let our_static_pub = id.static_public().ok_or(SessionError::Key)?;
        // Responder's DH(s_r, e_i) equals Initiator's es = DH(e_i, s_r).
        let es = dh_shared(&our_static_sk, &their_eph)?;
        let h_hs3 = transcript_hs3(&h_hs2, &our_static_pub);
        let k_hs3 = derive_hs3_key(&ee, &es, &h_hs3);

        let their_static_bytes = aead_open_with_aad(&k_hs3, rest, &h_hs3)?;
        if their_static_bytes.len() != 32 {
            return Err(SessionError::Decrypt);
        }
        let mut their_static = [0u8; 32];
        their_static.copy_from_slice(&their_static_bytes);

        // Responder's DH(e_r, s_i) equals Initiator's se = DH(s_i, e_r).
        let se = dh_shared(&our_eph_bytes, &their_static)?;
        let ss = dh_shared(&our_static_sk, &their_static)?;

        let h_final = transcript_final(&h_hs3, &their_static);
        let (k_i2r, k_r2i) = derive_transport_keys(&ee, &es, &se, &ss, &h_final);

        self.map.remove(&from);
        self.map.insert(
            from,
            Session {
                state: SessState::Ready {
                    their_static,
                    send_key: k_r2i,
                    recv_key: k_i2r,
                    send_dir: DIR_R2I,
                    recv_dir: DIR_I2R,
                    send_seq: 0,
                    recv_window: ReplayWindow::default(),
                    ready_since_ms: super::types::unix_ms(),
                },
            },
        );
        self.touch(from);
        self.evict_ready_if_over_cap();
        Ok(HsOutcome::Established {
            reply: None,
            flush: Vec::new(),
        })
    }

    fn touch(&mut self, peer: [u8; SENDER_LEN]) {
        self.lru.retain(|k| *k != peer);
        self.lru.push_back(peer);
    }

    fn is_handshake(&self, peer: &[u8; SENDER_LEN]) -> bool {
        matches!(
            self.map.get(peer).map(|s| &s.state),
            Some(SessState::InitSent { .. } | SessState::WaitHs3 { .. })
        )
    }

    fn is_wait_hs3(&self, peer: &[u8; SENDER_LEN]) -> bool {
        matches!(
            self.map.get(peer),
            Some(Session {
                state: SessState::WaitHs3 { .. }
            })
        )
    }

    fn handshake_count(&self) -> usize {
        self.map
            .values()
            .filter(|s| {
                matches!(
                    s.state,
                    SessState::InitSent { .. } | SessState::WaitHs3 { .. }
                )
            })
            .count()
    }

    fn ready_count(&self) -> usize {
        self.map
            .values()
            .filter(|s| matches!(s.state, SessState::Ready { .. }))
            .count()
    }

    fn hs1_too_soon(&self, peer: &[u8; SENDER_LEN], now: u64) -> bool {
        match self.hs1_recent.get(peer) {
            Some(last) => now.saturating_sub(*last) < HANDSHAKE_TIMEOUT_MS,
            None => false,
        }
    }

    fn note_hs1(&mut self, peer: [u8; SENDER_LEN], now: u64) {
        if self.hs1_recent.insert(peer, now).is_some() {
            self.hs1_order.retain(|k| *k != peer);
        }
        self.hs1_order.push_back(peer);
        while self.hs1_order.len() > MAX_HS1_TRACKED {
            let Some(old) = self.hs1_order.pop_front() else {
                break;
            };
            if !self.hs1_order.contains(&old) {
                self.hs1_recent.remove(&old);
            }
        }
    }

    /// Handshake slots are separate from Ready sessions.
    /// A flood of HS1 frames cannot evict a live session.
    fn evict_if_needed(&mut self) {
        while self.handshake_count() >= MAX_HANDSHAKES {
            let Some(k) = self.lru.iter().copied().find(|k| self.is_handshake(k)) else {
                break;
            };
            self.drop_peer(&k);
        }
    }

    /// Drop the oldest Ready session only after the Ready cap is exceeded.
    /// An in-progress handshake under the handshake cap is left in place.
    fn evict_ready_if_over_cap(&mut self) {
        while self.ready_count() > MAX_READY {
            let Some(k) = self.lru.iter().copied().find(|k| self.is_ready(k)) else {
                break;
            };
            self.drop_peer(&k);
        }
    }
}

#[derive(Debug, PartialEq, Eq)]
pub enum HsOutcome {
    Reply(Vec<u8>),
    Established {
        reply: Option<Vec<u8>>,
        flush: Vec<Vec<u8>>,
    },
}

/// Symmetric X25519-based shared-secret extractor using `ChaChaBox` (`HChaCha20(sk * pk, 0)`),
/// rejecting all small-order public keys (whose clamped scalar product equals the zero point).
fn dh_shared(our_sk: &[u8; 32], their_pk: &[u8; 32]) -> Result<Zeroizing<[u8; 32]>, SessionError> {
    let sk = SecretKey::from_slice(our_sk).map_err(|_| SessionError::Key)?;
    let pk = PublicKey::from_slice(their_pk).map_err(|_| SessionError::Key)?;
    let zero_pk = PublicKey::from_bytes([0u8; 32]);

    let zero_out = prf_from_box(&ChaChaBox::new(&zero_pk, &sk))?;
    let shared_out = prf_from_box(&ChaChaBox::new(&pk, &sk))?;

    // Constant-time check that `their_pk` is not a small-order X25519 point.
    let mut diff = 0u8;
    for (a, b) in shared_out.iter().zip(zero_out.iter()) {
        diff |= a ^ b;
    }
    if diff == 0 {
        return Err(SessionError::Key);
    }
    Ok(shared_out)
}

fn prf_from_box(b: &ChaChaBox) -> Result<Zeroizing<[u8; 32]>, SessionError> {
    let zero_nonce = [0u8; 24];
    let ct = b
        .encrypt(&zero_nonce.into(), DH_DOMAIN.as_slice())
        .map_err(|_| SessionError::Key)?;
    Ok(Zeroizing::new(*blake3::hash(&ct).as_bytes()))
}

fn transcript_hs2(
    init_id: &[u8; SENDER_LEN],
    resp_id: &[u8; SENDER_LEN],
    init_eph: &[u8; 32],
    resp_eph: &[u8; 32],
) -> [u8; 32] {
    let mut h = blake3::Hasher::new();
    h.update(b"nighthawk-mesh-v2-hs2-transcript");
    h.update(&[HS_VERSION]);
    h.update(init_id);
    h.update(resp_id);
    h.update(init_eph);
    h.update(resp_eph);
    *h.finalize().as_bytes()
}

fn transcript_hs3(h_hs2: &[u8; 32], resp_static: &[u8; 32]) -> [u8; 32] {
    let mut h = blake3::Hasher::new();
    h.update(b"nighthawk-mesh-v2-hs3-transcript");
    h.update(h_hs2);
    h.update(resp_static);
    *h.finalize().as_bytes()
}

fn transcript_final(h_hs3: &[u8; 32], init_static: &[u8; 32]) -> [u8; 32] {
    let mut h = blake3::Hasher::new();
    h.update(b"nighthawk-mesh-v2-final-transcript");
    h.update(h_hs3);
    h.update(init_static);
    *h.finalize().as_bytes()
}

fn derive_hs2_key(ee: &[u8; 32], h_hs2: &[u8; 32]) -> Zeroizing<[u8; 32]> {
    let mut ikm = Zeroizing::new([0u8; 64]);
    ikm[..32].copy_from_slice(ee);
    ikm[32..].copy_from_slice(h_hs2);
    Zeroizing::new(blake3::derive_key("nighthawk-mesh-v2-hs2-key", &*ikm))
}

fn derive_hs3_key(ee: &[u8; 32], es: &[u8; 32], h_hs3: &[u8; 32]) -> Zeroizing<[u8; 32]> {
    let mut ikm = Zeroizing::new([0u8; 96]);
    ikm[..32].copy_from_slice(ee);
    ikm[32..64].copy_from_slice(es);
    ikm[64..].copy_from_slice(h_hs3);
    Zeroizing::new(blake3::derive_key("nighthawk-mesh-v2-hs3-key", &*ikm))
}

fn derive_transport_keys(
    ee: &[u8; 32],
    es: &[u8; 32],
    se: &[u8; 32],
    ss: &[u8; 32],
    h_final: &[u8; 32],
) -> (Zeroizing<[u8; 32]>, Zeroizing<[u8; 32]>) {
    let mut ikm = Zeroizing::new([0u8; 160]);
    ikm[0..32].copy_from_slice(ee);
    ikm[32..64].copy_from_slice(es);
    ikm[64..96].copy_from_slice(se);
    ikm[96..128].copy_from_slice(ss);
    ikm[128..160].copy_from_slice(h_final);
    let k_i2r = Zeroizing::new(blake3::derive_key("nighthawk-mesh-v2-tx-i2r", &*ikm));
    let k_r2i = Zeroizing::new(blake3::derive_key("nighthawk-mesh-v2-tx-r2i", &*ikm));
    (k_i2r, k_r2i)
}

fn transport_aad(dir: u8, seq: u64) -> [u8; 10] {
    let mut aad = [0u8; 10];
    aad[0] = HS_VERSION;
    aad[1] = dir;
    aad[2..10].copy_from_slice(&seq.to_be_bytes());
    aad
}

fn aead_seal_with_aad(key: &[u8; 32], pt: &[u8], aad: &[u8]) -> Result<Vec<u8>, SessionError> {
    let cipher = XChaCha20Poly1305::new(key.into());
    let mut nonce = [0u8; 24];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    let ct = cipher
        .encrypt(XNonce::from_slice(&nonce), Payload { msg: pt, aad })
        .map_err(|_| SessionError::Encrypt)?;
    let mut out = Vec::with_capacity(24 + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    Ok(out)
}

fn aead_open_with_aad(key: &[u8; 32], payload: &[u8], aad: &[u8]) -> Result<Vec<u8>, SessionError> {
    if payload.len() < 24 + 16 {
        return Err(SessionError::Truncated);
    }
    let cipher = XChaCha20Poly1305::new(key.into());
    cipher
        .decrypt(
            XNonce::from_slice(&payload[..24]),
            Payload {
                msg: &payload[24..],
                aad,
            },
        )
        .map_err(|_| SessionError::Decrypt)
}

fn box_seal(key: &[u8; 32], dir: u8, pt: &[u8], seq: u64) -> Result<Vec<u8>, SessionError> {
    let cipher = XChaCha20Poly1305::new(key.into());
    let mut nonce = [0u8; 24];
    rand::rngs::OsRng.fill_bytes(&mut nonce);
    let aad = transport_aad(dir, seq);
    let ct = cipher
        .encrypt(XNonce::from_slice(&nonce), Payload { msg: pt, aad: &aad })
        .map_err(|_| SessionError::Encrypt)?;
    let mut out = Vec::with_capacity(24 + 8 + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&seq.to_be_bytes());
    out.extend_from_slice(&ct);
    Ok(out)
}

fn box_open(key: &[u8; 32], dir: u8, payload: &[u8]) -> Result<Vec<u8>, SessionError> {
    if payload.len() < 24 + 8 + 16 {
        return Err(SessionError::Truncated);
    }
    let seq = u64::from_be_bytes(payload[24..32].try_into().unwrap());
    let aad = transport_aad(dir, seq);
    let cipher = XChaCha20Poly1305::new(key.into());
    cipher
        .decrypt(
            XNonce::from_slice(&payload[..24]),
            Payload {
                msg: &payload[32..],
                aad: &aad,
            },
        )
        .map_err(|_| SessionError::Decrypt)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn complete_handshake(a: &MeshIdentity, b: &MeshIdentity) -> (SessionTable, SessionTable) {
        let mut sa = SessionTable::default();
        let mut sb = SessionTable::default();
        let hs1 = sa.begin_initiator(b.peer_id(), Vec::new()).unwrap();
        let hs2 = match sb.ingest_hs(b, a.peer_id(), &hs1).unwrap() {
            HsOutcome::Reply(p) => p,
            HsOutcome::Established { .. } => panic!("expected HS2 reply"),
        };
        let hs3 = match sa.ingest_hs(a, b.peer_id(), &hs2).unwrap() {
            HsOutcome::Established { reply, .. } => reply.expect("expected HS3"),
            HsOutcome::Reply(_) => panic!("expected established"),
        };
        assert!(matches!(
            sb.ingest_hs(b, a.peer_id(), &hs3).unwrap(),
            HsOutcome::Established { reply: None, .. }
        ));
        (sa, sb)
    }

    fn session_keys(table: &SessionTable, peer: &[u8; SENDER_LEN]) -> ([u8; 32], [u8; 32]) {
        match table.map.get(peer) {
            Some(Session {
                state:
                    SessState::Ready {
                        send_key, recv_key, ..
                    },
            }) => (**send_key, **recv_key),
            _ => panic!("session not ready"),
        }
    }

    #[test]
    fn forward_secrecy_rotates_directional_keys_across_handshakes() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();

        let (sa1, sb1) = complete_handshake(&a, &b);
        let (sa2, sb2) = complete_handshake(&a, &b);

        let (a_send1, a_recv1) = session_keys(&sa1, &b.peer_id());
        let (b_send1, b_recv1) = session_keys(&sb1, &a.peer_id());
        let (a_send2, a_recv2) = session_keys(&sa2, &b.peer_id());

        // Directional match between peers, distinct per direction.
        assert_eq!(a_send1, b_recv1);
        assert_eq!(a_recv1, b_send1);
        assert_ne!(a_send1, a_recv1);

        // Fresh ephemeral keys produce completely distinct session keys for same static identities.
        assert_ne!(a_send1, a_send2);
        assert_ne!(a_recv1, a_recv2);

        // Ciphertext sealed under session 1 cannot be opened under session 2.
        let mut sa1 = sa1;
        let mut sb2 = sb2;
        let ct1 = sa1.seal(&a, &b.peer_id(), b"session-one").unwrap();
        assert_eq!(sb2.open(&b, &a.peer_id(), &ct1), Err(SessionError::Decrypt));
    }

    #[test]
    fn outer_seq_and_direction_are_bound_as_aead_aad() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();
        let (mut sa, mut sb) = complete_handshake(&a, &b);

        let ct = sa.seal(&a, &b.peer_id(), b"aad-bound").unwrap();

        // Tampering with outer seq byte in header must fail AEAD auth (not just replay check).
        let mut tampered = ct.clone();
        tampered[31] ^= 0x02; // change seq 1 -> 3
        assert_eq!(
            sb.open(&b, &a.peer_id(), &tampered),
            Err(SessionError::Decrypt)
        );

        // Reflecting sender's own ciphertext back must fail (wrong directional key + dir AAD).
        assert_eq!(sa.open(&a, &b.peer_id(), &ct), Err(SessionError::Decrypt));

        // Original unmodified frame still decrypts cleanly (failed tampered attempt did not advance window).
        assert_eq!(sb.open(&b, &a.peer_id(), &ct).unwrap(), b"aad-bound");
    }

    #[test]
    fn sliding_replay_window_allows_reordering_and_rejects_duplicates_and_stale() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();
        let (mut sa, mut sb) = complete_handshake(&a, &b);

        // Produce 70 frames (seq 1..=70).
        let frames: Vec<Vec<u8>> = (1..=70u32)
            .map(|i| {
                sa.seal(&a, &b.peer_id(), format!("msg-{i}").as_bytes())
                    .unwrap()
            })
            .collect();

        // Deliver out of order: 5, 3, 1, 4, 2 (all within 64-packet window).
        for idx in [4usize, 2, 0, 3, 1] {
            let expected = format!("msg-{}", idx + 1);
            assert_eq!(
                sb.open(&b, &a.peer_id(), &frames[idx]).unwrap(),
                expected.as_bytes()
            );
        }

        // Replaying any already-accepted packet (highest or interior) is strictly rejected.
        assert_eq!(
            sb.open(&b, &a.peer_id(), &frames[4]),
            Err(SessionError::Replay)
        );
        assert_eq!(
            sb.open(&b, &a.peer_id(), &frames[2]),
            Err(SessionError::Replay)
        );

        // Jump window high watermark to seq 70 (frames[69]).
        assert_eq!(sb.open(&b, &a.peer_id(), &frames[69]).unwrap(), b"msg-70");

        // seq 7 (diff = 63) is still inside the 64-packet window -> accepted.
        assert_eq!(sb.open(&b, &a.peer_id(), &frames[6]).unwrap(), b"msg-7");
        // Duplicate of seq 7 -> rejected.
        assert_eq!(
            sb.open(&b, &a.peer_id(), &frames[6]),
            Err(SessionError::Replay)
        );

        // seq 6 (diff = 64) fell outside the 64-packet window -> rejected.
        assert_eq!(
            sb.open(&b, &a.peer_id(), &frames[5]),
            Err(SessionError::Replay)
        );
    }

    #[test]
    fn legacy_and_unknown_handshake_versions_fail_cleanly() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();
        let mut sb = SessionTable::default();

        // Legacy v1 HS1: [1, 32-byte pubkey]
        let mut legacy_hs1 = vec![1u8];
        legacy_hs1.extend_from_slice(&[0x42u8; 32]);
        assert_eq!(
            sb.ingest_hs(&b, a.peer_id(), &legacy_hs1),
            Err(SessionError::UnsupportedVersion)
        );

        // Future version 99
        let mut future_hs1 = vec![99u8, HS1];
        future_hs1.extend_from_slice(&[0x42u8; 32]);
        assert_eq!(
            sb.ingest_hs(&b, a.peer_id(), &future_hs1),
            Err(SessionError::UnsupportedVersion)
        );

        // Valid version byte with unknown message tag (e.g. legacy v1 HS2 starting with 2)
        let mut bad_tag = vec![HS_VERSION, 0x99];
        bad_tag.extend_from_slice(&[0x42u8; 32]);
        assert_eq!(
            sb.ingest_hs(&b, a.peer_id(), &bad_tag),
            Err(SessionError::UnsupportedVersion)
        );
    }

    #[test]
    fn handshake_binds_peer_ids_and_rejects_low_order_ephemeral() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();
        let mut sa = SessionTable::default();
        let mut sb = SessionTable::default();

        // Zero / low-order ephemeral public key in HS1 is rejected.
        let mut low_order_hs1 = vec![HS_VERSION, HS1];
        low_order_hs1.extend_from_slice(&[0u8; 32]);
        assert_eq!(
            sb.ingest_hs(&b, a.peer_id(), &low_order_hs1),
            Err(SessionError::Key)
        );

        // Transcript binds peer_ids: relaying HS2 with mismatched sender peer_id fails decrypt.
        let hs1 = sa.begin_initiator(b.peer_id(), Vec::new()).unwrap();
        let wrong_peer = [0xEEu8; SENDER_LEN];
        let hs2_wrong_id = match sb.ingest_hs(&b, wrong_peer, &hs1).unwrap() {
            HsOutcome::Reply(p) => p,
            _ => panic!("expected HS2"),
        };
        assert_eq!(
            sa.ingest_hs(&a, b.peer_id(), &hs2_wrong_id),
            Err(SessionError::Decrypt)
        );
    }

    fn hs1_payload() -> Vec<u8> {
        let eph = SecretKey::generate(&mut rand::rngs::OsRng);
        let mut payload = vec![HS_VERSION, HS1];
        payload.extend_from_slice(&eph.public_key().to_bytes());
        payload
    }

    fn establish_ready(table: &mut SessionTable, responder: &MeshIdentity) -> [u8; SENDER_LEN] {
        let init = MeshIdentity::generate();
        let mut init_table = SessionTable::default();
        let peer = init.peer_id();
        let hs1 = init_table
            .begin_initiator(responder.peer_id(), Vec::new())
            .unwrap();
        let hs2 = match table.ingest_hs(responder, peer, &hs1).unwrap() {
            HsOutcome::Reply(p) => p,
            HsOutcome::Established { .. } => panic!("expected HS2"),
        };
        let hs3 = match init_table
            .ingest_hs(&init, responder.peer_id(), &hs2)
            .unwrap()
        {
            HsOutcome::Established { reply, .. } => reply.expect("HS3"),
            HsOutcome::Reply(_) => panic!("expected established"),
        };
        match table.ingest_hs(responder, peer, &hs3).unwrap() {
            HsOutcome::Established { reply: None, .. } => {}
            other => panic!("expected responder ready, got {other:?}"),
        }
        peer
    }

    #[test]
    fn initiator_marks_ready_and_flushes_with_hs3_before_responder_accepts() {
        let a = MeshIdentity::generate();
        let b = MeshIdentity::generate();
        let mut sa = SessionTable::default();
        let mut sb = SessionTable::default();
        let queued = b"queued-frame".to_vec();
        let hs1 = sa.begin_initiator(b.peer_id(), queued.clone()).unwrap();
        let hs2 = match sb.ingest_hs(&b, a.peer_id(), &hs1).unwrap() {
            HsOutcome::Reply(p) => p,
            HsOutcome::Established { .. } => panic!("expected HS2"),
        };
        match sa.ingest_hs(&a, b.peer_id(), &hs2).unwrap() {
            HsOutcome::Established { reply, flush } => {
                let hs3 = reply.expect("HS3");
                assert_eq!(hs3[0], HS_VERSION);
                assert_eq!(hs3[1], HS3);
                assert_eq!(flush, vec![queued]);
                assert!(sa.is_ready(&b.peer_id()));
                assert!(!sb.is_ready(&a.peer_id()));
                assert!(sb.is_handshake(&a.peer_id()));
            }
            HsOutcome::Reply(_) => panic!("expected established"),
        }
    }

    #[test]
    fn one_peer_id_cannot_spin_hs1() {
        let responder = MeshIdentity::generate();
        let mut table = SessionTable::default();
        let live = establish_ready(&mut table, &responder);
        let spin = [0xABu8; SENDER_LEN];
        let first = hs1_payload();
        let second = hs1_payload();
        assert!(matches!(
            table.ingest_hs(&responder, spin, &first).unwrap(),
            HsOutcome::Reply(_)
        ));
        let parked = match &table.map[&spin].state {
            SessState::WaitHs3 { their_eph, .. } => *their_eph,
            _ => panic!("expected WaitHs3"),
        };
        assert_eq!(
            table.ingest_hs(&responder, spin, &second),
            Err(SessionError::BadState)
        );
        match &table.map[&spin].state {
            SessState::WaitHs3 { their_eph, .. } => assert_eq!(*their_eph, parked),
            _ => panic!("repeat HS1 replaced the handshake"),
        }
        assert!(table.is_ready(&live));

        let other = [0x5Au8; SENDER_LEN];
        assert!(matches!(
            table.ingest_hs(&responder, other, &hs1_payload()).unwrap(),
            HsOutcome::Reply(_)
        ));
        assert!(table.is_handshake(&spin));
        assert!(table.is_handshake(&other));
        assert!(table.is_ready(&live));
    }

    #[test]
    fn hs1_flood_does_not_evict_ready_session() {
        let responder = MeshIdentity::generate();
        let mut table = SessionTable::default();
        let live = establish_ready(&mut table, &responder);
        assert_eq!(
            table.ingest_hs(&responder, live, &hs1_payload()),
            Err(SessionError::BadState)
        );
        assert!(table.is_ready(&live));

        for i in 0..(MAX_HANDSHAKES + 3) {
            let mut id = [0x44u8; SENDER_LEN];
            id[7] = i as u8;
            assert_ne!(id, live);
            assert!(table.ingest_hs(&responder, id, &hs1_payload()).is_ok());
        }
        assert!(table.is_ready(&live));
        assert_eq!(table.ready_count(), 1);
        assert_eq!(table.handshake_count(), MAX_HANDSHAKES);
    }

    #[test]
    fn ready_cap_evicts_oldest_ready_not_an_under_cap_handshake() {
        let responder = MeshIdentity::generate();
        let mut table = SessionTable::default();
        let parked_id = [0x71u8; SENDER_LEN];
        assert!(matches!(
            table
                .ingest_hs(&responder, parked_id, &hs1_payload())
                .unwrap(),
            HsOutcome::Reply(_)
        ));
        assert!(table.is_handshake(&parked_id));

        let mut ready_ids = Vec::new();
        for _ in 0..MAX_READY {
            let id = establish_ready(&mut table, &responder);
            assert_ne!(id, parked_id);
            ready_ids.push(id);
        }
        assert_eq!(table.ready_count(), MAX_READY);
        assert!(table.is_handshake(&parked_id));
        assert!(table.handshake_count() < MAX_HANDSHAKES);

        let newest = establish_ready(&mut table, &responder);
        assert_ne!(newest, parked_id);
        assert_eq!(table.ready_count(), MAX_READY);
        assert!(!table.is_ready(&ready_ids[0]));
        assert!(table.is_ready(&newest));
        assert!(table.is_handshake(&parked_id));
        for id in &ready_ids[1..] {
            assert!(table.is_ready(id));
        }
        assert_eq!(table.handshake_count(), 1);
    }
}
