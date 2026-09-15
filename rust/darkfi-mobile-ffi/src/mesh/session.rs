//! Directed sessions: crypto_box XX *shape* (X25519 + XChaCha20-Poly1305).
//!
//! Static public keys travel only inside handshake ciphertexts. Ready
//! sessions are bound to `their_static`; the 8-byte on-air id is a label.
//! `NoiseEnc` carries a monotonic seq in the envelope and as AEAD AAD.

use crypto_box::aead::Aead;
use crypto_box::{ChaChaBox, PublicKey, SecretKey};
use rand::RngCore;

use super::identity::MeshIdentity;
use super::lwd_ctrl::{pad_frame, unpad_frame};
use super::types::SENDER_LEN;

const HS1: u8 = 1;
const HS2: u8 = 2;
const HS3: u8 = 3;

const MAX_SESSIONS: usize = 8;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SessionError {
    Key,
    Encrypt,
    Decrypt,
    Truncated,
    BadState,
    Pad,
    Replay,
}

pub struct SessionTable {
    map: std::collections::HashMap<[u8; SENDER_LEN], Session>,
    lru: std::collections::VecDeque<[u8; SENDER_LEN]>,
}

struct Session {
    state: SessState,
}

enum SessState {
    InitSent {
        eph: SecretKey,
        queued: Vec<Vec<u8>>,
    },
    WaitHs3 {
        their_eph: [u8; 32],
        our_eph: SecretKey,
    },
    Ready {
        their_static: [u8; 32],
        send_seq: u64,
        recv_seq: u64,
    },
}

impl Default for SessionTable {
    fn default() -> Self {
        Self {
            map: std::collections::HashMap::new(),
            lru: std::collections::VecDeque::new(),
        }
    }
}

impl SessionTable {
    pub fn wipe(&mut self) {
        self.map.clear();
        self.lru.clear();
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
        let eph = SecretKey::generate(&mut rand::thread_rng());
        let mut payload = Vec::with_capacity(33);
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
        if payload.is_empty() {
            return Err(SessionError::Truncated);
        }
        match payload[0] {
            HS1 => self.on_hs1(id, from, &payload[1..]),
            HS2 => self.on_hs2(id, from, &payload[1..]),
            HS3 => self.on_hs3(id, from, &payload[1..]),
            _ => Err(SessionError::BadState),
        }
    }

    pub fn seal(
        &mut self,
        id: &MeshIdentity,
        peer: &[u8; SENDER_LEN],
        inner: &[u8],
    ) -> Result<Vec<u8>, SessionError> {
        let (their, seq) = match self.map.get_mut(peer) {
            Some(Session {
                state:
                    SessState::Ready {
                        their_static,
                        send_seq,
                        ..
                    },
            }) => {
                *send_seq = send_seq.saturating_add(1);
                let seq = *send_seq;
                (*their_static, seq)
            }
            _ => return Err(SessionError::BadState),
        };
        self.touch(*peer);
        let padded = pad_frame(inner).map_err(|_| SessionError::Pad)?;
        let mut tagged = Vec::with_capacity(8 + padded.len());
        tagged.extend_from_slice(&seq.to_be_bytes());
        tagged.extend_from_slice(&padded);
        box_seal(&their, &id.secret_bytes(), &tagged, seq)
    }

    pub fn open(
        &mut self,
        id: &MeshIdentity,
        peer: &[u8; SENDER_LEN],
        payload: &[u8],
    ) -> Result<Vec<u8>, SessionError> {
        if payload.len() < 24 + 8 {
            return Err(SessionError::Truncated);
        }
        let seq = u64::from_be_bytes(payload[24..32].try_into().unwrap());
        let their = match self.map.get_mut(peer) {
            Some(Session {
                state:
                    SessState::Ready {
                        their_static,
                        recv_seq,
                        ..
                    },
            }) => {
                if seq <= *recv_seq {
                    return Err(SessionError::Replay);
                }
                *recv_seq = seq;
                *their_static
            }
            _ => return Err(SessionError::BadState),
        };
        let tagged = box_open(&their, &id.secret_bytes(), payload)?;
        if tagged.len() < 8 {
            return Err(SessionError::Truncated);
        }
        let inner_seq = u64::from_be_bytes(tagged[..8].try_into().unwrap());
        if inner_seq != seq {
            return Err(SessionError::Replay);
        }
        unpad_frame(&tagged[8..]).map_err(|_| SessionError::Pad)
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
        // Ready sessions stay bound to their_static. Ignore HS1 resets.
        if self.is_ready(&from) {
            return Err(SessionError::BadState);
        }
        // Simultaneous open: the lexicographically smaller id stays initiator.
        // If `from` is larger than us, ignore their HS1 and wait for HS2.
        if self.is_init_sent(&from) && from > id.peer_id() {
            return Err(SessionError::BadState);
        }
        if self.is_init_sent(&from) {
            self.map.remove(&from);
        }
        let mut their_eph = [0u8; 32];
        their_eph.copy_from_slice(rest);
        self.evict_if_needed();
        let our_eph = SecretKey::generate(&mut rand::thread_rng());
        let sealed = box_seal_hs(&their_eph, &our_eph.to_bytes(), &id.static_public())?;
        let mut payload = Vec::with_capacity(1 + 32 + sealed.len());
        payload.push(HS2);
        payload.extend_from_slice(&our_eph.public_key().to_bytes());
        payload.extend_from_slice(&sealed);
        self.map.insert(
            from,
            Session {
                state: SessState::WaitHs3 {
                    their_eph,
                    our_eph,
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
        if rest.len() < 32 {
            return Err(SessionError::Truncated);
        }
        let eph = match self.map.remove(&from) {
            Some(Session {
                state: SessState::InitSent { eph, queued },
            }) => (eph, queued),
            other => {
                if let Some(s) = other {
                    self.map.insert(from, s);
                }
                return Err(SessionError::BadState);
            }
        };
        let (eph, queued) = eph;
        let mut their_eph = [0u8; 32];
        their_eph.copy_from_slice(&rest[..32]);
        let their_static_bytes = box_open_hs(&their_eph, &eph.to_bytes(), &rest[32..])?;
        if their_static_bytes.len() != 32 {
            return Err(SessionError::Decrypt);
        }
        let mut their_static = [0u8; 32];
        their_static.copy_from_slice(&their_static_bytes);
        let hs3_body = box_seal_hs(&their_eph, &eph.to_bytes(), &id.static_public())?;
        let mut payload = Vec::with_capacity(1 + hs3_body.len());
        payload.push(HS3);
        payload.extend_from_slice(&hs3_body);
        self.map.insert(
            from,
            Session {
                state: SessState::Ready {
                    their_static,
                    send_seq: 0,
                    recv_seq: 0,
                },
            },
        );
        self.touch(from);
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
        let (their_eph, our_eph) = match self.map.remove(&from) {
            Some(Session {
                state: SessState::WaitHs3 {
                    their_eph,
                    our_eph,
                },
            }) => (their_eph, our_eph),
            other => {
                if let Some(s) = other {
                    self.map.insert(from, s);
                }
                return Err(SessionError::BadState);
            }
        };
        let _ = id;
        let their_static_bytes = box_open_hs(&their_eph, &our_eph.to_bytes(), rest)?;
        if their_static_bytes.len() != 32 {
            return Err(SessionError::Decrypt);
        }
        let mut their_static = [0u8; 32];
        their_static.copy_from_slice(&their_static_bytes);
        self.map.insert(
            from,
            Session {
                state: SessState::Ready {
                    their_static,
                    send_seq: 0,
                    recv_seq: 0,
                },
            },
        );
        self.touch(from);
        Ok(HsOutcome::Established {
            reply: None,
            flush: Vec::new(),
        })
    }

    fn touch(&mut self, peer: [u8; SENDER_LEN]) {
        self.lru.retain(|k| *k != peer);
        self.lru.push_back(peer);
    }

    fn evict_if_needed(&mut self) {
        while self.map.len() >= MAX_SESSIONS {
            let Some(k) = self.lru.pop_front() else {
                break;
            };
            self.map.remove(&k);
        }
    }
}

pub enum HsOutcome {
    Reply(Vec<u8>),
    Established {
        reply: Option<Vec<u8>>,
        flush: Vec<Vec<u8>>,
    },
}

fn box_seal(
    their_pk: &[u8; 32],
    our_sk: &[u8; 32],
    pt: &[u8],
    seq: u64,
) -> Result<Vec<u8>, SessionError> {
    let sk = SecretKey::from_slice(our_sk).map_err(|_| SessionError::Key)?;
    let pk = PublicKey::from_slice(their_pk).map_err(|_| SessionError::Key)?;
    let b = ChaChaBox::new(&pk, &sk);
    let mut nonce = [0u8; 24];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = b
        .encrypt(&nonce.into(), pt)
        .map_err(|_| SessionError::Encrypt)?;
    let mut out = Vec::with_capacity(24 + 8 + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&seq.to_be_bytes());
    out.extend_from_slice(&ct);
    Ok(out)
}

fn box_open(
    their_pk: &[u8; 32],
    our_sk: &[u8; 32],
    payload: &[u8],
) -> Result<Vec<u8>, SessionError> {
    if payload.len() < 32 {
        return Err(SessionError::Truncated);
    }
    let sk = SecretKey::from_slice(our_sk).map_err(|_| SessionError::Key)?;
    let pk = PublicKey::from_slice(their_pk).map_err(|_| SessionError::Key)?;
    let b = ChaChaBox::new(&pk, &sk);
    let nonce: [u8; 24] = payload[..24].try_into().unwrap();
    b.decrypt(&nonce.into(), &payload[32..])
        .map_err(|_| SessionError::Decrypt)
}

fn box_seal_hs(their_pk: &[u8; 32], our_sk: &[u8; 32], pt: &[u8]) -> Result<Vec<u8>, SessionError> {
    let sk = SecretKey::from_slice(our_sk).map_err(|_| SessionError::Key)?;
    let pk = PublicKey::from_slice(their_pk).map_err(|_| SessionError::Key)?;
    let b = ChaChaBox::new(&pk, &sk);
    let mut nonce = [0u8; 24];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = b
        .encrypt(&nonce.into(), pt)
        .map_err(|_| SessionError::Encrypt)?;
    let mut out = Vec::with_capacity(24 + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    Ok(out)
}

fn box_open_hs(their_pk: &[u8; 32], our_sk: &[u8; 32], payload: &[u8]) -> Result<Vec<u8>, SessionError> {
    if payload.len() < 24 {
        return Err(SessionError::Truncated);
    }
    let sk = SecretKey::from_slice(our_sk).map_err(|_| SessionError::Key)?;
    let pk = PublicKey::from_slice(their_pk).map_err(|_| SessionError::Key)?;
    let b = ChaChaBox::new(&pk, &sk);
    let nonce: [u8; 24] = payload[..24].try_into().unwrap();
    b.decrypt(&nonce.into(), &payload[24..])
        .map_err(|_| SessionError::Decrypt)
}
