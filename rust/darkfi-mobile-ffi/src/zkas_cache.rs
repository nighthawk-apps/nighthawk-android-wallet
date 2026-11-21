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
    /// Optional explicit cache root (used by unit tests; defaults to `DISK_CACHE_DIR`).
    disk_dir: Option<PathBuf>,
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
        .map(|c| {
            if c.is_ascii_alphanumeric() || c == '-' || c == '_' {
                c
            } else {
                '_'
            }
        })
        .collect()
}

fn disk_file(dir: &Path, contract_id: &str, namespace: &str) -> PathBuf {
    dir.join("zkas_cache").join(format!(
        "{}_{}.bin",
        sanitize_component(contract_id),
        sanitize_component(namespace)
    ))
}

fn disk_pk_file(dir: &Path, contract_id: &str, namespace: &str) -> PathBuf {
    dir.join("zkas_cache").join(format!(
        "{}_{}.pk",
        sanitize_component(contract_id),
        sanitize_component(namespace)
    ))
}

fn disk_namespaces_file(dir: &Path, contract_id: &str) -> PathBuf {
    dir.join("zkas_cache").join(format!(
        "{}_namespaces.txt",
        sanitize_component(contract_id)
    ))
}

static DISK_CACHE_DIR: OnceLock<PathBuf> = OnceLock::new();

/// Install the wallet cache directory used for on-disk zkas bincodes.
pub fn set_disk_cache_dir(path: PathBuf) {
    let _ = DISK_CACHE_DIR.set(path);
}

fn record_namespace_in(dir: &Path, contract_id: &str, namespace: &str) {
    let idx_path = disk_namespaces_file(dir, contract_id);
    if let Some(parent) = idx_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let existing = std::fs::read_to_string(&idx_path).unwrap_or_default();
    if existing.lines().any(|line| line == namespace) {
        return;
    }
    let mut updated = existing;
    if !updated.is_empty() && !updated.ends_with('\n') {
        updated.push('\n');
    }
    updated.push_str(namespace);
    updated.push('\n');
    let _ = std::fs::write(idx_path, updated);
}

fn persist_entry_in(dir: &Path, entry: &ZkasCacheEntry) {
    let path = disk_file(dir, &entry.contract_id, &entry.namespace);
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let bincode_digest = Sha256::digest(&entry.bincode);
    // [32-byte sha256][bincode]
    let mut blob = bincode_digest.to_vec();
    blob.extend_from_slice(&entry.bincode);
    if std::fs::write(path, blob).is_ok() {
        record_namespace_in(dir, &entry.contract_id, &entry.namespace);
    }
    if let Some(pk) = &entry.proving_key {
        persist_proving_key_in(
            dir,
            &entry.contract_id,
            &entry.namespace,
            bincode_digest.as_slice(),
            pk,
        );
    }
}

fn persist_proving_key_in(
    dir: &Path,
    contract_id: &str,
    namespace: &str,
    bincode_sha256: &[u8],
    pk: &[u8],
) {
    if bincode_sha256.len() != 32 {
        return;
    }
    let pk_path = disk_pk_file(dir, contract_id, namespace);
    if let Some(parent) = pk_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let mut blob = Vec::with_capacity(32 + pk.len());
    blob.extend_from_slice(bincode_sha256);
    blob.extend_from_slice(pk);
    let _ = std::fs::write(pk_path, blob);
}

fn load_entry_from(dir: &Path, contract_id: &str, namespace: &str) -> Option<ZkasCacheEntry> {
    let bytes = std::fs::read(disk_file(dir, contract_id, namespace)).ok()?;
    if bytes.len() < 32 {
        return None;
    }
    let (stored_hash, bincode) = bytes.split_at(32);
    let computed = Sha256::digest(bincode);
    if computed.as_slice() != stored_hash {
        return None;
    }
    let proving_key = std::fs::read(disk_pk_file(dir, contract_id, namespace))
        .ok()
        .and_then(|pk_blob| {
            if pk_blob.len() > 32 && &pk_blob[..32] == stored_hash {
                Some(pk_blob[32..].to_vec())
            } else {
                None
            }
        });
    Some(ZkasCacheEntry {
        contract_id: contract_id.to_string(),
        namespace: namespace.to_string(),
        bincode_hash: hex_encode(stored_hash),
        bincode: bincode.to_vec(),
        proving_key,
    })
}

fn discover_disk_namespaces(dir: &Path, contract_id: &str) -> Vec<String> {
    let mut namespaces = Vec::new();
    if let Ok(raw) = std::fs::read_to_string(disk_namespaces_file(dir, contract_id)) {
        for line in raw.lines() {
            let trimmed = line.trim();
            if !trimmed.is_empty() && !namespaces.iter().any(|n| n == trimmed) {
                namespaces.push(trimmed.to_string());
            }
        }
    }
    if contract_id == darkfi_sdk::crypto::contract_id::MONEY_CONTRACT_ID.to_string() {
        for known in [
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_MINT_NS_V1,
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_BURN_NS_V1,
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_FEE_NS_V1,
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_TOKEN_MINT_NS_V1,
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_AUTH_TOKEN_MINT_NS_V1,
            darkfi_money_contract::MONEY_CONTRACT_ZKAS_AUTH_TOKEN_FREEZE_NS_V1,
        ] {
            if !namespaces.iter().any(|n| n == known) {
                namespaces.push(known.to_string());
            }
        }
    }
    namespaces
}

impl ZkasCache {
    /// Create a new empty cache.
    pub fn new() -> Self {
        Self {
            memory: Arc::new(RwLock::new(HashMap::new())),
            disk_dir: None,
        }
    }

    #[cfg(test)]
    pub fn with_disk_dir(dir: PathBuf) -> Self {
        Self {
            memory: Arc::new(RwLock::new(HashMap::new())),
            disk_dir: Some(dir),
        }
    }

    fn effective_disk_dir(&self) -> Option<PathBuf> {
        self.disk_dir
            .clone()
            .or_else(|| DISK_CACHE_DIR.get().cloned())
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
        let dir = self.effective_disk_dir()?;
        let disk = load_entry_from(&dir, contract_id, namespace)?;
        if let Some(expected) = expected_bincode_hash {
            if disk.bincode_hash != expected {
                return None;
            }
        }
        if let Ok(mut guard) = self.memory.write() {
            guard.insert(
                (contract_id.to_string(), namespace.to_string()),
                disk.clone(),
            );
        }
        Some(disk)
    }

    /// All cached `(namespace, bincode)` pairs for a contract (falling back to
    /// `DISK_CACHE_DIR/zkas_cache/` and populating memory when memory is empty).
    pub fn entries_for_contract(&self, contract_id: &str) -> Vec<(String, Vec<u8>)> {
        if let Ok(guard) = self.memory.read() {
            let mem_entries: Vec<(String, Vec<u8>)> = guard
                .iter()
                .filter(|((cid, _), _)| cid == contract_id)
                .map(|((_, ns), e)| (ns.clone(), e.bincode.clone()))
                .collect();
            if !mem_entries.is_empty() {
                return mem_entries;
            }
        }
        let Some(dir) = self.effective_disk_dir() else {
            return Vec::new();
        };
        let namespaces = discover_disk_namespaces(&dir, contract_id);
        let mut loaded = Vec::new();
        let mut out = Vec::new();
        for ns in namespaces {
            if let Some(entry) = load_entry_from(&dir, contract_id, &ns) {
                out.push((entry.namespace.clone(), entry.bincode.clone()));
                loaded.push(entry);
            }
        }
        if !loaded.is_empty() {
            if let Ok(mut guard) = self.memory.write() {
                for entry in loaded {
                    let key = (entry.contract_id.clone(), entry.namespace.clone());
                    guard.entry(key).or_insert(entry);
                }
            }
        }
        out
    }

    /// Insert or update a cache entry.
    pub fn insert(&self, entry: ZkasCacheEntry) {
        if let Some(dir) = self.effective_disk_dir() {
            persist_entry_in(&dir, &entry);
        }
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
        let mut bincode_digest: Option<[u8; 32]> = None;
        if let Ok(mut guard) = self.memory.write() {
            let key = (contract_id.to_string(), namespace.to_string());
            if let Some(entry) = guard.get_mut(&key) {
                let digest: [u8; 32] = Sha256::digest(&entry.bincode).into();
                bincode_digest = Some(digest);
                entry.proving_key = Some(proving_key.clone());
            }
        }
        if let (Some(digest), Some(dir)) = (bincode_digest, self.effective_disk_dir()) {
            persist_proving_key_in(&dir, contract_id, namespace, &digest, &proving_key);
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

fn compile_proving_key_bytes(bincode: &[u8]) -> Option<Vec<u8>> {
    let zkbin = darkfi::zkas::ZkBinary::decode(bincode, false).ok()?;
    let empty = darkfi::zk::vm_heap::empty_witnesses(&zkbin).ok()?;
    let circuit = darkfi::zk::vm::ZkCircuit::new(empty, &zkbin);
    let pk = darkfi::zk::proof::ProvingKey::build(zkbin.k, &circuit);
    let mut out = Vec::new();
    pk.write(&mut out).ok()?;
    Some(out)
}

/// Warm the zkas cache by fetching bincodes for known contract IDs.
///
/// Called after sync reaches the chain tip. Runs at low priority.
/// If bincodes are already cached (in memory or on disk), skips `LookupZkas`
/// and avoids rewriting `.bin` files on every `Synced` cycle.
pub async fn warm_zkas_cache(
    client: &crate::lightwallet_client::LightwalletClient,
    contract_ids: &[&str],
) -> Result<u32, String> {
    warm_zkas_cache_with(global_zkas_cache(), contract_ids, |cid| {
        let cid = cid.to_string();
        async move { client.lookup_zkas(&cid).await }
    })
    .await
}

async fn warm_zkas_cache_with<F, Fut>(
    cache: &ZkasCache,
    contract_ids: &[&str],
    mut fetch_zkas: F,
) -> Result<u32, String>
where
    F: FnMut(&str) -> Fut,
    Fut: std::future::Future<Output = Result<Vec<(String, Vec<u8>)>, String>>,
{
    let mut warmed = 0u32;

    for contract_id in contract_ids {
        let cached = cache.entries_for_contract(contract_id);
        if !cached.is_empty() {
            for (namespace, bincode) in cached {
                let needs_pk = cache
                    .get(contract_id, &namespace, None)
                    .is_some_and(|e| e.proving_key.is_none());
                if needs_pk {
                    let bincode_clone = bincode.clone();
                    if let Some(pk_bytes) =
                        smol::unblock(move || compile_proving_key_bytes(&bincode_clone)).await
                    {
                        cache.set_proving_key(contract_id, &namespace, pk_bytes);
                        warmed += 1;
                    }
                }
            }
            continue;
        }

        match fetch_zkas(contract_id).await {
            Ok(bincodes) => {
                for (namespace, bincode) in bincodes {
                    let bincode_hash = sha256_hex(&bincode);
                    if let Some(existing) = cache.get(contract_id, &namespace, Some(&bincode_hash))
                    {
                        if existing.proving_key.is_none() {
                            let bincode_clone = bincode.clone();
                            if let Some(pk_bytes) =
                                smol::unblock(move || compile_proving_key_bytes(&bincode_clone))
                                    .await
                            {
                                cache.set_proving_key(contract_id, &namespace, pk_bytes);
                                warmed += 1;
                            }
                        }
                        continue;
                    }
                    cache.insert_bincode(contract_id, &namespace, bincode.clone());
                    if let Some(pk_bytes) =
                        smol::unblock(move || compile_proving_key_bytes(&bincode)).await
                    {
                        cache.set_proving_key(contract_id, &namespace, pk_bytes);
                    }
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

    #[test]
    fn entries_for_contract_falls_back_to_disk_and_populates_memory_and_pk() {
        let dir = std::env::temp_dir().join(format!(
            "nh_zkas_cache_test_{}_{}",
            std::process::id(),
            rand::random::<u64>()
        ));
        std::fs::create_dir_all(&dir).unwrap();

        {
            let writer = ZkasCache::with_disk_dir(dir.clone());
            writer.insert_bincode("cid_1", "Mint_V1", b"mint_bin".to_vec());
            writer.insert_bincode("cid_1", "Burn_V1", b"burn_bin".to_vec());
            writer.set_proving_key("cid_1", "Mint_V1", b"mint_pk".to_vec());
        }

        let reader = ZkasCache::with_disk_dir(dir.clone());
        assert!(reader.is_empty());

        let entries = reader.entries_for_contract("cid_1");
        assert_eq!(entries.len(), 2);
        assert!(entries.contains(&("Mint_V1".to_string(), b"mint_bin".to_vec())));
        assert!(entries.contains(&("Burn_V1".to_string(), b"burn_bin".to_vec())));
        assert_eq!(reader.len(), 2, "disk fallback must populate memory");

        let mint = reader.get("cid_1", "Mint_V1", None).unwrap();
        assert_eq!(mint.proving_key.as_deref(), Some(b"mint_pk".as_slice()));

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn warm_zkas_cache_skips_redundant_lookup_when_cached_on_disk() {
        let dir = std::env::temp_dir().join(format!(
            "nh_zkas_warm_test_{}_{}",
            std::process::id(),
            rand::random::<u64>()
        ));
        std::fs::create_dir_all(&dir).unwrap();

        {
            let seed = ZkasCache::with_disk_dir(dir.clone());
            seed.insert_bincode("cid_warm", "Fee_V1", b"fee_bin".to_vec());
            seed.set_proving_key("cid_warm", "Fee_V1", b"fee_pk".to_vec());
        }

        let cold_cache = ZkasCache::with_disk_dir(dir.clone());
        let mut rpc_calls = 0u32;
        let warmed = smol::block_on(warm_zkas_cache_with(&cold_cache, &["cid_warm"], |_| {
            rpc_calls += 1;
            async { Ok(vec![("Fee_V1".into(), b"fee_bin".to_vec())]) }
        }))
        .unwrap();

        assert_eq!(rpc_calls, 0, "must not call LookupZkas when cached on disk");
        assert_eq!(warmed, 0);
        assert_eq!(cold_cache.entries_for_contract("cid_warm").len(), 1);

        let _ = std::fs::remove_dir_all(&dir);
    }
}
