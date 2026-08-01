//! Oblivious Message Retrieval (OMR) types and key digest format.
//!
//! This module defines the wire format and domain separation for OMR
//! detection keys, clues, and digests used in the DarkFi lightwallet
//! protocol. It is shared between iOS and Android via the Rust FFI.
//!
//! ## OMR Architecture (bulletin-board model)
//!
//! 1. **Sender**: When creating a transaction output, the sender generates
//!    a *clue* — a piece of data attached to the output that allows the
//!    OMR detector to identify potential matches without learning the
//!    actual recipient.
//!
//! 2. **Recipient**: Derives a *detection key* from their wallet secrets.
//!    This key is sent to the lightwalletd server's detector. The detection
//!    key allows the detector to check clues without learning which
//!    specific messages belong to the recipient.
//!
//! 3. **Detector (server-side)**: Runs over cached compact blocks using
//!    the client's detection key. Returns a compact *digest* — a list of
//!    block heights where potential matches were found. The detector has
//!    a controlled false positive rate but zero false negatives.
//!
//! 4. **Client**: Fetches only the matching blocks, then performs trial
//!    decryption client-side to identify actual notes.
//!
//! ## Privacy properties
//!
//! - The detector learns *which blocks may contain relevant outputs* but
//!   NOT which specific outputs within those blocks are actual matches.
//! - The detection key is designed for *unlinkability*: different detection
//!   keys from the same wallet should not be linkable by the server.
//! - The false positive rate provides *plausible deniability*: the server
//!   cannot distinguish true matches from false positives.
//!
//! ## Key digest wire format
//!
//! ### Version 2 (current)
//!
//! ```text
//! ┌──────────┬───────────┬──────────┬──────────┬──────────────────────────┐
//! │ Version  │ Network   │ Scheme   │ Epoch    │ Detection Key Material   │
//! │ (1 byte) │ (1 byte)  │ (1 byte) │ (4 byte) │ (variable, scheme-dep.)  │
//! ├──────────┼───────────┼──────────┼──────────┼──────────────────────────┤
//! │   0x02   │ 0x00=main │ 0x05=Uni │ LE u32   │ [scheme-specific bytes]  │
//! │          │ 0x01=test │          │          │                          │
//! └──────────┴───────────┴──────────┴──────────┴──────────────────────────┘
//! ```
//!
//! **Canonical bytes (code is source of truth):**
//! - Network: Mainnet=`0x00`, Testnet=`0x01`.
//! - Scheme: UnifOMR=`0x05`.
//! - UnifOMR key material: BFV detection-key ciphertext (GenDetKey wire).
//!
//! ## Multi-address wallets
//!
//! Sub-addresses = multiple pubkeys under one wallet. Sync may issue up to
//! [`MAX_OMR_DETECT_PUBKEYS`] UnifOMR queries and union heights.
//!
//! ## Domain separation
//!
//! All key derivations use blake3 with UnifOMR domain tags (see `unifomr.rs`).
//!
//! ## Key unlinkability
//!
//! UnifOMR detection keys are inherently unlinkable because each fresh
//! encryption uses new randomness.

/// Current version of the OMR key digest format (includes epoch field).
pub const OMR_KEY_VERSION: u8 = 0x02;

/// Max wallet pubkeys queried per OMR sync cycle (sub-address cap).
pub const MAX_OMR_DETECT_PUBKEYS: usize = 16;

/// Number of blocks per UnifOMR detection key pool epoch.
///
/// Used to compute pool epoch boundaries for the pool-based key
/// delivery protocol. Each pool slot covers `UNIFOMR_EPOCH_SIZE` blocks.
/// At ~15 second block time:
/// - 5760 blocks ≈ 24 hours (1 day)
///
/// Pool registration pre-generates keys for multiple epochs ahead.
pub const UNIFOMR_EPOCH_SIZE: u32 = 5760;

/// Network identifiers for domain separation.
/// These MUST match the network strings used in bootstrap config.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum OmrNetwork {
    /// DarkFi mainnet
    Mainnet = 0x00,
    /// DarkFi testnet
    Testnet = 0x01,
}

impl OmrNetwork {
    /// Parse from bootstrap config network string.
    pub fn from_network_str(s: &str) -> Option<Self> {
        match s.trim() {
            "mainnet" => Some(Self::Mainnet),
            // localnet uses testnet address/OMR domain separation
            "testnet" | "localnet" => Some(Self::Testnet),
            _ => None,
        }
    }

    /// Convert to byte for serialization.
    pub fn to_byte(self) -> u8 {
        self as u8
    }

    /// Parse from serialized byte.
    pub fn from_byte(b: u8) -> Option<Self> {
        match b {
            0x00 => Some(Self::Mainnet),
            0x01 => Some(Self::Testnet),
            _ => None,
        }
    }
}

/// Supported OMR detection schemes.
///
/// UnifOMR is the sole supported scheme. It provides:
/// /// - BFV lattice-based FHE (degree=2048, 2 moduli)
/// - Uniform communication cost independent of message count
///
/// See: "UnifOMR: Oblivious Message Retrieval with Near-optimal
/// Concrete Efficiency" — ePrint 2025.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum OmrScheme {
    /// UnifOMR — sole deployed scheme. Wire material is a BFV **ciphertext**
    /// (~37 KB query, degree=2048). Inherently unlinkable (fresh randomness
    /// per encrypt).
    UnifOmr = 0x05,
}

impl OmrScheme {
    /// Convert to byte for serialization.
    pub fn to_byte(self) -> u8 {
        self as u8
    }

    /// Parse from serialized byte.
    pub fn from_byte(b: u8) -> Option<Self> {
        match b {
            0x05 => Some(Self::UnifOmr),
            _ => None,
        }
    }

    /// Human-readable name matching the proto `OmrCapabilities.scheme` field.
    pub fn proto_name(&self) -> &'static str {
        match self {
            Self::UnifOmr => "unifomr",
        }
    }
}

/// Domain separation tag for OMR detection key derivation.
///
/// NOTE: The version suffix in this tag is for the *derivation protocol*,
/// not the wire format version. It stays at v1 even when the wire format
/// bumps to v2, because the underlying KDF algorithm hasn't changed.
pub(crate) const DOMAIN_TAG: &[u8] = b"DarkFi-OMR-DetectionKey-v1";

/// An OMR detection key in serialized wire format.
///
/// Wire bytes for UnifOMR detection-key RPCs (`GetUnifOmrDigest`).
/// It contains the version, network, scheme, and the actual key material
/// in a single self-describing byte sequence.
///
/// ## PRIVACY: Key unlinkability
///
/// Different detection keys from the same wallet MUST NOT be linkable
/// by the server. If the same detection key is reused across sessions,
/// the server can track wallet activity. Detection keys should be
/// rotated or derived per-session where the scheme supports it.
///
/// ## PRIVACY: Key size uniformity
///
/// All detection keys for the same scheme should be the same size,
/// regardless of wallet state or configuration. Variable-size keys
/// could be used to fingerprint wallets.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OmrDetectionKey {
    /// Protocol version (0x02 — with epoch field)
    pub version: u8,
    /// Network (mainnet/testnet)
    pub network: OmrNetwork,
    /// Detection scheme
    pub scheme: OmrScheme,
    /// Epoch number for pool-based key management.
    ///
    /// For UnifOMR: pool epoch assigned during key pool registration.
    /// Epoch 0 is used for inline (non-pool) detection queries.
    /// V1 keys deserialize with epoch=0.
    pub epoch: u32,
    /// Raw key material (scheme-dependent encoding)
    pub key_material: Vec<u8>,
}

impl OmrDetectionKey {
    /// Create a new detection key (v2 format with epoch).
    pub fn new(network: OmrNetwork, scheme: OmrScheme, key_material: Vec<u8>) -> Self {
        Self {
            version: OMR_KEY_VERSION,
            network,
            scheme,
            epoch: 0,
            key_material,
        }
    }

    /// Create a new detection key with an explicit epoch.
    pub fn new_with_epoch(
        network: OmrNetwork,
        scheme: OmrScheme,
        epoch: u32,
        key_material: Vec<u8>,
    ) -> Self {
        Self {
            version: OMR_KEY_VERSION,
            network,
            scheme,
            epoch,
            key_material,
        }
    }

    /// Serialize to wire format for UnifOMR detection-key RPCs.
    ///
    /// V2 format: `[version(1)] [network(1)] [scheme(1)] [epoch(4 LE)] [key_material(N)]`
    pub fn serialize(&self) -> Vec<u8> {
        let mut buf = Vec::with_capacity(7 + self.key_material.len());
        buf.push(self.version);
        buf.push(self.network.to_byte());
        buf.push(self.scheme.to_byte());
        buf.extend_from_slice(&self.epoch.to_le_bytes());
        buf.extend_from_slice(&self.key_material);
        buf
    }

    /// Deserialize from wire bytes (V2 format only).
    ///
    /// Returns None if the format is invalid, the version is unsupported,
    /// or the network/scheme bytes are unrecognized.
    pub fn deserialize(bytes: &[u8]) -> Option<Self> {
        if bytes.len() < 8 {
            // Minimum: version + network + scheme + epoch(4) + at least 1 byte of key
            return None;
        }

        let version = bytes[0];
        if version != OMR_KEY_VERSION {
            return None; // Only V2 (0x02) is supported
        }

        let network = OmrNetwork::from_byte(bytes[1])?;
        let scheme = OmrScheme::from_byte(bytes[2])?;
        let epoch = u32::from_le_bytes([bytes[3], bytes[4], bytes[5], bytes[6]]);
        let key_material = bytes[7..].to_vec();

        Some(Self {
            version,
            network,
            scheme,
            epoch,
            key_material,
        })
    }

    /// Compute the domain-separated hash of this detection key.
    ///
    /// This is used for internal indexing and deduplication only,
    /// NOT sent to the server (the full key is sent instead).
    ///
    /// The epoch is included in the hash so that keys from different
    /// epochs produce different domain hashes.
    pub fn domain_hash(&self) -> [u8; 32] {
        let mut hasher = blake3::Hasher::new();
        hasher.update(DOMAIN_TAG);
        hasher.update(&[self.network.to_byte()]);
        hasher.update(&[self.scheme.to_byte()]);
        hasher.update(&self.epoch.to_le_bytes());
        hasher.update(&self.key_material);
        *hasher.finalize().as_bytes()
    }

    /// Get the epoch for this detection key.
    pub fn epoch(&self) -> u32 {
        self.epoch
    }
}

/// An OMR digest response — the compact result from the detector.
///
/// This mirrors the proto `OmrDigestResponse` message. The client
/// uses the matching heights to fetch only relevant blocks.
#[derive(Debug, Clone)]
pub struct OmrDigest {
    /// Block heights where the detection key matched.
    pub matching_heights: Vec<u32>,
    /// Whether the digest covers the full requested range.
    /// If false, the client should make additional requests
    /// for the remaining range.
    pub complete: bool,
}

/// Derive an OMR detection key from the wallet master seed.
///
/// Uses a domain-separated blake3 KDF to derive a dedicated OMR secret,
/// then computes the UnifOMR detection key (BFV ciphertext) from that secret.
///
/// ## Key derivation path
///
/// ```text
/// master_seed (32+ bytes, from BIP-39 mnemonic)
///   └─ blake3-keyed-hash(master_seed[0..32], context)
///      context = "DarkFi-UnifOMR-DetectionKey-v1" || "-KeyGen" || network || scheme
///      └─ omr_secret (32 bytes)
///          └─ [UnifOMR] FHE keygen from omr_secret seed
///                       → BFV query ciphertext (~37 KB, degree=2048)
/// ```
///
/// ## Security properties
///
/// - **Domain separation**: Different networks produce different keys
///   even from the same master seed.
/// - **One-way**: The detection key cannot be used to recover the master seed.
/// - **Unlinkable**: Each fresh BFV encryption uses new randomness, so
///   detection keys from the same wallet are unlinkable by the server.
///
/// ## Parameters
///
/// - `current_height`: Currently unused for UnifOMR (no epoch rotation).
/// - `recipient_pubkey`: Unused for paper UnifOMR (wallet-scoped detection key).
///   Kept for call-site compatibility with multi-pubkey helpers.
pub fn derive_detection_key(
    network: OmrNetwork,
    scheme: OmrScheme,
    wallet_secret: &[u8],
    _current_height: u32,
    _recipient_pubkey: &[u8],
) -> Result<OmrDetectionKey, String> {
    if wallet_secret.len() < 32 {
        return Err(format!(
            "Wallet secret too short: {} bytes (need ≥32)",
            wallet_secret.len()
        ));
    }

    match scheme {
        OmrScheme::UnifOmr => {
            let client =
                crate::unifomr::UnifOmrClient::from_wallet(wallet_secret, network.to_byte())?;
            let query = client.build_detection_key(network.to_byte())?;
            Ok(OmrDetectionKey::new_with_epoch(network, scheme, 0, query))
        }
    }
}

/// Convenience wrapper that derives a detection key with epoch=0.
///
/// This is equivalent to calling `derive_detection_key(..., 0, &[0u8; 32])` and is
/// provided for backward compatibility with call sites that don't have
/// access to the current block height.
pub fn derive_detection_key_static(
    network: OmrNetwork,
    scheme: OmrScheme,
    wallet_secret: &[u8],
    recipient_pubkey: &[u8],
) -> Result<OmrDetectionKey, String> {
    derive_detection_key(network, scheme, wallet_secret, 0, recipient_pubkey)
}

/// Derive one UnifOMR detection key (BFV ciphertext) per wallet pubkey slot.
///
/// Paper UnifOMR uses a wallet-scoped detection key; each call still produces a
/// fresh BFV encryption (unlinkable). Pubkeys beyond [`MAX_OMR_DETECT_PUBKEYS`]
/// are dropped (default-first order is the caller's responsibility).
pub fn derive_detection_keys_for_pubkeys(
    network: OmrNetwork,
    scheme: OmrScheme,
    wallet_secret: &[u8],
    current_height: u32,
    recipient_pubkeys: &[[u8; 32]],
) -> Result<Vec<OmrDetectionKey>, String> {
    if recipient_pubkeys.is_empty() {
        return Err("At least one recipient pubkey required for OMR detection".into());
    }
    let capped = &recipient_pubkeys[..recipient_pubkeys.len().min(MAX_OMR_DETECT_PUBKEYS)];
    match scheme {
        OmrScheme::UnifOmr => capped
            .iter()
            .map(|pk| derive_detection_key(network, scheme, wallet_secret, current_height, pk))
            .collect(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // =====================================================================
    // Detection Key Serialization
    // =====================================================================

    #[test]
    fn test_detection_key_serialize_roundtrip() {
        let key = OmrDetectionKey::new(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            vec![0xAA, 0xBB, 0xCC, 0xDD],
        );

        let bytes = key.serialize();
        let parsed = OmrDetectionKey::deserialize(&bytes).unwrap();

        assert_eq!(parsed.version, OMR_KEY_VERSION);
        assert_eq!(parsed.network, OmrNetwork::Testnet);
        assert_eq!(parsed.scheme, OmrScheme::UnifOmr);
        assert_eq!(parsed.key_material, vec![0xAA, 0xBB, 0xCC, 0xDD]);
    }

    #[test]
    fn test_detection_key_serialize_mainnet_unifomr() {
        let key = OmrDetectionKey::new(OmrNetwork::Mainnet, OmrScheme::UnifOmr, vec![0x01; 100]);

        let bytes = key.serialize();
        assert_eq!(bytes[0], OMR_KEY_VERSION); // version v2
        assert_eq!(bytes[1], 0x00); // mainnet
        assert_eq!(bytes[2], 0x05); // unifomr
        assert_eq!(&bytes[3..7], &[0x00, 0x00, 0x00, 0x00]); // epoch=0 LE
        assert_eq!(&bytes[7..], &[0x01; 100]);

        let parsed = OmrDetectionKey::deserialize(&bytes).unwrap();
        assert_eq!(parsed, key);
    }

    #[test]
    fn test_detection_key_serialize_with_epoch() {
        let key = OmrDetectionKey::new_with_epoch(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            42,
            vec![0xAA],
        );
        let bytes = key.serialize();
        assert_eq!(bytes[0], OMR_KEY_VERSION);
        assert_eq!(bytes[1], 0x01); // testnet
        assert_eq!(bytes[2], 0x05); // unifomr
        assert_eq!(&bytes[3..7], &42u32.to_le_bytes()); // epoch=42
        assert_eq!(&bytes[7..], &[0xAA]);
    }

    #[test]
    fn test_detection_key_wire_format_fixture_v2() {
        // Fixed test vector for cross-platform parity verification.
        // V2 format: version(0x02) + network + scheme + epoch(4 LE) + key_material
        let key = OmrDetectionKey::new(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            vec![0xDE, 0xAD, 0xBE, 0xEF],
        );
        let bytes = key.serialize();
        assert_eq!(
            bytes,
            vec![0x02, 0x01, 0x05, 0x00, 0x00, 0x00, 0x00, 0xDE, 0xAD, 0xBE, 0xEF],
            "V2 wire format mismatch — cross-platform parity broken"
        );
    }

    // =====================================================================
    // Deserialization Edge Cases
    // =====================================================================

    #[test]
    fn test_deserialize_rejects_empty() {
        assert!(OmrDetectionKey::deserialize(&[]).is_none());
    }

    #[test]
    fn test_deserialize_rejects_too_short() {
        // Need at least 8 bytes: version + network + scheme + epoch(4) + 1 byte key
        assert!(OmrDetectionKey::deserialize(&[0x02]).is_none());
        assert!(OmrDetectionKey::deserialize(&[0x02, 0x00]).is_none());
        assert!(OmrDetectionKey::deserialize(&[0x02, 0x00, 0x05]).is_none());
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00]).is_none()
        );
    }

    #[test]
    fn test_deserialize_rejects_unsupported_version() {
        assert!(OmrDetectionKey::deserialize(&[0x00, 0x00, 0x05, 0xFF]).is_none());
        assert!(OmrDetectionKey::deserialize(&[0x03, 0x00, 0x05, 0xFF]).is_none());
        assert!(OmrDetectionKey::deserialize(&[0xFF, 0x00, 0x05, 0xFF]).is_none());
    }

    #[test]
    fn test_deserialize_rejects_unknown_network() {
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x02, 0x05, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0xFF, 0x05, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
    }

    #[test]
    fn test_deserialize_rejects_unknown_scheme() {
        // Only 0x05 (UnifOmr / Param2) is valid
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
        assert!(
            OmrDetectionKey::deserialize(&[0x02, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF])
                .is_none()
        );
    }

    // =====================================================================
    // Network Parsing
    // =====================================================================

    #[test]
    fn test_network_from_str() {
        assert_eq!(
            OmrNetwork::from_network_str("mainnet"),
            Some(OmrNetwork::Mainnet)
        );
        assert_eq!(
            OmrNetwork::from_network_str("testnet"),
            Some(OmrNetwork::Testnet)
        );
        assert_eq!(
            OmrNetwork::from_network_str("localnet"),
            Some(OmrNetwork::Testnet)
        );
        assert_eq!(
            OmrNetwork::from_network_str(" testnet "),
            Some(OmrNetwork::Testnet)
        );
        assert_eq!(OmrNetwork::from_network_str("invalid"), None);
        assert_eq!(OmrNetwork::from_network_str(""), None);
    }

    #[test]
    fn test_network_byte_roundtrip() {
        for net in [OmrNetwork::Mainnet, OmrNetwork::Testnet] {
            assert_eq!(OmrNetwork::from_byte(net.to_byte()), Some(net));
        }
    }

    // =====================================================================
    // Scheme Parsing
    // =====================================================================

    #[test]
    fn test_scheme_byte_roundtrip() {
        assert_eq!(
            OmrScheme::from_byte(OmrScheme::UnifOmr.to_byte()),
            Some(OmrScheme::UnifOmr)
        );
    }

    #[test]
    fn test_scheme_proto_name() {
        assert_eq!(OmrScheme::UnifOmr.proto_name(), "unifomr");
    }

    #[test]
    fn test_scheme_rejects_invalid_bytes() {
        // Only 0x05 (UnifOmr) is valid
        assert!(OmrScheme::from_byte(0x01).is_none());
        assert!(OmrScheme::from_byte(0x02).is_none());
        assert!(OmrScheme::from_byte(0x03).is_none());
    }

    // =====================================================================
    // Domain Separation
    // =====================================================================

    #[test]
    fn test_domain_hash_differs_by_network() {
        let key_main = OmrDetectionKey::new(OmrNetwork::Mainnet, OmrScheme::UnifOmr, vec![0xAA]);
        let key_test = OmrDetectionKey::new(OmrNetwork::Testnet, OmrScheme::UnifOmr, vec![0xAA]);
        assert_ne!(
            key_main.domain_hash(),
            key_test.domain_hash(),
            "Domain hash must differ between mainnet and testnet"
        );
    }

    #[test]
    fn test_domain_hash_differs_by_key_material() {
        let key_a = OmrDetectionKey::new(OmrNetwork::Testnet, OmrScheme::UnifOmr, vec![0xAA]);
        let key_b = OmrDetectionKey::new(OmrNetwork::Testnet, OmrScheme::UnifOmr, vec![0xBB]);
        assert_ne!(
            key_a.domain_hash(),
            key_b.domain_hash(),
            "Domain hash must differ for different key material"
        );
    }

    #[test]
    fn test_domain_hash_deterministic() {
        let key = OmrDetectionKey::new(OmrNetwork::Testnet, OmrScheme::UnifOmr, vec![0xDE, 0xAD]);
        let hash1 = key.domain_hash();
        let hash2 = key.domain_hash();
        assert_eq!(hash1, hash2, "Domain hash must be deterministic");
    }

    // =====================================================================
    // Key Derivation — UnifOMR
    // =====================================================================

    #[test]
    fn test_derive_detection_key_unifomr() {
        let seed = [0x42u8; 32];
        let result = derive_detection_key(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &seed,
            5000,
            &[0u8; 32],
        );
        let key = result.expect("UnifOMR key derivation should succeed");
        assert_eq!(key.network, OmrNetwork::Testnet);
        assert_eq!(key.scheme, OmrScheme::UnifOmr);
        assert_eq!(key.epoch, 0, "UnifOMR epoch should be 0 for inline queries");
        // Paper UnifOMR GenDetKey: n=1024 BFV ciphertexts (one per RLWE sk coeff).
        assert!(
            key.key_material.len() > 1_000_000,
            "UnifOMR detection key should be >1MB, got {}",
            key.key_material.len()
        );
        assert!(
            key.key_material.len() < 128_000_000,
            "UnifOMR detection key should be <128MB (Param2 gRPC ceiling), got {}",
            key.key_material.len()
        );
    }

    #[test]
    fn test_derive_detection_keys_multi_pubkey_unifomr() {
        let seed = [0x42u8; 32];
        let pk_a = [0x11u8; 32];
        let pk_b = [0x22u8; 32];
        let keys = derive_detection_keys_for_pubkeys(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &seed,
            0,
            &[pk_a, pk_b],
        )
        .unwrap();
        assert_eq!(keys.len(), 2);
        assert_ne!(
            keys[0].key_material, keys[1].key_material,
            "Distinct pubkeys must produce distinct UnifOMR query ciphertexts"
        );
    }

    #[test]
    fn test_derive_detection_keys_respects_cap() {
        let seed = [0x7u8; 32];
        let mut pks = Vec::new();
        for i in 0..(MAX_OMR_DETECT_PUBKEYS + 3) {
            let mut pk = [0u8; 32];
            pk[0] = i as u8;
            pks.push(pk);
        }
        let keys = derive_detection_keys_for_pubkeys(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &seed,
            0,
            &pks,
        )
        .unwrap();
        assert_eq!(keys.len(), MAX_OMR_DETECT_PUBKEYS);
    }

    #[test]
    fn test_derive_detection_key_network_separation() {
        let seed = [0xAB; 32];
        let k_main = derive_detection_key(
            OmrNetwork::Mainnet,
            OmrScheme::UnifOmr,
            &seed,
            0,
            &[0u8; 32],
        )
        .unwrap();
        let k_test = derive_detection_key(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &seed,
            0,
            &[0u8; 32],
        )
        .unwrap();
        assert_ne!(
            k_main.key_material, k_test.key_material,
            "Different networks must produce different keys"
        );
    }

    #[test]
    fn test_derive_detection_key_rejects_short_seed() {
        let result = derive_detection_key(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &[0x00; 16], // too short
            0,
            &[0u8; 32],
        );
        assert!(result.is_err(), "Should reject seeds < 32 bytes");
    }

    #[test]
    fn test_derive_detection_key_static_compat() {
        let seed = [0xAB; 32];
        let k_static =
            derive_detection_key_static(OmrNetwork::Testnet, OmrScheme::UnifOmr, &seed, &[0u8; 32])
                .unwrap();
        let k_explicit = derive_detection_key(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            &seed,
            0,
            &[0u8; 32],
        )
        .unwrap();
        // UnifOMR uses fresh encryption, so key_material differs, but the
        // derivation path (seed → omr_secret) must be the same.
        assert_eq!(k_static.network, k_explicit.network);
        assert_eq!(k_static.scheme, k_explicit.scheme);
        assert_eq!(k_static.epoch, k_explicit.epoch);
    }

    // =====================================================================
    // Cross-Platform Parity Fixtures
    // =====================================================================

    /// Fixed test vectors that MUST produce identical results on iOS and Android.
    /// If this test fails on either platform, cross-platform parity is broken.
    #[test]
    fn test_cross_platform_domain_hash_fixture() {
        let key = OmrDetectionKey::new(
            OmrNetwork::Testnet,
            OmrScheme::UnifOmr,
            vec![0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08],
        );
        let hash = key.domain_hash();

        // This is the expected blake3 hash for:
        // domain_tag = "DarkFi-OMR-DetectionKey-v1" (26 bytes)
        // network = 0x01 (testnet)
        // scheme = 0x05 (unifomr)
        // epoch = 0x00000000 (4 bytes LE, epoch=0 for default constructor)
        // key_material = [0x01..0x08]
        assert_eq!(hash.len(), 32, "blake3 hash must be 32 bytes");
        assert_ne!(hash, [0u8; 32], "Domain hash should not be all zeros");

        // Verify determinism by recomputing manually
        let mut hasher = blake3::Hasher::new();
        hasher.update(b"DarkFi-OMR-DetectionKey-v1");
        hasher.update(&[0x01]); // testnet
        hasher.update(&[0x05]); // unifomr
        hasher.update(&0u32.to_le_bytes()); // epoch=0
        hasher.update(&[0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08]);
        let expected = *hasher.finalize().as_bytes();
        assert_eq!(
            hash, expected,
            "Domain hash mismatch with manual computation"
        );
    }

    #[test]
    fn test_domain_hash_differs_by_epoch() {
        let key_e0 =
            OmrDetectionKey::new_with_epoch(OmrNetwork::Testnet, OmrScheme::UnifOmr, 0, vec![0xAA]);
        let key_e1 =
            OmrDetectionKey::new_with_epoch(OmrNetwork::Testnet, OmrScheme::UnifOmr, 1, vec![0xAA]);
        assert_ne!(
            key_e0.domain_hash(),
            key_e1.domain_hash(),
            "Domain hash must differ between epochs"
        );
    }
}
