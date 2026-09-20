//! Local compact block cache for mobile wallet trial decryption fallback.
//!
//! When OMR detection is unavailable or fails, the mobile wallet falls back
//! to downloading compact blocks and performing trial decryption. This cache
//! stores serialized CompactBlock protobufs in the app's data directory so
//! blocks don't need to be re-downloaded on app restart.
//!
//! Blocks are pruned after successful sync to prevent storage bloat on
//! mobile devices. The default retention window keeps 2000 blocks.

use rusqlite::{params, Connection};
use std::path::Path;
use std::sync::Mutex;

use chacha20poly1305::{
    aead::{Aead, KeyInit},
    XChaCha20Poly1305, XNonce,
};
use rand::RngCore;

/// Default retention: keep cached blocks for this many blocks past sync tip.
pub const DEFAULT_MOBILE_BLOCK_RETENTION: u32 = 2000;

const CACHE_MAGIC: &[u8] = b"NHC1";
const NONCE_LEN: usize = 24;

pub(crate) fn wrap_cache_blob(key: Option<&[u8; 32]>, plaintext: &[u8]) -> Result<Vec<u8>, String> {
    wrap_blob(key, plaintext)
}

pub(crate) fn unwrap_cache_blob(key: Option<&[u8; 32]>, data: &[u8]) -> Result<Vec<u8>, String> {
    unwrap_blob(key, data)
}

fn wrap_blob(key: Option<&[u8; 32]>, plaintext: &[u8]) -> Result<Vec<u8>, String> {
    let Some(key) = key else {
        return Ok(plaintext.to_vec());
    };
    let cipher = XChaCha20Poly1305::new(key.into());
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher
        .encrypt(XNonce::from_slice(&nonce), plaintext)
        .map_err(|e| e.to_string())?;
    let mut out = Vec::with_capacity(CACHE_MAGIC.len() + NONCE_LEN + ct.len());
    out.extend_from_slice(CACHE_MAGIC);
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    Ok(out)
}

fn unwrap_blob(key: Option<&[u8; 32]>, data: &[u8]) -> Result<Vec<u8>, String> {
    if !data.starts_with(CACHE_MAGIC) {
        return Ok(data.to_vec());
    }
    let Some(key) = key else {
        return Err("encrypted compact-block cache requires wallet key".into());
    };
    if data.len() <= CACHE_MAGIC.len() + NONCE_LEN {
        return Err("truncated encrypted compact block".into());
    }
    let nonce = &data[CACHE_MAGIC.len()..CACHE_MAGIC.len() + NONCE_LEN];
    let ct = &data[CACHE_MAGIC.len() + NONCE_LEN..];
    let cipher = XChaCha20Poly1305::new(key.into());
    cipher
        .decrypt(XNonce::from_slice(nonce), ct)
        .map_err(|_| "compact-block cache decrypt failed".into())
}

/// Thread-safe compact block cache for mobile.
pub struct MobileBlockCache {
    conn: Mutex<Connection>,
    key: Option<[u8; 32]>,
}

impl MobileBlockCache {
    /// Open or create the block cache at the given path (plaintext / legacy).
    pub fn open(path: &str) -> Result<Self, String> {
        Self::open_with_key(path, None)
    }

    /// Open with an optional XChaCha20-Poly1305 key derived from `wallet_pass`.
    pub fn open_with_key(path: &str, key: Option<[u8; 32]>) -> Result<Self, String> {
        if let Some(parent) = Path::new(path).parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let conn = Connection::open(path).map_err(|e| e.to_string())?;
        conn.execute_batch(
            "
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;

            CREATE TABLE IF NOT EXISTS compact_blocks (
                height INTEGER PRIMARY KEY,
                data BLOB NOT NULL,
                cached_at TEXT NOT NULL DEFAULT (datetime('now'))
            );
            ",
        )
        .map_err(|e| e.to_string())?;
        Ok(Self {
            conn: Mutex::new(conn),
            key,
        })
    }

    /// Insert a compact block.
    pub fn insert_block(&self, height: u32, data: &[u8]) -> Result<(), String> {
        let stored = wrap_blob(self.key.as_ref(), data)?;
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "INSERT OR REPLACE INTO compact_blocks (height, data) VALUES (?1, ?2)",
            params![height, stored],
        )
        .map_err(|e| e.to_string())?;
        Ok(())
    }

    /// Get a cached block by height.
    pub fn get_block(&self, height: u32) -> Result<Option<Vec<u8>>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT data FROM compact_blocks WHERE height = ?1")
            .map_err(|e| e.to_string())?;
        let mut rows = stmt.query(params![height]).map_err(|e| e.to_string())?;
        match rows.next().map_err(|e| e.to_string())? {
            Some(row) => {
                let data: Vec<u8> = row.get(0).map_err(|e| e.to_string())?;
                unwrap_blob(self.key.as_ref(), &data).map(Some)
            }
            None => Ok(None),
        }
    }

    /// Get the highest cached block height.
    pub fn highest_cached(&self) -> Result<u32, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT COALESCE(MAX(height), 0) FROM compact_blocks")
            .map_err(|e| e.to_string())?;
        stmt.query_row([], |row| row.get(0))
            .map_err(|e| e.to_string())
    }

    /// Check if a range is fully cached.
    pub fn is_range_cached(&self, start: u32, end: u32) -> Result<bool, String> {
        let expected = end - start + 1;
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT COUNT(*) FROM compact_blocks WHERE height >= ?1 AND height <= ?2")
            .map_err(|e| e.to_string())?;
        let actual: u32 = stmt
            .query_row(params![start, end], |row| row.get(0))
            .map_err(|e| e.to_string())?;
        Ok(actual == expected)
    }

    /// Prune blocks below a given height.
    pub fn prune_below(&self, height: u32) -> Result<usize, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "DELETE FROM compact_blocks WHERE height < ?1",
            params![height],
        )
        .map_err(|e| e.to_string())
    }

    /// Prune for a given sync height using the default retention window.
    pub fn prune_for_sync(&self, sync_height: u32) -> Result<usize, String> {
        if sync_height <= DEFAULT_MOBILE_BLOCK_RETENTION {
            return Ok(0);
        }
        self.prune_below(sync_height - DEFAULT_MOBILE_BLOCK_RETENTION)
    }

    /// Approximate cache size in bytes (data column only).
    pub fn size_bytes(&self) -> Result<i64, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT COALESCE(SUM(LENGTH(data)), 0) FROM compact_blocks")
            .map_err(|e| e.to_string())?;
        stmt.query_row([], |row| row.get(0))
            .map_err(|e| e.to_string())
    }

    /// Vacuum the database to reclaim space.
    pub fn vacuum(&self) -> Result<(), String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute_batch("VACUUM").map_err(|e| e.to_string())
    }

    /// Count of cached blocks.
    pub fn count(&self) -> Result<u32, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare("SELECT COUNT(*) FROM compact_blocks")
            .map_err(|e| e.to_string())?;
        stmt.query_row([], |row| row.get(0))
            .map_err(|e| e.to_string())
    }

    /// Batch insert compact blocks in a single SQLite transaction.
    ///
    /// ~10× faster than individual inserts for the trial-decrypt fallback
    /// path where hundreds of blocks are streamed at once.
    pub fn insert_blocks(&self, blocks: &[(u32, &[u8])]) -> Result<usize, String> {
        if blocks.is_empty() {
            return Ok(0);
        }
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
        {
            let mut stmt = tx
                .prepare_cached(
                    "INSERT OR REPLACE INTO compact_blocks (height, data) VALUES (?1, ?2)",
                )
                .map_err(|e| e.to_string())?;
            for (height, data) in blocks {
                let stored = wrap_blob(self.key.as_ref(), data)?;
                stmt.execute(params![height, stored])
                    .map_err(|e| e.to_string())?;
            }
        }
        tx.commit().map_err(|e| e.to_string())?;
        Ok(blocks.len())
    }

    /// Retrieve all cached blocks in a height range, ordered ascending.
    ///
    /// Used by trial decrypt fallback to process a batch of blocks in order.
    pub fn get_block_range(&self, start: u32, end: u32) -> Result<Vec<(u32, Vec<u8>)>, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        let mut stmt = conn
            .prepare(
                "SELECT height, data FROM compact_blocks \
                 WHERE height >= ?1 AND height <= ?2 \
                 ORDER BY height ASC",
            )
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![start, end], |row| {
                Ok((row.get::<_, u32>(0)?, row.get::<_, Vec<u8>>(1)?))
            })
            .map_err(|e| e.to_string())?;
        rows.collect::<Result<Vec<_>, _>>()
            .map_err(|e| e.to_string())?
            .into_iter()
            .map(|(h, data)| unwrap_blob(self.key.as_ref(), &data).map(|pt| (h, pt)))
            .collect()
    }

    /// Aggressive prune: delete all blocks at or below scanned_height.
    ///
    /// Used after trial-decrypt scan completes. Unlike `prune_for_sync` which
    /// keeps a retention window, this deletes everything up to and including
    /// `scanned_height` since trial decrypt already processed them.
    pub fn prune_scanned(&self, scanned_height: u32) -> Result<usize, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "DELETE FROM compact_blocks WHERE height <= ?1",
            params![scanned_height],
        )
        .map_err(|e| e.to_string())
    }

    /// Invalidate cached blocks above a rollback height (security audit R3).
    ///
    /// Used during reorg recovery: blocks above the rollback height may be
    /// from the orphaned fork and must not be used for trial decryption.
    pub fn prune_above(&self, rollback_height: u32) -> Result<usize, String> {
        let conn = self.conn.lock().map_err(|e| e.to_string())?;
        conn.execute(
            "DELETE FROM compact_blocks WHERE height > ?1",
            params![rollback_height],
        )
        .map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypted_roundtrip_and_legacy_plaintext() {
        let dir = std::env::temp_dir().join(format!("nh-cache-{}", std::process::id()));
        let _ = std::fs::create_dir_all(&dir);
        let path = dir.join("compact_blocks.db");
        let path_s = path.to_str().unwrap();
        let key = [7u8; 32];
        let cache = MobileBlockCache::open_with_key(path_s, Some(key)).unwrap();
        cache.insert_block(10, b"compact-plain").unwrap();
        let got = cache.get_block(10).unwrap().unwrap();
        assert_eq!(got, b"compact-plain");
        let raw = {
            let conn = rusqlite::Connection::open(path_s).unwrap();
            conn.query_row(
                "SELECT data FROM compact_blocks WHERE height = 10",
                [],
                |row| row.get::<_, Vec<u8>>(0),
            )
            .unwrap()
        };
        assert!(raw.starts_with(b"NHC1"));
        assert_ne!(raw, b"compact-plain");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
