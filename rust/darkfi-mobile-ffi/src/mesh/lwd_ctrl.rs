//! Inner `NH_LWD_CTRL` body (plaintext only after Noise decrypt).

use rand::RngCore;

use super::allowlist::{validate_lwd_ctrl, LwdCtrlError};
use super::types::{EVENT_INNER_MAX, LWD_CTRL_MAX};

pub const KIND_REQ: u8 = 1;
pub const KIND_OK: u8 = 2;
pub const KIND_ERR: u8 = 3;
pub const KIND_BULK_OFFER: u8 = 0x10;
pub const KIND_BULK_JOIN: u8 = 0x11;
pub const KIND_EVENT_PUT: u8 = 0x20;
pub const KIND_DAG_SYNC: u8 = 0x21;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LwdInner {
    pub kind: u8,
    pub corr_id: [u8; 16],
    pub method: String,
    pub body: Vec<u8>,
}

pub fn new_corr_id() -> [u8; 16] {
    let mut id = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut id);
    id
}

pub fn encode_inner(inner: &LwdInner) -> Result<Vec<u8>, LwdCtrlError> {
    let mut payload = Vec::with_capacity(1 + 16 + inner.method.len() + 1 + inner.body.len());
    payload.push(inner.kind);
    payload.extend_from_slice(&inner.corr_id);
    payload.extend_from_slice(inner.method.as_bytes());
    payload.push(b'\n');
    payload.extend_from_slice(&inner.body);
    let cap = inner_cap(inner.kind);
    if payload.len() > cap {
        return Err(LwdCtrlError::TooLarge);
    }
    if inner.kind == KIND_REQ {
        let mut allow = inner.method.as_bytes().to_vec();
        allow.push(b'\n');
        allow.extend_from_slice(&inner.body);
        validate_lwd_ctrl(&allow)?;
    }
    Ok(payload)
}

pub fn decode_inner(bytes: &[u8]) -> Result<LwdInner, LwdCtrlError> {
    if bytes.len() < 18 {
        return Err(LwdCtrlError::Empty);
    }
    if bytes.len() > EVENT_INNER_MAX {
        return Err(LwdCtrlError::TooLarge);
    }
    let kind = bytes[0];
    if kind != KIND_REQ
        && kind != KIND_OK
        && kind != KIND_ERR
        && kind != KIND_BULK_OFFER
        && kind != KIND_BULK_JOIN
        && kind != KIND_EVENT_PUT
        && kind != KIND_DAG_SYNC
    {
        return Err(LwdCtrlError::UnknownMethod);
    }
    if !is_event_kind(kind) && bytes.len() > LWD_CTRL_MAX {
        return Err(LwdCtrlError::TooLarge);
    }
    let mut corr_id = [0u8; 16];
    corr_id.copy_from_slice(&bytes[1..17]);
    let rest = &bytes[17..];
    let nl = rest.iter().position(|&b| b == b'\n').unwrap_or(rest.len());
    let method = std::str::from_utf8(&rest[..nl])
        .map_err(|_| LwdCtrlError::UnknownMethod)?
        .to_string();
    let body = if nl < rest.len() {
        rest[nl + 1..].to_vec()
    } else {
        Vec::new()
    };
    if kind == KIND_REQ {
        let mut allow = method.as_bytes().to_vec();
        allow.push(b'\n');
        allow.extend_from_slice(&body);
        validate_lwd_ctrl(&allow)?;
    }
    Ok(LwdInner {
        kind,
        corr_id,
        method,
        body,
    })
}

fn is_event_kind(kind: u8) -> bool {
    kind == KIND_EVENT_PUT || kind == KIND_DAG_SYNC
}

fn inner_cap(kind: u8) -> usize {
    if is_event_kind(kind) {
        EVENT_INNER_MAX
    } else {
        LWD_CTRL_MAX
    }
}

/// Pad to 256/512/1024/2048, then powers of two up to the event inner cap.
pub fn pad_frame(plain: &[u8]) -> Result<Vec<u8>, LwdCtrlError> {
    let need = plain.len().saturating_add(4);
    const BUCKETS: &[usize] = &[
        256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, EVENT_INNER_MAX + 64,
    ];
    let target = BUCKETS
        .iter()
        .copied()
        .find(|t| *t >= need)
        .ok_or(LwdCtrlError::TooLarge)?;
    if target > EVENT_INNER_MAX + 64 {
        return Err(LwdCtrlError::TooLarge);
    }
    let mut out = vec![0u8; target];
    out[..4].copy_from_slice(&(plain.len() as u32).to_be_bytes());
    out[4..4 + plain.len()].copy_from_slice(plain);
    Ok(out)
}

pub fn unpad_frame(padded: &[u8]) -> Result<Vec<u8>, LwdCtrlError> {
    if padded.len() < 4 {
        return Err(LwdCtrlError::Empty);
    }
    let n = usize::try_from(u32::from_be_bytes(padded[0..4].try_into().unwrap()))
        .map_err(|_| LwdCtrlError::TooLarge)?;
    let end = 4usize.checked_add(n).ok_or(LwdCtrlError::TooLarge)?;
    if n > EVENT_INNER_MAX || end > padded.len() {
        return Err(LwdCtrlError::TooLarge);
    }
    Ok(padded[4..end].to_vec())
}

/// `u32 event_len || event || u32 blob_len || blob` — EventGraph wire for mesh.
pub fn encode_mesh_event(event: &[u8], blob: &[u8]) -> Result<Vec<u8>, LwdCtrlError> {
    let total = 8usize
        .saturating_add(event.len())
        .saturating_add(blob.len());
    if total > EVENT_INNER_MAX {
        return Err(LwdCtrlError::TooLarge);
    }
    let mut out = Vec::with_capacity(total);
    out.extend_from_slice(&(event.len() as u32).to_be_bytes());
    out.extend_from_slice(event);
    out.extend_from_slice(&(blob.len() as u32).to_be_bytes());
    out.extend_from_slice(blob);
    Ok(out)
}

pub fn decode_mesh_event(bytes: &[u8]) -> Result<(Vec<u8>, Vec<u8>), LwdCtrlError> {
    if bytes.len() < 8 {
        return Err(LwdCtrlError::Empty);
    }
    if bytes.len() > EVENT_INNER_MAX {
        return Err(LwdCtrlError::TooLarge);
    }
    let el = usize::try_from(u32::from_be_bytes(bytes[0..4].try_into().unwrap()))
        .map_err(|_| LwdCtrlError::Empty)?;
    let el_end = 4usize.checked_add(el).ok_or(LwdCtrlError::Empty)?;
    let bl_off = el_end.checked_add(4).ok_or(LwdCtrlError::Empty)?;
    if bl_off > bytes.len() {
        return Err(LwdCtrlError::Empty);
    }
    let bl = usize::try_from(u32::from_be_bytes(bytes[el_end..bl_off].try_into().unwrap()))
        .map_err(|_| LwdCtrlError::Empty)?;
    let total = bl_off.checked_add(bl).ok_or(LwdCtrlError::Empty)?;
    if total != bytes.len() {
        return Err(LwdCtrlError::Empty);
    }
    Ok((bytes[4..el_end].to_vec(), bytes[bl_off..].to_vec()))
}
