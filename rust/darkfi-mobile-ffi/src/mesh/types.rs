//! Nighthawk Mesh wire constants. Opcodes are in 0xA* so they cannot
//! interoperate with BitChat (0x01 / 0x21).

/// ASCII `NH` — rejects accidental BitChat or DarkFi-net frames.
pub const MAGIC: [u8; 2] = [0x4E, 0x48];
pub const VERSION: u8 = 1;

pub const SENDER_LEN: usize = 8;
pub const HEADER_LEN: usize = 2 + 1 + 1 + 1 + 1 + 8 + 8 + 4;

pub const DEFAULT_TTL: u8 = 7;
pub const DENSE_TTL_CAP: u8 = 5;
pub const LOCAL_TTL: u8 = 0;

pub const MAX_PAYLOAD: usize = 1024 * 1024;
pub const FRAGMENT_CHUNK: usize = 469;
pub const MAX_IN_FLIGHT_ASSEMBLIES: usize = 128;
pub const FRAGMENT_TTL_SECS: u64 = 30;
pub const MAX_FRAGMENTS: u16 = 256;

pub const DEDUP_CAP: usize = 1000;
pub const DEDUP_TTL_MS: u64 = 5 * 60 * 1000;
pub const LWD_CTRL_MAX: usize = 32 * 1024;
/// EventPut / DagSync inner cap — fits JNI/Swift 64 KiB pop buffers.
pub const EVENT_INNER_MAX: usize = 64 * 1024;
pub const DAG_CACHE_MAX_EVENTS: usize = 256;
pub const DAG_CACHE_MAX_BYTES: usize = 256 * 1024;
pub const DAG_SYNC_REPLY_MAX: usize = 32;
pub const INBOUND_RATE_MAX: usize = 32;
pub const INBOUND_RATE_WINDOW_MS: u64 = 60_000;

pub const NH_ANNOUNCE: u8 = 0xA1;
pub const NH_NOISE_HS: u8 = 0xA2;
pub const NH_NOISE_ENC: u8 = 0xA3;
pub const NH_FRAGMENT: u8 = 0xA4;
pub const NH_DAG_SYNC: u8 = 0xA5;
pub const NH_DAG_EVENT: u8 = 0xA6;
pub const NH_LWD_CTRL: u8 = 0xA7;
pub const NH_BULK_OFFER: u8 = 0xA8;
pub const NH_BULK_JOIN: u8 = 0xA9;
pub const NH_PING: u8 = 0xAA;
pub const NH_PONG: u8 = 0xAB;

pub const FLAG_HAS_RECIPIENT: u8 = 0x01;

/// BLE must never carry these lightwalletd method tags.
pub const BLE_FORBIDDEN_LWD: &[&str] = &[
    "GetUnifOmrDigest",
    "FetchPirBatch",
    "GetBlockRange",
    "GetNoteCommitments",
    "GetNullifiers",
    "GetCompactBlocks",
];

/// Small RPCs allowed on directed BLE `NH_LWD_CTRL`.
pub const BLE_ALLOWED_LWD: &[&str] = &[
    "GetLightInfo",
    "GetOmrCapabilities",
    "SendTransaction",
    "RegisterCluePublicKey",
    "GetCluePublicKey",
];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PacketType {
    Announce,
    NoiseHs,
    NoiseEnc,
    Fragment,
    DagSync,
    DagEvent,
    LwdCtrl,
    BulkOffer,
    BulkJoin,
    Ping,
    Pong,
}

impl PacketType {
    pub fn from_u8(v: u8) -> Option<Self> {
        Some(match v {
            NH_ANNOUNCE => Self::Announce,
            NH_NOISE_HS => Self::NoiseHs,
            NH_NOISE_ENC => Self::NoiseEnc,
            NH_FRAGMENT => Self::Fragment,
            NH_DAG_SYNC => Self::DagSync,
            NH_DAG_EVENT => Self::DagEvent,
            NH_LWD_CTRL => Self::LwdCtrl,
            NH_BULK_OFFER => Self::BulkOffer,
            NH_BULK_JOIN => Self::BulkJoin,
            NH_PING => Self::Ping,
            NH_PONG => Self::Pong,
            _ => return None,
        })
    }

    pub fn as_u8(self) -> u8 {
        match self {
            Self::Announce => NH_ANNOUNCE,
            Self::NoiseHs => NH_NOISE_HS,
            Self::NoiseEnc => NH_NOISE_ENC,
            Self::Fragment => NH_FRAGMENT,
            Self::DagSync => NH_DAG_SYNC,
            Self::DagEvent => NH_DAG_EVENT,
            Self::LwdCtrl => NH_LWD_CTRL,
            Self::BulkOffer => NH_BULK_OFFER,
            Self::BulkJoin => NH_BULK_JOIN,
            Self::Ping => NH_PING,
            Self::Pong => NH_PONG,
        }
    }

    pub fn local_only(self) -> bool {
        matches!(
            self,
            Self::Ping
                | Self::Pong
                | Self::BulkOffer
                | Self::BulkJoin
                | Self::LwdCtrl
                | Self::NoiseHs
                | Self::NoiseEnc
                | Self::DagEvent
                | Self::DagSync
                | Self::Announce
        )
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MeshPacket {
    pub typ: PacketType,
    pub ttl: u8,
    pub flags: u8,
    pub timestamp_ms: u64,
    pub sender: [u8; SENDER_LEN],
    pub recipient: Option<[u8; SENDER_LEN]>,
    pub payload: Vec<u8>,
}

impl MeshPacket {
    pub fn new(typ: PacketType, sender: [u8; SENDER_LEN], payload: Vec<u8>) -> Self {
        let ttl = if typ.local_only() {
            LOCAL_TTL
        } else {
            DEFAULT_TTL
        };
        Self {
            typ,
            ttl,
            flags: 0,
            timestamp_ms: unix_ms(),
            sender,
            recipient: None,
            payload,
        }
    }

    /// Directed 1-hop frame (Noise handshake / sealed EventPut). Not flooded.
    pub fn directed(
        typ: PacketType,
        sender: [u8; SENDER_LEN],
        recipient: [u8; SENDER_LEN],
        payload: Vec<u8>,
    ) -> Self {
        let mut pkt = Self::new(typ, sender, payload);
        pkt.recipient = Some(recipient);
        pkt.flags |= FLAG_HAS_RECIPIENT;
        pkt.ttl = LOCAL_TTL;
        pkt
    }

    pub fn packet_id(&self) -> [u8; 16] {
        let mut h = blake3::Hasher::new();
        h.update(&[self.typ.as_u8()]);
        h.update(&self.sender);
        h.update(&self.timestamp_ms.to_be_bytes());
        h.update(&self.payload);
        let full = h.finalize();
        let mut id = [0u8; 16];
        id.copy_from_slice(&full.as_bytes()[..16]);
        id
    }
}

pub fn unix_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
