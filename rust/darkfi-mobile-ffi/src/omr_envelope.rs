//! OMR transaction envelope: embeds memo metadata and FHE clue hints outside the
//! on-chain `Transaction` bytes (stripped before darkfid broadcast).
//!
//! ## Wire format
//!
//! ```text
//! [b"O2" (2)] [omr_memo_len u16 LE] [omr_memo]
//! [fhe_clue_len u32 LE] [fhe_clue] [raw_tx]
//! ```
//!
//! Supports large UnifOMR RLWE clues (multi-KB). Only `O2` is accepted.

pub const OMR_ENVELOPE_TAG: &[u8; 2] = b"O2";
pub const MAX_OMR_MEMO_BYTES: usize = 4096;
pub const MAX_FHE_CLUE_BYTES: usize = 65_536;

/// Build an OMR envelope. Rejects oversized memo/clue (no silent u16 truncation).
pub fn wrap_envelope(
    omr_memo: &[u8],
    fhe_clue: &[u8],
    tx_bytes: &[u8],
) -> Result<Vec<u8>, String> {
    if omr_memo.len() > MAX_OMR_MEMO_BYTES {
        return Err(format!(
            "OMR memo too large: {} (max {MAX_OMR_MEMO_BYTES})",
            omr_memo.len()
        ));
    }
    if fhe_clue.len() > MAX_FHE_CLUE_BYTES {
        return Err(format!(
            "FHE clue too large: {} (max {MAX_FHE_CLUE_BYTES})",
            fhe_clue.len()
        ));
    }
    let mut out = Vec::with_capacity(4 + omr_memo.len() + 4 + fhe_clue.len() + tx_bytes.len());
    out.extend_from_slice(OMR_ENVELOPE_TAG);
    out.extend_from_slice(&(omr_memo.len() as u16).to_le_bytes());
    out.extend_from_slice(omr_memo);
    out.extend_from_slice(&(fhe_clue.len() as u32).to_le_bytes());
    out.extend_from_slice(fhe_clue);
    out.extend_from_slice(tx_bytes);
    Ok(out)
}

/// Parsed envelope contents.
pub struct OmrEnvelope<'a> {
    pub omr_memo: &'a [u8],
    pub fhe_clue: &'a [u8],
    pub raw_tx: &'a [u8],
}

/// Parse an `O2` envelope. Returns `None` if the tag is absent or malformed.
pub fn parse_envelope(data: &[u8]) -> Option<OmrEnvelope<'_>> {
    if data.len() < 5 {
        return None;
    }
    if data[0] == b'O' && data[1] == b'2' {
        return parse_o2(data);
    }
    None
}

fn parse_o2(data: &[u8]) -> Option<OmrEnvelope<'_>> {
    let memo_len = u16::from_le_bytes([data[2], data[3]]) as usize;
    if memo_len > MAX_OMR_MEMO_BYTES {
        return None;
    }
    let memo_end = 4 + memo_len;
    if data.len() < memo_end + 4 {
        return None;
    }
    let clue_len = u32::from_le_bytes(data[memo_end..memo_end + 4].try_into().ok()?) as usize;
    if clue_len > MAX_FHE_CLUE_BYTES {
        return None;
    }
    let clue_start = memo_end + 4;
    let clue_end = clue_start.checked_add(clue_len)?;
    if clue_end > data.len() {
        return None;
    }
    Some(OmrEnvelope {
        omr_memo: &data[4..memo_end],
        fhe_clue: &data[clue_start..clue_end],
        raw_tx: &data[clue_end..],
    })
}

/// Strip envelope if present. If data starts with O2 but is malformed, error
/// (fail closed — do not broadcast garbage as raw tx).
pub fn strip_envelope(data: &[u8]) -> Result<&[u8], String> {
    if data.len() >= 2 && data[0] == b'O' && data[1] == b'2' {
        return parse_envelope(data)
            .map(|e| e.raw_tx)
            .ok_or_else(|| "malformed OMR envelope".into());
    }
    Ok(data)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_roundtrip_large_clue() {
        let memo = vec![0x4F, 0x05, 0x02];
        let clue = vec![0xABu8; 8200];
        let tx = vec![0x01, 0x02, 0x03, 0x04];
        let env = wrap_envelope(&memo, &clue, &tx).unwrap();
        assert_eq!(&env[..2], b"O2");
        let parsed = parse_envelope(&env).expect("parse");
        assert_eq!(parsed.omr_memo, memo.as_slice());
        assert_eq!(parsed.fhe_clue, clue.as_slice());
        assert_eq!(parsed.raw_tx, tx.as_slice());
        assert_eq!(strip_envelope(&env).unwrap(), tx.as_slice());
    }

    #[test]
    fn rejects_oversized_memo() {
        let memo = vec![0u8; MAX_OMR_MEMO_BYTES + 1];
        assert!(wrap_envelope(&memo, &[], b"tx").is_err());
    }

    #[test]
    fn rejects_malformed_o2_strip() {
        let bad = b"O2\x01\x00"; // truncated
        assert!(strip_envelope(bad).is_err());
    }

    #[test]
    fn rejects_invalid_om_tag() {
        let memo = vec![0x4F, 0x05, 0x02];
        let clue = vec![0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88];
        let tx = vec![0xDE, 0xAD];
        let mut env = Vec::new();
        env.extend_from_slice(b"OM");
        env.extend_from_slice(&(memo.len() as u16).to_le_bytes());
        env.extend_from_slice(&memo);
        env.push(clue.len() as u8);
        env.extend_from_slice(&clue);
        env.extend_from_slice(&tx);
        assert!(parse_envelope(&env).is_none());
        assert_eq!(strip_envelope(&env).unwrap(), env.as_slice());
    }
}
