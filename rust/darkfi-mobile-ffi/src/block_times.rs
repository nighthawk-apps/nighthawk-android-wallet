//! Height → unix-timestamp index written next to the wallet cache.
//!
//! `DrkTransactionRecord` has no timestamp field. Compact blocks do. This
//! module records those timestamps and flushes
//! `{cache}/block_timestamps.json` so the Swift UI can stop rendering "---"
//! once a block time is known. The prebuilt xcframework does not include
//! this writer until the FFI crate is rebuilt.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{LazyLock, Mutex};

static STORE: LazyLock<Mutex<BlockTimeStore>> = LazyLock::new(|| {
    Mutex::new(BlockTimeStore {
        path: None,
        times: HashMap::new(),
        dirty: 0,
    })
});

struct BlockTimeStore {
    path: Option<PathBuf>,
    times: HashMap<u32, u64>,
    dirty: u32,
}

pub fn set_store_directory(cache_path: &str) {
    let path = Path::new(cache_path).join("block_timestamps.json");
    let mut guard = match STORE.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    if guard.path.as_deref() != Some(path.as_path()) {
        guard.times = load_file(&path);
        guard.path = Some(path);
        guard.dirty = 0;
    }
}

pub fn note_block_time(height: u32, timestamp: u64) {
    if timestamp == 0 {
        return;
    }
    let mut guard = match STORE.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    if guard.times.get(&height) == Some(&timestamp) {
        return;
    }
    guard.times.insert(height, timestamp);
    guard.dirty = guard.dirty.saturating_add(1);
    if guard.dirty >= 200 {
        flush_locked(&mut guard);
    }
}

pub fn flush() {
    let mut guard = match STORE.lock() {
        Ok(guard) => guard,
        Err(poisoned) => poisoned.into_inner(),
    };
    flush_locked(&mut guard);
}

fn flush_locked(store: &mut BlockTimeStore) {
    let Some(path) = store.path.clone() else {
        return;
    };
    if store.dirty == 0 && path.exists() {
        return;
    }
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let mut body = String::from("{");
    let mut first = true;
    let mut heights: Vec<u32> = store.times.keys().copied().collect();
    heights.sort_unstable();
    for height in heights {
        let Some(timestamp) = store.times.get(&height) else {
            continue;
        };
        if !first {
            body.push(',');
        }
        first = false;
        body.push_str(&format!("\"{height}\":{timestamp}"));
    }
    body.push('}');
    if std::fs::write(&path, body).is_ok() {
        store.dirty = 0;
    }
}

fn load_file(path: &Path) -> HashMap<u32, u64> {
    let mut map = HashMap::new();
    let Ok(text) = std::fs::read_to_string(path) else {
        return map;
    };
    let trimmed = text.trim().trim_start_matches('{').trim_end_matches('}');
    if trimmed.is_empty() {
        return map;
    }
    for pair in trimmed.split(',') {
        let mut parts = pair.split(':');
        let Some(raw_key) = parts.next() else {
            continue;
        };
        let Some(raw_value) = parts.next() else {
            continue;
        };
        let key = raw_key.trim().trim_matches('"');
        let Ok(height) = key.parse::<u32>() else {
            continue;
        };
        let Ok(timestamp) = raw_value.trim().parse::<u64>() else {
            continue;
        };
        if timestamp > 0 {
            map.insert(height, timestamp);
        }
    }
    map
}
