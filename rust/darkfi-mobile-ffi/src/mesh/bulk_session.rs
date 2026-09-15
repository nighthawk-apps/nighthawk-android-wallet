//! High-bandwidth bulk session (UnifOMR). Never rides BLE.
//!
//! Quotas: one window, ~200 MiB, 15 minutes. Offer/join travel inside
//! Noise after a directed session exists. The radio (SoftAP / Aware /
//! Multipeer) is OS-owned.

use super::allowlist::LwdCtrlError;

pub const MAX_BULK_BYTES: u64 = 200 * 1024 * 1024;
pub const MAX_BULK_SECS: u64 = 15 * 60;
pub const KIND_AWARE: u8 = 1;
pub const KIND_SOFTAP: u8 = 2;
pub const KIND_MULTIPEER: u8 = 3;

pub const CTRL_RATE_MAX: usize = 8;
pub const CTRL_RATE_WINDOW_MS: u64 = 60_000;
pub const ID_ROTATE_MS: u64 = 6 * 60 * 60 * 1000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BulkOffer {
    pub session_id: [u8; 16],
    pub kind: u8,
    pub port: u16,
    pub ssid: String,
    pub psk: Vec<u8>,
}

pub fn encode_offer(o: &BulkOffer) -> Result<Vec<u8>, LwdCtrlError> {
    if o.ssid.len() > 32 || o.psk.len() > 64 {
        return Err(LwdCtrlError::TooLarge);
    }
    if o.kind != KIND_AWARE && o.kind != KIND_SOFTAP && o.kind != KIND_MULTIPEER {
        return Err(LwdCtrlError::UnknownMethod);
    }
    let mut out = Vec::with_capacity(16 + 1 + 2 + 1 + o.ssid.len() + 1 + o.psk.len());
    out.extend_from_slice(&o.session_id);
    out.push(o.kind);
    out.extend_from_slice(&o.port.to_be_bytes());
    out.push(o.ssid.len() as u8);
    out.extend_from_slice(o.ssid.as_bytes());
    out.push(o.psk.len() as u8);
    out.extend_from_slice(&o.psk);
    Ok(out)
}

pub fn decode_offer(bytes: &[u8]) -> Result<BulkOffer, LwdCtrlError> {
    if bytes.len() < 16 + 1 + 2 + 1 + 1 {
        return Err(LwdCtrlError::Empty);
    }
    let mut session_id = [0u8; 16];
    session_id.copy_from_slice(&bytes[0..16]);
    let kind = bytes[16];
    let port = u16::from_be_bytes(bytes[17..19].try_into().unwrap());
    let sl = bytes[19] as usize;
    if bytes.len() < 20 + sl + 1 {
        return Err(LwdCtrlError::Empty);
    }
    let ssid = std::str::from_utf8(&bytes[20..20 + sl])
        .map_err(|_| LwdCtrlError::UnknownMethod)?
        .to_string();
    let pl = bytes[20 + sl] as usize;
    let psk_off = 21 + sl;
    if bytes.len() < psk_off + pl {
        return Err(LwdCtrlError::Empty);
    }
    Ok(BulkOffer {
        session_id,
        kind,
        port,
        ssid,
        psk: bytes[psk_off..psk_off + pl].to_vec(),
    })
}

#[derive(Debug, Clone)]
pub struct BulkQuota {
    started_ms: u64,
    bytes: u64,
}

impl BulkQuota {
    pub fn start(now_ms: u64) -> Self {
        Self {
            started_ms: now_ms,
            bytes: 0,
        }
    }

    pub fn allow(&mut self, add: u64, now_ms: u64) -> bool {
        if now_ms.saturating_sub(self.started_ms) > MAX_BULK_SECS * 1000 {
            return false;
        }
        if self.bytes.saturating_add(add) > MAX_BULK_BYTES {
            return false;
        }
        self.bytes = self.bytes.saturating_add(add);
        true
    }

    pub fn expired(&self, now_ms: u64) -> bool {
        now_ms.saturating_sub(self.started_ms) > MAX_BULK_SECS * 1000
            || self.bytes >= MAX_BULK_BYTES
    }
}

/// BGAppRefresh / `dataSync` workers must never start UnifOMR bulk.
pub fn may_start_bulk(foreground: bool, gateway_ready: bool, from_bg_refresh: bool) -> bool {
    foreground && gateway_ready && !from_bg_refresh
}
