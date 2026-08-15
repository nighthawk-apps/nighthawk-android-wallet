/* This file is part of Nighthawk Apps (https://nighthawkapps.com)
 *
 * Copyright (C) 2026 Nighthawk Apps
 *
 * UnifOMR — paper-faithful Oblivious Message Retrieval (ePrint 2026/910).
 *
 * Construction (Algorithm 1, simplified for the `fhe` BFV backend):
 *   1. GenClueKey: RLWE-PKE secret/public key (sk_clue, pk_clue)
 *   2. GenClue:    encrypt 0 under pk_clue  → public board clue (a, b)
 *   3. GenDetKey:  BFV-encrypt each sk_clue coefficient (broadcast SIMD)
 *   4. Detect:     LWEmongrass pre-filter → AHE partial decrypt
 *                  Enc(b − ⟨a, sk⟩) packed across D message slots
 *   5. RangeCheck: client decrypts digest; |slot| ≤ R_PRIME ⇒ pertinent
 *   6. BatchPIR:   retrieve payloads at matched indices (see pir_server.rs)
 *
 * Snake-eye / DoS resistance: LWEmongrass validates clue structure before
 * any AHE evaluation (upgrade plan + paper §3.1 citing [57]).
 */

extern crate rand09 as rand;

use fhe::bfv::{
    BfvParameters, BfvParametersBuilder, Ciphertext, Encoding, Plaintext, PublicKey, SecretKey,
};
use fhe_traits::{
    DeserializeParametrized, FheDecoder, FheDecrypter, FheEncoder, FheEncrypter, Serialize,
};
use rand::rngs::StdRng;
use rand::{rng, RngCore, SeedableRng};
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};

// ---------------------------------------------------------------------------
// Scheme / wire constants
// ---------------------------------------------------------------------------

/// UnifOMR scheme byte — distinct from BFV-OMR (0x03) and PerfOMR (0x02).
pub const SCHEME_UNIFOMR: u8 = 0x05;

/// Clue wire magic / version.
pub const CLUE_VERSION: u8 = 0x01;

/// Domain separation for clue-key derivation from wallet seed.
pub const UNIFOMR_CLUE_DOMAIN: &[u8] = b"DarkFi-UnifOMR-ClueKey-v1";

/// Domain separation for detection-key BFV seed.
pub const UNIFOMR_DET_DOMAIN: &[u8] = b"DarkFi-UnifOMR-DetKey-v1";

const MIN_RESPONSE_TIME: Duration = Duration::from_millis(100);

// ---------------------------------------------------------------------------
// Parameter profile — paper Table-1 Param2 (ePrint 2026/910) is ACTIVE.
// Archived honest-MVP constants: see docs/unifomr_mvp_archive.md
// (or darkfi-lightwalletd/docs/unifomr_mvp_archive.md).
// ---------------------------------------------------------------------------

/// RLWE clue dimension — paper Param2 `n=1024`.
pub const CLUE_N: usize = 1024;

/// RLWE ciphertext modulus — paper Param2 `q=1032193` (also BFV plaintext `t`).
pub const CLUE_Q: u64 = 1_032_193;

/// Hamming weight bound — paper Param2 `h=80`.
pub const CLUE_H: usize = 80;

/// Fresh-clue noise tail bound — paper Param2 `r=84`.
///
/// Errors are sampled from a **discrete Gaussian σ=0.5** (see
/// [`sample_gaussian_sigma_half`]); `r` is the paper's whp bound on any fresh
/// error coefficient (σ=0.5 ⇒ P(|e| > 4) < 2⁻⁴⁶, so 84 is a huge margin).
pub const CLUE_ERROR_BOUND: i64 = 84;

/// Discrete Gaussian standard deviation for RLWE errors — paper Param2 `σ=0.5`.
pub const CLUE_ERROR_SIGMA: f64 = 0.5;

/// Number of RLWE plaintext bits — paper Param2 `ℓ=2`.
///
/// The detector evaluates negacyclic coefficients `0..ℓ` per clue layer and
/// the client requires **all ℓ** to pass the range check (AND), so the false
/// positive rate is `((2r′+1)/q)^ℓ ≈ 2⁻²³·⁵`.
pub const CLUE_PLAINTEXT_BITS: usize = 2;

/// Client range-check radius after digest decrypt — paper Param2 `r′=149`.
///
/// Pertinent digest noise per coefficient is `e·u + e₁ − e₂·s` with all error
/// terms Gaussian σ=0.5 and `‖u‖₀=h/2`, `‖s‖₀=h` ⇒ σ_total = √(30.25) ≈ 5.5,
/// so `r′=149 ≈ 27σ` gives ε_n ≈ erfc(19.2) ≈ 2⁻⁵³⁰ per bit. Digest CTs are
/// modulus-switched to the last BFV level before serialization (paper's digest
/// mod-switch); BFV plaintext values are invariant under the switch.
pub const R_PRIME: u64 = 149;

/// Max clue bytes after LWEmongrass raise (a||b + header).
pub const UNIFOMR_MAX_CLUE_SIZE: usize = 32_768;

/// Max clue layers per digest window (matches server + client `layer_count` cap).
pub const MAX_CLUE_LAYERS: usize = 64;

/// Minimal note view for UnifOMR detection (clue bytes only).
#[derive(Clone, Debug)]
pub struct ClueNote {
    pub omr_clue: Vec<u8>,
}

pub type OmrError = String;

// ---------------------------------------------------------------------------
// Shared BFV parameters (AHE for partial decryption + PIR)
// ---------------------------------------------------------------------------

static UNIFOMR_BFV_PARAMS: OnceLock<Arc<BfvParameters>> = OnceLock::new();

/// BFV params for UnifOMR AHE — paper Param2 `D=4096`, plaintext `t=q`.
pub fn bfv_params() -> Result<Arc<BfvParameters>, OmrError> {
    if let Some(p) = UNIFOMR_BFV_PARAMS.get() {
        return Ok(Arc::clone(p));
    }
    let built = BfvParametersBuilder::new()
        .set_degree(4096)
        .set_plaintext_modulus(CLUE_Q)
        .set_moduli_sizes(&[40, 40, 40])
        .build()
        .map(Arc::new)
        .map_err(|e| format!("UnifOMR Param2 BFV parameters: {e}"))?;
    // Another thread may have won the race; prefer the stored value.
    let _ = UNIFOMR_BFV_PARAMS.set(Arc::clone(&built));
    Ok(UNIFOMR_BFV_PARAMS.get().map(Arc::clone).unwrap_or(built))
}

pub fn packing_degree() -> Result<usize, OmrError> {
    Ok(bfv_params()?.degree())
}

// ---------------------------------------------------------------------------
// RLWE clue PKE (paper RLWEenc)
// ---------------------------------------------------------------------------

use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct RlweSecretKey {
    /// Coefficients in (-q/2, q/2], sparse ternary-ish with weight ≤ CLUE_H.
    coeffs: Vec<i64>,
}

#[derive(Clone)]
pub struct RlwePublicKey {
    pub a: Vec<u64>,
    pub b: Vec<u64>,
}

#[derive(Clone, Debug)]
pub struct RlweCiphertext {
    pub a: Vec<u64>,
    pub b: Vec<u64>,
}

/// Cumulative distribution table for |X|, X ~ discrete Gaussian σ=0.5.
///
/// ρ(k) = exp(−k²/2σ²) = exp(−2k²); tail cut at |k|=4 (mass < 2⁻⁴⁶).
static GAUSS_SIGMA_HALF_CDT: OnceLock<[f64; 4]> = OnceLock::new();

fn gauss_sigma_half_cdt() -> &'static [f64; 4] {
    GAUSS_SIGMA_HALF_CDT.get_or_init(|| {
        let rho = |k: i32| (-2.0 * (k * k) as f64).exp();
        let z = rho(0) + 2.0 * (rho(1) + rho(2) + rho(3) + rho(4));
        let mut cdt = [0.0f64; 4];
        let mut acc = rho(0) / z;
        cdt[0] = acc;
        for k in 1..=3 {
            acc += 2.0 * rho(k) / z;
            cdt[k as usize] = acc;
        }
        cdt
    })
}

/// Sample the paper's error distribution: discrete Gaussian, σ = 0.5.
///
/// P(0) ≈ 0.7866, P(±1) ≈ 0.1064, P(±2) ≈ 2.6e-4, P(±3) ≈ 1.2e-8.
pub fn sample_gaussian_sigma_half<R: RngCore>(rng: &mut R) -> i64 {
    let cdt = gauss_sigma_half_cdt();
    // 53 uniform mantissa bits — resolution 2⁻⁵³ ≪ smallest CDT step (≈2⁻²⁶·³).
    let u = (rng.next_u64() >> 11) as f64 * (1.0 / (1u64 << 53) as f64);
    let mag: i64 = if u < cdt[0] {
        0
    } else if u < cdt[1] {
        1
    } else if u < cdt[2] {
        2
    } else if u < cdt[3] {
        3
    } else {
        4
    };
    if mag == 0 {
        0
    } else if rng.next_u32() & 1 == 0 {
        mag
    } else {
        -mag
    }
}

fn mod_q(x: i64) -> u64 {
    let q = CLUE_Q as i64;
    let mut r = x % q;
    if r < 0 {
        r += q;
    }
    r as u64
}

fn center_lift(u: u64) -> i64 {
    let q = CLUE_Q as i64;
    let v = u as i64;
    if v > q / 2 {
        v - q
    } else {
        v
    }
}

#[allow(clippy::needless_range_loop)]
fn poly_mul_mod(a: &[u64], s: &[i64]) -> Vec<u64> {
    // Negacyclic convolution mod (x^n + 1, q) — required for RLWE.
    let n = a.len();
    let mut out = vec![0i128; n];
    for i in 0..n {
        let ai = a[i] as i128;
        if ai == 0 {
            continue;
        }
        for j in 0..n {
            let sj = s[j];
            if sj == 0 {
                continue;
            }
            let exp = i + j;
            let term = ai * (sj as i128);
            if exp < n {
                out[exp] += term;
            } else {
                // x^n ≡ -1
                out[exp - n] -= term;
            }
        }
    }
    out.into_iter()
        .map(|x| mod_q(x.rem_euclid(CLUE_Q as i128) as i64))
        .collect()
}

impl RlweSecretKey {
    pub fn coeffs(&self) -> &[i64] {
        &self.coeffs
    }

    pub fn random<R: RngCore + CryptoRng>(rng: &mut R) -> Self {
        let mut coeffs = vec![0i64; CLUE_N];
        // Exactly CLUE_H non-zeros in {±1}.
        let mut idxs: Vec<usize> = (0..CLUE_N).collect();
        // Fisher–Yates partial shuffle
        for i in 0..CLUE_H {
            let j = i + (rng.next_u32() as usize) % (CLUE_N - i);
            idxs.swap(i, j);
            coeffs[idxs[i]] = if rng.next_u32().is_multiple_of(2) {
                1
            } else {
                -1
            };
        }
        Self { coeffs }
    }

    pub fn from_seed(seed: [u8; 32]) -> Self {
        let mut r = StdRng::from_seed(seed);
        Self::random(&mut r)
    }

    /// Decrypt clue → centered error (≈0 if pertinent / encrypt(0)).
    pub fn decrypt_error(&self, ct: &RlweCiphertext) -> Vec<i64> {
        let as_ = poly_mul_mod(&ct.a, &self.coeffs);
        (0..CLUE_N)
            .map(|i| center_lift(ct.b[i]) - center_lift(as_[i]))
            .collect()
    }
}

impl RlwePublicKey {
    pub fn from_secret<R: RngCore + CryptoRng>(sk: &RlweSecretKey, rng: &mut R) -> Self {
        let a: Vec<u64> = (0..CLUE_N).map(|_| rng.next_u64() % CLUE_Q).collect();
        // Discrete Gaussian σ=0.5 (paper Param2), not uniform.
        let e: Vec<i64> = (0..CLUE_N)
            .map(|_| sample_gaussian_sigma_half(rng))
            .collect();
        let as_ = poly_mul_mod(&a, &sk.coeffs);
        let b: Vec<u64> = (0..CLUE_N)
            .map(|i| mod_q(center_lift(as_[i]) + e[i]))
            .collect();
        Self { a, b }
    }

    /// Encrypt message bits in {0,1}^ℓ lifted into first ℓ coefficients (ℓ=1 → all-zero).
    pub fn encrypt_zeros<R: RngCore + CryptoRng>(&self, rng: &mut R) -> RlweCiphertext {
        // Fresh (a', b' = a'·sk + e) via re-randomization using pk:
        // standard PK encrypt of 0: sample u, e1, e2; c0 = b·u + e1; c1 = a·u + e2
        // With sparse ternary u of weight CLUE_H/2.
        let mut u = vec![0i64; CLUE_N];
        let h = CLUE_H / 2;
        let mut idxs: Vec<usize> = (0..CLUE_N).collect();
        for i in 0..h {
            let j = i + (rng.next_u32() as usize) % (CLUE_N - i);
            idxs.swap(i, j);
            u[idxs[i]] = if rng.next_u32().is_multiple_of(2) {
                1
            } else {
                -1
            };
        }
        // Discrete Gaussian σ=0.5 (paper Param2), not uniform.
        let e1: Vec<i64> = (0..CLUE_N)
            .map(|_| sample_gaussian_sigma_half(rng))
            .collect();
        let e2: Vec<i64> = (0..CLUE_N)
            .map(|_| sample_gaussian_sigma_half(rng))
            .collect();
        let bu = poly_mul_mod(&self.b, &u);
        let au = poly_mul_mod(&self.a, &u);
        let b: Vec<u64> = (0..CLUE_N)
            .map(|i| mod_q(center_lift(bu[i]) + e1[i]))
            .collect();
        let a: Vec<u64> = (0..CLUE_N)
            .map(|i| mod_q(center_lift(au[i]) + e2[i]))
            .collect();
        RlweCiphertext { a, b }
    }
}

use rand::CryptoRng;

// ---------------------------------------------------------------------------
// Clue wire codec
// ---------------------------------------------------------------------------

/// Serialize RLWE clue for CompactOutput.omr_clue / RegisterOmrClue.
///
/// Format: `[ver=0x01 | scheme=0x05 | n:u16 LE | a (n×u64 LE) | b (n×u64 LE) | blake3(body)[..4]]`
pub fn serialize_clue(ct: &RlweCiphertext) -> Vec<u8> {
    let n = ct.a.len();
    let mut body = Vec::with_capacity(3 + 2 + 16 * n);
    body.push(CLUE_VERSION);
    body.push(SCHEME_UNIFOMR);
    body.extend_from_slice(&(n as u16).to_le_bytes());
    for x in &ct.a {
        body.extend_from_slice(&x.to_le_bytes());
    }
    for x in &ct.b {
        body.extend_from_slice(&x.to_le_bytes());
    }
    let hash = blake3::hash(&body);
    body.extend_from_slice(&hash.as_bytes()[..4]);
    body
}

pub fn deserialize_clue(bytes: &[u8]) -> Result<RlweCiphertext, String> {
    if bytes.len() < 7 {
        return Err("clue too short".into());
    }
    if bytes[0] != CLUE_VERSION {
        return Err(format!("bad clue version {:02x}", bytes[0]));
    }
    if bytes[1] != SCHEME_UNIFOMR {
        return Err(format!("not UnifOMR clue (scheme {:02x})", bytes[1]));
    }
    let n = u16::from_le_bytes(bytes[2..4].try_into().unwrap()) as usize;
    if n == 0 || n > 4096 {
        return Err(format!("invalid clue n={n}"));
    }
    let expected = 4 + 16 * n + 4;
    if bytes.len() != expected {
        return Err(format!(
            "clue length {} != expected {expected}",
            bytes.len()
        ));
    }
    let body = &bytes[..bytes.len() - 4];
    let expect = &bytes[bytes.len() - 4..];
    if &blake3::hash(body).as_bytes()[..4] != expect {
        return Err("clue checksum mismatch".into());
    }
    let mut a = Vec::with_capacity(n);
    let mut b = Vec::with_capacity(n);
    let mut off = 4usize;
    for _ in 0..n {
        a.push(u64::from_le_bytes(bytes[off..off + 8].try_into().unwrap()));
        off += 8;
    }
    for _ in 0..n {
        b.push(u64::from_le_bytes(bytes[off..off + 8].try_into().unwrap()));
        off += 8;
    }
    Ok(RlweCiphertext { a, b })
}

/// LWEmongrass-compatible validation for UnifOMR RLWE clues.
pub fn validate_unifomr_clue(clue: &[u8]) -> Result<(), String> {
    if clue.is_empty() {
        return Err("Clue cannot be empty".into());
    }
    if clue.len() > UNIFOMR_MAX_CLUE_SIZE {
        return Err(format!(
            "Clue too large: {} bytes (max {UNIFOMR_MAX_CLUE_SIZE})",
            clue.len()
        ));
    }
    // Full parse + checksum.
    let ct = deserialize_clue(clue)?;
    if ct.a.len() != CLUE_N || ct.b.len() != CLUE_N {
        return Err(format!(
            "Unexpected clue dimension {} (expected {CLUE_N})",
            ct.a.len()
        ));
    }
    for x in ct.a.iter().chain(ct.b.iter()) {
        if *x >= CLUE_Q {
            return Err("Clue coefficient out of range (possible poison)".into());
        }
    }
    Ok(())
}

// ---------------------------------------------------------------------------
// Key derivation (wallet → clue sk / det sk)
// ---------------------------------------------------------------------------

pub fn derive_clue_seed(wallet_secret: &[u8], network: u8) -> Result<[u8; 32], String> {
    if wallet_secret.len() < 32 {
        return Err("wallet secret too short".into());
    }
    let master: [u8; 32] = wallet_secret[..32]
        .try_into()
        .map_err(|_| "bad wallet secret")?;
    let mut h = blake3::Hasher::new_keyed(&master);
    h.update(UNIFOMR_CLUE_DOMAIN);
    h.update(b"-KeyGen");
    h.update(&[network]);
    h.update(&[SCHEME_UNIFOMR]);
    Ok(*h.finalize().as_bytes())
}

pub fn derive_det_seed(wallet_secret: &[u8], network: u8) -> Result<[u8; 32], String> {
    if wallet_secret.len() < 32 {
        return Err("wallet secret too short".into());
    }
    let master: [u8; 32] = wallet_secret[..32]
        .try_into()
        .map_err(|_| "bad wallet secret")?;
    let mut h = blake3::Hasher::new_keyed(&master);
    h.update(UNIFOMR_DET_DOMAIN);
    h.update(b"-KeyGen");
    h.update(&[network]);
    h.update(&[SCHEME_UNIFOMR]);
    Ok(*h.finalize().as_bytes())
}

/// Build sender clue for a recipient's public clue key bytes.
///
/// `pk_bytes` is `serialize_public_key` output (or derived from seed on recipient).
pub fn build_omr_clue_from_pk(pk: &RlwePublicKey) -> Vec<u8> {
    let mut r = rng();
    serialize_clue(&pk.encrypt_zeros(&mut r))
}

pub fn serialize_public_key(pk: &RlwePublicKey) -> Vec<u8> {
    let mut out = Vec::with_capacity(4 + 16 * CLUE_N);
    out.push(CLUE_VERSION);
    out.push(SCHEME_UNIFOMR);
    out.extend_from_slice(&(CLUE_N as u16).to_le_bytes());
    for x in &pk.a {
        out.extend_from_slice(&x.to_le_bytes());
    }
    for x in &pk.b {
        out.extend_from_slice(&x.to_le_bytes());
    }
    out
}

pub fn deserialize_public_key(bytes: &[u8]) -> Result<RlwePublicKey, String> {
    if bytes.len() < 4 {
        return Err("pk too short".into());
    }
    if bytes[0] != CLUE_VERSION || bytes[1] != SCHEME_UNIFOMR {
        return Err("bad pk header".into());
    }
    let n = u16::from_le_bytes(bytes[2..4].try_into().unwrap()) as usize;
    if n != CLUE_N {
        return Err(format!("pk n={n}, expected {CLUE_N}"));
    }
    if bytes.len() != 4 + 16 * n {
        return Err("bad pk length".into());
    }
    let mut a = Vec::with_capacity(n);
    let mut b = Vec::with_capacity(n);
    let mut off = 4usize;
    for _ in 0..n {
        a.push(u64::from_le_bytes(bytes[off..off + 8].try_into().unwrap()));
        off += 8;
    }
    for _ in 0..n {
        b.push(u64::from_le_bytes(bytes[off..off + 8].try_into().unwrap()));
        off += 8;
    }
    Ok(RlwePublicKey { a, b })
}

/// Public clue key derived from wallet (sender needs this — typically from address metadata).
pub fn clue_keypair_from_wallet(
    wallet_secret: &[u8],
    network: u8,
) -> Result<(RlweSecretKey, RlwePublicKey), String> {
    let seed = derive_clue_seed(wallet_secret, network)?;
    let sk = RlweSecretKey::from_seed(seed);
    let mut h = blake3::Hasher::new();
    h.update(&seed);
    h.update(b"-pk-rand");
    let pk_seed = *h.finalize().as_bytes();
    let mut r2 = StdRng::from_seed(pk_seed);
    let pk = RlwePublicKey::from_secret(&sk, &mut r2);
    Ok((sk, pk))
}

// ---------------------------------------------------------------------------
// Detection key (BFV Enc of sk_clue coefficients)
// ---------------------------------------------------------------------------

pub struct UnifOmrClient {
    pub det_sk: SecretKey,
    pub det_pk: PublicKey,
    pub clue_sk: RlweSecretKey,
    pub params: Arc<BfvParameters>,
}

impl UnifOmrClient {
    pub fn from_wallet(wallet_secret: &[u8], network: u8) -> Result<Self, String> {
        let (clue_sk, _) = clue_keypair_from_wallet(wallet_secret, network)?;
        let det_seed = derive_det_seed(wallet_secret, network)?;
        let mut r = StdRng::from_seed(det_seed);
        let params = bfv_params()?;
        let det_sk = SecretKey::random(&params, &mut r);
        let det_pk = PublicKey::new(&det_sk, &mut r);
        Ok(Self {
            det_sk,
            det_pk,
            clue_sk,
            params,
        })
    }

    /// Detection key wire: header + n length-prefixed BFV CTs encrypting sk_j in all slots.
    ///
    /// `[0x02 | network | scheme=0x05 | n:u16 LE | (u32 len || ct_bytes) × n ]`
    pub fn build_detection_key(&self, network: u8) -> Result<Vec<u8>, String> {
        let mut r = rng();
        let t = self.params.plaintext();
        let degree = self.params.degree();
        let mut out = Vec::new();
        out.push(0x02); // key version v2-style without epoch
        out.push(network);
        out.push(SCHEME_UNIFOMR);
        out.extend_from_slice(&(CLUE_N as u16).to_le_bytes());

        for &coeff in &self.clue_sk.coeffs {
            // Map {-1,0,1} → plaintext field.
            let v = if coeff < 0 {
                t - ((-coeff) as u64 % t)
            } else {
                coeff as u64 % t
            };
            let slots = vec![v; degree];
            let pt = Plaintext::try_encode(&slots, Encoding::simd(), &self.params)
                .map_err(|e| format!("encode sk coeff: {e:?}"))?;
            let ct = self
                .det_pk
                .try_encrypt(&pt, &mut r)
                .map_err(|e| format!("encrypt sk coeff: {e:?}"))?;
            let bytes = ct.to_bytes();
            out.extend_from_slice(&(bytes.len() as u32).to_le_bytes());
            out.extend_from_slice(&bytes);
        }
        Ok(out)
    }

    pub fn decrypt_digest_slots(&self, digest: &[u8]) -> Result<Vec<u64>, String> {
        let mut flags = Vec::new();
        // Wire (any-match, ℓ bits): [u32 chunk_count]
        //   for each chunk: [u32 layer_count]
        //     for each layer: ([u32 len][ct]) × CLUE_PLAINTEXT_BITS
        // Each CT packs one SIMD slot per height (bit b = negacyclic coefficient
        // b of the partial decrypt). A height matches iff **any** layer has
        // **all ℓ** coefficients inside R_PRIME (client OR of per-layer ANDs —
        // avoids CT×CT noise blow-up while keeping ε_p = ((2r′+1)/q)^ℓ).
        if digest.len() < 4 {
            return Err("empty digest".into());
        }
        let chunk_count = u32::from_le_bytes(digest[0..4].try_into().unwrap()) as usize;
        let mut off = 4usize;
        let degree = self.params.degree();
        let t = self.params.plaintext();

        for _ in 0..chunk_count {
            if off + 4 > digest.len() {
                return Err("truncated digest layer_count".into());
            }
            let layer_count = u32::from_le_bytes(digest[off..off + 4].try_into().unwrap()) as usize;
            off += 4;
            if layer_count == 0 || layer_count > MAX_CLUE_LAYERS {
                return Err(format!("invalid UnifOMR layer_count {layer_count}"));
            }

            // layers[l][b] = decoded slots for layer l, plaintext bit b.
            let mut layers: Vec<Vec<Vec<u64>>> = Vec::with_capacity(layer_count);
            for _ in 0..layer_count {
                let mut bits: Vec<Vec<u64>> = Vec::with_capacity(CLUE_PLAINTEXT_BITS);
                for _ in 0..CLUE_PLAINTEXT_BITS {
                    if off + 4 > digest.len() {
                        return Err("truncated digest".into());
                    }
                    let len = u32::from_le_bytes(digest[off..off + 4].try_into().unwrap()) as usize;
                    off += 4;
                    if off + len > digest.len() {
                        return Err("truncated digest ct".into());
                    }
                    let ct = Ciphertext::from_bytes(&digest[off..off + len], &self.params)
                        .map_err(|e| format!("digest ct: {e:?}"))?;
                    off += len;
                    let pt = self
                        .det_sk
                        .try_decrypt(&ct)
                        .map_err(|e| format!("digest decrypt: {e:?}"))?;
                    let slots = Vec::<u64>::try_decode(&pt, Encoding::simd())
                        .map_err(|e| format!("digest decode: {e:?}"))?;
                    if slots.len() < degree {
                        return Err("digest slot count < BFV degree".into());
                    }
                    bits.push(slots);
                }
                layers.push(bits);
            }

            let in_range = |raw: u64| -> bool {
                let centered = if raw > t / 2 {
                    (t as i64) - (raw as i64)
                } else {
                    raw as i64
                };
                centered.unsigned_abs() <= R_PRIME
            };

            for i in 0..degree {
                let matched = layers
                    .iter()
                    .any(|bits| bits.iter().all(|slots| in_range(slots[i])));
                // 0 ⇒ match; t/2 ⇒ non-match (fails the range check by design).
                flags.push(if matched { 0 } else { t / 2 });
            }
        }
        if off != digest.len() {
            return Err("trailing digest bytes".into());
        }
        Ok(flags)
    }

    /// Paper range check: centered lift into (-t/2,t/2], match if |v| ≤ R_PRIME.
    pub fn range_check_matches(slots: &[u64], start: u32, end: u32) -> Result<Vec<u32>, OmrError> {
        let t = bfv_params()?.plaintext();
        let mut out = Vec::new();
        for (i, &raw) in slots.iter().enumerate() {
            let height = start.saturating_add(i as u32);
            if height > end {
                break;
            }
            let centered = if raw > t / 2 {
                (t as i64) - (raw as i64)
            } else {
                raw as i64
            };
            if centered.unsigned_abs() <= R_PRIME {
                out.push(height);
            }
        }
        Ok(out)
    }
}

/// Parse detection key into per-coefficient broadcast ciphertexts.
pub fn parse_detection_key(
    key: &[u8],
    params: &Arc<BfvParameters>,
) -> Result<(u8, Vec<Ciphertext>), String> {
    if key.len() < 5 {
        return Err("detection key too short".into());
    }
    if key[0] != 0x02 {
        return Err(format!("unsupported det key version {:02x}", key[0]));
    }
    let network = key[1];
    if key[2] != SCHEME_UNIFOMR {
        return Err(format!("expected UnifOMR scheme, got {:02x}", key[2]));
    }
    let n = u16::from_le_bytes(key[3..5].try_into().unwrap()) as usize;
    if n != CLUE_N {
        return Err(format!("det key n={n}, expected {CLUE_N}"));
    }
    let mut cts = Vec::with_capacity(n);
    let mut off = 5usize;
    for _ in 0..n {
        if off + 4 > key.len() {
            return Err("truncated det key".into());
        }
        let len = u32::from_le_bytes(key[off..off + 4].try_into().unwrap()) as usize;
        off += 4;
        if off + len > key.len() {
            return Err("truncated det key ct".into());
        }
        let ct = Ciphertext::from_bytes(&key[off..off + len], params)
            .map_err(|e| format!("det key ct: {e:?}"))?;
        off += len;
        cts.push(ct);
    }
    if off != key.len() {
        return Err("trailing det key bytes".into());
    }
    Ok((network, cts))
}

// ---------------------------------------------------------------------------
// Server detector
// ---------------------------------------------------------------------------

pub struct UnifOmrDetector {
    pub network: u8,
    pub params: Arc<BfvParameters>,
}

impl UnifOmrDetector {
    pub fn new(network: u8) -> Result<Self, OmrError> {
        Ok(Self {
            network,
            params: bfv_params()?,
        })
    }

    /// Evaluate UnifOMD over notes with LWEmongrass pre-filter.
    ///
    /// `detection_key` is the full wire key from [`UnifOmrClient::build_detection_key`].
    /// Returns framed digest covering every height in order (one SIMD slot per height).
    ///
    /// **Any-match (paper):** when a height has multiple UnifOMR clues, each clue is
    /// evaluated as its own BFV layer (`Enc(e_i)`). The client ORs range-checks across
    /// layers so the height matches if **any** clue is pertinent. (Homomorphic ∏ via
    /// CT×CT exceeds this MVP noise budget; layered OR is semantically equivalent.)
    pub fn evaluate(
        &self,
        detection_key: &[u8],
        block_notes: &[(u32, Vec<ClueNote>)],
    ) -> Result<Vec<u8>, OmrError> {
        let (key_net, sk_cts) = parse_detection_key(detection_key, &self.params)?;
        if key_net != self.network {
            return Err(format!(
                "detection key network {key_net:#04x} does not match detector network {:#04x}",
                self.network
            ));
        }
        if sk_cts.len() != CLUE_N {
            return Err(format!("expected {CLUE_N} sk ciphertexts"));
        }

        let degree = self.params.degree();
        let t = self.params.plaintext();
        let mut digest = Vec::new();
        let chunks: Vec<_> = block_notes.chunks(degree).collect();
        digest.extend_from_slice(&(chunks.len() as u32).to_le_bytes());

        for chunk in chunks {
            let mut per_height: Vec<Vec<RlweCiphertext>> = Vec::with_capacity(degree);
            let mut overflow: Vec<bool> = Vec::with_capacity(degree);
            for (h, notes) in chunk {
                let mut valid = Vec::new();
                for note in notes {
                    if note.omr_clue.is_empty() {
                        continue;
                    }
                    match validate_unifomr_clue(&note.omr_clue) {
                        Ok(()) => match deserialize_clue(&note.omr_clue) {
                            Ok(ct) => valid.push(ct),
                            Err(e) => {
                                tracing::warn!(
                                    target: "lightwalletd::unifomr",
                                    "LWEmongrass/UnifOMR clue parse failed: {e}"
                                );
                            }
                        },
                        Err(e) => {
                            tracing::warn!(
                                target: "lightwalletd::unifomr",
                                "LWEmongrass rejected UnifOMR clue: {e}"
                            );
                        }
                    }
                }
                // Layer-overflow: never silently drop clues (censorship vector —
                // an attacker could stuff MAX_CLUE_LAYERS clues at a height to
                // hide the next one). Instead the whole height is forced to
                // match for every client (fail-open: costs one extra fetch).
                let over = valid.len() > MAX_CLUE_LAYERS;
                if over {
                    tracing::warn!(
                        target: "lightwalletd::unifomr",
                        "height {h}: {} clues exceed MAX_CLUE_LAYERS={MAX_CLUE_LAYERS}; \
                         forcing match for all clients (fail-open, no censorship)",
                        valid.len()
                    );
                    valid.clear();
                }
                overflow.push(over);
                per_height.push(valid);
            }
            while per_height.len() < degree {
                per_height.push(Vec::new());
                overflow.push(false);
            }

            let max_clues = per_height.iter().map(|v| v.len()).max().unwrap_or(0).max(1);
            debug_assert!(max_clues <= MAX_CLUE_LAYERS);
            digest.extend_from_slice(&(max_clues as u32).to_le_bytes());

            for layer in 0..max_clues {
                let mut layer_clues: Vec<SlotClue> = Vec::with_capacity(degree);
                for (i, height_clues) in per_height.iter().enumerate() {
                    if overflow[i] {
                        // Forced match on layer 0; padding on the rest.
                        layer_clues.push(if layer == 0 {
                            SlotClue::ForcedMatch
                        } else {
                            SlotClue::Impertinent
                        });
                    } else if layer < height_clues.len() {
                        layer_clues.push(SlotClue::Clue(height_clues[layer].clone()));
                    } else {
                        // Pad shorter heights: impertinent so OR is unchanged.
                        layer_clues.push(SlotClue::Impertinent);
                    }
                }
                for bit in 0..CLUE_PLAINTEXT_BITS {
                    let mut layer_ct = Self::partial_decrypt_simd(
                        &self.params,
                        &sk_cts,
                        &layer_clues,
                        degree,
                        t,
                        bit,
                    )?;
                    // Digest modulus-switch (paper): drop to the last BFV level
                    // (Q ≈ 2¹²⁰ → single 40-bit modulus). Plaintext slots are
                    // invariant; rounding noise (≈‖s‖₁/2 ≈ 2¹¹) is far below
                    // the last-level budget (Q′/2t ≈ 2¹⁹). Shrinks each CT 3×.
                    layer_ct
                        .switch_to_level(layer_ct.max_switchable_level())
                        .map_err(|e| format!("mod-switch: {e:?}"))?;
                    let bytes = layer_ct.to_bytes();
                    digest.extend_from_slice(&(bytes.len() as u32).to_le_bytes());
                    digest.extend_from_slice(&bytes);
                }
            }
        }

        Ok(digest)
    }

    /// Homomorphic `Enc((b − a∗sk)[bit])` packed one height per SIMD slot.
    ///
    /// [`SlotClue::Impertinent`] slots use `a=0`, `b=t/2` (range check fails);
    /// [`SlotClue::ForcedMatch`] slots use `a=0`, `b=0` (range check passes on
    /// every bit for every detection key — layer-overflow fail-open).
    fn partial_decrypt_simd(
        params: &Arc<BfvParameters>,
        sk_cts: &[Ciphertext],
        clues: &[SlotClue],
        degree: usize,
        t: u64,
        bit: usize,
    ) -> Result<Ciphertext, OmrError> {
        let mut acc: Option<Ciphertext> = None;
        #[allow(clippy::needless_range_loop)]
        for j in 0..CLUE_N {
            let mut a_slots = vec![0u64; degree];
            for (i, clue) in clues.iter().enumerate().take(degree) {
                if let SlotClue::Clue(ct) = clue {
                    // Negacyclic (a∗s)[k] = Σ_{j≤k} a[k−j]s[j] − Σ_{j>k} a[n+k−j]s[j]
                    let raw = if j <= bit {
                        ct.a[bit - j] % t
                    } else {
                        let v = ct.a[CLUE_N + bit - j] % t;
                        (t - v) % t
                    };
                    a_slots[i] = raw;
                }
            }
            let pt = Plaintext::try_encode(&a_slots, Encoding::simd(), params)
                .map_err(|e| format!("{e:?}"))?;
            let term = &sk_cts[j] * &pt;
            acc = Some(match acc {
                None => term,
                Some(prev) => &prev + &term,
            });
        }
        let acc = acc.ok_or_else(|| "empty acc".to_string())?;

        let mut b_slots = vec![0u64; degree];
        for (i, clue) in clues.iter().enumerate().take(degree) {
            match clue {
                SlotClue::Clue(ct) => b_slots[i] = ct.b[bit] % t,
                SlotClue::Impertinent => b_slots[i] = (t / 2) % t,
                SlotClue::ForcedMatch => b_slots[i] = 0,
            }
        }
        let pt_b = Plaintext::try_encode(&b_slots, Encoding::simd(), params)
            .map_err(|e| format!("{e:?}"))?;

        let diff = &acc - &pt_b;
        let neg = Plaintext::try_encode(&vec![t - 1; degree], Encoding::simd(), params)
            .map_err(|e| format!("{e:?}"))?;
        Ok(&diff * &neg)
    }
}

/// Per-slot clue state during digest evaluation.
enum SlotClue {
    /// A validated clue to evaluate.
    Clue(RlweCiphertext),
    /// No clue at this (height, layer): decrypts to t/2 → never matches.
    Impertinent,
    /// Layer-overflow fail-open: decrypts to 0 → matches every detection key.
    ForcedMatch,
}

impl UnifOmrDetector {
    pub fn scheme(&self) -> &str {
        "unifomr"
    }
}

/// Timing-padded wrapper used by gRPC handlers.
pub fn evaluate_padded(
    detector: &UnifOmrDetector,
    detection_key: &[u8],
    block_notes: &[(u32, Vec<ClueNote>)],
) -> Result<Vec<u8>, String> {
    let start = Instant::now();
    let out = detector.evaluate(detection_key, block_notes)?;
    let elapsed = start.elapsed();
    if elapsed < MIN_RESPONSE_TIME {
        std::thread::sleep(MIN_RESPONSE_TIME - elapsed);
    }
    Ok(out)
}

/// Domain-separated message prefix for clue-PK ownership proofs (v2:
/// binds network byte + monotonic key_version against replay).
pub const CLUE_PK_OWNERSHIP_DOMAIN: &[u8] = b"DarkFi-UnifOMR-CluePK-v2";

/// Build the signed message for RegisterCluePublicKey ownership proofs.
///
/// `domain || network || key_version (u64 LE) || payment_pubkey || clue_public_key`
pub fn clue_pk_ownership_message(
    network: u8,
    key_version: u64,
    payment_pubkey: &[u8],
    clue_public_key: &[u8],
) -> Vec<u8> {
    let mut msg = Vec::with_capacity(
        CLUE_PK_OWNERSHIP_DOMAIN.len() + 9 + payment_pubkey.len() + clue_public_key.len(),
    );
    msg.extend_from_slice(CLUE_PK_OWNERSHIP_DOMAIN);
    msg.push(network);
    msg.extend_from_slice(&key_version.to_le_bytes());
    msg.extend_from_slice(payment_pubkey);
    msg.extend_from_slice(clue_public_key);
    msg
}

/// Monotonic clue-key version for registrations (unix seconds).
pub fn clue_key_version_now() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(1)
}

/// Sign `RegisterCluePublicKey` with the payment [`darkfi_sdk::crypto::SecretKey`].
pub fn sign_clue_pk_ownership(
    payment_sk: &darkfi_sdk::crypto::SecretKey,
    network: u8,
    key_version: u64,
    payment_pubkey: &[u8; 32],
    clue_public_key: &[u8],
) -> Vec<u8> {
    use darkfi_sdk::crypto::schnorr::SchnorrSecret;
    use darkfi_serial::serialize;
    let msg = clue_pk_ownership_message(network, key_version, payment_pubkey, clue_public_key);
    let sig = payment_sk.sign(&msg);
    serialize(&sig)
}

/// Verify a clue-PK ownership proof against a 32-byte payment public key.
pub fn verify_clue_pk_ownership(
    network: u8,
    key_version: u64,
    payment_pubkey: &[u8; 32],
    clue_public_key: &[u8],
    ownership_proof: &[u8],
) -> Result<(), String> {
    use darkfi_sdk::crypto::schnorr::SchnorrPublic;
    use darkfi_sdk::crypto::PublicKey;
    use darkfi_serial::deserialize;
    const OWNERSHIP_PROOF_WIRE_LEN: usize = 128;
    let proof_bytes = if ownership_proof.len() == OWNERSHIP_PROOF_WIRE_LEN {
        if ownership_proof.len() < 2 {
            return Err("ownership proof wire too short".into());
        }
        let len = u16::from_le_bytes([ownership_proof[0], ownership_proof[1]]) as usize;
        if len == 0 || 2 + len > ownership_proof.len() {
            return Err("ownership proof length invalid".into());
        }
        &ownership_proof[2..2 + len]
    } else {
        ownership_proof
    };
    let pk = PublicKey::from_bytes(*payment_pubkey)
        .map_err(|e| format!("invalid payment pubkey: {e}"))?;
    let sig: darkfi_sdk::crypto::schnorr::Signature =
        deserialize(proof_bytes).map_err(|e| format!("invalid ownership proof encoding: {e}"))?;
    let msg = clue_pk_ownership_message(network, key_version, payment_pubkey, clue_public_key);
    if !pk.verify(&msg, &sig) {
        return Err("clue public key ownership proof verification failed".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_rlwe_encrypt_zeros_decrypts_small_error() {
        let mut r = StdRng::seed_from_u64(7);
        let sk = RlweSecretKey::random(&mut r);
        let pk = RlwePublicKey::from_secret(&sk, &mut r);
        let ct = pk.encrypt_zeros(&mut r);
        let err = sk.decrypt_error(&ct);
        let max_abs = err.iter().map(|e| e.unsigned_abs()).max().unwrap();
        assert!(
            max_abs < R_PRIME,
            "pertinent clue error too large: {max_abs} (R_PRIME={R_PRIME})"
        );
    }

    #[test]
    fn test_clue_wire_roundtrip() {
        let mut r = StdRng::seed_from_u64(9);
        let sk = RlweSecretKey::random(&mut r);
        let pk = RlwePublicKey::from_secret(&sk, &mut r);
        let ct = pk.encrypt_zeros(&mut r);
        let bytes = serialize_clue(&ct);
        validate_unifomr_clue(&bytes).unwrap();
        let ct2 = deserialize_clue(&bytes).unwrap();
        assert_eq!(ct.a, ct2.a);
        assert_eq!(ct.b, ct2.b);
    }

    #[test]
    fn test_poison_clue_rejected() {
        let mut bad = vec![0u8; 100];
        bad[0] = CLUE_VERSION;
        bad[1] = SCHEME_UNIFOMR;
        assert!(validate_unifomr_clue(&bad).is_err());
    }

    #[test]
    fn test_unifomr_e2e_partial_decrypt_match() {
        let wallet = [0x42u8; 32];
        let client = UnifOmrClient::from_wallet(&wallet, 0x01).unwrap();
        let (_, pk) = clue_keypair_from_wallet(&wallet, 0x01).unwrap();
        let mut r = rng();
        let clue = serialize_clue(&pk.encrypt_zeros(&mut r));

        let det_key = client.build_detection_key(0x01).unwrap();
        let detector = UnifOmrDetector::new(0x01).unwrap();

        let notes = vec![(100u32, vec![ClueNote { omr_clue: clue }])];
        // Pad to exercise S19-style single height in a chunk.
        let digest = detector.evaluate(&det_key, &notes).unwrap();
        let slots = client.decrypt_digest_slots(&digest).unwrap();
        let matches = UnifOmrClient::range_check_matches(&slots, 100, 100).unwrap();
        assert!(
            matches.contains(&100),
            "pertinent height must match after range check; first slots={:?}",
            &slots[..4.min(slots.len())]
        );
    }

    #[test]
    fn test_unifomr_any_match_second_clue() {
        let alice = [0xAAu8; 32];
        let bob = [0xBBu8; 32];
        let alice_client = UnifOmrClient::from_wallet(&alice, 0x01).unwrap();
        let (_, alice_pk) = clue_keypair_from_wallet(&alice, 0x01).unwrap();
        let (_, bob_pk) = clue_keypair_from_wallet(&bob, 0x01).unwrap();
        let mut r = rng();
        let bob_clue = serialize_clue(&bob_pk.encrypt_zeros(&mut r));
        let alice_clue = serialize_clue(&alice_pk.encrypt_zeros(&mut r));

        let det_key = alice_client.build_detection_key(0x01).unwrap();
        let detector = UnifOmrDetector::new(0x01).unwrap();
        let notes = vec![(
            77u32,
            vec![
                ClueNote { omr_clue: bob_clue },
                ClueNote {
                    omr_clue: alice_clue,
                },
            ],
        )];
        let digest = detector.evaluate(&det_key, &notes).unwrap();
        let slots = alice_client.decrypt_digest_slots(&digest).unwrap();
        let matches = UnifOmrClient::range_check_matches(&slots, 77, 77).unwrap();
        assert!(
            matches.contains(&77),
            "alice must match when her clue is second; slots={:?}",
            &slots[..4.min(slots.len())]
        );
    }

    #[test]
    fn test_unifomr_non_match_other_wallet() {
        // With Gaussian σ=0.5 errors, r′=149, and all ℓ=2 bits evaluated,
        // ε_p = ((2r′+1)/q)² ≈ 2⁻²³·⁵ — every seed must be clean.
        let alice = [0x11u8; 32];
        let bob = [0x22u8; 32];
        let alice_client = UnifOmrClient::from_wallet(&alice, 0x01).unwrap();
        let (_, bob_pk) = clue_keypair_from_wallet(&bob, 0x01).unwrap();
        let det_key = alice_client.build_detection_key(0x01).unwrap();
        let detector = UnifOmrDetector::new(0x01).unwrap();

        for seed in [42u64, 99, 1337] {
            let mut r = StdRng::seed_from_u64(seed);
            let clue = serialize_clue(&bob_pk.encrypt_zeros(&mut r));
            let notes = vec![(50u32, vec![ClueNote { omr_clue: clue }])];
            let digest = detector.evaluate(&det_key, &notes).unwrap();
            let slots = alice_client.decrypt_digest_slots(&digest).unwrap();
            let matches = UnifOmrClient::range_check_matches(&slots, 50, 50).unwrap();
            assert!(
                matches.is_empty(),
                "false positive on seed {seed}: alice matched bob's clue"
            );
        }
    }

    #[test]
    fn test_gaussian_sampler_distribution() {
        // Empirical check of the σ=0.5 CDT sampler: P(0)≈0.7866, P(±1)≈0.1064.
        let mut r = StdRng::seed_from_u64(0xC0FFEE);
        let n = 200_000usize;
        let mut counts = std::collections::HashMap::new();
        let mut sum_sq = 0f64;
        for _ in 0..n {
            let v = sample_gaussian_sigma_half(&mut r);
            *counts.entry(v).or_insert(0usize) += 1;
            sum_sq += (v * v) as f64;
            assert!(v.abs() <= 4, "tail cut exceeded: {v}");
        }
        let p0 = counts[&0] as f64 / n as f64;
        assert!((p0 - 0.7866).abs() < 0.005, "P(0) off: {p0}");
        // Discrete Gaussian with parameter σ=0.5 has true variance ≈ 0.2150.
        let var = sum_sq / n as f64;
        assert!((var - 0.2150).abs() < 0.01, "variance off 0.2150: {var}");
    }

    #[test]
    fn test_layer_overflow_forces_match_no_censorship() {
        // A height stuffed with > MAX_CLUE_LAYERS clues must match every
        // detection key (fail-open) instead of silently dropping clues.
        let alice = [0x33u8; 32];
        let spammer = [0x44u8; 32];
        let alice_client = UnifOmrClient::from_wallet(&alice, 0x01).unwrap();
        let (_, spam_pk) = clue_keypair_from_wallet(&spammer, 0x01).unwrap();
        let mut r = StdRng::seed_from_u64(4242);
        let spam_clue = serialize_clue(&spam_pk.encrypt_zeros(&mut r));
        let notes = vec![(
            123u32,
            vec![
                ClueNote {
                    omr_clue: spam_clue
                };
                MAX_CLUE_LAYERS + 1
            ],
        )];
        let det_key = alice_client.build_detection_key(0x01).unwrap();
        let detector = UnifOmrDetector::new(0x01).unwrap();
        let digest = detector.evaluate(&det_key, &notes).unwrap();
        let slots = alice_client.decrypt_digest_slots(&digest).unwrap();
        let matches = UnifOmrClient::range_check_matches(&slots, 123, 123).unwrap();
        assert!(
            matches.contains(&123),
            "overflowed height must fail open (match) — censorship otherwise"
        );
    }
}
