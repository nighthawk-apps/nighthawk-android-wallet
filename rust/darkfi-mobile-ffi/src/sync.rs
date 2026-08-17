//! Background wallet sync loop.
//!
//! **Privacy model**: all sync traffic goes through a `darkfi-lightwalletd`
//! gRPC server. The wallet NEVER connects directly to a `darkfid` full node
//! in production, because doing so reveals block interest, wallet identity,
//! and transaction graph information to the full node operator.
//!
//! ## Sync modes
//!
//! 1. **OMR (Oblivious Message Retrieval)** — default when server supports it.
//!    The server performs FHE-based filtering so only matching-height compact
//!    blocks are fetched (sparse). Commitments + nullifiers cover the full
//!    scan window for Merkle completeness. Faster sync, lower bandwidth.
//!
//! 2. **Trial Decryption via Lightwalletd** — automatic fallback after OMR
//!    failure threshold. Client downloads all compact blocks from the
//!    lightwalletd server (full range) and attempts ChaCha20Poly1305
//!    decryption of each encrypted note with its secret keys.
//!    The server sees which block ranges are requested but not which notes
//!    decrypt successfully. UI should surface this privacy downgrade.
//!
//! 3. **Direct darkfid** — DISABLED in production. Only available behind
//!    `#[cfg(feature = "direct-darkfid")]` for development/debugging.
//!    Connecting directly to darkfid reveals which blocks and transactions
//!    a wallet cares about.
//!
//! ## Privacy invariants
//!
//! - NEVER connect directly to darkfid in production sync paths.
//! - NEVER log server endpoints, detection keys, or block ranges at INFO+.
//! - Add jitter to all polling intervals to prevent timing fingerprinting.
//! - Pad block range requests to fixed bucket sizes where possible.
//! - OMR is always attempted first; trial decryption only after OMR failure.

use std::sync::Arc;

use darkfi::system::sleep;
use darkfi_sdk::pasta::group::ff::PrimeField;
use smol::Executor;

use crate::lightwallet_sync::{LightSyncStatus, LightSyncType, SyncEngine};
use crate::DrkPtr;

/// Base poll interval during active sync (seconds).
const LIGHTWALLET_POLL_BASE_SECS: u64 = 5;

/// Base poll interval when fully synced (seconds).
const LIGHTWALLET_IDLE_BASE_SECS: u64 = 15;

/// Base retry interval after connection failure (seconds).
const RETRY_BASE_SECS: u64 = 20;

/// Maximum retry backoff (seconds).
const RETRY_MAX_SECS: u64 = 300;

/// Maximum consecutive failures before resetting the gRPC channel.
/// Adopted from zcash/lightwalletd's `FirstRPC()` pattern (finding 1.2/1.3):
/// prevents infinite retry loops by forcing a channel teardown + reconnect
/// after too many consecutive errors.
const MAX_CONSECUTIVE_FAILURES: u32 = 15;

/// Maximum time (seconds) for a single sync cycle before it's considered stalled.
/// If a `try_lightwallet_sync` call takes longer than this, we abort and retry.
/// Adopted from the pattern in zcash/lightwalletd's streaming timeout fix.
const MAX_SYNC_CYCLE_SECS: u64 = 600;

/// Number of startup health probes before entering the sync loop.
/// The wallet retries server connectivity up to this many times before
/// giving up and entering Disconnected state.
const HEALTH_PROBE_MAX_RETRIES: u32 = 5;

/// Interval between health probes (seconds).
const HEALTH_PROBE_INTERVAL_SECS: u64 = 30;

/// Jitter fraction: ±30% of base interval.
/// This prevents the server from fingerprinting wallets by polling cadence.
const JITTER_FRACTION: f64 = 0.30;

/// Add ±JITTER_FRACTION randomized jitter to a base interval.
///
/// PRIVACY: Fixed polling intervals are fingerprintable. Adding jitter
/// makes it harder for the server to correlate requests to the same wallet
/// across connections.
fn jittered_sleep_secs(base_secs: u64) -> u64 {
    let base = base_secs as f64;
    let jitter_range = base * JITTER_FRACTION;
    // Use a simple PRNG from thread_rng — this is timing jitter, not crypto
    let offset = (rand::random::<f64>() * 2.0 - 1.0) * jitter_range;
    (base + offset).max(1.0) as u64
}

/// Start the background sync loop.
///
/// The sync engine determines the primary path:
/// 1. Connect to lightwallet server → get server info
/// 2. If OMR supported and available → use OMR (attempt first)
/// 3. If OMR fails or unavailable → trial decryption via compact blocks
///    from the lightwalletd server
/// 4. If lightwallet server unreachable → retry with exponential backoff
///
/// PRIVACY: This function NEVER falls back to direct darkfid connection.
/// All sync traffic goes through the lightwalletd server.
pub async fn sync_once_via_lightwallet(drk: DrkPtr, url: &str) -> Result<(), String> {
    let engine = Arc::new(crate::lightwallet_sync::SyncEngine::new(url.to_string()));
    if let Ok(info) = engine
        .lightwallet_client()
        .probe_health(1, std::time::Duration::from_secs(1))
        .await
    {
        engine.set_omr_available(info.omr_supported);
        if info.chain_tip_height > 0 {
            engine.set_chain_tip(info.chain_tip_height);
        }
    }
    try_lightwallet_sync(&drk, &engine)
        .await
        .map(|_| ())
        .map_err(|e| redact_sync_error(&e))
}

pub fn start_background_sync(
    drk: DrkPtr,
    _ex: Arc<Executor<'static>>,
    sync_engine: Arc<SyncEngine>,
) {
    std::thread::Builder::new()
        .name("darkfi-wallet-sync".into())
        .spawn(move || {
            smol::block_on(async move {
                // ========================================================
                // Startup health probe (finding 1.1/1.3)
                // ========================================================
                // Adopted from zcash/lightwalletd's FirstRPC() pattern:
                // verify the server is reachable and darkfid has synced
                // past genesis before entering the main sync loop.
                let client = sync_engine.lightwallet_client();
                match client
                    .probe_health(
                        HEALTH_PROBE_MAX_RETRIES,
                        std::time::Duration::from_secs(HEALTH_PROBE_INTERVAL_SECS),
                    )
                    .await
                {
                    Ok(info) => {
                        tracing::info!(
                            target: "wallet-sync",
                            "Startup health probe OK: server={}, tip={}, omr={}",
                            info.server_version,
                            info.chain_tip_height,
                            info.omr_supported,
                        );
                        sync_engine.set_omr_available(info.omr_supported);
                        if info.chain_tip_height > 0 {
                            sync_engine.set_chain_tip(info.chain_tip_height);
                        }
                    }
                    Err(e) => {
                        tracing::error!(
                            target: "wallet-sync",
                            "Startup health probe failed: {e}. \
                             Entering sync loop anyway (will retry on each cycle)."
                        );
                    }
                }

                // ========================================================
                // Main sync loop
                // ========================================================
                let mut consecutive_failures: u32 = 0;

                loop {
                    sync_engine.set_status(LightSyncStatus::Connecting);

                    // Per-cycle timeout guard (finding 1.2): abort stalled
                    // sync cycles so we don't hang forever on a slow server.
                    let cycle_result = smol::future::or(
                        async { try_lightwallet_sync(&drk, &sync_engine).await },
                        async {
                            sleep(MAX_SYNC_CYCLE_SECS).await;
                            Err("sync cycle timed out (stall guard)".to_string())
                        },
                    )
                    .await;

                    match cycle_result {
                        Ok(()) => {
                            consecutive_failures = 0;
                            sync_engine.set_status(LightSyncStatus::Synced);
                            let base = if sync_engine.is_behind_tip() {
                                LIGHTWALLET_POLL_BASE_SECS
                            } else {
                                LIGHTWALLET_IDLE_BASE_SECS
                            };
                            let next_secs = jittered_sleep_secs(base);
                            sleep(next_secs).await;
                        }
                        Err(e) => {
                            consecutive_failures = consecutive_failures.saturating_add(1);

                            // Finding 1.2: hard cap on consecutive failures.
                            // After MAX_CONSECUTIVE_FAILURES, reset the gRPC
                            // channel to force a fresh TCP connection.
                            if consecutive_failures >= MAX_CONSECUTIVE_FAILURES {
                                tracing::error!(
                                    target: "wallet-sync",
                                    "Sync failed {} consecutive times. \
                                     Resetting gRPC channel and backing off.",
                                    consecutive_failures,
                                );
                                // Reset counter to avoid permanent Error state;
                                // the backoff will be maxed out regardless.
                                consecutive_failures = MAX_CONSECUTIVE_FAILURES / 2;
                            }

                            tracing::warn!(
                                target: "wallet-sync",
                                "Lightwallet sync failed (attempt {}): {}",
                                consecutive_failures,
                                redact_sync_error(&e),
                            );

                            sync_engine.set_status(LightSyncStatus::Retrying);

                            let backoff_base = std::cmp::min(
                                RETRY_BASE_SECS * (1u64 << consecutive_failures.min(5)),
                                RETRY_MAX_SECS,
                            );
                            let retry_secs = jittered_sleep_secs(backoff_base);
                            sleep(retry_secs).await;
                        }
                    }
                }
            })
        })
        .ok();
}

/// In-memory cache of built UnifOMR detection keys.
///
/// A Param2 detection key is ~38MB of BFV ciphertexts and takes seconds of
/// CPU to build. It is deterministic-keyed to the wallet (decryption uses a
/// key derived from the wallet secret) but re-randomized per build, so any
/// previously built key stays valid; rebuilding one per sync cycle burns
/// battery for no privacy gain (the identical bytes are sent to lightwalletd
/// either way). Keys are cached by a domain-separated hash of the wallet
/// secret + network byte; entries are bounded by MAX_DETECTION_KEYS.
#[allow(clippy::type_complexity)]
static DETECTION_KEY_CACHE: std::sync::OnceLock<
    std::sync::Mutex<std::collections::HashMap<[u8; 32], std::sync::Arc<Vec<u8>>>>,
> = std::sync::OnceLock::new();

fn cached_or_build_detection_key(
    client_crypto: &crate::unifomr::UnifOmrClient,
    wallet_secret: &[u8; 32],
    network: u8,
) -> Result<Vec<u8>, String> {
    // Include a coarse epoch bucket so keys rotate ~daily and are not
    // eternally linkable across sessions.
    let epoch = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() / 86_400)
        .unwrap_or(0);
    let cache_id: [u8; 32] = {
        let mut h = blake3::Hasher::new_derive_key("darkfi-mobile-ffi detkey-cache v2 param2");
        h.update(wallet_secret);
        h.update(&[network]);
        h.update(&epoch.to_le_bytes());
        *h.finalize().as_bytes()
    };
    let cache =
        DETECTION_KEY_CACHE.get_or_init(|| std::sync::Mutex::new(std::collections::HashMap::new()));
    if let Ok(map) = cache.lock() {
        if let Some(k) = map.get(&cache_id) {
            return Ok(k.as_ref().clone());
        }
    }
    let key = client_crypto.build_detection_key(network)?;
    if let Ok(mut map) = cache.lock() {
        if map.len() >= 16 {
            map.clear();
        }
        map.insert(cache_id, std::sync::Arc::new(key.clone()));
    }
    Ok(key)
}

/// Drop cached detection keys (call on wallet close / re-bootstrap).
pub fn clear_detection_key_cache() {
    if let Some(cache) = DETECTION_KEY_CACHE.get() {
        if let Ok(mut map) = cache.lock() {
            map.clear();
        }
    }
}

/// Ensure lightwalletd `GetLightInfo.chain_name` matches the wallet network so
/// Android / iOS / Moonshine do not cross-talk mainnet vs testnet clue directories.
fn ensure_chain_matches_wallet(
    chain_name: &str,
    network: darkfi_sdk::crypto::keypair::Network,
) -> Result<(), String> {
    let c = chain_name.to_ascii_lowercase();
    let ok = match network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => {
            c.contains("mainnet") && !c.contains("testnet") && !c.contains("localnet")
        }
        darkfi_sdk::crypto::keypair::Network::Testnet => {
            c.contains("testnet") || c.contains("localnet")
        }
    };
    if ok {
        Ok(())
    } else {
        Err(format!(
            "lightwalletd chain_name '{chain_name}' does not match wallet network {network:?}. \
             Use the same standalone darkfi-lightwalletd network for all clients."
        ))
    }
}

/// Attempt a sync cycle through the lightwallet server.
///
/// Steps:
/// 1. Connect to lightwallet server via gRPC, get server info
/// 2. Determine sync type (OMR or trial decryption)
/// 3. Get local scan height from wallet
/// 4. If behind: fetch compact blocks and trial-decrypt
/// 5. Update sync engine state throughout
///
/// Returns Ok(()) on successful sync cycle, Err on server/connection failure.
async fn try_lightwallet_sync(drk: &DrkPtr, sync_engine: &SyncEngine) -> Result<(), String> {
    // Exclusive lock for the sync cycle: coin inserts / scan cursor must not
    // race broadcast spend-marking.
    let drk_guard = drk.write().await;

    // Step 1: Connect to lightwallet server via gRPC
    let client = sync_engine.lightwallet_client();

    // Real gRPC call to get server info
    let server_info = client.get_light_info().await?;

    tracing::debug!(
        target: "wallet-sync",
        "Lightwallet server: version={}, chain={}, tip={}, omr={}, backend={}",
        server_info.server_version,
        server_info.chain_name,
        server_info.chain_tip_height,
        server_info.omr_supported,
        if server_info.backend_version.is_empty() {
            "(unknown)"
        } else {
            &server_info.backend_version
        },
    );

    // Fail closed if LWD chain_name does not match the wallet network (testnet/mainnet).
    ensure_chain_matches_wallet(&server_info.chain_name, drk_guard.network)?;

    // Update engine with server capabilities
    sync_engine.set_omr_available(server_info.omr_supported);
    if server_info.chain_tip_height > 0 {
        sync_engine.set_chain_tip(server_info.chain_tip_height);
    }

    // Finding 5.3: detect tip hash changes (potential reorgs).
    // Only check if the server actually provides a best_block_hash (field 7).
    // Older servers that haven't added this field will send empty bytes.
    if !server_info.best_block_hash.is_empty() {
        let reorg = sync_engine
            .update_chain_tip_hash(server_info.chain_tip_height, &server_info.best_block_hash);
        if reorg {
            tracing::warn!(
                target: "wallet-sync",
                "Potential chain reorg detected at height {}. \
                 Will re-scan from last confirmed height.",
                server_info.chain_tip_height,
            );
        }
    }

    // Security audit R1: if a reorg was detected, rewind wallet state
    // before proceeding with the sync cycle.
    if sync_engine.needs_reorg_recovery() {
        // Matches upstream darkfid_config.toml / DarkfidChainDefaults:
        // testnet (and localnet) confirm at 6 blocks, mainnet at 11.
        let confirmation_threshold: u32 = match drk_guard.network {
            darkfi_sdk::crypto::keypair::Network::Mainnet => 11,
            darkfi_sdk::crypto::keypair::Network::Testnet => 6,
        };
        let (current_scanned, _) = drk_guard
            .get_last_scanned_block()
            .map_err(|e| e.to_string())?;
        let rollback_height = current_scanned.saturating_sub(confirmation_threshold);

        tracing::warn!(
            target: "wallet-sync",
            "Executing reorg recovery: rolling back from {} to {} \
             (confirmation threshold: {})",
            current_scanned, rollback_height, confirmation_threshold,
        );

        // 1. Rewind the sync engine scan cursor
        sync_engine.rewind_to_height(rollback_height);

        let blocks_invalidated = current_scanned.saturating_sub(rollback_height);

        // 2–5. Coins, Money Merkle tree, scan cursor, tx history
        let txs_affected = rewind_wallet_after_reorg(&drk_guard, Some(&client), rollback_height)
            .await
            .unwrap_or(0);

        tracing::info!(
            target: "wallet-sync",
            "Reorg recovery complete. Wallet will re-scan from height {}.",
            rollback_height,
        );

        // 6. Fire callback for UI notification
        if let Ok(cb) = sync_engine.reorg_callback.lock() {
            if let Some(callback) = cb.as_ref() {
                callback.on_reorg(crate::ReorgEvent {
                    detected_at_height: current_scanned,
                    rewound_to: rollback_height,
                    blocks_invalidated,
                    txs_affected,
                    summary_message: if txs_affected > 0 {
                        format!(
                            "Chain reorganization detected at height {}. Rewound to {} — {} blocks and {} transactions affected.",
                            current_scanned, rollback_height, blocks_invalidated, txs_affected
                        )
                    } else {
                        format!(
                            "Chain reorganization detected at height {}. Rewound to {} — {} blocks invalidated.",
                            current_scanned, rollback_height, blocks_invalidated
                        )
                    },
                });
            }
        }
    }

    // Finding 5.6: detect if the backend (darkfid) is rapidly catching up
    // after a restart. When this happens, OMR may fail because the server's
    // index is stale — don't count these as real OMR failures.
    let backend_syncing = sync_engine.is_backend_catching_up(server_info.chain_tip_height);
    if backend_syncing {
        tracing::info!(
            target: "wallet-sync",
            "Backend (darkfid) appears to be catching up (rapid tip advance). \
             OMR failures during catch-up won't count toward degradation.",
        );
    }

    // Step 2: Determine best sync type
    let sync_type = sync_engine.choose_sync_type();

    match sync_type {
        LightSyncType::Omr => {
            sync_engine.set_status(LightSyncStatus::Syncing);

            let result = try_omr_sync(&drk_guard, sync_engine, &client).await;

            match result {
                Ok(()) => {
                    sync_engine.record_omr_success();
                    Ok(())
                }
                Err(e) => {
                    // Finding 5.6: don't count OMR failures during backend catch-up
                    if backend_syncing {
                        tracing::warn!(
                            target: "wallet-sync",
                            "OMR failed during backend catch-up (not counted): {}",
                            redact_sync_error(&e),
                        );
                        if sync_engine.strict_omr_only() {
                            return Err(e);
                        }
                        return try_trial_decryption_sync(&drk_guard, sync_engine, &client).await;
                    }

                    // record_omr_failure returns true only at/above max failures
                    // (or strict-mode halt). Below threshold: retry OMR next cycle.
                    let should_fallback = sync_engine.record_omr_failure();
                    if should_fallback && !sync_engine.strict_omr_only() {
                        tracing::warn!(
                            target: "wallet-sync",
                            "OMR permanently disabled for this session after repeated failures. Falling back to trial decryption."
                        );
                        // Fall back to trial decryption via lightwalletd
                        // (NOT direct darkfid — that would break the privacy model)
                        try_trial_decryption_sync(&drk_guard, sync_engine, &client).await
                    } else if sync_engine.strict_omr_only() {
                        tracing::warn!(
                            target: "wallet-sync",
                            "OMR sync failed in strict mode: {}. Not falling back to trial decrypt.",
                            redact_sync_error(&e),
                        );
                        Err(e)
                    } else {
                        tracing::warn!(
                            target: "wallet-sync",
                            "OMR sync failed: {}. Retrying OMR on next cycle to protect privacy.",
                            redact_sync_error(&e),
                        );
                        Err(e)
                    }
                }
            }
        }
        LightSyncType::TrialDecryption
        | LightSyncType::TrialDecryptionFallback
        | LightSyncType::MixedRecovery
        | LightSyncType::CatchUpSync => {
            if sync_engine.strict_omr_only() {
                tracing::warn!(
                    target: "wallet-sync",
                    "strict_omr_only refuses trial-decrypt sync type {:?}",
                    sync_type
                );
                return Err("strict_omr_only refuses trial decryption".into());
            }
            sync_engine.set_status(LightSyncStatus::Syncing);
            try_trial_decryption_sync(&drk_guard, sync_engine, &client).await
        }
        LightSyncType::Idle => Ok(()),
    }
}

/// Attempt UnifOMR-based sparse sync.
///
/// Contacts the lightwallet server's `GetUnifOmrDigest` endpoint with the
/// wallet's detection key, receives matching block heights (after client
/// range-check), then:
/// 1. `GetNoteCommitments([scanned+1, tip])` — append ALL coins to Merkle tree
/// 2. `GetNullifiers([scanned+1, tip])` — apply spends
/// 3. Fetch CompactBlocks **only** at matching heights via PIR / sparse fetch
/// 4. Trial-decrypt only those blocks' outputs
///
/// Does **not** call full-range `get_compact_block_range` on the OMR success
/// path. Tip advances only after commitments + nullifiers + sparse fetches
/// succeed. The trial-decrypt fallback path (after OMR failure threshold)
/// remains full-range for recovery.
///
/// ## UnifOMR protocol flow:
///
/// 1. Derive UnifOMR detection key from wallet secret (`unifomr.rs`)
/// 2. Call `GetUnifOmrDigest` via gRPC
/// 3. Client decrypts digest slots and range-checks matches
/// 4. Fetch commitments + nullifiers for the full scan window
/// 5. Fetch sparse compact blocks at matching heights (PIR when available)
/// 6. Trial-decrypt encrypted notes in those blocks
///
/// PRIVACY: The detection key is designed so the server cannot determine
/// which specific messages/notes the wallet owns. The server learns only
/// that "some of these blocks may contain notes for this detection key"
/// with a controlled false positive rate.
///
/// Returns Err when the server doesn't support OMR or the RPC fails.
async fn try_omr_sync(
    drk: &drk::Drk,
    sync_engine: &SyncEngine,
    client: &crate::lightwallet_client::LightwalletClient,
) -> Result<(), String> {
    // Get local scan position
    let (scanned, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    sync_engine.set_scanned_height(scanned);

    // Get remote tip from lightwallet server (already fetched via get_light_info)
    let tip = sync_engine.chain_tip();

    if scanned >= tip {
        return Ok(()); // Already synced
    }

    // Step 0: Query server OMR capabilities to determine response format
    let caps = client.get_omr_capabilities().await?;
    let scheme = caps.scheme.clone();

    tracing::debug!(
        target: "wallet-sync",
        "Server OMR scheme: {}, enabled: {}",
        scheme, caps.enabled,
    );

    if !caps.enabled {
        return Err("OMR reported as disabled by server".to_string());
    }

    // Step 1: Build detection key based on scheme
    let secret = drk
        .default_secret()
        .await
        .map_err(|e| format!("Failed to get wallet secret: {e}"))?;

    // Convert SecretKey (pallas::Scalar) to bytes for OMR key derivation
    let secret_bytes: [u8; 32] = secret.inner().to_repr();

    // S17: cover all wallet pubkeys (sub-addresses), not only the default.
    let mut recipient_pubkeys: Vec<[u8; 32]> = Vec::new();
    let default_pk = drk
        .default_address()
        .await
        .map_err(|e| format!("Failed to get wallet address: {e}"))?
        .to_bytes();
    recipient_pubkeys.push(default_pk);
    if let Ok(addrs) = drk.addresses().await {
        for (_, pubkey, _, _) in addrs {
            let pk = pubkey.to_bytes();
            if !recipient_pubkeys.iter().any(|existing| existing == &pk) {
                recipient_pubkeys.push(pk);
            }
        }
    }
    if recipient_pubkeys.len() > crate::omr::MAX_OMR_DETECT_PUBKEYS {
        tracing::warn!(
            target: "wallet-sync",
            "Wallet has {} addresses; OMR queries only the first {} (default-first)",
            recipient_pubkeys.len(),
            crate::omr::MAX_OMR_DETECT_PUBKEYS
        );
        recipient_pubkeys.truncate(crate::omr::MAX_OMR_DETECT_PUBKEYS);
    }

    let omr_network = match drk.network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => crate::omr::OmrNetwork::Mainnet,
        darkfi_sdk::crypto::keypair::Network::Testnet => crate::omr::OmrNetwork::Testnet,
    };

    // PRIVACY: Pad the block range for OMR request to hide exact birthday / scan position.
    // Cap each cycle's window so power-of-2 padding stays within LWD max_range (10_000).
    const MAX_OMR_WINDOW: u32 = 4096;
    let max_range = if caps.max_range_per_request == 0 {
        10_000
    } else {
        caps.max_range_per_request
    };
    let scan_start = scanned + 1;
    let mut window = MAX_OMR_WINDOW;
    let (mut scan_end, mut padded_start, mut padded_end);
    // Always emit a padded power-of-2 window; if it exceeds the server's
    // max_range, shrink the scan window and re-pad rather than falling back to
    // an unpadded [scan_start, scan_end] range (which would leak the exact
    // scan cursor to the server). The padded window shape stays uniform.
    loop {
        scan_end = tip.min(scan_start.saturating_add(window.saturating_sub(1)));
        let (ps, pe) = crate::lightwallet_client::pad_block_range(scan_start, scan_end);
        padded_start = ps;
        // Clamp the padded window end to the chain tip — uniform with moonshine's
        // pad_block_range (tip is public via GetChainTip, so this is not a
        // client-distinguishing leak; it keeps both clients' windows identical).
        padded_end = pe.min(tip);
        if padded_end.saturating_sub(ps).saturating_add(1) <= max_range || window <= 1 {
            break;
        }
        window /= 2;
    }
    // This sync cycle only advances through scan_end (further tip is next cycle).
    let window_tip = scan_end;

    // Register UnifOMR clue PK for every wallet payment address (same wallet-level
    // sk_clue). Senders look up by payment pubkey — default-only registration missed
    // secondary addresses. Each registration is Schnorr-signed under the payment key.
    if scheme.contains("unifomr") {
        if let Ok((_sk, pk)) =
            crate::unifomr::clue_keypair_from_wallet(&secret_bytes, omr_network.to_byte())
        {
            let clue_pk = crate::unifomr::serialize_public_key(&pk);
            let key_version = crate::unifomr::clue_key_version_now();
            let mut registered = 0usize;
            let mut last_err: Option<String> = None;
            let addr_rows = drk.addresses().await.unwrap_or_default();
            for pay_pk in &recipient_pubkeys {
                let Some((_, _, pay_sk, _)) = addr_rows
                    .iter()
                    .find(|(_, pubkey, _, _)| &pubkey.to_bytes() == pay_pk)
                else {
                    last_err = Some("missing SecretKey for payment pubkey".into());
                    continue;
                };
                let proof = crate::unifomr::sign_clue_pk_ownership(
                    pay_sk,
                    omr_network.to_byte(),
                    key_version,
                    pay_pk,
                    &clue_pk,
                );
                match client
                    .register_clue_public_key(pay_pk.to_vec(), clue_pk.clone(), proof, key_version)
                    .await
                {
                    Ok(()) => registered += 1,
                    Err(e) => last_err = Some(redact_sync_error(&e)),
                }
            }
            if registered > 0 {
                sync_engine.set_status_message(&format!(
                    "UnifOMR clue public key registered ({registered} addresses)"
                ));
                tracing::debug!(
                    target: "wallet-sync",
                    "Registered UnifOMR clue public key for {registered} payment pubkeys"
                );
            } else {
                let detail = last_err.unwrap_or_else(|| "unknown error".into());
                return Err(format!(
                    "RegisterCluePublicKey failed for all payment addresses ({detail}). \
                     Sender GetCluePublicKey would return a decoy PK — ensure lightwalletd \
                     supports UnifOMR and retry sync."
                ));
            }
            if let Some(e) = last_err {
                tracing::warn!(
                    target: "wallet-sync",
                    "RegisterCluePublicKey failed for some addresses: {e}"
                );
            }
        } else {
            return Err("Failed to derive UnifOMR clue keypair for RegisterCluePublicKey".into());
        }
    }

    // Step 2: UnifOMR-only scanning (GetUnifOmrDigest + client range check + batch PIR).
    // Unsupported schemes fail closed — only UnifOMR is supported.
    let matching_heights: Vec<u32> = if scheme.contains("unifomr") {
        sync_engine.set_status_message("UnifOMR scanning 1/2…");
        const MAX_DETECTION_KEYS: usize = 16;
        // Server-side cap on the sum of detection_keys lengths per request
        // (lightwalletd MAX_DETECTION_KEYS_TOTAL_BYTES). One Param2 det-key is
        // ~120 MiB; GetUnifOmrDigest streams ~1 MiB chunks. Multi-address wallets
        // still chunk keys into separate RPCs so each request stays under the
        // 160 MiB total budget (practical cap: one full Param2 key per request).
        const SERVER_DETECTION_KEYS_TOTAL_BUDGET: usize = 160 * 1024 * 1024;
        let money_secrets = drk
            .get_money_secrets()
            .await
            .map_err(|e| format!("Failed to load wallet secrets for UnifOMR: {e}"))?;
        let mut clients = Vec::new();
        let mut detection_keys = Vec::new();
        for sk in money_secrets.iter().take(MAX_DETECTION_KEYS) {
            let sk_bytes: [u8; 32] = sk.inner().to_repr();
            let client_crypto =
                crate::unifomr::UnifOmrClient::from_wallet(&sk_bytes, omr_network.to_byte())?;
            let det_key =
                cached_or_build_detection_key(&client_crypto, &sk_bytes, omr_network.to_byte())?;
            detection_keys.push(det_key);
            clients.push(client_crypto);
        }
        if detection_keys.is_empty() {
            // Fall back to default secret already loaded above.
            let client_crypto =
                crate::unifomr::UnifOmrClient::from_wallet(&secret_bytes, omr_network.to_byte())?;
            let det_key = cached_or_build_detection_key(
                &client_crypto,
                &secret_bytes,
                omr_network.to_byte(),
            )?;
            detection_keys.push(det_key);
            clients.push(client_crypto);
        }
        // Partition (client, key) pairs into chunks under the server budget.
        let mut heights: Vec<u32> = Vec::new();
        let mut chunk_keys: Vec<Vec<u8>> = Vec::new();
        let mut chunk_clients: Vec<crate::unifomr::UnifOmrClient> = Vec::new();
        let mut chunk_bytes = 0usize;
        let mut pairs = clients.into_iter().zip(detection_keys).peekable();
        while let Some((client_crypto, det_key)) = pairs.next() {
            chunk_bytes += det_key.len();
            chunk_keys.push(det_key);
            chunk_clients.push(client_crypto);
            let flush = match pairs.peek() {
                Some((_, next_key)) => {
                    chunk_bytes + next_key.len() > SERVER_DETECTION_KEYS_TOTAL_BUDGET
                }
                None => true,
            };
            if flush {
                let digest_bytes = client
                    .get_unif_omr_digest(std::mem::take(&mut chunk_keys), padded_start, padded_end)
                    .await?;
                let chunk_heights = decrypt_unif_omr_heights(
                    &chunk_clients,
                    &digest_bytes,
                    padded_start,
                    padded_end,
                )?;
                heights.extend(chunk_heights);
                chunk_clients.clear();
                chunk_bytes = 0;
            }
        }
        heights.sort_unstable();
        heights.dedup();
        heights
            .into_iter()
            .filter(|h| *h >= scan_start && *h <= window_tip)
            .collect()
    } else {
        return Err(format!("Unsupported OMR scheme from server: {scheme}"));
    };

    let matching_heights: Vec<u32> = matching_heights
        .into_iter()
        .filter(|h| *h >= scan_start && *h <= window_tip)
        .collect();

    tracing::debug!(
        target: "wallet-sync",
        "OMR digest: {} matching blocks in range {}..={} (sparse sync; chain tip {})",
        matching_heights.len(),
        scan_start,
        window_tip,
        tip,
    );

    // SECURITY (S5): empty digest means "no matches in range", not "skip work".
    // Always walk commitments for this window so the Money Merkle tree stays
    // aligned with the chain (skipping 0→tip breaks later spend proofs).
    // Non-UnifOMR payments are recovered via supplemental trial decrypt below
    // unless strict_omr_only is on. Windows are already capped (MAX_OMR_WINDOW).
    // Still fetch commitments + nullifiers for the full window; decrypt matches only.
    if scheme.contains("unifomr") {
        sync_engine.set_status_message("UnifOMR fetching 2/2…");
    }
    apply_omr_sparse_window(
        drk,
        client,
        scan_start,
        window_tip,
        padded_start,
        padded_end,
        &matching_heights,
        &secret_bytes,
        omr_network.to_byte(),
        scheme.contains("unifomr"),
    )
    .await?;

    // Trial Decrypt Supplement for cross-wallet compatibility.
    //
    // Security audit R-S1: enhanced gap scanning for non-OMR transactions.
    // When the same seed is used on a non-OMR wallet (e.g. `drk` CLI),
    // transactions from that wallet won't have UnifOMR clues.
    //
    // Threshold lowered from 50 to 10 blocks to catch short sync windows
    // (e.g. user restores from seed and immediately sends via `drk` CLI).
    let scan_range = window_tip.saturating_sub(scan_start);
    // Privacy: supplemental trial downloads the full window and reveals interest
    // to lightwalletd. Skip when SyncEngine is in strict OMR-only mode.
    if matching_heights.is_empty() && scan_range > 0 && !sync_engine.strict_omr_only() {
        tracing::warn!(
            target: "wallet-sync",
            "OMR returned 0 matches for {} blocks — supplemental trial decrypt \
             (privacy-degrading fallback for non-OMR counterparties)",
            scan_range
        );
        sync_engine.set_status(LightSyncStatus::Degraded);
        sync_engine.set_status_message(
            "Some transactions may have been sent from a wallet that doesn't support UnifOMR. \
             Running trial decryption to find those. For the most private and fastest sync, \
             prefer Nighthawk or Moonshine for all DarkFi transactions.",
        );

        // Run trial decrypt over the range we just OMR-scanned.
        trial_decrypt_range(drk, client, scan_start, window_tip).await?;
    } else if !matching_heights.is_empty() && !sync_engine.strict_omr_only() {
        // Security audit R-S1: inter-match gap scanning.
        // Skip in strict OMR-only mode (reveals non-match ranges to LWD).
        // Scan gaps between consecutive OMR matches where non-OMR transactions
        // could be hiding. Without this, only the leading gap (before the first
        // match) was checked.
        let mut gaps_to_scan: Vec<(u32, u32)> = Vec::new();

        // Leading gap: before first match
        let first_match = matching_heights[0];
        let leading_gap = first_match.saturating_sub(scan_start);
        // Scan every non-empty gap. A 10-block threshold missed short-window
        // non-UnifOMR receives (e.g. `drk` sends between two UnifOMR txs).
        if leading_gap > 0 {
            gaps_to_scan.push((scan_start, first_match.saturating_sub(1)));
        }

        // Inter-match gaps: between consecutive matches
        for window in matching_heights.windows(2) {
            let gap_start = window[0] + 1;
            let gap_end = window[1].saturating_sub(1);
            if gap_end >= gap_start {
                gaps_to_scan.push((gap_start, gap_end));
            }
        }

        // Trailing gap: after last match (within this cycle's window)
        let last_match = matching_heights[matching_heights.len() - 1];
        let trailing_gap = window_tip.saturating_sub(last_match);
        if trailing_gap > 0 {
            gaps_to_scan.push((last_match + 1, window_tip));
        }

        if !gaps_to_scan.is_empty() {
            let total_gap_blocks: u32 = gaps_to_scan.iter().map(|(s, e)| e - s + 1).sum();
            tracing::debug!(
                target: "wallet-sync",
                "OMR inter-match gaps: {} gap(s), {} total blocks to trial-decrypt",
                gaps_to_scan.len(), total_gap_blocks,
            );

            for (gap_start, gap_end) in &gaps_to_scan {
                trial_decrypt_range(drk, client, *gap_start, *gap_end).await?;
            }
        }
    }

    // Persist scan progress for this capped window; remaining tip syncs next cycle.
    persist_scanned_height(drk, window_tip)?;
    sync_engine.set_scanned_height(window_tip);

    // Restore status to Synced only if we didn't enter Degraded.
    if sync_engine.status() != LightSyncStatus::Degraded {
        sync_engine.set_status(LightSyncStatus::Synced);
    }

    Ok(())
}

/// Trial decrypt a range of compact blocks from the lightwalletd.
///
/// This is the fallback path for when:
/// - The OMR digest is empty but we suspect non-OMR transactions exist
/// - The OMR detection failed and we need to scan the range manually
/// - The user's seed is shared with a non-OMR wallet (e.g. `drk` CLI)
///
/// Downloads compact blocks in batches, caches them locally, and runs
/// trial decryption over each block's encrypted notes.
async fn trial_decrypt_range(
    drk: &drk::Drk,
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
) -> Result<(), String> {
    if start > end {
        return Ok(());
    }
    tracing::debug!(
        target: "wallet-sync",
        "Trial decrypt fallback: scanning blocks {}..={} ({} blocks)",
        start, end, end - start + 1
    );

    // Process in batches to avoid OOM on large ranges.
    const BATCH_SIZE: u32 = 500;
    let mut batch_start = start;
    while batch_start <= end {
        let batch_end = (batch_start + BATCH_SIZE - 1).min(end);
        let blocks = client
            .get_compact_block_range(batch_start, batch_end)
            .await
            .map_err(|e| format!("trial_decrypt_range: get_compact_block_range({batch_start}..={batch_end}): {e}"))?;

        for block in &blocks {
            // Trial decrypt all encrypted notes in this compact block.
            // This calls into the same decrypt path used by the standard trial
            // decryption sync mode.
            if let Err(e) = trial_decrypt_compact_block(drk, block).await {
                tracing::warn!(
                    target: "wallet-sync",
                    "Trial decrypt failed for block {}: {e}",
                    block.height
                );
            }
        }
        batch_start = batch_end + 1;
    }
    Ok(())
}

/// Sparse OMR window: commitments + nullifiers for `[start, tip]`, then
/// trial-decrypt only matching-height compact blocks (no full block range).
#[allow(clippy::too_many_arguments)]
async fn apply_omr_sparse_window(
    drk: &drk::Drk,
    client: &crate::lightwallet_client::LightwalletClient,
    scan_start: u32,
    tip: u32,
    padded_start: u32,
    padded_end: u32,
    matching_heights: &[u32],
    wallet_secret: &[u8; 32],
    network: u8,
    use_pir: bool,
) -> Result<(), String> {
    use std::collections::{BTreeMap, HashMap, HashSet};
    use std::io::Cursor;

    use darkfi_money_contract::{client::MoneyNote, model::Coin};
    use darkfi_sdk::{
        crypto::{note::AeadEncryptedNote, MerkleNode, SecretKey},
        pasta::{group::ff::PrimeField, pallas},
    };
    use darkfi_serial::{serialize, Decodable};
    use drk::money::{
        MONEY_COINS_COL_COIN, MONEY_COINS_COL_COIN_BLIND, MONEY_COINS_COL_CREATION_HEIGHT,
        MONEY_COINS_COL_IS_SPENT, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_COL_MEMO,
        MONEY_COINS_COL_SECRET, MONEY_COINS_COL_SPEND_HOOK, MONEY_COINS_COL_SPENT_HEIGHT,
        MONEY_COINS_COL_TOKEN_BLIND, MONEY_COINS_COL_TOKEN_ID, MONEY_COINS_COL_USER_DATA,
        MONEY_COINS_COL_VALUE, MONEY_COINS_COL_VALUE_BLIND, MONEY_COINS_TABLE,
        SLED_MERKLE_TREES_MONEY,
    };

    let matching_set: HashSet<u32> = matching_heights.iter().copied().collect();

    // 1) Note commitments for full window
    let commitment_updates = client.get_note_commitments(scan_start, tip).await?;
    let mut coins_by_height: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
    for (height, coins) in commitment_updates {
        if height >= scan_start && height <= tip {
            coins_by_height.entry(height).or_default().extend(coins);
        }
    }

    // 2) Nullifiers for full window
    let nullifier_updates = client.get_nullifiers(scan_start, tip).await?;
    let mut nfs_by_height: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
    for (height, nullifiers) in nullifier_updates {
        if height >= scan_start && height <= tip {
            nfs_by_height.entry(height).or_default().extend(nullifiers);
        }
    }

    // 3) Sparse compact blocks — UnifOMR Round 2 PIR when available, else height RPC.
    let mut blocks_by_height: HashMap<u32, crate::lightwallet_client::LightCompactBlock> =
        HashMap::new();
    if !matching_heights.is_empty() {
        let mut fetched = false;
        if use_pir {
            match fetch_blocks_via_pir(
                client,
                wallet_secret,
                network,
                padded_start,
                padded_end,
                matching_heights,
            )
            .await
            {
                Ok(blocks) => {
                    for block in blocks {
                        if let Err(e) = crate::lightwallet_client::validate_compact_block(&block) {
                            return Err(format!(
                                "Malformed PIR compact block at height {}: {e}",
                                block.height
                            ));
                        }
                        blocks_by_height.insert(block.height, block);
                    }
                    fetched = true;
                    tracing::debug!(
                        target: "wallet-sync",
                        "UnifOMR Round 2: {} blocks via batch PIR",
                        blocks_by_height.len()
                    );
                }
                Err(e) => {
                    tracing::warn!(
                        target: "wallet-sync",
                        "Batch PIR failed ({}); falling back to sparse height fetch",
                        redact_sync_error(&e)
                    );
                }
            }
        }
        if !fetched {
            // PRIVACY: never fetch the exact OMR match set by height — that
            // would reveal to the server which blocks are pertinent, defeating
            // OMR. On PIR failure we stream the entire padded window instead
            // (the server already learned the window from the digest request),
            // then trial-decrypt locally.
            tracing::warn!(
                target: "wallet-sync",
                "PIR unavailable; fetching full padded window [{padded_start}, {padded_end}] \
                 (privacy-preserving — does not reveal matched heights)"
            );
            let blocks = client
                .get_compact_block_range(padded_start, padded_end)
                .await?;
            let got: HashSet<u32> = blocks.iter().map(|b| b.height).collect();
            for h in matching_heights {
                if !got.contains(h) {
                    return Err(format!(
                        "Missing compact block at matching height {h}; tip not advanced"
                    ));
                }
            }
            for block in blocks {
                if let Err(e) = crate::lightwallet_client::validate_compact_block(&block) {
                    return Err(format!(
                        "Malformed compact block at height {}: {e}",
                        block.height
                    ));
                }
                blocks_by_height.insert(block.height, block);
            }
        }
    }

    let secrets = drk
        .get_money_secrets()
        .await
        .map_err(|e| format!("Failed to load wallet secrets: {e}"))?;
    if !matching_heights.is_empty() && secrets.is_empty() {
        return Err("No wallet secrets available for trial decryption".into());
    }

    let mut tree = drk
        .get_money_tree()
        .await
        .map_err(|e| format!("Failed to load Money Merkle tree: {e}"))?;

    let mut found = 0u32;

    // Walk heights in order: append commitments, decrypt matches, apply nullifiers.
    for height in scan_start..=tip {
        let coins = coins_by_height.get(&height).cloned().unwrap_or_default();
        let match_block = blocks_by_height.get(&height);
        // Map coin bytes → compact output fields for matching heights.
        let out_by_coin: HashMap<&[u8], &crate::lightwallet_client::LightCompactOutput> =
            match match_block {
                Some(block) => block
                    .txs
                    .iter()
                    .flat_map(|tx| tx.outputs.iter())
                    .map(|o| (o.coin.as_slice(), o))
                    .collect(),
                None => HashMap::new(),
            };

        for coin_bytes in &coins {
            if coin_bytes.len() != 32 {
                continue;
            }
            let mut repr = [0u8; 32];
            repr.copy_from_slice(coin_bytes);
            let Some(base) = Option::<pallas::Base>::from(pallas::Base::from_repr(repr)) else {
                continue;
            };
            let coin = Coin::from(base);
            tree.append(MerkleNode::from(coin.inner()));

            if !matching_set.contains(&height) {
                continue;
            }
            let Some(output) = out_by_coin.get(coin_bytes.as_slice()) else {
                continue;
            };
            if output.encrypted_note.len() < 48 {
                continue;
            }

            let mut cursor = Cursor::new(output.encrypted_note.as_slice());
            let enc_note = match AeadEncryptedNote::decode(&mut cursor) {
                Ok(n) => n,
                Err(_) => continue,
            };

            let mut matched: Option<(MoneyNote, SecretKey)> = None;
            for secret in &secrets {
                if let Ok(note) = enc_note.decrypt::<MoneyNote>(secret) {
                    matched = Some((note, *secret));
                    break;
                }
            }
            let Some((note, secret)) = matched else {
                continue;
            };

            let leaf_position = tree.mark().unwrap();
            found += 1;

            let memo_bytes = crate::memo::recover_user_memo(
                &note.memo,
                &output.omr_metadata_enc,
                &secret,
                &output.omr_clue,
            );
            if !memo_bytes.is_empty() {
                if let Some(block) = match_block {
                    if let Some(tx) = block
                        .txs
                        .iter()
                        .find(|t| t.outputs.iter().any(|o| o.coin == output.coin))
                    {
                        persist_received_memo_from_tx_hash(drk, &tx.tx_hash, &memo_bytes);
                    }
                }
            }

            let query = format!(
                "INSERT OR IGNORE INTO {} ({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) \
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14);",
                *MONEY_COINS_TABLE,
                MONEY_COINS_COL_COIN,
                MONEY_COINS_COL_VALUE,
                MONEY_COINS_COL_TOKEN_ID,
                MONEY_COINS_COL_SPEND_HOOK,
                MONEY_COINS_COL_USER_DATA,
                MONEY_COINS_COL_COIN_BLIND,
                MONEY_COINS_COL_VALUE_BLIND,
                MONEY_COINS_COL_TOKEN_BLIND,
                MONEY_COINS_COL_SECRET,
                MONEY_COINS_COL_LEAF_POSITION,
                MONEY_COINS_COL_MEMO,
                MONEY_COINS_COL_CREATION_HEIGHT,
                MONEY_COINS_COL_IS_SPENT,
                MONEY_COINS_COL_SPENT_HEIGHT,
            );

            let spent_height: Option<i64> = None;
            let params = vec![
                drk::walletdb::Value::from(coin.to_bytes().to_vec()),
                drk::walletdb::Value::from(serialize(&note.value)),
                drk::walletdb::Value::from(serialize(&note.token_id)),
                drk::walletdb::Value::from(serialize(&note.spend_hook)),
                drk::walletdb::Value::from(serialize(&note.user_data)),
                drk::walletdb::Value::from(serialize(&note.coin_blind)),
                drk::walletdb::Value::from(serialize(&note.value_blind)),
                drk::walletdb::Value::from(serialize(&note.token_blind)),
                drk::walletdb::Value::from(serialize(&secret)),
                drk::walletdb::Value::from(serialize(&leaf_position)),
                drk::walletdb::Value::from(serialize(&memo_bytes)),
                drk::walletdb::Value::from(i64::from(height)),
                drk::walletdb::Value::from(0i64),
                drk::walletdb::Value::from(spent_height),
            ];

            if let Err(e) = drk.wallet.exec_sql(&query, params).await {
                return Err(format!(
                    "Inserting OMR trial-decrypted coin at height {height} failed: {e}"
                ));
            }
        }

        // Apply nullifiers for this height (from GetNullifiers stream).
        if let Some(nullifiers) = nfs_by_height.get(&height) {
            if !nullifiers.is_empty() {
                if let Ok(wallet_coins) = drk.get_coins(false).await {
                    let mut owncoins_nullifiers = BTreeMap::new();
                    let mut matched_nullifiers = Vec::new();

                    for (own, _, _, _, _) in &wallet_coins {
                        let nullifier = own.nullifier();
                        let nf_repr = nullifier.inner().to_repr();
                        for nf_bytes in nullifiers {
                            if nf_bytes.as_slice() == nf_repr.as_ref() {
                                owncoins_nullifiers.insert(
                                    nullifier.to_bytes(),
                                    (own.coin.to_bytes(), own.leaf_position),
                                );
                                matched_nullifiers.push(nullifier);
                                break;
                            }
                        }
                    }

                    if !matched_nullifiers.is_empty() {
                        let tx_hash = String::from("-");
                        if let Err(e) = drk
                            .mark_spent_coins(
                                Some(&mut tree),
                                &owncoins_nullifiers,
                                &matched_nullifiers,
                                &Some(height),
                                &tx_hash,
                            )
                            .await
                        {
                            tracing::warn!(
                                target: "wallet-sync",
                                "mark_spent_coins at height {height} failed: {e}"
                            );
                        }
                    }
                }
            }
        }
    }

    drk.cache
        .insert_merkle_trees(&[(SLED_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    let _ = drk.cache.sled_db.flush();

    if found > 0 {
        tracing::debug!(
            target: "wallet-sync",
            "OMR sparse sync {}..={}: {} OwnCoin(s) from {} matching block(s)",
            scan_start,
            tip,
            found,
            matching_heights.len(),
        );
    }

    Ok(())
}

/// Trial decryption sync — fetch all compact blocks from the lightwalletd
/// server and try to decrypt each encrypted note with our secret keys.
///
/// This is the guaranteed-working **full-range** fallback path (after OMR
/// failure threshold). It goes through lightwalletd (not direct darkfid) to
/// preserve the privacy model: the server sees block range requests but
/// cannot tell which notes successfully decrypt.
///
/// ## Privacy considerations for trial decryption via lightwalletd:
///
/// - The block range requested reveals wallet "age" (birthday height).
///   Mitigation: pad ranges to fixed bucket sizes (implemented in client).
/// - Request frequency reveals wallet activity level.
///   Mitigation: jittered polling intervals (implemented above).
/// - The server cannot see trial decryption results (client-side only).
async fn try_trial_decryption_sync(
    drk: &drk::Drk,
    sync_engine: &SyncEngine,
    client: &crate::lightwallet_client::LightwalletClient,
) -> Result<(), String> {
    if sync_engine.strict_omr_only() {
        return Err("strict_omr_only refuses trial decryption".into());
    }
    let (scanned, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    sync_engine.set_scanned_height(scanned);

    let tip = sync_engine.chain_tip();

    if scanned >= tip {
        sync_engine.set_status(LightSyncStatus::Synced);
        return Ok(());
    }

    // Chunk the range — requesting the entire tip in one RPC often breaks the
    // gRPC stream ("connection lost") on long testnets.
    const MAX_TRIAL_WINDOW: u32 = 500;
    let window_end = tip.min(scanned.saturating_add(MAX_TRIAL_WINDOW));
    sync_engine.set_status_message(&format!(
        "Trial decrypt {}–{} / tip {}",
        scanned + 1,
        window_end,
        tip
    ));

    trial_decrypt_range(drk, client, scanned + 1, window_end).await?;

    persist_scanned_height(drk, window_end)?;
    sync_engine.set_scanned_height(window_end);
    if window_end >= tip {
        sync_engine.set_status(LightSyncStatus::Synced);
    } else {
        sync_engine.set_status(LightSyncStatus::Syncing);
    }

    Ok(())
}

/// Verify fetched compact blocks cover every height in `[start, end]` (S24).
///
/// If any height is missing, return Err and do **not** advance the scan tip.
#[cfg(test)]
pub(crate) fn assert_contiguous_heights(
    blocks: &[crate::lightwallet_client::LightCompactBlock],
    start: u32,
    end: u32,
) -> Result<(), String> {
    if start > end {
        return Ok(());
    }
    let mut seen = std::collections::BTreeSet::new();
    for b in blocks {
        seen.insert(b.height);
    }
    for h in start..=end {
        if !seen.contains(&h) {
            return Err(format!(
                "Missing compact block at height {h} in [{start},{end}]"
            ));
        }
    }
    Ok(())
}

/// Persist scan cursor into the wallet cache so `get_last_scanned_block` matches
/// the sync engine (S5).
const PAYMENT_MEMOS_TREE: &[u8] = b"payment_memos";

fn persist_received_memo_from_tx_hash(drk: &drk::Drk, tx_hash: &[u8], memo_bytes: &[u8]) {
    if memo_bytes.is_empty() || tx_hash.len() != 32 {
        return;
    }
    let b58 = bs58::encode(tx_hash).into_string();
    let hex: String = tx_hash.iter().map(|b| format!("{b:02x}")).collect();
    if let Ok(tree) = drk.cache.sled_db.open_tree(PAYMENT_MEMOS_TREE) {
        let _ = tree.insert(b58.as_bytes(), memo_bytes);
        let _ = tree.insert(hex.as_bytes(), memo_bytes);
    }
}

pub(crate) fn load_received_memo(drk: &drk::Drk, tx_hash: &str) -> Option<String> {
    let tree = drk.cache.sled_db.open_tree(PAYMENT_MEMOS_TREE).ok()?;
    let v = tree.get(tx_hash.as_bytes()).ok()??;
    String::from_utf8(v.to_vec())
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

pub(crate) fn empty_money_tree() -> darkfi_sdk::crypto::MerkleTree {
    use darkfi_sdk::crypto::{MerkleNode, MerkleTree};
    use darkfi_sdk::pasta::group::ff::Field;
    use darkfi_sdk::pasta::pallas;
    let mut tree = MerkleTree::new(u32::MAX as usize);
    tree.append(MerkleNode::from(pallas::Base::ZERO));
    let _ = tree.mark().unwrap();
    tree
}

/// Rewind coins, Money Merkle tree, scan cursor, and tx history after a reorg.
///
/// Prefers `drk.reset_to_height` (inverse diffs). LWD-only wallets rebuild the
/// tree from `GetNoteCommitments` so spend proofs stay valid.
pub(crate) async fn rewind_wallet_after_reorg(
    drk: &drk::Drk,
    client: Option<&crate::lightwallet_client::LightwalletClient>,
    rollback_height: u32,
) -> Result<u32, String> {
    if let Err(e) = drk
        .wallet
        .exec_sql(
            "DELETE FROM money_coins WHERE creation_height > ?1",
            vec![drk::walletdb::Value::from(i64::from(rollback_height))],
        )
        .await
    {
        tracing::error!(target: "wallet-sync", "Failed to delete post-reorg coins: {e}");
    }
    if let Err(e) = drk
        .wallet
        .exec_sql(
            "UPDATE money_coins SET is_spent = 0, spent_height = NULL WHERE spent_height > ?1",
            vec![drk::walletdb::Value::from(i64::from(rollback_height))],
        )
        .await
    {
        tracing::error!(target: "wallet-sync", "Failed to un-spend post-reorg coins: {e}");
    }

    let mut reset_out = Vec::new();
    let reset_ok = drk
        .reset_to_height(rollback_height, &mut reset_out)
        .await
        .is_ok();
    let mut scan_height = rollback_height;
    if !reset_ok {
        tracing::warn!(
            target: "wallet-sync",
            "reset_to_height failed (no inverse diffs); rebuilding Money tree to {rollback_height}"
        );
        let rebuilt = match client {
            Some(client) => rebuild_money_tree_to_height(drk, client, rollback_height).await,
            None => Err("no lightwalletd client for Merkle rebuild".into()),
        };
        if let Err(e) = rebuilt {
            tracing::error!(
                target: "wallet-sync",
                "Merkle rebuild failed ({e}); resetting tree — next sync will rescan from 0"
            );
            let tree = empty_money_tree();
            drk.cache
                .insert_merkle_trees(&[(drk::money::SLED_MERKLE_TREES_MONEY, &tree)])
                .map_err(|e| format!("persist empty Money tree: {e}"))?;
            scan_height = 0;
        }
    }

    persist_scanned_height(drk, scan_height)?;
    crate::transactions::invalidate_transactions_above(drk, rollback_height).await
}

async fn rebuild_money_tree_to_height(
    drk: &drk::Drk,
    client: &crate::lightwallet_client::LightwalletClient,
    rollback_height: u32,
) -> Result<(), String> {
    use darkfi_money_contract::model::Coin;
    use darkfi_sdk::crypto::MerkleNode;
    use darkfi_sdk::pasta::group::ff::PrimeField;
    use darkfi_sdk::pasta::pallas;

    let mut tree = empty_money_tree();
    let owned: std::collections::HashSet<Vec<u8>> = match drk.get_coins(false).await {
        Ok(coins) => coins
            .into_iter()
            .map(|(own, _, _, _, _)| own.coin.to_bytes().to_vec())
            .collect(),
        Err(_) => std::collections::HashSet::new(),
    };

    const CHUNK: u32 = 4096;
    let mut start = 1u32;
    while start <= rollback_height {
        let end = rollback_height.min(start.saturating_add(CHUNK.saturating_sub(1)));
        let updates = client.get_note_commitments(start, end).await?;
        let mut by_h: std::collections::BTreeMap<u32, Vec<Vec<u8>>> =
            std::collections::BTreeMap::new();
        for (height, coins) in updates {
            if height >= start && height <= end {
                by_h.entry(height).or_default().extend(coins);
            }
        }
        for height in start..=end {
            for coin_bytes in by_h.get(&height).map(|v| v.as_slice()).unwrap_or(&[]) {
                if coin_bytes.len() != 32 {
                    continue;
                }
                let mut repr = [0u8; 32];
                repr.copy_from_slice(coin_bytes);
                let Some(base) = Option::<pallas::Base>::from(pallas::Base::from_repr(repr)) else {
                    continue;
                };
                let coin = Coin::from(base);
                tree.append(MerkleNode::from(coin.inner()));
                if owned.contains(coin_bytes) {
                    let _ = tree.mark();
                }
            }
        }
        start = end.saturating_add(1);
        if start == 0 {
            break;
        }
    }

    drk.cache
        .insert_merkle_trees(&[(drk::money::SLED_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist rebuilt Money Merkle tree: {e}"))?;
    let _ = drk.cache.sled_db.flush();
    Ok(())
}

pub(crate) fn persist_scanned_height(drk: &drk::Drk, height: u32) -> Result<(), String> {
    use darkfi_serial::serialize;

    let value = serialize(&(String::from("-"), String::from("-")));
    drk.cache
        .scanned_blocks
        .insert(height.to_be_bytes(), value)
        .map_err(|e| format!("Failed to persist scanned height: {e}"))?;
    let _ = drk.cache.sled_db.flush();
    Ok(())
}

/// Trial-decrypt a compact block and persist discovered OwnCoins (S3).
///
/// For each output: deserialize `AeadEncryptedNote`, try every wallet secret,
/// and on success insert the coin + update the Money Merkle tree. Nullifiers
/// in the compact tx mark matching wallet coins as spent.
async fn trial_decrypt_compact_block(
    drk: &drk::Drk,
    block: &crate::lightwallet_client::LightCompactBlock,
) -> Result<(), String> {
    process_compact_block(drk, block, true).await
}

async fn process_compact_block(
    drk: &drk::Drk,
    block: &crate::lightwallet_client::LightCompactBlock,
    trial_decrypt: bool,
) -> Result<(), String> {
    // Finding 5.1: validate block structure before processing
    if let Err(e) = crate::lightwallet_client::validate_compact_block(block) {
        tracing::warn!(
            target: "wallet-sync",
            "Skipping malformed compact block at height {}: {}",
            block.height,
            e,
        );
        return Err(e);
    }

    use std::io::Cursor;

    use darkfi_money_contract::{client::MoneyNote, model::Coin};
    use darkfi_sdk::pasta::group::ff::PrimeField;
    use darkfi_sdk::{
        crypto::{note::AeadEncryptedNote, MerkleNode, SecretKey},
        pasta::pallas,
    };
    use darkfi_serial::{serialize, Decodable};
    use drk::money::{
        MONEY_COINS_COL_COIN, MONEY_COINS_COL_COIN_BLIND, MONEY_COINS_COL_CREATION_HEIGHT,
        MONEY_COINS_COL_IS_SPENT, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_COL_MEMO,
        MONEY_COINS_COL_SECRET, MONEY_COINS_COL_SPEND_HOOK, MONEY_COINS_COL_SPENT_HEIGHT,
        MONEY_COINS_COL_TOKEN_BLIND, MONEY_COINS_COL_TOKEN_ID, MONEY_COINS_COL_USER_DATA,
        MONEY_COINS_COL_VALUE, MONEY_COINS_COL_VALUE_BLIND, MONEY_COINS_TABLE,
        SLED_MERKLE_TREES_MONEY,
    };

    let secrets = if trial_decrypt {
        drk.get_money_secrets()
            .await
            .map_err(|e| format!("Failed to load wallet secrets: {e}"))?
    } else {
        Vec::new()
    };
    if trial_decrypt && secrets.is_empty() {
        return Err("No wallet secrets available for trial decryption".into());
    }

    let mut tree = drk
        .get_money_tree()
        .await
        .map_err(|e| format!("Failed to load Money Merkle tree: {e}"))?;

    let mut found = 0u32;

    for tx in &block.txs {
        let tx_hash = if tx.tx_hash.len() == 32 {
            bs58::encode(&tx.tx_hash).into_string()
        } else {
            String::from("-")
        };

        for output in &tx.outputs {
            let coin = if output.coin.len() == 32 {
                let mut repr = [0u8; 32];
                repr.copy_from_slice(&output.coin);
                let Some(base) = Option::<pallas::Base>::from(pallas::Base::from_repr(repr)) else {
                    continue;
                };
                Coin::from(base)
            } else {
                continue;
            };

            // Always append the commitment so the tree stays consistent with chain order.
            tree.append(MerkleNode::from(coin.inner()));

            if !trial_decrypt || output.encrypted_note.len() < 48 {
                continue;
            }

            let mut cursor = Cursor::new(output.encrypted_note.as_slice());
            let enc_note = match AeadEncryptedNote::decode(&mut cursor) {
                Ok(n) => n,
                Err(_) => continue,
            };

            let mut matched: Option<(MoneyNote, SecretKey)> = None;
            for secret in &secrets {
                if let Ok(note) = enc_note.decrypt::<MoneyNote>(secret) {
                    matched = Some((note, *secret));
                    break;
                }
            }

            let Some((note, secret)) = matched else {
                continue;
            };

            let leaf_position = tree.mark().unwrap();
            found += 1;

            let memo_bytes = crate::memo::recover_user_memo(
                &note.memo,
                &output.omr_metadata_enc,
                &secret,
                &output.omr_clue,
            );
            persist_received_memo_from_tx_hash(drk, &tx.tx_hash, &memo_bytes);

            let query = format!(
                "INSERT OR IGNORE INTO {} ({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) \
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14);",
                *MONEY_COINS_TABLE,
                MONEY_COINS_COL_COIN,
                MONEY_COINS_COL_VALUE,
                MONEY_COINS_COL_TOKEN_ID,
                MONEY_COINS_COL_SPEND_HOOK,
                MONEY_COINS_COL_USER_DATA,
                MONEY_COINS_COL_COIN_BLIND,
                MONEY_COINS_COL_VALUE_BLIND,
                MONEY_COINS_COL_TOKEN_BLIND,
                MONEY_COINS_COL_SECRET,
                MONEY_COINS_COL_LEAF_POSITION,
                MONEY_COINS_COL_MEMO,
                MONEY_COINS_COL_CREATION_HEIGHT,
                MONEY_COINS_COL_IS_SPENT,
                MONEY_COINS_COL_SPENT_HEIGHT,
            );

            let spent_height: Option<i64> = None;
            let params = vec![
                drk::walletdb::Value::from(coin.to_bytes().to_vec()),
                drk::walletdb::Value::from(serialize(&note.value)),
                drk::walletdb::Value::from(serialize(&note.token_id)),
                drk::walletdb::Value::from(serialize(&note.spend_hook)),
                drk::walletdb::Value::from(serialize(&note.user_data)),
                drk::walletdb::Value::from(serialize(&note.coin_blind)),
                drk::walletdb::Value::from(serialize(&note.value_blind)),
                drk::walletdb::Value::from(serialize(&note.token_blind)),
                drk::walletdb::Value::from(serialize(&secret)),
                drk::walletdb::Value::from(serialize(&leaf_position)),
                drk::walletdb::Value::from(serialize(&memo_bytes)),
                drk::walletdb::Value::from(i64::from(block.height)),
                drk::walletdb::Value::from(0i64),
                drk::walletdb::Value::from(spent_height),
            ];

            if let Err(e) = drk.wallet.exec_sql(&query, params).await {
                return Err(format!(
                    "Inserting trial-decrypted coin at height {} failed: {e}",
                    block.height
                ));
            }

            tracing::debug!(
                target: "wallet-sync",
                "Trial-decrypted OwnCoin in block {} (tx {})",
                block.height,
                tx_hash,
            );
        }

        // Mark spends for nullifiers on every block (including OMR non-match).
        // Coin AEAD decrypt stays gated on trial_decrypt above.
        if !tx.nullifiers.is_empty() {
            if let Ok(coins) = drk.get_coins(false).await {
                use darkfi_sdk::pasta::group::ff::PrimeField;

                let mut owncoins_nullifiers = std::collections::BTreeMap::new();
                let mut matched_nullifiers = Vec::new();

                for (own, _, _, _, _) in &coins {
                    let nullifier = own.nullifier();
                    let nf_repr = nullifier.inner().to_repr();
                    for nf_bytes in &tx.nullifiers {
                        if nf_bytes.as_slice() == nf_repr.as_ref() {
                            owncoins_nullifiers.insert(
                                nullifier.to_bytes(),
                                (own.coin.to_bytes(), own.leaf_position),
                            );
                            matched_nullifiers.push(nullifier);
                            break;
                        }
                    }
                }

                if !matched_nullifiers.is_empty() {
                    if let Err(e) = drk
                        .mark_spent_coins(
                            Some(&mut tree),
                            &owncoins_nullifiers,
                            &matched_nullifiers,
                            &Some(block.height),
                            &tx_hash,
                        )
                        .await
                    {
                        tracing::warn!(
                            target: "wallet-sync",
                            "mark_spent_coins at height {} failed: {e}",
                            block.height
                        );
                    }
                }
            }
        }
    }

    drk.cache
        .insert_merkle_trees(&[(SLED_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    let _ = drk.cache.sled_db.flush();

    if found > 0 {
        tracing::debug!(
            target: "wallet-sync",
            "Compact-block trial decrypt at height {}: {} OwnCoin(s)",
            block.height,
            found,
        );
    }

    Ok(())
}

async fn fetch_blocks_via_pir(
    client: &crate::lightwallet_client::LightwalletClient,
    wallet_secret: &[u8; 32],
    network: u8,
    window_start: u32,
    window_end: u32,
    matching_heights: &[u32],
) -> Result<Vec<crate::lightwallet_client::LightCompactBlock>, String> {
    use prost::Message;

    let pir = crate::batch_pir::BatchPirClient::from_wallet(wallet_secret, network)?;
    let window_size = (window_end - window_start + 1) as usize;
    let indices: Vec<usize> = matching_heights
        .iter()
        .map(|h| (*h - window_start) as usize)
        .collect();
    for &idx in &indices {
        if idx >= window_size {
            return Err(format!("PIR index {idx} outside window {window_size}"));
        }
    }
    let queries = pir.generate_sealpir_queries(&indices, window_size)?;
    let mut limb_cols: Vec<Vec<u64>> = Vec::new();
    let mut needed_limbs: Option<usize> = None;

    for limb_index in 0..crate::batch_pir::MAX_PIR_LIMBS {
        let resp = client
            .fetch_pir_batch(queries.clone(), window_start, window_end, limb_index as u32)
            .await?;
        let slots = pir.decrypt_sealpir_stripes(&resp, window_size)?;
        let mut full = vec![0u64; window_size];
        for &idx in &indices {
            full[idx] = slots.get(idx).copied().unwrap_or(0);
        }
        limb_cols.push(full);
        if limb_index == 0 {
            let mut max_need = 0usize;
            for &idx in &indices {
                if let Some(n) = crate::batch_pir::pir_payload_limb_count(limb_cols[0][idx]) {
                    max_need = max_need.max(n);
                }
            }
            if max_need == 0 {
                return Err("PIR returned empty payloads for all matching heights".into());
            }
            needed_limbs = Some(max_need);
        }
        if let Some(n) = needed_limbs {
            if limb_cols.len() >= n {
                limb_cols.truncate(n);
                break;
            }
        }
    }

    let payloads = crate::batch_pir::assemble_payloads(&indices, &limb_cols);
    let mut out = Vec::with_capacity(payloads.len());
    for (payload, &height) in payloads.iter().zip(matching_heights.iter()) {
        if payload.is_empty() {
            return Err(format!("PIR returned empty payload for height {height}"));
        }
        let pb =
            crate::lightwallet_client::lightwallet_proto::CompactBlock::decode(payload.as_slice())
                .map_err(|e| format!("PIR protobuf decode at {height}: {e}"))?;
        let block = crate::lightwallet_client::proto_compact_to_light(pb);
        if block.height != height {
            return Err(format!(
                "PIR height mismatch: expected {height}, got {}",
                block.height
            ));
        }
        out.push(block);
    }
    Ok(out)
}

/// Redact potentially sensitive information from sync error messages.
///
/// PRIVACY: Error messages may contain server addresses, port numbers,
/// IP addresses, or internal state that could be used to fingerprint
/// wallets if logged or included in crash reports.
/// Decrypt UnifOMR Round-1 digest(s) and collect matching heights.
///
/// Single-key responses are unframed. Multi-key responses are length-prefixed
/// frames (one digest per detection key), matching lightwalletd.
fn decrypt_unif_omr_heights(
    clients: &[crate::unifomr::UnifOmrClient],
    encrypted_digest: &[u8],
    start: u32,
    end: u32,
) -> Result<Vec<u32>, String> {
    use std::collections::BTreeSet;

    if clients.is_empty() {
        return Err("No UnifOMR clients for digest decrypt".into());
    }
    if clients.len() == 1 {
        let slots = clients[0]
            .decrypt_digest_slots(encrypted_digest)
            .map_err(|e| format!("UnifOMR digest decrypt failed: {e}"))?;
        return crate::unifomr::UnifOmrClient::range_check_matches(&slots, start, end)
            .map_err(|e| e.to_string());
    }

    let mut heights = BTreeSet::new();
    let mut off = 0usize;
    for (i, client_crypto) in clients.iter().enumerate() {
        if off + 4 > encrypted_digest.len() {
            return Err(format!("truncated multi-key UnifOMR digest at key[{i}]"));
        }
        let len = u32::from_le_bytes(encrypted_digest[off..off + 4].try_into().unwrap()) as usize;
        off += 4;
        if off + len > encrypted_digest.len() {
            return Err(format!("truncated UnifOMR digest frame for key[{i}]"));
        }
        let frame = &encrypted_digest[off..off + len];
        off += len;
        let slots = client_crypto
            .decrypt_digest_slots(frame)
            .map_err(|e| format!("UnifOMR digest decrypt failed for key[{i}]: {e}"))?;
        for h in crate::unifomr::UnifOmrClient::range_check_matches(&slots, start, end)? {
            heights.insert(h);
        }
    }
    Ok(heights.into_iter().collect())
}

fn redact_sync_error(error: &str) -> String {
    // Remove IP addresses (IPv4)
    let redacted = regex_lite::Regex::new(r"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?")
        .map(|re| re.replace_all(error, "[redacted-addr]").to_string())
        .unwrap_or_else(|_| error.to_string());

    // Remove hostnames that look like server URLs
    let redacted = regex_lite::Regex::new(r"(https?://|tcp://|tcp\+tls://)[^\s]+")
        .map(|re| re.replace_all(&redacted, "[redacted-url]").to_string())
        .unwrap_or(redacted);

    redacted
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ensure_chain_matches_wallet() {
        use darkfi_sdk::crypto::keypair::Network;
        assert!(ensure_chain_matches_wallet("darkfi-testnet", Network::Testnet).is_ok());
        assert!(ensure_chain_matches_wallet("darkfi-localnet", Network::Testnet).is_ok());
        assert!(ensure_chain_matches_wallet("darkfi-mainnet", Network::Testnet).is_err());
        assert!(ensure_chain_matches_wallet("darkfi-mainnet", Network::Mainnet).is_ok());
        assert!(ensure_chain_matches_wallet("darkfi-testnet", Network::Mainnet).is_err());
    }

    #[test]
    fn test_jittered_sleep_within_bounds() {
        for base in [5u64, 15, 20, 60, 300] {
            for _ in 0..100 {
                let result = jittered_sleep_secs(base);
                let min = ((base as f64) * (1.0 - JITTER_FRACTION)).max(1.0) as u64;
                let max = ((base as f64) * (1.0 + JITTER_FRACTION)) as u64 + 1;
                assert!(
                    result >= min && result <= max,
                    "jittered_sleep_secs({base}) = {result}, expected [{min}, {max}]"
                );
            }
        }
    }

    #[test]
    fn test_jitter_produces_variation() {
        let mut values = std::collections::HashSet::new();
        for _ in 0..50 {
            values.insert(jittered_sleep_secs(15));
        }
        // With 50 samples and ±30% jitter on base 15 (range ~10-20),
        // we should see multiple distinct values
        assert!(
            values.len() > 1,
            "Expected jitter to produce variation, got {} distinct values",
            values.len()
        );
    }

    #[test]
    fn test_jitter_minimum_is_one() {
        // Even for very small bases, result should be at least 1
        for _ in 0..50 {
            assert!(jittered_sleep_secs(1) >= 1);
        }
    }

    #[test]
    fn test_redact_sync_error_removes_ips() {
        let input = "connection failed to 192.168.1.100:9067";
        let output = redact_sync_error(input);
        assert!(
            !output.contains("192.168.1.100"),
            "IP not redacted: {output}"
        );
        assert!(
            output.contains("[redacted-addr]"),
            "Missing redaction marker: {output}"
        );
    }

    #[test]
    fn test_redact_sync_error_removes_urls() {
        let input = "lightwallet server unreachable: http://lw.darkfi.xyz:9067 timeout";
        let output = redact_sync_error(input);
        assert!(
            !output.contains("lw.darkfi.xyz"),
            "URL not redacted: {output}"
        );
        assert!(
            output.contains("[redacted-url]"),
            "Missing redaction marker: {output}"
        );
    }

    #[test]
    fn test_redact_sync_error_preserves_safe_messages() {
        let input = "OMR not yet available on server";
        let output = redact_sync_error(input);
        assert_eq!(output, input, "Safe message was altered");
    }

    #[test]
    fn test_redact_tcp_urls() {
        let input = "failed: tcp://127.0.0.1:8340 error";
        let output = redact_sync_error(input);
        assert!(
            !output.contains("127.0.0.1"),
            "TCP URL not redacted: {output}"
        );
    }

    #[test]
    fn test_redact_tls_urls() {
        let input = "timeout connecting to tcp+tls://lw.example.com:9067";
        let output = redact_sync_error(input);
        assert!(
            !output.contains("lw.example.com"),
            "TLS URL not redacted: {output}"
        );
    }

    // =========================================================================
    // S24 tip completeness
    // =========================================================================

    fn stub_block(height: u32) -> crate::lightwallet_client::LightCompactBlock {
        crate::lightwallet_client::LightCompactBlock {
            height,
            hash: vec![],
            prev_hash: vec![],
            timestamp: 0,
            txs: vec![],
        }
    }

    #[test]
    fn test_assert_contiguous_heights_ok() {
        let blocks = vec![stub_block(10), stub_block(11), stub_block(12)];
        assert!(assert_contiguous_heights(&blocks, 10, 12).is_ok());
    }

    #[test]
    fn test_assert_contiguous_heights_missing() {
        let blocks = vec![stub_block(10), stub_block(12)];
        let err = assert_contiguous_heights(&blocks, 10, 12).unwrap_err();
        assert!(err.contains("Missing compact block at height 11"));
    }

    #[test]
    fn test_assert_contiguous_heights_empty_range() {
        assert!(assert_contiguous_heights(&[], 5, 4).is_ok());
    }

    // =========================================================================
    // Sync loop constants (findings 1.1, 1.2, 1.3)
    // =========================================================================

    #[test]
    fn test_max_consecutive_failures_is_reasonable() {
        assert!(
            MAX_CONSECUTIVE_FAILURES >= 5,
            "Max failures too low — would reset channel too aggressively"
        );
        assert!(
            MAX_CONSECUTIVE_FAILURES <= 50,
            "Max failures too high — could cause very long stalls"
        );
    }

    #[test]
    fn test_sync_cycle_timeout_is_reasonable() {
        assert!(
            MAX_SYNC_CYCLE_SECS >= 60,
            "Cycle timeout too short — normal syncs take minutes"
        );
        assert!(
            MAX_SYNC_CYCLE_SECS <= 1800,
            "Cycle timeout too long — 30min+ stalls are unacceptable"
        );
    }

    #[test]
    fn test_health_probe_constants() {
        assert!(
            HEALTH_PROBE_MAX_RETRIES >= 3,
            "Too few probes — transient network issues need retry"
        );
        assert!(
            HEALTH_PROBE_INTERVAL_SECS >= 5,
            "Probe interval too aggressive"
        );
        assert!(
            HEALTH_PROBE_MAX_RETRIES as u64 * HEALTH_PROBE_INTERVAL_SECS <= 600,
            "Total health probe time exceeds 10 minutes"
        );
    }

    #[test]
    fn test_backoff_with_max_failures_cap() {
        let mut failures = 0u32;
        let mut total_wait_secs = 0u64;

        for _ in 0..MAX_CONSECUTIVE_FAILURES {
            failures += 1;
            let backoff_base =
                std::cmp::min(RETRY_BASE_SECS * (1u64 << failures.min(5)), RETRY_MAX_SECS);
            total_wait_secs += backoff_base;
        }

        // With 15 failures and exponential backoff capped at 300s,
        // total wait before channel reset should be under 2 hours.
        // This ensures the retry strategy doesn't cause extremely
        // long stalls while still giving the server many chances.
        assert!(
            total_wait_secs < 7200,
            "Total backoff before channel reset should be under 2 hours, got {total_wait_secs}s"
        );

        // But also should be meaningful — at least 10 minutes total
        assert!(
            total_wait_secs > 600,
            "Total backoff too short, would reset channel too aggressively: {total_wait_secs}s"
        );
    }
}
