//! Application-level fragmentation. Chunk size tracks a 512-byte BLE MTU.

use super::types::{
    MeshPacket, PacketType, FRAGMENT_CHUNK, FRAGMENT_TTL_SECS, MAX_FRAGMENTS,
    MAX_IN_FLIGHT_ASSEMBLIES, MAX_PAYLOAD, SENDER_LEN,
};

const FRAG_HDR: usize = 8 + 2 + 2 + 1;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FragError {
    Empty,
    TooLarge,
    TooManyFragments,
    BadHeader,
    CompleteButShort,
}

pub fn split(original: &MeshPacket, chunk: usize) -> Result<Vec<MeshPacket>, FragError> {
    let encoded_payload = &original.payload;
    if encoded_payload.is_empty() {
        return Err(FragError::Empty);
    }
    if encoded_payload.len() > MAX_PAYLOAD {
        return Err(FragError::TooLarge);
    }
    let size = chunk.max(1).min(FRAGMENT_CHUNK);
    if encoded_payload.len() <= size {
        return Ok(vec![original.clone()]);
    }
    let total = encoded_payload.len().div_ceil(size);
    if total > MAX_FRAGMENTS as usize {
        return Err(FragError::TooManyFragments);
    }
    let mut stream = [0u8; 8];
    stream.copy_from_slice(&original.packet_id()[..8]);
    let mut out = Vec::with_capacity(total);
    for (i, piece) in encoded_payload.chunks(size).enumerate() {
        let mut payload = Vec::with_capacity(FRAG_HDR + piece.len());
        payload.extend_from_slice(&stream);
        payload.extend_from_slice(&(i as u16).to_be_bytes());
        payload.extend_from_slice(&(total as u16).to_be_bytes());
        payload.push(original.typ.as_u8());
        payload.extend_from_slice(piece);
        let mut frag = MeshPacket::new(PacketType::Fragment, original.sender, payload);
        frag.timestamp_ms = original.timestamp_ms;
        frag.ttl = original.ttl;
        frag.recipient = original.recipient;
        out.push(frag);
    }
    Ok(out)
}

struct Assembly {
    sender: [u8; SENDER_LEN],
    original: u8,
    total: u16,
    chunks: Vec<Option<Vec<u8>>>,
    started_ms: u64,
}

#[derive(Default)]
pub struct FragmentAssembler {
    inflight: std::collections::HashMap<[u8; 8], Assembly>,
}

impl FragmentAssembler {
    pub fn ingest(&mut self, pkt: &MeshPacket, now_ms: u64) -> Result<Option<MeshPacket>, FragError> {
        self.gc(now_ms);
        if pkt.typ != PacketType::Fragment {
            return Ok(Some(pkt.clone()));
        }
        if pkt.payload.len() < FRAG_HDR {
            return Err(FragError::BadHeader);
        }
        let mut stream = [0u8; 8];
        stream.copy_from_slice(&pkt.payload[0..8]);
        let index = u16::from_be_bytes(pkt.payload[8..10].try_into().unwrap());
        let total = u16::from_be_bytes(pkt.payload[10..12].try_into().unwrap());
        let original = pkt.payload[12];
        if total == 0 || index >= total || total > MAX_FRAGMENTS {
            return Err(FragError::BadHeader);
        }
        if self.inflight.len() >= MAX_IN_FLIGHT_ASSEMBLIES && !self.inflight.contains_key(&stream)
        {
            return Err(FragError::TooManyFragments);
        }
        let entry = self.inflight.entry(stream).or_insert_with(|| Assembly {
            sender: pkt.sender,
            original,
            total,
            chunks: vec![None; total as usize],
            started_ms: now_ms,
        });
        if entry.total != total || entry.original != original {
            return Err(FragError::BadHeader);
        }
        entry.chunks[index as usize] = Some(pkt.payload[FRAG_HDR..].to_vec());
        if entry.chunks.iter().any(|c| c.is_none()) {
            return Ok(None);
        }
        let mut body = Vec::new();
        for c in entry.chunks.iter().flatten() {
            body.extend_from_slice(c);
        }
        if body.len() > MAX_PAYLOAD {
            self.inflight.remove(&stream);
            return Err(FragError::TooLarge);
        }
        let typ = PacketType::from_u8(original).ok_or(FragError::BadHeader)?;
        let rebuilt = MeshPacket {
            typ,
            ttl: pkt.ttl,
            flags: pkt.flags,
            timestamp_ms: pkt.timestamp_ms,
            sender: entry.sender,
            recipient: pkt.recipient,
            payload: body,
        };
        self.inflight.remove(&stream);
        if rebuilt.payload.is_empty() {
            return Err(FragError::CompleteButShort);
        }
        Ok(Some(rebuilt))
    }

    fn gc(&mut self, now_ms: u64) {
        let ttl = FRAGMENT_TTL_SECS * 1000;
        self.inflight
            .retain(|_, a| now_ms.saturating_sub(a.started_ms) <= ttl);
    }
}
