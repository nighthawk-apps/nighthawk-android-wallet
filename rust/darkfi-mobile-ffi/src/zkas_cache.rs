//! Disk-backed cache for zkas bincodes and compiled proving keys.
//!
//! `LookupZkas` + proving key compilation is the heaviest spend-time cost
//! (~2–5s per circuit). This cache pre-warms during post-sync idle so the
//! first spend after sync is near-instant.
//!
//! In-memory map for the hot path; optional file backup under the wallet
//! `cache_path/zkas_cache/` directory. Staleness is detected with SHA-256
//! of the bincode bytes.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, OnceLock, RwLock};

use sha2::{Digest, Sha256};

/// In-memory LRU for hot path (spend-time lookup). Falls back to disk.
#[derive(Debug, Clone)]
pub struct ZkasCacheEntry {
    pub contract_id: String,
    pub namespace: String,
    pub bincode_hash: String,
    pub bincode: Vec<u8>,
    /// Serialized proving key bytes. `None` if not yet compiled.
    pub proving_key: Option<Vec<u8>>,
}

/// Process-wide zkas cache.
pub struct ZkasCache {
    /// In-memory entries keyed by `(contract_id, namespace)`.
    memory: Arc<RwLock<HashMap<(String, String), ZkasCacheEntry>>>,
}

fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn sha256_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    hex_encode(&hasher.finalize())
}

fn sanitize_component(s: &str) -> String {
    s.chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .collect()
}

fn disk_file(dir: &Path, contract_id: &str, namespace: &str) -> PathBuf {
    dir.join("zkas_cache").join(format!(
        "{}_{}.bin",
        sanitize_component(contract_id),
        sanitize_component(namespace)
    ))
}

static DISK_CACHE_DIR: OnceLock<PathBuf> = OnceLock::new();

/// Install the wallet cache directory used for on-disk zkas bincodes.
pub fn set_disk_cache_dir(path: PathBuf) {
    let _ = DISK_CACHE_DIR.set(path);
}

fn persist_entry(entry: &ZkasCacheEntry) {
    let Some(dir) = DISK_CACHE_DIR.get() else {
        return;
    };
    let path = disk_file(dir, &entry.contract_id, &entry.namespace);
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    // [32-byte sha256][bincode]
    let mut blob = Sha256::digest(&entry.bincode).to_vec();
    blob.extend_from_slice(&entry.bincode);
    let _ = std::fs::write(path, blob);
}

fn load_entry(contract_id: &str, namespace: &str) -> Option<ZkasCacheEntry> {
    let dir = DISK_CACHE_DIR.get()?;
    let bytes = std::fs::read(disk_file(dir, contract_id, namespace)).ok()?;
    if bytes.len() < 32 {
        return None;
    }
    let (stored_hash, bincode) = bytes.split_at(32);
    let computed = Sha256::digest(bincode);
    if computed.as_slice() != stored_hash {
        return None;
    }
    Some(ZkasCacheEntry {
        contract_id: contract_id.to_string(),
        namespace: namespace.to_string(),
        bincode_hash: hex_encode(stored_hash),
        bincode: bincode.to_vec(),
        proving_key: None,
    })
}

impl ZkasCache {
    /// Create a new empty cache.
    pub fn new() -> Self {
        Self {
            memory: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// Look up a cached entry by contract ID and namespace.
    /// Returns `None` if not cached or if the bincode hash doesn't match.
    pub fn get(
        &self,
        contract_id: &str,
        namespace: &str,
        expected_bincode_hash: Option<&str>,
    ) -> Option<ZkasCacheEntry> {
        {
            let guard = self.memory.read().ok()?;
            let key = (contract_id.to_string(), namespace.to_string());
            if let Some(entry) = guard.get(&key) {
                if let Some(expected) = expected_bincode_hash {
                    if entry.bincode_hash != expected {
                        return None;
                    }
                }
                return Some(entry.clone());
            }
        }
        let disk = load_entry(contract_id, namespace)?;
        if let Some(expected) = expected_bincode_hash {
            if disk.bincode_hash != expected {
                return None;
            }
        }
        self.insert(disk.clone());
        Some(disk)
    }

    /// Insert or update a cache entry.
    pub fn insert(&self, entry: ZkasCacheEntry) {
        persist_entry(&entry);
        if let Ok(mut guard) = self.memory.write() {
            let key = (entry.contract_id.clone(), entry.namespace.clone());
            guard.insert(key, entry);
        }
    }

    /// Insert bincode from a `LookupZkas` response. The proving key is
    /// compiled lazily or by `warm_cache`.
    pub fn insert_bincode(&self, contract_id: &str, namespace: &str, bincode: Vec<u8>) {
        let bincode_hash = sha256_hex(&bincode);
        self.insert(ZkasCacheEntry {
            contract_id: contract_id.to_string(),
            namespace: namespace.to_string(),
            bincode_hash,
            bincode,
            proving_key: None,
        });
    }

    /// Store a compiled proving key for an existing entry.
    pub fn set_proving_key(&self, contract_id: &str, namespace: &str, proving_key: Vec<u8>) {
        if let Ok(mut guard) = self.memory.write() {
            let key = (contract_id.to_string(), namespace.to_string());
            if let Some(entry) = guard.get_mut(&key) {
                entry.proving_key = Some(proving_key);
            }
        }
    }

    /// Number of cached entries.
    pub fn len(&self) -> usize {
        self.memory.read().map(|g| g.len()).unwrap_or(0)
    }

    /// Whether the cache is empty.
    pub fn is_empty(&self) -> bool {
        self.len() == 0
    }

    /// Clear all cached entries.
    pub fn clear(&self) {
        if let Ok(mut guard) = self.memory.write() {
            guard.clear();
        }
    }
}

impl Default for ZkasCache {
    fn default() -> Self {
        Self::new()
    }
}

/// Global singleton for the zkas cache.
static ZKAS_CACHE: std::sync::LazyLock<ZkasCache> = std::sync::LazyLock::new(ZkasCache::new);

/// Get a reference to the global zkas cache.
pub fn global_zkas_cache() -> &'static ZkasCache {
    &ZKAS_CACHE
}

/// Warm the zkas cache by fetching bincodes for known contract IDs.
///
/// Called after sync reaches the chain tip. Runs at low priority.
/// The proving key compilation is CPU-heavy (~2–5s per circuit) but
/// doing it during post-sync idle means the first spend is near-instant.
pub async fn warm_zkas_cache(
    client: &crate::lightwallet_client::LightwalletClient,
    contract_ids: &[&str],
) -> Result<u32, String> {
    let cache = global_zkas_cache();
    let mut warmed = 0u32;

    for contract_id in contract_ids {
        match client.lookup_zkas(contract_id).await {
            Ok(bincodes) => {
                for (namespace, bincode) in bincodes {
                    let bincode_hash = sha256_hex(&bincode);
                    if let Some(existing) = cache.get(contract_id, &namespace, Some(&bincode_hash)) {
                        if existing.proving_key.is_some() {
                            tracing::debug!(
                                target: "zkas-cache",
                                "Cache hit for {contract_id}/{namespace} (proving key ready)"
                            );
                            continue;
                        }
                    }
                    cache.insert_bincode(contract_id, &namespace, bincode);
                    warmed += 1;
                    tracing::info!(
                        target: "zkas-cache",
                        "Cached bincode for {contract_id}/{namespace} (hash={bincode_hash})"
                    );
                }
            }
            Err(e) => {
                tracing::warn!(
                    target: "zkas-cache",
                    "LookupZkas failed for {contract_id}: {e}"
                );
            }
        }
    }

    Ok(warmed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_insert_and_get() {
        let cache = ZkasCache::new();
        cache.insert_bincode("contract_abc", "Money::Transfer", b"bincode_data".to_vec());
        assert_eq!(cache.len(), 1);

        let entry = cache.get("contract_abc", "Money::Transfer", None);
        assert!(entry.is_some());
        let entry = entry.unwrap();
        assert_eq!(entry.bincode, b"bincode_data");
        assert!(entry.proving_key.is_none());
        assert_eq!(entry.bincode_hash, sha256_hex(b"bincode_data"));
    }

    #[test]
    fn cache_stale_bincode_hash() {
        let cache = ZkasCache::new();
        cache.insert_bincode("contract_abc", "Money::Transfer", b"old_bincode".to_vec());

        let entry = cache.get("contract_abc", "Money::Transfer", Some("wrong_hash"));
        assert!(entry.is_none());
    }

    #[test]
    fn cache_proving_key_roundtrip() {
        let cache = ZkasCache::new();
        cache.insert_bincode("contract_abc", "Money::Transfer", b"bincode".to_vec());
        cache.set_proving_key("contract_abc", "Money::Transfer", b"pk_data".to_vec());

        let entry = cache.get("contract_abc", "Money::Transfer", None).unwrap();
        assert_eq!(entry.proving_key.unwrap(), b"pk_data");
    }

    #[test]
    fn cache_clear() {
        let cache = ZkasCache::new();
        cache.insert_bincode("a", "ns1", vec![1]);
        cache.insert_bincode("b", "ns2", vec![2]);
        assert_eq!(cache.len(), 2);
        cache.clear();
        assert!(cache.is_empty());
    }

    #[test]
    fn sha256_hex_is_stable() {
        assert_eq!(
            sha256_hex(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
}
