//! Position the scan cursor at a wallet birthday height before the first `scan_blocks`.
//!
//! When `get_last_scanned_block()` is `(0, "-")`, upstream `scan_blocks` performs a full
//! wallet reset. Seeding block `birthday_height - 1` in the cache makes the next scan start
//! at `birthday_height` without walking genesis → birthday.

use darkfi::blockchain::HeaderHash;
use darkfi_serial::serialize;
use drk::Drk;

pub async fn seed_birthday_scan_cursor(drk: &Drk, birthday_height: u32) -> Result<(), String> {
    if birthday_height == 0 {
        return Ok(());
    }

    let (last, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    if last > 0 {
        return Ok(());
    }

    let cursor = birthday_height.saturating_sub(1);
    if cursor == 0 {
        return Ok(());
    }

    let block = drk
        .get_block_by_height(cursor)
        .await
        .map_err(|e| format!("get_block_by_height({cursor}): {e}"))?;
    let hash: HeaderHash = block.header.hash();
    let value = serialize(&(hash.to_string(), String::from("-")));

    drk.cache
        .scanned_blocks
        .insert(cursor.to_be_bytes(), value)
        .map_err(|e| format!("insert scanned block {cursor}: {e}"))?;

    Ok(())
}
