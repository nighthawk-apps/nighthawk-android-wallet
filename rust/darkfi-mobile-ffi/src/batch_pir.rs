//! Batch PIR (Private Information Retrieval) for UnifOMR Round 2.
//!
//! SealPIR-style striped BFV selection — must match `darkfi-lightwalletd::pir_server`.

extern crate rand09 as rand;

use fhe::bfv::{BfvParameters, Ciphertext, Encoding, Plaintext, PublicKey, SecretKey};
use fhe_traits::{
    DeserializeParametrized, FheDecoder, FheDecrypter, FheEncoder, FheEncrypter, Serialize,
};
use rand::rngs::StdRng;
use rand::{rng, SeedableRng};
use std::sync::Arc;

use crate::unifomr::{bfv_params, SCHEME_UNIFOMR};

/// Domain separation for Round-2 PIR key material.
pub const UNIFOMR_PIR_DOMAIN: &[u8] = b"DarkFi-UnifOMR-PirKey-v1";

/// Hard cap on limbs fetched per height (32 KiB payloads).
pub const MAX_PIR_LIMBS: usize = 4096;

/// Max stripes (must match server `MAX_PIR_STRIPES`).
pub const MAX_PIR_STRIPES: usize = 8;

/// Derive PIR client seed from wallet secret (independent of detection key).
pub fn derive_pir_seed(wallet_secret: &[u8], network: u8) -> Result<[u8; 32], String> {
    if wallet_secret.len() < 32 {
        return Err("wallet secret too short".into());
    }
    let master: [u8; 32] = wallet_secret[..32]
        .try_into()
        .map_err(|_| "bad wallet secret")?;
    let mut h = blake3::Hasher::new_keyed(&master);
    h.update(UNIFOMR_PIR_DOMAIN);
    h.update(b"-KeyGen");
    h.update(&[network]);
    h.update(&[SCHEME_UNIFOMR]);
    Ok(*h.finalize().as_bytes())
}

/// Number of SealPIR stripes for a height window.
pub fn sealpir_stripe_count(window_size: usize, degree: usize) -> usize {
    if window_size == 0 {
        return 1;
    }
    window_size.div_ceil(degree).max(1)
}

/// Max compact-block bytes reassemblable via PIR (`MAX_PIR_LIMBS` includes length limb).
pub const MAX_PIR_PAYLOAD_BYTES: usize = (MAX_PIR_LIMBS.saturating_sub(1)) * 8;

/// Total limb columns required for a length-prefixed payload (`length` = first limb).
pub fn pir_payload_limb_count(length_limb: u64) -> Option<usize> {
    let len = length_limb as usize;
    if len == 0 || len > MAX_PIR_PAYLOAD_BYTES {
        return None;
    }
    Some(1 + len.div_ceil(8))
}

/// Decode length-prefixed PIR limbs: `limbs[0] = byte_len`, then LE u64 data limbs.
pub fn decode_length_prefixed_limbs(limbs: &[u64]) -> Vec<u8> {
    let Some(need) = limbs.first().copied().and_then(pir_payload_limb_count) else {
        return Vec::new();
    };
    if limbs.len() < need {
        return Vec::new();
    }
    let len = limbs[0] as usize;
    limbs_to_bytes(&limbs[1..need], len)
}

/// Reconstruct per-index payloads from limb columns.
///
/// Wire format (per height): limb0 = original byte length, then packed LE u64 limbs.
/// `limb_cols[limb_i][idx]` is the decrypted limb for height-index `idx`.
pub fn assemble_payloads(indices: &[usize], limb_cols: &[Vec<u64>]) -> Vec<Vec<u8>> {
    indices
        .iter()
        .map(|&idx| {
            let mut limbs = Vec::with_capacity(limb_cols.len());
            for col in limb_cols {
                limbs.push(col.get(idx).copied().unwrap_or(0));
            }
            decode_length_prefixed_limbs(&limbs)
        })
        .collect()
}

/// Server-side batch / SealPIR evaluator.
pub struct BatchPirServer {
    params: Arc<BfvParameters>,
}

impl BatchPirServer {
    pub fn new(params: Arc<BfvParameters>) -> Self {
        Self { params }
    }

    pub fn with_unifomr_params() -> Self {
        Self::new(bfv_params())
    }

    /// Evaluate `Σ query_slots[i] * db[i]` under encryption for one stripe.
    pub fn evaluate_limb(&self, query: &Ciphertext, db: &[u64]) -> Result<Ciphertext, String> {
        let degree = self.params.degree();
        let mut padded = db.to_vec();
        if padded.len() > degree {
            return Err(format!(
                "db length {} exceeds BFV degree {degree}",
                padded.len()
            ));
        }
        padded.resize(degree, 0);
        let pt = Plaintext::try_encode(&padded, Encoding::simd(), &self.params)
            .map_err(|e| format!("PIR db encode: {e:?}"))?;
        Ok(query * &pt)
    }

    pub fn evaluate_limb_bytes(&self, query_bytes: &[u8], db: &[u64]) -> Result<Vec<u8>, String> {
        let query = Ciphertext::from_bytes(query_bytes, &self.params)
            .map_err(|e| format!("PIR query deserialize: {e:?}"))?;
        Ok(self.evaluate_limb(&query, db)?.to_bytes())
    }

    /// SealPIR-style striped evaluation: one query/response per stripe.
    pub fn evaluate_sealpir_stripes(
        &self,
        queries: &[Vec<u8>],
        db: &[u64],
        window_size: usize,
    ) -> Result<Vec<Vec<u8>>, String> {
        let degree = self.params.degree();
        let stripes = sealpir_stripe_count(window_size, degree);
        if queries.len() != stripes {
            return Err(format!(
                "expected {stripes} stripe queries, got {}",
                queries.len()
            ));
        }
        let mut out = Vec::with_capacity(stripes);
        for (s, q) in queries.iter().enumerate() {
            let start = s * degree;
            if start >= window_size {
                // Padding stripe beyond window — evaluate against zeros.
                out.push(self.evaluate_limb_bytes(q, &vec![0u64; degree])?);
                continue;
            }
            let end = (start + degree).min(window_size).min(db.len());
            let mut stripe_db = db.get(start..end).unwrap_or(&[]).to_vec();
            stripe_db.resize(degree, 0);
            out.push(self.evaluate_limb_bytes(q, &stripe_db)?);
        }
        Ok(out)
    }
}

/// Client-side batch PIR helper (shared by moonshine / mobile FFI).
pub struct BatchPirClient {
    pub secret_key: SecretKey,
    pub public_key: PublicKey,
    pub params: Arc<BfvParameters>,
}

impl BatchPirClient {
    pub fn from_seed(seed: [u8; 32]) -> Self {
        let mut r = StdRng::from_seed(seed);
        let params = bfv_params();
        let secret_key = SecretKey::random(&params, &mut r);
        let public_key = PublicKey::new(&secret_key, &mut r);
        Self {
            secret_key,
            public_key,
            params,
        }
    }

    pub fn from_wallet(wallet_secret: &[u8], network: u8) -> Result<Self, String> {
        Ok(Self::from_seed(derive_pir_seed(wallet_secret, network)?))
    }

    fn encrypt_selection(&self, selection: &[u64]) -> Result<Vec<u8>, String> {
        let pt = Plaintext::try_encode(selection, Encoding::simd(), &self.params)
            .map_err(|e| format!("PIR query encode: {e:?}"))?;
        let mut r = rng();
        let ct = self
            .public_key
            .try_encrypt(&pt, &mut r)
            .map_err(|e| format!("PIR query encrypt: {e:?}"))?;
        Ok(ct.to_bytes())
    }

    /// SealPIR-style queries: one CT per stripe covering `window_size`.
    ///
    /// Always emits `sealpir_stripe_count` ciphertexts (dummy zero encrypts for
    /// stripes without selected indices) so the server cannot see the stripe.
    pub fn generate_sealpir_queries(
        &self,
        indices: &[usize],
        window_size: usize,
    ) -> Result<Vec<Vec<u8>>, String> {
        if indices.is_empty() {
            return Err("empty PIR index set".into());
        }
        let degree = self.params.degree();
        let stripes = sealpir_stripe_count(window_size, degree);
        if stripes > MAX_PIR_STRIPES {
            return Err(format!(
                "window {window_size} needs {stripes} stripes (max {MAX_PIR_STRIPES})"
            ));
        }
        for &idx in indices {
            if idx >= window_size {
                return Err(format!("index {idx} out of window {window_size}"));
            }
        }

        let mut out = Vec::with_capacity(stripes);
        for s in 0..stripes {
            let mut selection = vec![0u64; degree];
            for &idx in indices {
                if idx / degree == s {
                    selection[idx % degree] = 1;
                }
            }
            out.push(self.encrypt_selection(&selection)?);
        }
        Ok(out)
    }

    /// Single-stripe one-hot query (window ≤ degree).
    pub fn generate_query(&self, index: usize, window_size: usize) -> Result<Vec<u8>, String> {
        Ok(self
            .generate_sealpir_queries(&[index], window_size)?
            .remove(0))
    }

    /// Multi-hot query within one stripe (window ≤ degree).
    pub fn generate_batch_query(
        &self,
        indices: &[usize],
        window_size: usize,
    ) -> Result<Vec<u8>, String> {
        let mut q = self.generate_sealpir_queries(indices, window_size)?;
        if q.len() != 1 {
            return Err(format!(
                "window {window_size} spans {} stripes; use generate_sealpir_queries",
                q.len()
            ));
        }
        Ok(q.remove(0))
    }

    pub fn decrypt_response(&self, response: &[u8]) -> Result<Vec<u64>, String> {
        let ct = Ciphertext::from_bytes(response, &self.params)
            .map_err(|e| format!("PIR response: {e:?}"))?;
        let pt = self
            .secret_key
            .try_decrypt(&ct)
            .map_err(|e| format!("PIR decrypt: {e:?}"))?;
        Vec::<u64>::try_decode(&pt, Encoding::simd()).map_err(|e| format!("PIR decode: {e:?}"))
    }

    /// Decrypt SealPIR stripe responses into a flat `window_size` limb vector.
    pub fn decrypt_sealpir_stripes(
        &self,
        responses: &[Vec<u8>],
        window_size: usize,
    ) -> Result<Vec<u64>, String> {
        let degree = self.params.degree();
        let stripes = sealpir_stripe_count(window_size, degree);
        if responses.len() != stripes {
            return Err(format!(
                "expected {stripes} stripe responses, got {}",
                responses.len()
            ));
        }
        let mut out = vec![0u64; window_size];
        for (s, resp) in responses.iter().enumerate() {
            let slots = self.decrypt_response(resp)?;
            let base = s * degree;
            for i in 0..degree {
                let g = base + i;
                if g >= window_size {
                    break;
                }
                out[g] = slots.get(i).copied().unwrap_or(0);
            }
        }
        Ok(out)
    }
}

/// Pack compact-block bytes into length-prefixed u64 limbs for PIR columns.
///
/// Limb 0 is `data.len()` so Round-2 reassembly preserves trailing zero bytes
/// and exact protobuf length (not `limbs×8`).
pub fn bytes_to_limbs(data: &[u8]) -> Vec<u64> {
    let mut out = Vec::with_capacity(1 + data.len().div_ceil(8));
    out.push(data.len() as u64);
    for chunk in data.chunks(8) {
        let mut buf = [0u8; 8];
        buf[..chunk.len()].copy_from_slice(chunk);
        out.push(u64::from_le_bytes(buf));
    }
    out
}

pub fn limbs_to_bytes(limbs: &[u64], len: usize) -> Vec<u8> {
    let mut out = Vec::with_capacity(len);
    for limb in limbs {
        out.extend_from_slice(&limb.to_le_bytes());
    }
    out.truncate(len);
    out
}

/// Build one PIR db column: limb `limb_index` of each height's payload (0 if missing/short).
pub fn limb_column(payloads: &[Vec<u8>], limb_index: usize) -> Vec<u64> {
    payloads
        .iter()
        .map(|p| {
            let limbs = bytes_to_limbs(p);
            limbs.get(limb_index).copied().unwrap_or(0)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_batch_pir_single_select() {
        let client = BatchPirClient::from_seed([9u8; 32]);
        let server = BatchPirServer::with_unifomr_params();
        let mut db = vec![0u64; 100];
        for i in 0..100 {
            db[i] = 1000 + i as u64;
        }
        let qs = client.generate_sealpir_queries(&[42], 100).unwrap();
        assert_eq!(qs.len(), 1);
        let resp = server.evaluate_sealpir_stripes(&qs, &db, 100).unwrap();
        let slots = client.decrypt_sealpir_stripes(&resp, 100).unwrap();
        assert_eq!(slots[42], 1042);
        assert_eq!(slots[0], 0);
    }

    #[test]
    fn test_sealpir_cross_stripe() {
        let client = BatchPirClient::from_seed([3u8; 32]);
        let server = BatchPirServer::with_unifomr_params();
        let degree = client.params.degree();
        let window = degree + 50;
        let mut db = vec![0u64; window];
        for i in 0..window {
            db[i] = 7_000 + i as u64;
        }
        let target = degree + 17;
        let qs = client.generate_sealpir_queries(&[target], window).unwrap();
        assert_eq!(qs.len(), 2);
        let resp = server.evaluate_sealpir_stripes(&qs, &db, window).unwrap();
        let slots = client.decrypt_sealpir_stripes(&resp, window).unwrap();
        assert_eq!(slots[target], 7_000 + target as u64);
        assert_eq!(slots[0], 0);
    }

    #[test]
    fn test_limb_roundtrip() {
        let data = b"hello compact block payload";
        let limbs = bytes_to_limbs(data);
        assert_eq!(limbs[0], data.len() as u64);
        let back = decode_length_prefixed_limbs(&limbs);
        assert_eq!(back, data);
    }

    #[test]
    fn test_limb_roundtrip_trailing_zeros() {
        let mut data = b"compact".to_vec();
        data.extend_from_slice(&[0u8; 5]);
        let limbs = bytes_to_limbs(&data);
        let cols: Vec<Vec<u64>> = (0..limbs.len()).map(|i| vec![limbs[i]]).collect();
        let got = assemble_payloads(&[0], &cols);
        assert_eq!(got[0], data);
    }
}
