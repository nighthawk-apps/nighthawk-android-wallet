use std::sync::Arc;

use darkfi_sdk::crypto::keypair::Network;
use drk::Drk;
use smol::Executor;

use crate::birthday::{seed_birthday_scan_cursor, seed_scan_cursor};
use crate::lightwallet_client::LightwalletClient;
use crate::mnemonic::secret_key_from_mnemonic;
use crate::DrkBootstrapConfig;
use crate::DrkPtr;

pub async fn bootstrap_drk(
    config: &DrkBootstrapConfig,
    ex: &Arc<Executor<'static>>,
) -> Result<DrkPtr, String> {
    // LWD-first: do not bind a hardcoded darkfid RPC. Broadcast falls back to
    // `drk.broadcast_tx` only when `rpc_client` is Some; leaving it None makes
    // that path fail closed so we never hit the wrong network (e.g. :18345).
    let darkfid_endpoint = config
        .darkfid_rpc_url
        .as_ref()
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .map(|s| url::Url::parse(s).map_err(|e| format!("darkfid_rpc_url: {e}")))
        .transpose()?;
    let has_darkfid = darkfid_endpoint.is_some();

    let drk = Drk::new(
        parse_network(&config.network),
        config.cache_path.clone(),
        config.wallet_db_path.clone(),
        config.wallet_pass.clone(),
        darkfid_endpoint,
        ex,
        false,
    )
    .await
    .map_err(|e| format!("Drk::new: {e}"))?;

    drk.initialize_wallet()
        .await
        .map_err(|e| format!("initialize_wallet: {e}"))?;

    let mut output = Vec::new();
    drk.initialize_money(&mut output)
        .await
        .map_err(|e| format!("initialize_money: {e}"))?;

    let _ = drk.initialize_dao().await;
    let _ = drk.initialize_deployooor().await;

    ensure_default_money_key(&drk, &config.mnemonic, &mut output).await?;

    if config.birthday_height > 0 {
        let birthday = u32::try_from(config.birthday_height)
            .map_err(|_| format!("birthday_height out of range: {}", config.birthday_height))?;
        // Backfill genesis..birthday-1 *before* seeding the scan cursor.
        // Seeding first left a truncated tree whenever LWD was unreachable,
        // and later sync only appended birthday..tip (invalid spend roots).
        let pin = pin_from_config(config);
        match backfill_money_tree_to_birthday(
            &drk,
            birthday,
            &config.lightwallet_server_url,
            pin,
        )
        .await
        {
            Ok(()) => {
                // Prefer a real block hash when darkfid is configured. Seeding
                // with placeholder "-" makes scan_blocks treat the cursor as a
                // reorg and fail with RowNotFound while walking missing heights.
                let cursor = birthday.saturating_sub(1);
                let real_hash = if cursor > 0 && has_darkfid {
                    match drk.get_block_by_height(cursor).await {
                        Ok(block) => Some(block.hash().to_string()),
                        Err(e) => {
                            tracing::warn!(
                                target: "wallet-bootstrap",
                                "birthday block {cursor} hash fetch failed ({e}); using placeholder"
                            );
                            None
                        }
                    }
                } else {
                    None
                };
                if let Some(ref hash) = real_hash {
                    seed_scan_cursor(&drk, cursor, Some(hash.as_str()))?;
                } else {
                    seed_birthday_scan_cursor(&drk, birthday).await?;
                }
            }
            Err(e) => {
                tracing::warn!(
                    target: "wallet-bootstrap",
                    "Birthday tree backfill failed; leaving scan cursor at genesis \
                     so the next sync rebuilds a valid Money tree: {e}"
                );
            }
        }
    } else if config.birthday_height == 0 {
        // Fresh create (birthday 0): jump scan cursor to LWD tip — new wallets
        // have no history; walking genesis → tip only causes trial-decrypt /
        // connection-lost noise.
        // Non-fatal: devices often default to 127.0.0.1:9067 which is unreachable
        // until a remote LWD is configured. Sync can seed the tip later.
        let pin = pin_from_config(config);
        if let Err(e) = seed_fresh_wallet_at_tip(&drk, &config.lightwallet_server_url, pin).await {
            tracing::warn!(
                target: "wallet-bootstrap",
                "Fresh-wallet tip probe skipped (wallet still opens): {e}"
            );
        }
    }
    // birthday_height < 0 (e.g. -1): unknown restore birthday — full history scan.

    Ok(drk.into_ptr())
}

fn pin_from_config(config: &DrkBootstrapConfig) -> Option<[u8; 32]> {
    let bytes = config.lightwallet_tls_pin_sha256.as_ref()?;
    if bytes.len() != 32 {
        return None;
    }
    let mut pin = [0u8; 32];
    pin.copy_from_slice(bytes);
    Some(pin)
}

/// Seed an empty wallet at the current lightwalletd tip (create-at-tip).
async fn seed_fresh_wallet_at_tip(
    drk: &Drk,
    lwd_url: &str,
    tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    let (last, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    if last > 0 {
        return Ok(());
    }
    // Same transport policy as sync (remote HTTPS requires pin).
    let client = LightwalletClient::from_endpoint_and_pin(lwd_url, tls_pin);
    let info = client
        .get_light_info()
        .await
        .map_err(|e| format!("fresh-wallet tip probe: {e}"))?;
    if info.chain_tip_height == 0 {
        return Ok(());
    }
    seed_scan_cursor(drk, info.chain_tip_height, None)?;
    tracing::info!(
        target: "wallet-bootstrap",
        "Fresh wallet seeded at tip {} (no history scan)",
        info.chain_tip_height
    );
    Ok(())
}

async fn ensure_default_money_key(
    drk: &Drk,
    mnemonic: &[String],
    output: &mut Vec<String>,
) -> Result<(), String> {
    if drk.default_address().await.is_ok() {
        return Ok(());
    }

    let secret = secret_key_from_mnemonic(mnemonic)?;
    drk.import_money_secrets(vec![secret], output)
        .await
        .map_err(|e| format!("import_money_secrets: {e}"))?;

    if let Ok(addrs) = drk.addresses().await {
        if let Some((key_id, _, _, _)) = addrs.last() {
            let idx = u16::try_from(*key_id)
                .map_err(|_| format!("set_default_address: key_id {key_id} out of u16 range"))?;
            drk.set_default_address(idx)
                .await
                .map_err(|e| format!("set_default_address: {e}"))?;
        }
    }

    Ok(())
}

fn parse_network(network: &str) -> Network {
    match network.trim() {
        "mainnet" => Network::Mainnet,
        _ => Network::Testnet, // testnet + localnet share Testnet address encoding
    }
}

/// Stream note commitments from genesis to `birthday - 1` and append them to
/// the Money Merkle tree without trial decryption.
///
/// This ensures that spend proofs generated after a birthday restore use a
/// Merkle root that includes ALL on-chain commitments, not just the ones
/// from `birthday..tip`. Without this backfill, the local tree root diverges
/// from the on-chain anchor and `tx.calculate_fee` / broadcast fails with
/// an invalid anchor error.
///
/// Starts at height 0 (genesis mint coins). The ZERO sentinel in
/// `empty_money_tree` is a dummy leaf, not block 0.
async fn backfill_money_tree_to_birthday(
    drk: &Drk,
    birthday: u32,
    lwd_url: &str,
    tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    use std::collections::HashSet;

    let Some((start, end)) = crate::birthday::pre_birthday_commitment_range(birthday) else {
        return Ok(());
    };

    let client = LightwalletClient::from_endpoint_and_pin(lwd_url, tls_pin);

    // Collect owned coin bytes so we can mark them in the tree (edge case:
    // a restored wallet may have coins discovered by a previous partial sync).
    let owned: HashSet<Vec<u8>> = match drk.get_coins(false).await {
        Ok(coins) => coins
            .into_iter()
            .map(|(own, _, _, _, _)| own.coin.to_bytes().to_vec())
            .collect(),
        Err(_) => HashSet::new(),
    };

    let mut tree = crate::sync::empty_money_tree();
    let appended =
        crate::sync::append_note_commitments(&mut tree, &client, start, end, &owned).await?;

    drk.cache
        .insert_merkle_trees(&[(drk::money::SLED_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("persist backfilled Money tree: {e}"))?;
    let _ = drk.cache.sled_db.flush();

    tracing::info!(
        target: "wallet-bootstrap",
        "Birthday backfill complete: appended {appended} pre-birthday commitments ({start}..={end})"
    );
    Ok(())
}
