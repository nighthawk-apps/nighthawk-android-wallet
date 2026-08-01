//! Position the scan cursor at a wallet birthday / creation height.
//!
//! When `get_last_scanned_block()` is `(0, "-")`, light sync walks from genesis.
//! Seeding the cache cursor lets a **new** wallet start at the chain tip (or a
//! restore birthday) without downloading history it never participated in.
//!
//! Lightwalletd-only wallets must not call darkfid `get_block_by_height`.

use darkfi_serial::serialize;
use drk::Drk;

/// Seed the scanned-block cursor so the next sync starts **after** `height`.
///
/// Uses an optional hash string (from LWD `GetLightInfo` / `GetChainTip`);
/// falls back to `"-"` when unknown — enough for progress bookkeeping.
pub fn seed_scan_cursor(drk: &Drk, height: u32, block_hash: Option<&str>) -> Result<(), String> {
    if height == 0 {
        return Ok(());
    }

    let (last, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    if last > 0 {
        return Ok(());
    }

    let hash = block_hash.unwrap_or("-");
    let value = serialize(&(hash.to_string(), String::from("-")));

    drk.cache
        .scanned_blocks
        .insert(height.to_be_bytes(), value)
        .map_err(|e| format!("insert scanned block {height}: {e}"))?;

    Ok(())
}

/// Birthday means "first interesting height"; cursor = birthday − 1.
pub async fn seed_birthday_scan_cursor(drk: &Drk, birthday_height: u32) -> Result<(), String> {
    if birthday_height == 0 {
        return Ok(());
    }
    let cursor = birthday_height.saturating_sub(1);
    if cursor == 0 {
        return Ok(());
    }
    // No darkfid fetch — placeholder hash is fine for LWD-first wallets.
    seed_scan_cursor(drk, cursor, None)
}
