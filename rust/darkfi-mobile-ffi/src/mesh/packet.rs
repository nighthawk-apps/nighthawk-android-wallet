//! Compact Nighthawk Mesh envelope.

use super::types::{
    MeshPacket, PacketType, FLAG_HAS_RECIPIENT, HEADER_LEN, MAGIC, MAX_PAYLOAD, SENDER_LEN, VERSION,
};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CodecError {
    TooShort,
    BadMagic,
    UnsupportedVersion,
    UnknownType,
    LengthMismatch,
    PayloadTooLarge,
}

impl std::fmt::Display for CodecError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::TooShort => write!(f, "mesh frame too short"),
            Self::BadMagic => write!(f, "not a nighthawk mesh frame"),
            Self::UnsupportedVersion => write!(f, "unsupported mesh version"),
            Self::UnknownType => write!(f, "unknown mesh packet type"),
            Self::LengthMismatch => write!(f, "mesh payload length mismatch"),
            Self::PayloadTooLarge => write!(f, "mesh payload exceeds 1 MiB cap"),
        }
    }
}

pub fn encode(pkt: &MeshPacket) -> Result<Vec<u8>, CodecError> {
    if pkt.payload.len() > MAX_PAYLOAD {
        return Err(CodecError::PayloadTooLarge);
    }
    let has_rx = pkt.recipient.is_some();
    let flags = if has_rx {
        pkt.flags | FLAG_HAS_RECIPIENT
    } else {
        pkt.flags & !FLAG_HAS_RECIPIENT
    };
    let extra = if has_rx { SENDER_LEN } else { 0 };
    let mut out = Vec::with_capacity(HEADER_LEN + extra + pkt.payload.len());
    out.extend_from_slice(&MAGIC);
    out.push(VERSION);
    out.push(pkt.typ.as_u8());
    out.push(pkt.ttl);
    out.push(flags);
    out.extend_from_slice(&pkt.timestamp_ms.to_be_bytes());
    out.extend_from_slice(&pkt.sender);
    if let Some(rx) = pkt.recipient {
        out.extend_from_slice(&rx);
    }
    out.extend_from_slice(&(pkt.payload.len() as u32).to_be_bytes());
    out.extend_from_slice(&pkt.payload);
    Ok(out)
}

pub fn decode(bytes: &[u8]) -> Result<MeshPacket, CodecError> {
    if bytes.len() < HEADER_LEN {
        return Err(CodecError::TooShort);
    }
    if bytes[0..2] != MAGIC {
        return Err(CodecError::BadMagic);
    }
    if bytes[2] != VERSION {
        return Err(CodecError::UnsupportedVersion);
    }
    let typ = PacketType::from_u8(bytes[3]).ok_or(CodecError::UnknownType)?;
    let ttl = bytes[4];
    let flags = bytes[5];
    let timestamp_ms = u64::from_be_bytes(bytes[6..14].try_into().unwrap());
    let mut sender = [0u8; SENDER_LEN];
    sender.copy_from_slice(&bytes[14..22]);
    let mut off = 22;
    let recipient = if flags & FLAG_HAS_RECIPIENT != 0 {
        if bytes.len() < off + SENDER_LEN + 4 {
            return Err(CodecError::TooShort);
        }
        let mut rx = [0u8; SENDER_LEN];
        rx.copy_from_slice(&bytes[off..off + SENDER_LEN]);
        off += SENDER_LEN;
        Some(rx)
    } else {
        None
    };
    if bytes.len() < off + 4 {
        return Err(CodecError::TooShort);
    }
    let plen = u32::from_be_bytes(bytes[off..off + 4].try_into().unwrap()) as usize;
    off += 4;
    if plen > MAX_PAYLOAD {
        return Err(CodecError::PayloadTooLarge);
    }
    if bytes.len() != off + plen {
        return Err(CodecError::LengthMismatch);
    }
    Ok(MeshPacket {
        typ,
        ttl,
        flags,
        timestamp_ms,
        sender,
        recipient,
        payload: bytes[off..].to_vec(),
    })
}
