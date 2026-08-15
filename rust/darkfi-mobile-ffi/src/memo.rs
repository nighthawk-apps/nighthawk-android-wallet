//! Payment memo encoding and encrypted OMR metadata for LWD off-chain channel.
//!
//! ## Architecture
//!
//! OMR metadata (scheme, clue seed, user memo) is encrypted with the
//! recipient's public key and sent via LWD's `omr_metadata_enc` proto field.
//! LWD stores the encrypted blob opaquely and merges it into the CompactOutput
//! when the transaction confirms. Only the recipient can decrypt it.
//!
//! `MoneyNote::memo` is reserved for plain user-visible text only (no OMR framing).
//!
//! ## Encrypted metadata wire format (inside AeadEncryptedNote)
//!
//! ```text
//! Byte 0:   OMR_MEMO_MAGIC (0x4F = 'O')
//! Byte 1:   Scheme byte (0x05 = UnifOMR default)
//! Byte 2:   Flags (0x01 = has_user_memo, 0x02 = unifomr_validated)
//! Bytes 3-34: Detection clue seed (32 bytes, Blake3 of sender secret + recipient pubkey)
//! Byte 35:  User memo length (u8, 0 if no memo)
//! Bytes 36+: User memo (UTF-8, up to 255 bytes)
//! ```
//!
//! The plaintext is encrypted into an `AeadEncryptedNote` using the recipient's
//! public key, producing the `omr_metadata_enc` bytes sent to LWD.

/// Magic byte identifying an OMR-aware memo.
pub const OMR_MEMO_MAGIC: u8 = 0x4F; // 'O' for OMR

/// OMR scheme identifiers (must match lightwalletd's SCHEME_* constants).
pub const SCHEME_UNIFOMR: u8 = 0x05;

/// Default scheme for all outgoing transactions: UnifOMR (0x05).
pub const DEFAULT_TX_OMR_SCHEME: u8 = SCHEME_UNIFOMR;

/// Flag bits for the flags byte.
const FLAG_HAS_USER_MEMO: u8 = 0x01;
/// Set when the memo scheme is UnifOMR (wire bit historically used for validated OMR).
const FLAG_UNIFOMR_VALIDATED: u8 = 0x02;

/// Maximum size of the user-visible memo text (UTF-8 bytes).
pub const MAX_PAYMENT_MEMO_BYTES: usize = 255;

/// Total maximum memo field size (header + user memo).
pub const MAX_MEMO_FIELD_SIZE: usize = 36 + MAX_PAYMENT_MEMO_BYTES;

/// Build the OMR-aware memo bytes for a `MoneyNote`.
///
/// This is called during transaction creation to embed the sender's
/// OMR detection clue in the encrypted note. The recipient decrypts
/// the note and extracts the clue to validate detection correctness.
///
/// ## Arguments
/// - `sender_secret`: The 32-byte sender wallet secret (used for clue derivation)
/// - `recipient_pubkey`: The recipient's 32-byte public key
/// - `user_memo`: Optional user-visible memo text
/// - `scheme`: OMR scheme byte (defaults to `DEFAULT_TX_OMR_SCHEME`)
pub fn build_omr_memo(
    sender_secret: &[u8; 32],
    recipient_pubkey: &[u8; 32],
    user_memo: Option<&str>,
    scheme: Option<u8>,
) -> Result<Vec<u8>, String> {
    let scheme = scheme.unwrap_or(DEFAULT_TX_OMR_SCHEME);

    // Derive the detection clue seed: Blake3(sender_secret || recipient_pubkey || "OMR-Clue")
    // This is NOT the sender's detection key. It's a per-transaction clue that:
    // - Proves the sender computed an OMR-compatible output
    // - Is unique per (sender, recipient) pair
    // - Cannot be used to identify the sender without knowing sender_secret
    let mut hasher = blake3::Hasher::new_keyed(sender_secret);
    hasher.update(recipient_pubkey);
    hasher.update(b"DarkFi-OMR-TxClue-v1");
    hasher.update(&[scheme]);
    let clue_seed: [u8; 32] = hasher.finalize().into();

    // Parse and validate user memo
    let memo_text = user_memo.map(str::trim).filter(|s| !s.is_empty());

    if let Some(text) = memo_text {
        if text.len() > MAX_PAYMENT_MEMO_BYTES {
            return Err(format!(
                "memo exceeds {} bytes (UTF-8 length {})",
                MAX_PAYMENT_MEMO_BYTES,
                text.len()
            ));
        }
    }

    // Build wire format
    let mut buf = Vec::with_capacity(36 + memo_text.map_or(0, |t| t.len()));

    // Header (3 bytes)
    buf.push(OMR_MEMO_MAGIC);
    buf.push(scheme);

    let mut flags: u8 = 0;
    if memo_text.is_some() {
        flags |= FLAG_HAS_USER_MEMO;
    }
    if scheme == SCHEME_UNIFOMR {
        flags |= FLAG_UNIFOMR_VALIDATED;
    }
    buf.push(flags);

    // Detection clue seed (32 bytes)
    buf.extend_from_slice(&clue_seed);

    // User memo
    if let Some(text) = memo_text {
        let text_bytes = text.as_bytes();
        buf.push(text_bytes.len() as u8);
        buf.extend_from_slice(text_bytes);
    } else {
        buf.push(0u8);
    }

    // Defense-in-depth: the header + length-capped memo can never exceed the
    // wire cap, but assert it so any future header/format change that would
    // overflow a note's memo field fails loudly here instead of on-chain.
    debug_assert!(
        buf.len() <= MAX_MEMO_FIELD_SIZE,
        "OMR memo {} bytes exceeds MAX_MEMO_FIELD_SIZE {}",
        buf.len(),
        MAX_MEMO_FIELD_SIZE
    );

    Ok(buf)
}

/// Parse a user-facing payment memo string into wire bytes.
#[cfg(test)]
pub fn parse_payment_memo(memo: Option<&str>) -> Result<Option<Vec<u8>>, String> {
    let Some(text) = memo.map(str::trim).filter(|s| !s.is_empty()) else {
        return Ok(None);
    };

    if text.len() > MAX_PAYMENT_MEMO_BYTES {
        return Err(format!(
            "memo exceeds {MAX_PAYMENT_MEMO_BYTES} bytes (UTF-8 length {})",
            text.len()
        ));
    }

    Ok(Some(text.as_bytes().to_vec()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_omr_memo_default_scheme() {
        let secret = [0x42u8; 32];
        let pubkey = [0xABu8; 32];
        let memo = build_omr_memo(&secret, &pubkey, None, None).unwrap();

        assert_eq!(memo[0], OMR_MEMO_MAGIC, "First byte must be OMR magic");
        assert_eq!(
            memo[1], SCHEME_UNIFOMR,
            "Default scheme must be UnifOMR (0x05)"
        );
        assert_eq!(
            memo[2] & FLAG_UNIFOMR_VALIDATED,
            FLAG_UNIFOMR_VALIDATED,
            "UnifOMR must have validated flag set"
        );
        assert_eq!(memo.len(), 36, "No user memo = 36 bytes total");
        assert_eq!(memo[35], 0, "Memo length byte should be 0");
    }

    #[test]
    fn test_build_omr_memo_with_user_text() {
        let secret = [0x42u8; 32];
        let pubkey = [0xABu8; 32];
        let memo = build_omr_memo(&secret, &pubkey, Some("Payment for coffee"), None).unwrap();

        assert_eq!(memo[0], OMR_MEMO_MAGIC);
        assert_eq!(memo[1], SCHEME_UNIFOMR);
        assert!(
            memo[2] & FLAG_HAS_USER_MEMO != 0,
            "Should have user memo flag"
        );
        assert_eq!(memo[35], 18, "Memo length = 18 bytes");
        assert_eq!(&memo[36..], b"Payment for coffee");
    }

    #[test]
    fn test_build_omr_memo_too_long() {
        let secret = [0x42u8; 32];
        let pubkey = [0xABu8; 32];
        let long_memo = "x".repeat(256);
        let result = build_omr_memo(&secret, &pubkey, Some(&long_memo), None);
        assert!(result.is_err(), "Should reject memo > 255 bytes");
    }

    #[test]
    fn test_clue_seed_deterministic() {
        let secret = [0x42u8; 32];
        let pubkey = [0xABu8; 32];
        let m1 = build_omr_memo(&secret, &pubkey, None, None).unwrap();
        let m2 = build_omr_memo(&secret, &pubkey, None, None).unwrap();
        assert_eq!(m1[3..35], m2[3..35], "Same inputs → same clue seed");
    }

    #[test]
    fn test_clue_seed_varies_with_recipient() {
        let secret = [0x42u8; 32];
        let pk1 = [0xAAu8; 32];
        let pk2 = [0xBBu8; 32];
        let m1 = build_omr_memo(&secret, &pk1, None, None).unwrap();
        let m2 = build_omr_memo(&secret, &pk2, None, None).unwrap();
        assert_ne!(
            m1[3..35],
            m2[3..35],
            "Different recipients → different clue seeds"
        );
    }

    #[test]
    fn test_default_tx_omr_scheme_is_unifomr() {
        assert_eq!(
            DEFAULT_TX_OMR_SCHEME, SCHEME_UNIFOMR,
            "Default outgoing tx OMR scheme MUST be UnifOMR (0x05)"
        );
    }

    #[test]
    fn test_parse_payment_memo() {
        assert!(parse_payment_memo(None).unwrap().is_none());
        assert!(parse_payment_memo(Some("")).unwrap().is_none());
        assert!(parse_payment_memo(Some("  ")).unwrap().is_none());
        assert_eq!(
            parse_payment_memo(Some("hello")).unwrap(),
            Some(b"hello".to_vec())
        );
    }

    #[test]
    fn test_encrypt_omr_metadata_roundtrip() {
        use darkfi_sdk::crypto::{PublicKey, SecretKey};
        use rand::rngs::OsRng;

        let sk = SecretKey::random(&mut OsRng);
        let pk = PublicKey::from_secret(sk);

        let sender_secret = [0x42u8; 32];
        let recipient_pubkey = [0xABu8; 32];
        let user_memo = Some("test payment for coffee ☕");

        // Build the plaintext OMR metadata
        let metadata = build_omr_memo(&sender_secret, &recipient_pubkey, user_memo, None).unwrap();

        // Encrypt for the recipient
        let encrypted = encrypt_omr_metadata(&metadata, &pk).expect("encryption should succeed");

        // Must be larger than plaintext (has ephemeral pubkey + AEAD overhead)
        assert!(encrypted.len() > metadata.len());

        // Decrypt with the recipient's secret key
        let decrypted = decrypt_omr_metadata(&encrypted, &sk).expect("decryption should succeed");

        assert_eq!(decrypted, metadata, "roundtrip must be lossless");

        // Verify the decrypted content parses correctly
        assert_eq!(decrypted[0], OMR_MEMO_MAGIC);
        assert_eq!(decrypted[1], SCHEME_UNIFOMR);
        let memo_len = decrypted[35] as usize;
        let memo_text = std::str::from_utf8(&decrypted[36..36 + memo_len]).unwrap();
        assert_eq!(memo_text, "test payment for coffee ☕");
    }

    #[test]
    fn test_decrypt_wrong_key_fails() {
        use darkfi_sdk::crypto::{PublicKey, SecretKey};
        use rand::rngs::OsRng;

        let sk1 = SecretKey::random(&mut OsRng);
        let pk1 = PublicKey::from_secret(sk1);
        let sk2 = SecretKey::random(&mut OsRng);

        let metadata = build_omr_memo(&[0x01; 32], &[0x02; 32], None, None).unwrap();
        let encrypted = encrypt_omr_metadata(&metadata, &pk1).unwrap();

        // Decrypting with a different key should fail
        assert!(
            decrypt_omr_metadata(&encrypted, &sk2).is_none(),
            "wrong key must fail to decrypt"
        );
    }

    #[test]
    fn test_decrypt_empty_returns_none() {
        use darkfi_sdk::crypto::SecretKey;
        use rand::rngs::OsRng;

        let sk = SecretKey::random(&mut OsRng);
        assert!(decrypt_omr_metadata(&[], &sk).is_none());
        assert!(decrypt_omr_metadata(&[0x00; 5], &sk).is_none());
    }

    #[test]
    fn test_parse_omr_metadata_with_clue_bind() {
        let secret = [0x42u8; 32];
        let pubkey = [0xABu8; 32];
        let mut body = build_omr_memo(&secret, &pubkey, Some("hello memo"), None).unwrap();
        let clue = vec![0x11u8; 64];
        let clue_hash = *blake3::hash(&clue).as_bytes();
        body.extend_from_slice(b"|CLUE|");
        body.extend_from_slice(&clue_hash);
        let parsed = parse_omr_metadata_plaintext(&body).expect("parse");
        assert_eq!(parsed.user_memo.as_deref(), Some("hello memo"));
        assert_eq!(parsed.clue_hash, Some(clue_hash));
    }
}

// ---------------------------------------------------------------------------
// Encrypted OMR metadata — off-chain channel via LWD
// ---------------------------------------------------------------------------

use darkfi_sdk::crypto::{note::AeadEncryptedNote, PublicKey, SecretKey};
use darkfi_serial::{serialize, Decodable, Encodable};

/// Parsed UnifOMR metadata recovered from `omr_metadata_enc`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedOmrMetadata {
    pub scheme: u8,
    pub user_memo: Option<String>,
    /// Present when the sender bound `|CLUE|` + blake3(clue) into the AEAD plaintext.
    pub clue_hash: Option<[u8; 32]>,
}

const CLUE_BIND_MARK: &[u8] = b"|CLUE|";

/// Wrapper for OMR metadata bytes so we can use `AeadEncryptedNote` (which
/// requires `SerialEncodable` / `SerialDecodable`).
#[derive(Clone, Debug)]
struct OmrMetadataBlob(Vec<u8>);

impl Encodable for OmrMetadataBlob {
    fn encode<S: std::io::Write>(&self, s: &mut S) -> std::result::Result<usize, std::io::Error> {
        self.0.encode(s)
    }
}

impl Decodable for OmrMetadataBlob {
    fn decode<D: std::io::Read>(d: &mut D) -> std::result::Result<Self, std::io::Error> {
        let v = Vec::<u8>::decode(d)?;
        Ok(Self(v))
    }
}

/// Encrypt OMR metadata (scheme + clue seed + user memo) for the recipient.
///
/// Uses the same `AeadEncryptedNote` (ephemeral DH + ChaCha20Poly1305) as
/// DarkFi's note encryption. The output bytes are opaque to LWD.
///
/// ## Arguments
/// - `metadata`: The plaintext OMR metadata bytes (from `build_omr_memo`)
/// - `recipient_pubkey`: The recipient's `PublicKey` (pallas curve)
///
/// ## Returns
/// Serialized `AeadEncryptedNote` bytes to send as `omr_metadata_enc`.
pub fn encrypt_omr_metadata(
    metadata: &[u8],
    recipient_pubkey: &PublicKey,
) -> Result<Vec<u8>, String> {
    let blob = OmrMetadataBlob(metadata.to_vec());
    let mut rng = rand::rngs::OsRng;
    let enc_note = AeadEncryptedNote::encrypt(&blob, recipient_pubkey, &mut rng)
        .map_err(|e| format!("Failed to encrypt OMR metadata: {e}"))?;
    Ok(serialize(&enc_note))
}

/// Decrypt OMR metadata from a `CompactOutput.omr_metadata_enc` field.
///
/// Returns `None` if:
/// - The bytes are empty or too short
/// - The AEAD decryption fails (wrong key / corrupted)
///
/// ## Arguments
/// - `encrypted_bytes`: Serialized `AeadEncryptedNote` from the compact output
/// - `secret_key`: The recipient's `SecretKey`
///
/// ## Returns
/// The plaintext OMR metadata bytes (same format as `build_omr_memo` output).
///
pub fn decrypt_omr_metadata(encrypted_bytes: &[u8], secret_key: &SecretKey) -> Option<Vec<u8>> {
    if encrypted_bytes.len() < 48 {
        // Too short for AeadEncryptedNote (32-byte ephemeral key + 16-byte MAC minimum)
        return None;
    }
    let mut cursor = std::io::Cursor::new(encrypted_bytes);
    let enc_note = AeadEncryptedNote::decode(&mut cursor).ok()?;
    let blob: OmrMetadataBlob = enc_note.decrypt(secret_key).ok()?;
    Some(blob.0)
}

/// Parse `build_omr_memo` bytes, optionally followed by `|CLUE|` + 32-byte clue hash.
pub fn parse_omr_metadata_plaintext(plain: &[u8]) -> Option<ParsedOmrMetadata> {
    let (body, clue_hash) = if plain.len() >= CLUE_BIND_MARK.len() + 32 {
        let split = plain.len() - 32 - CLUE_BIND_MARK.len();
        if &plain[split..split + CLUE_BIND_MARK.len()] == CLUE_BIND_MARK {
            let mut hash = [0u8; 32];
            hash.copy_from_slice(&plain[split + CLUE_BIND_MARK.len()..]);
            (&plain[..split], Some(hash))
        } else {
            (plain, None)
        }
    } else {
        (plain, None)
    };

    if body.len() < 36 || body[0] != OMR_MEMO_MAGIC {
        return None;
    }
    let scheme = body[1];
    let flags = body[2];
    let memo_len = body[35] as usize;
    let user_memo =
        if flags & FLAG_HAS_USER_MEMO != 0 && body.len() >= 36 + memo_len && memo_len > 0 {
            String::from_utf8(body[36..36 + memo_len].to_vec())
                .ok()
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
        } else {
            None
        };
    Some(ParsedOmrMetadata {
        scheme,
        user_memo,
        clue_hash,
    })
}

/// Prefer `MoneyNote::memo`; otherwise decrypt `omr_metadata_enc` and take the user text.
///
/// When `omr_clue` is present and the sender bound a clue hash, a mismatch is
/// logged (possible clue swap) but the memo is still returned.
pub fn recover_user_memo(
    note_memo: &[u8],
    omr_metadata_enc: &[u8],
    secret: &SecretKey,
    omr_clue: &[u8],
) -> Vec<u8> {
    if !note_memo.is_empty() {
        return note_memo.to_vec();
    }
    let Some(plain) = decrypt_omr_metadata(omr_metadata_enc, secret) else {
        return Vec::new();
    };
    let Some(parsed) = parse_omr_metadata_plaintext(&plain) else {
        return Vec::new();
    };
    if let Some(expected) = parsed.clue_hash {
        if !omr_clue.is_empty() {
            let actual = *blake3::hash(omr_clue).as_bytes();
            if expected != actual {
                tracing::warn!(
                    target: "wallet-memo",
                    "OMR metadata clue-hash mismatch (possible clue swap on the wire)"
                );
            }
        }
    }
    parsed.user_memo.map(|s| s.into_bytes()).unwrap_or_default()
}
