//! Deterministic money key derivation from DarkFi 22-word mnemonics.

use darkfi_sdk::crypto::SecretKey;

use std::{collections::HashMap, str::FromStr};

use hmac::Hmac;
use num_bigint::BigUint;
use num_bigint::RandBigInt;
use num_traits::identities::{One, Zero};
use pbkdf2::pbkdf2;
use rand::thread_rng;
use sha2::Sha512;
use thiserror::Error;
use unicode_normalization::{char::is_combining_mark, UnicodeNormalization};
use zeroize::Zeroize;

const DERIVE_CONTEXT: &str = "nighthawk-drk-v1";

/// Derive a [`SecretKey`] from wallet mnemonic words (deterministic per phrase).
///
/// Intermediate seed material is zeroized before return.
pub fn secret_key_from_mnemonic(mnemonic: &[String]) -> Result<SecretKey, String> {
    let mut hasher = blake3::Hasher::new();
    hasher.update(DERIVE_CONTEXT.as_bytes());
    hasher.update(&[0]);
    for word in mnemonic {
        hasher.update(word.trim().to_lowercase().as_bytes());
        hasher.update(&[0]);
    }
    let seed = hasher.finalize();
    let mut seed_bytes = *seed.as_bytes();

    for counter in 0u8..=255 {
        let mut bytes = seed_bytes;
        bytes[31] ^= counter;
        if let Ok(key) = SecretKey::from_bytes(bytes) {
            bytes.zeroize();
            seed_bytes.zeroize();
            return Ok(key);
        }
        bytes.zeroize();
    }

    seed_bytes.zeroize();
    Err("could not derive canonical SecretKey from mnemonic".into())
}

#[derive(Error, Debug)]
pub enum MnemonicError {
    #[error("Unsupported seed type {0}")]
    UnsupportedSeedType(String),
    #[error("Invalid word in mnemonic: {0}")]
    InvalidWord(String),
    #[error("Cannot extract same entropy from mnemonic!")]
    EntropyMismatch,
    #[allow(dead_code)]
    #[error("Other error: {0}")]
    Other(String),
}

#[repr(u8)]
#[derive(Copy, Clone)]
enum SeedPrefix {
    Standard = 0x01,
}

impl FromStr for SeedPrefix {
    type Err = MnemonicError;

    fn from_str(seed_type: &str) -> Result<Self, Self::Err> {
        match seed_type {
            "standard" => Ok(Self::Standard),
            _ => Err(MnemonicError::UnsupportedSeedType(seed_type.to_string())),
        }
    }
}

#[derive(Debug)]
pub struct Wordlist {
    index_from_word: HashMap<String, u64>,
    word_from_index: HashMap<u64, String>,
}

impl Wordlist {
    pub fn new(words: Vec<String>) -> Self {
        let mut index_from_word = HashMap::new();
        let mut word_from_index = HashMap::new();
        for (i, word) in words.iter().enumerate() {
            index_from_word.insert(word.clone(), i as u64);
            word_from_index.insert(i as u64, word.clone());
        }
        Self {
            index_from_word,
            word_from_index,
        }
    }

    pub fn index_from_word(&self, word: &str) -> Option<u64> {
        self.index_from_word.get(word).copied()
    }

    pub fn len(&self) -> usize {
        self.index_from_word.len()
    }

    pub fn is_empty(&self) -> bool {
        self.index_from_word.is_empty()
    }

    #[allow(clippy::should_implement_trait)]
    pub fn from_str(content: &str) -> Result<Self, MnemonicError> {
        let s = content.trim();
        let s: String = s.nfkd().collect();
        let lines = s.split('\n');
        let mut words = vec![];

        for line in lines {
            let line = line.split('#').next().unwrap_or("");
            let line = line.trim_matches(&[' ', '\r'][..]);
            if !line.is_empty() {
                words.push(line.to_string());
            }
        }

        Ok(Self::new(words))
    }
}

impl std::ops::Index<u64> for Wordlist {
    type Output = String;

    fn index(&self, index: u64) -> &Self::Output {
        self.word_from_index.get(&index).unwrap()
    }
}

pub struct DarkfiMnemonic {
    wordlist: Wordlist,
}

impl Default for DarkfiMnemonic {
    fn default() -> Self {
        let english = include_str!("english.txt");
        let wordlist = Wordlist::from_str(english).unwrap();
        Self { wordlist }
    }
}

impl DarkfiMnemonic {
    #[allow(dead_code)]
    pub fn mnemonic_to_seed(mnemonic: &str, passphrase: Option<&str>) -> [u8; 64] {
        const PBKDF_ROUNDS: u32 = 2048;
        let mnemonic = normalize_text(mnemonic);
        let passphrase = normalize_text(passphrase.unwrap_or(""));

        let mut salt = String::from("darkfi");
        salt.push_str(&passphrase);

        let mut key = [0u8; 64];
        // pbkdf2 returns Result on this crate version; infallible for HMAC-based PRFs.
        let _ =
            pbkdf2::<Hmac<Sha512>>(mnemonic.as_bytes(), salt.as_bytes(), PBKDF_ROUNDS, &mut key);
        key
    }

    pub fn mnemonic_encode(&self, i: &BigUint) -> String {
        let n = BigUint::from(self.wordlist.len());
        let mut words = vec![];
        let mut i = i.clone();
        while i > BigUint::zero() {
            let x = &i % &n;
            i /= &n;
            let idx_u64: u64 = x.try_into().unwrap();
            words.push(self.wordlist[idx_u64].clone());
        }
        words.join(" ")
    }

    pub fn mnemonic_decode(&self, seed: &str) -> Result<BigUint, MnemonicError> {
        let n = BigUint::from(self.wordlist.len());
        let mut words: Vec<&str> = seed.split_whitespace().collect();
        let mut i = BigUint::zero();
        while let Some(w) = words.pop() {
            let k = self
                .wordlist
                .index_from_word(w)
                .ok_or_else(|| MnemonicError::InvalidWord(w.to_string()))?;
            i = &i * &n + k;
        }
        Ok(i)
    }

    pub fn make_seed(
        &self,
        seed_type: Option<&str>,
        num_bits: Option<usize>,
    ) -> Result<String, MnemonicError> {
        let num_bits = num_bits.unwrap_or(232);
        let prefix = SeedPrefix::from_str(seed_type.unwrap_or("standard"))?;

        let bpw = (self.wordlist.len() as f64).log2();
        let adj_num_bits = ((num_bits as f64 / bpw).ceil() * bpw) as u32;

        let threshold_exp = (num_bits as f64 - bpw) as u32;
        let threshold = BigUint::from(2u32).pow(threshold_exp);
        let max_entropy = BigUint::from(2u32).pow(adj_num_bits);

        let mut rng_instance = thread_rng();
        let mut entropy = BigUint::one();
        while entropy < threshold {
            entropy = rng_instance.gen_biguint_below(&max_entropy);
        }

        let mut nonce = BigUint::zero();
        let mut seed;
        loop {
            nonce += 1u32;
            let i = &entropy + &nonce;

            seed = self.mnemonic_encode(&i);
            if i != self.mnemonic_decode(&seed)? {
                return Err(MnemonicError::EntropyMismatch);
            }
            if is_new_seed(&seed, prefix) {
                break;
            }
        }
        Ok(seed)
    }
}

fn hmac_oneshot(key: &[u8], msg: &[u8]) -> Vec<u8> {
    use hmac::Mac;
    let mut mac = Hmac::<Sha512>::new_from_slice(key).expect("HMAC can take key of any size");
    mac.update(msg);
    mac.finalize().into_bytes().to_vec()
}

fn is_new_seed(seed: &str, prefix: SeedPrefix) -> bool {
    let seed = normalize_text(seed);
    let seed = hmac_oneshot("Seed version".as_bytes(), seed.as_bytes());
    seed[0] == prefix as u8
}

const CJK_INTERVALS: &[(u32, u32, &str)] = &[
    (0x4E00, 0x9FFF, "CJK Unified Ideographs"),
    (0x3400, 0x4DBF, "CJK Unified Ideographs Extension A"),
    (0x20000, 0x2A6DF, "CJK Unified Ideographs Extension B"),
    (0x2A700, 0x2B73F, "CJK Unified Ideographs Extension C"),
    (0x2B740, 0x2B81F, "CJK Unified Ideographs Extension D"),
    (0xF900, 0xFAFF, "CJK Compatibility Ideographs"),
    (0x2F800, 0x2FA1D, "CJK Compatibility Ideographs Supplement"),
    (0x3190, 0x319F, "Kanbun"),
    (0x2E80, 0x2EFF, "CJK Radicals Supplement"),
    (0x2F00, 0x2FDF, "CJK Radicals"),
    (0x31C0, 0x31EF, "CJK Strokes"),
    (0x2FF0, 0x2FFF, "Ideographic Description Characters"),
    (0xE0100, 0xE01EF, "Variation Selectors Supplement"),
    (0x3100, 0x312F, "Bopomofo"),
    (0x31A0, 0x31BF, "Bopomofo Extended"),
    (0xFF00, 0xFFEF, "Halfwidth and Fullwidth Forms"),
    (0x3040, 0x309F, "Hiragana"),
    (0x30A0, 0x30FF, "Katakana"),
    (0x31F0, 0x31FF, "Katakana Phonetic Extensions"),
    (0x1B000, 0x1B0FF, "Kana Supplement"),
    (0xAC00, 0xD7AF, "Hangul Syllables"),
    (0x1100, 0x11FF, "Hangul Jamo"),
    (0xA960, 0xA97F, "Hangul Jamo Extended A"),
    (0xD7B0, 0xD7FF, "Hangul Jamo Extended B"),
    (0x3130, 0x318F, "Hangul Compatibility Jamo"),
    (0xA4D0, 0xA4FF, "Lisu"),
    (0x16F00, 0x16F9F, "Miao"),
    (0xA000, 0xA48F, "Yi Syllables"),
    (0xA490, 0xA4CF, "Yi Radicals"),
];

fn is_cjk(c: char) -> bool {
    let n = c as u32;
    for (imin, imax, _name) in CJK_INTERVALS {
        if imin <= &n && &n <= imax {
            return true;
        }
    }
    false
}

fn normalize_text(seed: &str) -> String {
    let seed: String = seed.nfkd().collect();
    let seed = seed.to_lowercase();
    let seed: String = seed.chars().filter(|&c| !is_combining_mark(c)).collect();
    let seed: String = seed.split_whitespace().collect::<Vec<&str>>().join(" ");
    let chars: Vec<char> = seed.chars().collect();
    let seed: String = chars
        .iter()
        .enumerate()
        .filter_map(|(i, &c)| {
            if c.is_whitespace() && i > 0 && i < chars.len() - 1 {
                if is_cjk(chars[i - 1]) && is_cjk(chars[i + 1]) {
                    None
                } else {
                    Some(c)
                }
            } else {
                Some(c)
            }
        })
        .collect();
    seed
}
