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

use std::sync::{Arc, LazyLock, RwLock};

use darkfi::blockchain::BlockInfo;
use darkfi::system::sleep;
use darkfi_sdk::pasta::group::ff::PrimeField;
use smol::Executor;

use crate::lightwallet_sync::{LightSyncStatus, LightSyncType, SyncEngine};
use crate::DrkPtr;

/// Apply a full darkfid [`BlockInfo`] through official `Drk::scan_block`.
///
/// Compact-block UnifOMR and trial-decrypt cannot use this — they only have
/// stripped `LightCompactBlock`s. Call this only when a full block is already
/// in memory. Do not fetch full blocks from darkfid just to scan them (S9).
pub async fn apply_full_block(drk: &drk::Drk, block: &BlockInfo) -> Result<(), String> {
    let mut scan_cache = drk
        .scan_cache(false)
        .await
        .map_err(|e| format!("scan_cache: {e}"))?;
    drk.scan_block(&mut scan_cache, block)
        .await
        .map_err(|e| format!("scan_block height {}: {e}", block.header.height))
}

/// Base poll interval during active sync (seconds).
const LIGHTWALLET_POLL_BASE_SECS: u64 = 5;

/// Base poll interval when fully synced (seconds).
const LIGHTWALLET_IDLE_BASE_SECS: u64 = 15;

/// Base retry interval after connection failure (seconds). The first retry is
/// `2 * base`; a large base made one dropped circuit cost the user 40s+.
const RETRY_BASE_SECS: u64 = 5;

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
/// Must exceed the 1800s UniFOMR digest RPC timeout.
const MAX_SYNC_CYCLE_SECS: u64 = 2100;

/// Number of startup health probes before entering the sync loop.
/// The wallet retries server connectivity up to this many times before
/// giving up and entering Disconnected state.
const HEALTH_PROBE_MAX_RETRIES: u32 = 5;

/// First wait between health probes (seconds). Doubles per probe, capped at 30s.
const HEALTH_PROBE_INTERVAL_SECS: u64 = 5;

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
    let force_trial = std::env::var("E2E_FORCE_TRIAL").ok().as_deref() == Some("1");
    if force_trial {
        // SyncEngine::new defaults to strict OMR. Forcing OMR off while that
        // flag is set is classified as a downgrade attack and the cycle
        // returns Idle, so the scan cursor never moves and own change — which
        // is not covered by the recipient clue — is never decrypted.
        engine.set_strict_omr_only(false);
    }
    if let Ok(info) = engine
        .lightwallet_client()
        .probe_health(1, std::time::Duration::from_secs(1))
        .await
    {
        if !force_trial {
            engine.set_omr_available(info.omr_supported);
        }
        if info.chain_tip_height > 0 {
            engine.set_chain_tip(info.chain_tip_height);
        }
    }
    if force_trial {
        engine.set_omr_available(false);
    }
    try_lightwallet_sync(&drk, &engine)
        .await
        .map(|_| ())
        .map_err(|e| redact_sync_error(&e))
}

/// Anonymity route the lightwalletd client dials through.
///
/// The SOCKS route is installed before the wallet opens, but the tunnel behind it
/// may still be bootstrapping. The sync thread waits on this gate instead of
/// dialing into an idle listener, so the local wallet opens without blocking on
/// the mixnet and no request ever goes out direct.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum RouteGate {
    Direct,
    Nym,
    Tor,
}

impl RouteGate {
    pub(crate) fn ready(self) -> bool {
        match self {
            RouteGate::Direct => true,
            RouteGate::Nym => crate::is_nym_running(),
            RouteGate::Tor => crate::tor::is_arti_running(),
        }
    }

    /// Bootstrap gave up or the proxy was stopped; only a proxy restart helps.
    fn failed(self) -> bool {
        match self {
            RouteGate::Nym => matches!(crate::nym_status().as_str(), "failed" | "stopped"),
            RouteGate::Tor => crate::tor::arti_failed(),
            RouteGate::Direct => false,
        }
    }

    fn waiting_message(self) -> &'static str {
        match self {
            RouteGate::Direct => "Connecting to lightwalletd…",
            RouteGate::Nym => "Waiting for Nym mixnet…",
            RouteGate::Tor => "Waiting for Tor circuit…",
        }
    }

    fn failed_message(self) -> &'static str {
        match self {
            RouteGate::Nym => "Nym mixnet unavailable — waiting for it to restart",
            _ => "Tor unavailable — waiting for it to restart",
        }
    }
}

/// Block until the route is usable. Returns `false` if sync was stopped first.
async fn wait_for_route(engine: &SyncEngine, route: RouteGate) -> bool {
    let mut shown_failed = None;
    while !engine.is_stopped() {
        if route.ready() {
            return true;
        }
        let failed = route.failed();
        if shown_failed != Some(failed) {
            shown_failed = Some(failed);
            if failed {
                engine.set_status(LightSyncStatus::Disconnected);
                engine.set_status_message(route.failed_message());
            } else {
                engine.set_status(LightSyncStatus::Connecting);
                engine.set_status_message(route.waiting_message());
            }
        }
        smol::Timer::after(std::time::Duration::from_millis(250)).await;
    }
    false
}

pub fn start_background_sync(
    drk: DrkPtr,
    _ex: Arc<Executor<'static>>,
    sync_engine: Arc<SyncEngine>,
    route: RouteGate,
    deferred_seed: Option<crate::bootstrap::LwdSeedPlan>,
) -> std::thread::JoinHandle<()> {
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
                // The stop side lets handle close abort a long probe and
                // drop `drk` so the Fjall cache can be reopened.
                if !wait_for_route(&sync_engine, route).await {
                    checkpoint_wallet(&drk).await;
                    return;
                }
                sync_engine.set_status(LightSyncStatus::Connecting);
                if let Some(plan) = deferred_seed {
                    let guard = drk.write().await;
                    crate::bootstrap::seed_from_lightwalletd(&guard, &plan).await;
                }
                let client = sync_engine.lightwallet_client();
                let probe_result = smol::future::or(
                    async {
                        client
                            .probe_health(
                                HEALTH_PROBE_MAX_RETRIES,
                                std::time::Duration::from_secs(HEALTH_PROBE_INTERVAL_SECS),
                            )
                            .await
                    },
                    async {
                        wait_until_stopped(&sync_engine).await;
                        Err("sync stopped".to_string())
                    },
                )
                .await;
                if sync_engine.is_stopped() {
                    checkpoint_wallet(&drk).await;
                    return;
                }
                match probe_result {
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
                        sync_engine.set_status(LightSyncStatus::Disconnected);
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

                while !sync_engine.is_stopped() {
                    if !wait_for_route(&sync_engine, route).await {
                        break;
                    }
                    // Keep a live connection showing as live between polls; only a
                    // fresh or failed session reads as connecting.
                    if !matches!(
                        sync_engine.status(),
                        LightSyncStatus::Synced | LightSyncStatus::Degraded | LightSyncStatus::Syncing
                    ) {
                        sync_engine.set_status(LightSyncStatus::Connecting);
                    }

                    // Per-cycle timeout guard (finding 1.2): abort stalled
                    // sync cycles so we don't hang forever on a slow server.
                    // The same race observes `request_stop` so close() can
                    // join this thread and release the wallet database.
                    let cycle_result = smol::future::or(
                        async { try_lightwallet_sync(&drk, &sync_engine).await },
                        async {
                            let mut waited = 0u64;
                            loop {
                                if sync_engine.is_stopped() {
                                    return Err("sync stopped".to_string());
                                }
                                if waited >= MAX_SYNC_CYCLE_SECS {
                                    return Err("sync cycle timed out (stall guard)".to_string());
                                }
                                sleep(1).await;
                                waited += 1;
                            }
                        },
                    )
                    .await;

                    if sync_engine.is_stopped() {
                        break;
                    }

                    match cycle_result {
                        Ok(()) if sync_engine.strict_halted() => {
                            sync_engine.set_status(LightSyncStatus::Error);
                            let next_secs = jittered_sleep_secs(LIGHTWALLET_IDLE_BASE_SECS);
                            idle_for_tip_or_timeout(&sync_engine, next_secs).await;
                        }
                        Ok(()) => {
                            consecutive_failures = 0;
                            let base = if sync_engine.is_behind_tip() {
                                LIGHTWALLET_POLL_BASE_SECS
                            } else {
                                LIGHTWALLET_IDLE_BASE_SECS
                            };
                            let next_secs = jittered_sleep_secs(base);
                            idle_for_tip_or_timeout(&sync_engine, next_secs).await;
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

                            sync_engine.set_status(if sync_engine.strict_halted() {
                                LightSyncStatus::Error
                            } else {
                                LightSyncStatus::Retrying
                            });

                            let backoff_base = std::cmp::min(
                                RETRY_BASE_SECS * (1u64 << consecutive_failures.min(5)),
                                RETRY_MAX_SECS,
                            );
                            let retry_secs = jittered_sleep_secs(backoff_base);
                            idle_for_tip_or_timeout(&sync_engine, retry_secs).await;
                        }
                    }
                }
                checkpoint_wallet(&drk).await;
            })
        })
        .expect("spawn darkfi-wallet-sync")
}

async fn checkpoint_wallet(drk: &DrkPtr) {
    let guard = drk.read().await;
    if let Err(e) = guard
        .wallet
        .exec_sql("PRAGMA wal_checkpoint(TRUNCATE)", Vec::new())
        .await
    {
        tracing::warn!(target: "wallet-sync", "wal checkpoint failed: {e}");
    }
}

async fn wait_until_stopped(engine: &SyncEngine) {
    while !engine.is_stopped() {
        sleep(1).await;
    }
}

/// Wait for a new `SubscribeBlocks` tip, or for the jittered poll to elapse.
///
/// A dead stream falls back to one `GetChainTip` and the jittered sleep.
/// Sync stays correct when the subscription is absent.
async fn idle_for_tip_or_timeout(engine: &SyncEngine, secs: u64) {
    enum IdleWake {
        Stopped,
        Timeout,
        Tip(crate::lightwallet_client::ChainTipNotice),
        Dead(String),
    }

    let client = engine.lightwallet_client();
    let snap = engine.snapshot();
    let last_height = snap.chain_tip;
    let last_hash = snap.chain_tip_hash;
    let wait = std::time::Duration::from_secs(secs.max(1));
    let wake = smol::future::or(
        async {
            wait_until_stopped(engine).await;
            IdleWake::Stopped
        },
        async {
            match client.await_tip_change(wait, last_height, &last_hash).await {
                Ok(Some(notice)) => IdleWake::Tip(notice),
                Ok(None) => IdleWake::Timeout,
                Err(e) => IdleWake::Dead(e),
            }
        },
    )
    .await;
    match wake {
        IdleWake::Tip(notice) => {
            let _ = engine.update_chain_tip_hash(notice.height, &notice.hash);
        }
        IdleWake::Dead(e) => {
            tracing::debug!(
                target: "wallet-sync",
                "SubscribeBlocks ended ({}); falling back to GetChainTip",
                redact_sync_error(&e)
            );
            match client.get_chain_tip().await {
                Ok(tip) => {
                    let changed =
                        tip.height != last_height || tip.hash.as_slice() != last_hash.as_slice();
                    let _ = engine.update_chain_tip_hash(tip.height, &tip.hash);
                    if !changed {
                        sleep_or_stop(engine, secs).await;
                    }
                }
                Err(_) => sleep_or_stop(engine, secs).await,
            }
        }
        IdleWake::Stopped | IdleWake::Timeout => {}
    }
}

async fn sleep_or_stop(engine: &SyncEngine, secs: u64) {
    let mut left = secs;
    while left > 0 && !engine.is_stopped() {
        let step = left.min(1);
        sleep(step).await;
        left -= step;
    }
}

/// Build a UnifOMR detection key for this cycle.
///
/// `build_detection_key` uses fresh BFV randomness each call. Caching the
/// ciphertext let lightwalletd link every request in a 24h window (and
/// across Tor circuits) by identical key bytes. Rebuild per request.
fn cached_or_build_detection_key(
    client_crypto: &crate::unifomr::UnifOmrClient,
    _wallet_secret: &[u8; 32],
    network: u8,
) -> Result<Vec<u8>, String> {
    client_crypto.build_detection_key(network)
}

/// Session cache of clue-directory registrations so sync does not re-publish
/// every payment pubkey (and a fresh unix `key_version`) on every cycle.
static CLUE_PK_REGISTERED: std::sync::OnceLock<
    std::sync::Mutex<std::collections::HashSet<[u8; 32]>>,
> = std::sync::OnceLock::new();

fn clue_registration_id(network: u8, pay_pk: &[u8; 32], clue_pk: &[u8]) -> [u8; 32] {
    let mut h = blake3::Hasher::new_derive_key("darkfi-mobile-ffi clue-reg v1");
    h.update(&[network]);
    h.update(pay_pk);
    h.update(clue_pk);
    *h.finalize().as_bytes()
}

fn clue_already_registered(id: &[u8; 32]) -> bool {
    CLUE_PK_REGISTERED
        .get_or_init(|| std::sync::Mutex::new(std::collections::HashSet::new()))
        .lock()
        .map(|s| s.contains(id))
        .unwrap_or(false)
}

fn mark_clue_registered(id: [u8; 32]) {
    if let Ok(mut s) = CLUE_PK_REGISTERED
        .get_or_init(|| std::sync::Mutex::new(std::collections::HashSet::new()))
        .lock()
    {
        s.insert(id);
    }
}

/// Drop cached detection keys (call on wallet close / re-bootstrap).
/// Detection keys are no longer cached across cycles; kept as a no-op for FFI.
pub fn clear_detection_key_cache() {}

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
    let client = sync_engine.lightwallet_client();

    // Network I/O without the wallet write lock so spends are not blocked for
    // the entire GetLightInfo round-trip.
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

    let network = drk.read().await.network;
    ensure_chain_matches_wallet(&server_info.chain_name, network)?;
    sync_engine.ensure_server_chain_identity(&server_info.chain_name)?;

    sync_engine.set_omr_available(server_info.omr_supported);
    if std::env::var("E2E_FORCE_TRIAL").ok().as_deref() == Some("1") {
        sync_engine.set_strict_omr_only(false);
        sync_engine.set_omr_available(false);
    }
    if server_info.proto_version_mismatch {
        tracing::warn!(
            target: "wallet-sync",
            "Server proto_version mismatch: server={}, client={}",
            server_info.proto_version,
            crate::lightwallet_client::CLIENT_PROTO_VERSION
        );
        sync_engine.set_proto_version_mismatch(true);
    } else {
        sync_engine.set_proto_version_mismatch(false);
    }

    // Hash check must see the previous tip height. set_chain_tip() first makes
    // every new block look like a same-height hash change.
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
    } else if server_info.chain_tip_height > 0 {
        sync_engine.set_chain_tip(server_info.chain_tip_height);
    }

    let backend_syncing = sync_engine.is_backend_catching_up(server_info.chain_tip_height);
    if backend_syncing {
        tracing::info!(
            target: "wallet-sync",
            "Backend (darkfid) appears to be catching up (rapid tip advance). \
             OMR failures during catch-up won't count toward degradation.",
        );
    }

    // Write lock covers reorg SQL only. Sync fetches run without it and
    // take the lock again for Merkle appends, coin inserts, and cursor flushes.
    let mut pending_reorg: Option<crate::ReorgEvent> = None;
    {
    let drk_guard = drk.write().await;

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

        // Collect the event; fire the callback after dropping `drk_guard`
        // so Kotlin `onReorg` can re-enter `confirmed_balance` / `list_transactions`.
        pending_reorg = Some(crate::ReorgEvent {
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

    // Step 2: Determine best sync type
    let sync_type = sync_engine.choose_sync_type();
    tracing::info!(
        target: "wallet-sync",
        "Selected sync path: {:?} (strict_omr_only={})",
        sync_type,
        sync_engine.strict_omr_only()
    );

    let sync_result = match sync_type {
        LightSyncType::Omr => {
            sync_engine.set_status(LightSyncStatus::Syncing);

            let result = try_omr_sync(drk, sync_engine, &client).await;

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
                            "OMR failed during backend catch-up (still counted): {}",
                            redact_sync_error(&e),
                        );
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
                        try_trial_decryption_sync(drk, sync_engine, &client).await
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
                Err("strict_omr_only refuses trial decryption".into())
            } else {
                sync_engine.set_status(LightSyncStatus::Syncing);
                try_trial_decryption_sync(drk, sync_engine, &client).await
            }
        }
        LightSyncType::Idle => Ok(()),
    };

    if sync_result.is_ok() {
        let guard = drk.read().await;
        publish_scan_status(sync_engine, &guard);
    }

    // If sync succeeded and the tree and cursor are at the tip, warm ZkAS proving keys.
    if sync_result.is_ok() && sync_engine.status() == LightSyncStatus::Synced {
        let money_id = darkfi_sdk::crypto::contract_id::MONEY_CONTRACT_ID.to_string();
        let _ = crate::zkas_cache::warm_zkas_cache(&client, &[&money_id]).await;
    }

    if let Some(event) = pending_reorg {
        let callback = sync_engine
            .reorg_callback
            .lock()
            .ok()
            .and_then(|g| g.clone());
        if let Some(callback) = callback {
            callback.on_reorg(event);
        }
    }

    sync_result
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

/// Power-of-two window inside the server max range. The wire range is padded
/// again inside the commitment and block fetches.
fn size_omr_window(scan_start: u32, tip: u32, max_range: u32) -> (u32, u32, u32) {
    const MAX_OMR_WINDOW: u32 = 4096;
    let mut window = MAX_OMR_WINDOW;
    loop {
        let scan_end = tip.min(scan_start.saturating_add(window.saturating_sub(1)));
        let (padded_start, padded_end) =
            crate::lightwallet_client::pad_block_range(scan_start, scan_end, tip);
        if padded_end.saturating_sub(padded_start).saturating_add(1) <= max_range || window <= 1 {
            return (scan_end, padded_start, padded_end);
        }
        window /= 2;
    }
}

/// Pool digest when a live handle exists. A miss or expiry builds one fresh
/// detection key, registers it, and retries the pool. `GetUnifOmrDigest` is
/// the fallback for that same key when the pool RPC is missing.
async fn fetch_detection_digest(
    client: &crate::lightwallet_client::LightwalletClient,
    crypto: &crate::unifomr::UnifOmrClient,
    network: u8,
    padded_start: u32,
    padded_end: u32,
) -> Result<(Vec<u8>, Vec<u8>, bool), String> {
    use crate::lightwallet_client::{
        clear_detection_key_pool, fresh_detection_handle, pool_digest_needs_reregister,
        store_detection_handle, unix_now,
    };
    if let Some(handle) = fresh_detection_handle(unix_now()) {
        match client
            .get_unif_omr_digest_from_pool(&handle, padded_start, padded_end)
            .await
        {
            Ok(resp) => return Ok(resp),
            Err(e) if pool_digest_needs_reregister(&e) => clear_detection_key_pool(),
            Err(e) => return Err(e),
        }
    }
    let key = cached_or_build_detection_key(crypto, &[0u8; 32], network)?;
    match client.register_detection_key(key.clone()).await {
        Ok((handle, expires)) => {
            store_detection_handle(handle.clone(), expires);
            match client
                .get_unif_omr_digest_from_pool(&handle, padded_start, padded_end)
                .await
            {
                Ok(resp) => return Ok(resp),
                Err(e) if pool_digest_needs_reregister(&e) => clear_detection_key_pool(),
                Err(e) => return Err(e),
            }
        }
        Err(e) => {
            tracing::warn!(
                target: "wallet-sync",
                "RegisterDetectionKey unavailable ({}); using GetUnifOmrDigest",
                redact_sync_error(&e)
            );
        }
    }
    client
        .get_unif_omr_digest(vec![key], padded_start, padded_end)
        .await
}

/// PIR first (three attempts live in `fetch_pir_batch`). Compact output bodies
/// cover the padded window next. `GetBlockRange` is the last fallback.
async fn load_window_blocks(
    client: &crate::lightwallet_client::LightwalletClient,
    wallet_secret: &[u8; 32],
    network: u8,
    padded_start: u32,
    padded_end: u32,
    matching_heights: &[u32],
    use_pir: bool,
    trial_decrypt_all: bool,
) -> Result<Vec<crate::lightwallet_client::LightCompactBlock>, String> {
    if !trial_decrypt_all && matching_heights.is_empty() {
        return Ok(Vec::new());
    }
    if !trial_decrypt_all && use_pir && !matching_heights.is_empty() {
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
            Ok(blocks) => return Ok(blocks),
            Err(e) => {
                tracing::warn!(
                    target: "wallet-sync",
                    "PIR batch failed after retries; using compact output bodies: {}",
                    redact_sync_error(&e)
                );
            }
        }
    }
    match client
        .get_compact_output_bodies(padded_start, padded_end)
        .await
    {
        Ok(bodies) => {
            let blocks = crate::lightwallet_client::compact_bodies_to_blocks(bodies);
            let valid = blocks
                .iter()
                .all(|block| crate::lightwallet_client::validate_compact_block(block).is_ok());
            if valid {
                return Ok(blocks);
            }
            tracing::warn!(
                target: "wallet-sync",
                "GetCompactOutputBodies failed validation; falling back to GetBlockRange"
            );
        }
        Err(e) => {
            tracing::warn!(
                target: "wallet-sync",
                "GetCompactOutputBodies failed; falling back to GetBlockRange: {}",
                redact_sync_error(&e)
            );
        }
    }
    client
        .get_compact_block_range_exact(padded_start, padded_end)
        .await
}

/// Digest, then padded commitments, nullifiers, and blocks for one window.
///
/// Returns `Ok(None)` when the server truncated the digest before any height
/// in this window. The caller must not start the next window in that case.
async fn prepare_omr_window(
    client: &crate::lightwallet_client::LightwalletClient,
    sync_engine: &SyncEngine,
    scan_start: u32,
    tip: u32,
    birthday: u32,
    max_range: u32,
    secret_bytes: &[u8],
    network: u8,
    trial_decrypt_all: bool,
) -> Result<Option<PreparedOmrWindow>, String> {
    let (mut scan_end, padded_start, padded_end) = size_omr_window(scan_start, tip, max_range);
    sync_engine.set_status_message("UnifOMR scanning 1/2…");
    let client_crypto = crate::unifomr::UnifOmrClient::from_wallet(secret_bytes, network)?;
    let (digest_bytes, slot_heights_bytes, complete) =
        fetch_detection_digest(client, &client_crypto, network, padded_start, padded_end).await?;
    let slot_heights = crate::unifomr::unpack_slot_heights(&slot_heights_bytes)?;
    crate::unifomr::validate_slot_heights(&slot_heights, padded_start, padded_end)?;
    if !complete {
        match clamp_omr_truncated_tip(scan_start, scan_end, slot_heights.iter().max().copied())? {
            Some(covered) => scan_end = covered,
            None => {
                tracing::warn!(
                    target: "wallet-sync",
                    "UnifOMR digest incomplete with no covered heights; \
                     retrying window [{scan_start}, {scan_end}] next cycle"
                );
                sync_engine.set_status_message("UnifOMR server busy; retrying…");
                return Ok(None);
            }
        }
    }
    let mut matching_heights = decrypt_unif_omr_heights(
        std::slice::from_ref(&client_crypto),
        &digest_bytes,
        &slot_heights,
    )?;
    matching_heights.sort_unstable();
    matching_heights.dedup();
    matching_heights.retain(|h| *h >= scan_start && *h <= scan_end);

    sync_engine.set_status_message("UnifOMR fetching 2/2…");
    let (commit_start, commit_end) =
        crate::lightwallet_client::pad_block_range(scan_start, scan_end, tip);
    let prefetched = crate::sync_pipeline::prefetch_omr_window(
        client,
        scan_start,
        scan_end,
        &matching_heights,
        birthday,
        tip,
    )
    .await?;
    let blocks = load_window_blocks(
        client,
        secret_bytes
            .get(..32)
            .and_then(|b| b.try_into().ok())
            .ok_or_else(|| "wallet secret is not 32 bytes".to_string())?,
        network,
        commit_start,
        commit_end,
        &matching_heights,
        true,
        trial_decrypt_all,
    )
    .await?;
    Ok(Some(PreparedOmrWindow {
        scan_start,
        scan_end,
        matching_heights,
        commitment_updates: prefetched.commitment_updates,
        nullifier_updates: prefetched.nullifier_updates,
        blocks,
        birthday,
        trial_decrypt_all,
    }))
}

async fn try_omr_sync(
    drk: &DrkPtr,
    sync_engine: &SyncEngine,
    client: &crate::lightwallet_client::LightwalletClient,
) -> Result<(), String> {
    // Copy wallet secrets before any gRPC. The read guard must not span the
    // digest, commitment, or PIR calls.
    let (mut scanned, secret_bytes, mut recipient_pubkeys, addr_rows, omr_network) = {
        let wallet = drk.read().await;
        let (scanned, _) = wallet.get_last_scanned_block().map_err(|e| e.to_string())?;
        let secret = wallet
            .default_secret()
            .await
            .map_err(|e| format!("Failed to get wallet secret: {e}"))?;
        let secret_bytes = zeroize::Zeroizing::new(secret.inner().to_repr());
        let mut recipient_pubkeys: Vec<[u8; 32]> = Vec::new();
        let default_pk = wallet
            .default_address()
            .await
            .map_err(|e| format!("Failed to get wallet address: {e}"))?
            .to_bytes();
        recipient_pubkeys.push(default_pk);
        let addr_rows = wallet.addresses().await.unwrap_or_default();
        for (_, pubkey, _, _) in &addr_rows {
            let pk = pubkey.to_bytes();
            if !recipient_pubkeys.iter().any(|existing| existing == &pk) {
                recipient_pubkeys.push(pk);
            }
        }
        let omr_network = match wallet.network {
            darkfi_sdk::crypto::keypair::Network::Mainnet => crate::omr::OmrNetwork::Mainnet,
            darkfi_sdk::crypto::keypair::Network::Testnet => crate::omr::OmrNetwork::Testnet,
        };
        (scanned, secret_bytes, recipient_pubkeys, addr_rows, omr_network)
    };
    sync_engine.set_scanned_height(scanned);

    let tip = sync_engine.chain_tip();

    if scanned >= tip {
        ensure_merkle_caught_up(drk, client, scanned, tip.saturating_add(1), tip).await?;
        {
            let wallet = drk.read().await;
            let (now, _) = wallet.get_last_scanned_block().map_err(|e| e.to_string())?;
            scanned = now;
        }
        sync_engine.set_scanned_height(scanned);
        return Ok(());
    }

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

    if recipient_pubkeys.len() > crate::omr::MAX_OMR_DETECT_PUBKEYS {
        tracing::warn!(
            target: "wallet-sync",
            "Wallet has {} addresses; OMR queries only the first {} (default-first)",
            recipient_pubkeys.len(),
            crate::omr::MAX_OMR_DETECT_PUBKEYS
        );
        recipient_pubkeys.truncate(crate::omr::MAX_OMR_DETECT_PUBKEYS);
    }

    // PRIVACY: Pad the block range for OMR request to hide exact birthday / scan position.
    // Cap each cycle's window so power-of-2 padding stays within LWD max_range (10_000).
    let max_range = if caps.max_range_per_request == 0 {
        10_000
    } else {
        caps.max_range_per_request
    };
    let birthday = sync_engine.birthday_height();
    let scan_start = crate::birthday::clamp_scan_start(scanned, birthday);
    ensure_merkle_caught_up(drk, client, scanned, scan_start, tip).await?;
    {
        let wallet = drk.read().await;
        let (now, _) = wallet.get_last_scanned_block().map_err(|e| e.to_string())?;
        scanned = now;
    }
    sync_engine.set_scanned_height(scanned);
    let scan_start = crate::birthday::clamp_scan_start(scanned, birthday);
    if scan_start > tip {
        tracing::debug!(
            target: "wallet-sync",
            "scan_start {scan_start} > tip {tip}; already caught up to tip"
        );
        return Ok(());
    }
    // Register UnifOMR clue PK for every wallet payment address (same wallet-level
    // sk_clue). Senders look up by payment pubkey — default-only registration missed
    // secondary addresses. Each registration is Schnorr-signed under the payment key.
    if scheme.contains("unifomr") {
        if let Ok((_sk, pk)) =
            crate::unifomr::clue_keypair_from_wallet(&secret_bytes[..], omr_network.to_byte())
        {
            let clue_pk = crate::unifomr::serialize_public_key(&pk);
            let key_version = crate::unifomr::clue_key_version_now();
            let mut registered = 0usize;
            let mut last_err: Option<String> = None;
            for pay_pk in &recipient_pubkeys {
                let reg_id = clue_registration_id(omr_network.to_byte(), pay_pk, &clue_pk);
                if clue_already_registered(&reg_id) {
                    registered += 1;
                    continue;
                }
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
                    Ok(()) => {
                        mark_clue_registered(reg_id);
                        registered += 1;
                    }
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

    if !scheme.contains("unifomr") {
        return Err(format!("Unsupported OMR scheme from server: {scheme}"));
    }
    let trial_decrypt_all = !sync_engine.strict_omr_only();
    if trial_decrypt_all {
        tracing::warn!(
            target: "wallet-sync",
            "Supplemental trial decrypt inside UnifOMR windows starting at {scan_start} \
             (single Merkle append; does not reveal the match set)"
        );
        sync_engine.set_status(LightSyncStatus::Degraded);
        sync_engine.set_status_message(
            "Trial-decrypting the UnifOMR window for wallets that do not attach clues. \
             Enable strict OMR-only to keep the sparse PIR fetch.",
        );
    }

    let mut reorder = crate::sync_pipeline::PipelineReorderBuffer::new(scan_start);
    let mut next_window: Option<PreparedOmrWindow> = None;
    let mut cursor = scan_start;
    loop {
        if cursor > tip {
            break;
        }
        sync_engine.set_pipeline_progress(cursor, tip);
        let window = if let Some(window) = next_window.take() {
            window
        } else {
            match prepare_omr_window(
                client,
                sync_engine,
                cursor,
                tip,
                birthday,
                max_range,
                &secret_bytes[..],
                omr_network.to_byte(),
                trial_decrypt_all,
            )
            .await?
            {
                None => return Ok(()),
                Some(window) => window,
            }
        };
        let stub = crate::sync_pipeline::OmrWindowResult {
            scan_start: window.scan_start,
            scan_end: window.scan_end,
            matching_heights: window.matching_heights.clone(),
            commitment_updates: Vec::new(),
            nullifier_updates: Vec::new(),
            sparse_blocks: Vec::new(),
            omr_used: !window.matching_heights.is_empty(),
        };
        let ready = reorder.insert(stub);
        if ready.is_empty() {
            return Err(format!(
                "OMR window {} is ahead of pipeline cursor {}",
                window.scan_start,
                reorder.next_expected()
            ));
        }
        let next_cursor = window.scan_end.saturating_add(1);
        if next_cursor <= tip {
            let apply_fut = async {
                let wallet = drk.write().await;
                apply_omr_sparse_window(&wallet, &window).await?;
                sync_engine.set_scanned_height(window.scan_end);
                Ok::<(), String>(())
            };
            let prefetch_fut = prepare_omr_window(
                client,
                sync_engine,
                next_cursor,
                tip,
                birthday,
                max_range,
                &secret_bytes[..],
                omr_network.to_byte(),
                trial_decrypt_all,
            );
            let (applied, prefetched) = futures::future::join(apply_fut, prefetch_fut).await;
            applied?;
            match prefetched? {
                None => return Ok(()),
                Some(window) => next_window = Some(window),
            }
        } else {
            let wallet = drk.write().await;
            apply_omr_sparse_window(&wallet, &window).await?;
            sync_engine.set_scanned_height(window.scan_end);
        }
        cursor = next_cursor;
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
async fn fetch_trial_blocks(
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
    tip: u32,
) -> Result<Vec<crate::lightwallet_client::LightCompactBlock>, String> {
    let (padded_start, padded_end) = crate::lightwallet_client::pad_block_range(start, end, tip);
    match client.get_compact_output_bodies(padded_start, padded_end).await {
        Ok(bodies) => {
            let mut blocks = crate::lightwallet_client::compact_bodies_to_blocks(bodies);
            blocks.retain(|block| block.height >= start && block.height <= end);
            blocks.sort_by_key(|block| block.height);
            let valid = blocks
                .iter()
                .all(|block| crate::lightwallet_client::validate_compact_block(block).is_ok());
            let complete = (start..=end).all(|height| blocks.iter().any(|block| block.height == height));
            if valid && complete {
                return Ok(blocks);
            }
            tracing::warn!(
                target: "wallet-sync",
                "GetCompactOutputBodies did not cover {start}..={end}; using GetBlockRange"
            );
        }
        Err(e) => {
            tracing::warn!(
                target: "wallet-sync",
                "GetCompactOutputBodies failed; using GetBlockRange: {}",
                redact_sync_error(&e)
            );
        }
    }
    client.get_compact_block_range(start, end, tip).await
}

async fn trial_decrypt_range(
    drk: &DrkPtr,
    client: &crate::lightwallet_client::LightwalletClient,
    start: u32,
    end: u32,
    tip: u32,
) -> Result<Option<u32>, String> {
    if start > end {
        return Ok(None);
    }
    tracing::debug!(
        target: "wallet-sync",
        "Trial decrypt fallback: scanning blocks {}..={} ({} blocks)",
        start, end, end - start + 1
    );

    // Process in batches to avoid OOM on large ranges.
    // Returns the last contiguous height actually present, starting at `start`.
    // A hole stops the scan so the caller does not mark missing blocks as done.
    const BATCH_SIZE: u32 = 500;
    let mut next = start;
    let mut batch_start = start;
    while batch_start <= end {
        let batch_end = (batch_start + BATCH_SIZE - 1).min(end);
        let mut blocks = fetch_trial_blocks(client, batch_start, batch_end, tip)
            .await
            .map_err(|e| {
                format!("trial_decrypt_range: blocks({batch_start}..={batch_end}): {e}")
            })?;
        let wallet = drk.write().await;
        blocks.sort_by_key(|b| b.height);
        crate::lightwallet_client::validate_compact_block_batch(&blocks)?;
        let mut prev: Option<(u32, Vec<u8>)> = None;
        for block in &blocks {
            if block.height < next {
                continue;
            }
            if block.height > next {
                return Ok(if next == start { None } else { Some(next - 1) });
            }
            if let Some((h, hash)) = &prev {
                crate::lightwallet_client::validate_block_follows_parent(block, *h, hash)?;
            }
            prev = Some((block.height, block.hash.clone()));
            if let Err(e) = trial_decrypt_compact_block(&wallet, block).await {
                tracing::warn!(
                    target: "wallet-sync",
                    "Trial decrypt failed for block {}: {e}",
                    block.height
                );
            }
            next = next.saturating_add(1);
        }
        if next <= batch_end {
            return Ok(if next == start { None } else { Some(next - 1) });
        }
        batch_start = batch_end + 1;
    }
    Ok(if next == start { None } else { Some(next - 1) })
}

/// One OMR window after the network reads have finished.
struct PreparedOmrWindow {
    scan_start: u32,
    scan_end: u32,
    matching_heights: Vec<u32>,
    commitment_updates: Vec<(u32, Vec<Vec<u8>>)>,
    nullifier_updates: Vec<(u32, Vec<Vec<u8>>)>,
    blocks: Vec<crate::lightwallet_client::LightCompactBlock>,
    birthday: u32,
    trial_decrypt_all: bool,
}

const COIN_INSERT_BATCH: usize = 256;
const TREE_FLUSH_HEIGHTS: u32 = 256;

/// Sparse OMR window apply. Callers fetch commitments, nullifiers, and blocks
/// before taking the wallet write lock.
async fn apply_omr_sparse_window(
    drk: &drk::Drk,
    window: &PreparedOmrWindow,
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
        KVDB_MERKLE_TREES_MONEY, MONEY_COINS_COL_COIN, MONEY_COINS_COL_COIN_BLIND,
        MONEY_COINS_COL_CREATION_HEIGHT, MONEY_COINS_COL_IS_SPENT, MONEY_COINS_COL_LEAF_POSITION,
        MONEY_COINS_COL_MEMO, MONEY_COINS_COL_SECRET, MONEY_COINS_COL_SPEND_HOOK,
        MONEY_COINS_COL_SPENT_HEIGHT, MONEY_COINS_COL_TOKEN_BLIND, MONEY_COINS_COL_TOKEN_ID,
        MONEY_COINS_COL_USER_DATA, MONEY_COINS_COL_VALUE, MONEY_COINS_COL_VALUE_BLIND,
        MONEY_COINS_TABLE,
    };

    let scan_start = window.scan_start;
    let tip = window.scan_end;
    let matching_heights = &window.matching_heights;
    let birthday = window.birthday;
    let trial_decrypt_all = window.trial_decrypt_all;
    let matching_set: HashSet<u32> = matching_heights.iter().copied().collect();
    // Leaves at or below this height are already in the installed tree
    // (checkpoint snapshot or a previous flush). Do not append them again.
    let append_after = merkle_tree_height(drk);

    let mut coins_by_height: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
    for (height, coins) in &window.commitment_updates {
        if crate::sync_pipeline::in_scan_window(*height, scan_start, tip) {
            coins_by_height.entry(*height).or_default().extend(coins.clone());
        }
    }

    let mut nfs_by_height: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
    for (height, nullifiers) in &window.nullifier_updates {
        if crate::sync_pipeline::in_scan_window(*height, scan_start, tip) {
            nfs_by_height
                .entry(*height)
                .or_default()
                .extend(nullifiers.clone());
        }
    }

    let mut blocks_by_height: HashMap<u32, crate::lightwallet_client::LightCompactBlock> =
        HashMap::new();
    for block in &window.blocks {
        if !crate::sync_pipeline::in_scan_window(block.height, scan_start, tip) {
            continue;
        }
        if let Err(e) = crate::lightwallet_client::validate_compact_block(block) {
            return Err(format!(
                "Malformed compact block at height {}: {e}",
                block.height
            ));
        }
        blocks_by_height.insert(block.height, block.clone());
    }
    if !trial_decrypt_all {
        for h in matching_heights {
            let has_coins = coins_by_height
                .get(h)
                .map(|coins| !coins.is_empty())
                .unwrap_or(false);
            if has_coins && !blocks_by_height.contains_key(h) {
                return Err(format!(
                    "Missing compact block at matching height {h}; tip not advanced"
                ));
            }
        }
    }
    crate::lightwallet_client::validate_compact_block_batch(
        &blocks_by_height.values().cloned().collect::<Vec<_>>(),
    )?;

    let secrets = drk
        .get_money_secrets()
        .await
        .map_err(|e| format!("Failed to load wallet secrets: {e}"))?;
    if (trial_decrypt_all || !matching_heights.is_empty()) && secrets.is_empty() {
        return Err("No wallet secrets available for trial decryption".into());
    }

    let mut tree = drk
        .get_money_tree()
        .await
        .map_err(|e| format!("Failed to load Money Merkle tree: {e}"))?;

    // Coins recorded at broadcast (own change / fee) have a placeholder leaf.
    // Strict UnifOMR does not trial-decrypt those heights, and INSERT OR IGNORE
    // will not repair the placeholder. Update the leaf whenever this walk
    // appends a commitment the wallet already holds.
    let mut known_own: HashMap<Vec<u8>, Vec<u8>> = HashMap::new();
    if let Ok(wallet_coins) = drk.get_coins(true).await {
        for (oc, _, _, _, _) in &wallet_coins {
            known_own.insert(
                oc.coin.inner().to_repr().to_vec(),
                oc.coin.to_bytes().to_vec(),
            );
        }
    }

    let mut found = 0u32;
    let wallet_unspent = drk.get_coins(false).await.unwrap_or_default();
    let mut unspent_by_nf = HashMap::new();
    for (own, _, _, _, _) in wallet_unspent {
        let nf = own.nullifier();
        unspent_by_nf.insert(
            nf.to_bytes().to_vec(),
            (own.coin.to_bytes(), own.leaf_position, nf.to_bytes()),
        );
    }
    let insert_sql = format!(
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
    let mut pending_inserts: Vec<Vec<drk::walletdb::Value>> = Vec::new();
    let mut heights_since_flush = 0u32;

    // Walk heights in order: append commitments, decrypt matches, apply nullifiers.
    let mut prev_compact: Option<(u32, Vec<u8>)> = None;
    for height in scan_start..=tip {
        let coins = coins_by_height.get(&height).cloned().unwrap_or_default();
        let match_block = blocks_by_height.get(&height);
        if let Some(block) = match_block {
            if let Some((ph, phash)) = &prev_compact {
                crate::lightwallet_client::validate_block_follows_parent(block, *ph, phash)?;
            }
            prev_compact = Some((block.height, block.hash.clone()));
        }
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

        let append_height = match append_after {
            Some(floor) => height > floor,
            None => true,
        };
        for coin_bytes in &coins {
            if !append_height {
                break;
            }
            if coin_bytes.len() != 32 {
                continue;
            }
            let mut repr = [0u8; 32];
            repr.copy_from_slice(coin_bytes);
            let Some(base) = Option::<pallas::Base>::from(pallas::Base::from_repr(repr)) else {
                return Err(format!(
                    "Invalid note commitment at height {height}: not a canonical pallas::Base"
                ));
            };
            let coin = Coin::from(base);
            tree.append(MerkleNode::from(coin.inner()));

            let mut marked_leaf = None;
            if let Some(sql_key) = known_own.get(coin_bytes.as_slice()) {
                if let Some(pos) = tree.mark() {
                    marked_leaf = Some(pos);
                    let query = format!(
                        "UPDATE {} SET {} = ?1 WHERE {} = ?2;",
                        *MONEY_COINS_TABLE, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_COL_COIN,
                    );
                    if let Err(e) = drk
                        .wallet
                        .exec_sql(
                            &query,
                            vec![
                                drk::walletdb::Value::from(serialize(&pos)),
                                drk::walletdb::Value::from(sql_key.clone()),
                            ],
                        )
                        .await
                    {
                        tracing::warn!(
                            target: "wallet-sync",
                            "leaf_position update for known coin at height {height}: {e}"
                        );
                    }
                }
            }

            // Birthday enforcement: never trial-decrypt notes below birthday height.
            // Supplemental path decrypts every height in the window (single append).
            let decrypt_height =
                height >= birthday && (trial_decrypt_all || matching_set.contains(&height));
            if !decrypt_height {
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

            let leaf_position = marked_leaf.or_else(|| tree.mark()).unwrap();
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

            let spent_height: Option<i64> = None;
            let coin_bytes_arr = coin.to_bytes();
            let params = vec![
                drk::walletdb::Value::from(coin_bytes_arr.to_vec()),
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
            let owned = darkfi_money_contract::client::OwnCoin {
                coin,
                note: note.clone(),
                secret,
                leaf_position,
            };
            let nf = owned.nullifier();
            unspent_by_nf.insert(
                nf.to_bytes().to_vec(),
                (owned.coin.to_bytes(), owned.leaf_position, nf.to_bytes()),
            );
            pending_inserts.push(params);
            if pending_inserts.len() >= COIN_INSERT_BATCH {
                commit_coin_inserts(drk, &insert_sql, &mut pending_inserts).await?;
            }
        }

        // Apply nullifiers for this height against the one map built for the window.
        if let Some(nullifiers) = nfs_by_height.get(&height) {
            if !nullifiers.is_empty() {
                let mut owncoins_nullifiers = BTreeMap::new();
                let mut matched_nullifiers = Vec::new();
                let mut spent_keys = Vec::new();
                for nf_bytes in nullifiers {
                    if let Some((coin_bytes, leaf, nf_key)) =
                        nullifier_map_lookup(&unspent_by_nf, nf_bytes)
                    {
                        if let Ok(nullifier) =
                            darkfi_money_contract::model::Nullifier::from_bytes(*nf_key)
                        {
                            owncoins_nullifiers.insert(*nf_key, (*coin_bytes, *leaf));
                            matched_nullifiers.push(nullifier);
                            spent_keys.push(nf_bytes.clone());
                        }
                    }
                }
                if !matched_nullifiers.is_empty() {
                    commit_coin_inserts(drk, &insert_sql, &mut pending_inserts).await?;
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
                    } else {
                        for key in spent_keys {
                            unspent_by_nf.remove(&key);
                        }
                    }
                }
            }
        }
        heights_since_flush = heights_since_flush.saturating_add(1);
        if heights_since_flush >= TREE_FLUSH_HEIGHTS {
            commit_coin_inserts(drk, &insert_sql, &mut pending_inserts).await?;
            flush_tree_and_cursor(drk, &tree, height)?;
            heights_since_flush = 0;
        }
    }
    commit_coin_inserts(drk, &insert_sql, &mut pending_inserts).await?;

    drk.cache
        .insert_merkle_trees(&[(KVDB_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    // Persist the scan cursor in the same flush so a later supplemental
    // failure cannot leave the tree ahead of the cursor.
    persist_scanned_height(drk, tip)?;
    set_merkle_tree_height(drk, tip)?;
    if scan_start == 0 {
        set_merkle_from_genesis(drk, true)?;
    }

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
fn publish_scan_status(sync_engine: &SyncEngine, drk: &drk::Drk) {
    let tip = sync_engine.chain_tip();
    let scanned = sync_engine.scanned_height();
    let ready = merkle_from_genesis(drk) && scanned >= tip;
    if !ready {
        sync_engine.set_status(LightSyncStatus::Scanning);
    } else if sync_engine.status() != LightSyncStatus::Degraded
        && sync_engine.status() != LightSyncStatus::Error
    {
        sync_engine.set_status(LightSyncStatus::Synced);
    }
}

async fn try_trial_decryption_sync(
    drk: &DrkPtr,
    sync_engine: &SyncEngine,
    client: &crate::lightwallet_client::LightwalletClient,
) -> Result<(), String> {
    if sync_engine.strict_omr_only() {
        return Err("strict_omr_only refuses trial decryption".into());
    }
    let (scanned, _) = {
        let wallet = drk.read().await;
        wallet.get_last_scanned_block().map_err(|e| e.to_string())?
    };
    sync_engine.set_scanned_height(scanned);

    let tip = sync_engine.chain_tip();

    if scanned >= tip {
        ensure_merkle_caught_up(drk, client, scanned, tip.saturating_add(1), tip).await?;
        return Ok(());
    }

    // Chunk the range — requesting the entire tip in one RPC often breaks the
    // gRPC stream ("connection lost") on long testnets.
    const MAX_TRIAL_WINDOW: u32 = 500;
    let birthday = sync_engine.birthday_height();
    let td_start = crate::birthday::clamp_scan_start(scanned, birthday);
    ensure_merkle_caught_up(drk, client, scanned, td_start, tip).await?;
    let window_end = tip.min(scanned.saturating_add(MAX_TRIAL_WINDOW));
    sync_engine.set_status_message(&format!(
        "Trial decrypt {}–{} / tip {}",
        td_start, window_end, tip
    ));

    if td_start <= window_end {
        // Advance only through heights the server actually returned. Persisting
        // `window_end` when the stream has a hole skips the block that holds
        // our own change and never trial-decrypts it.
        let reached = trial_decrypt_range(drk, client, td_start, window_end, tip).await?;
        let wallet = drk.write().await;
        if td_start == 0 {
            set_merkle_from_genesis(&wallet, true)?;
        }
        if let Some(last) = reached {
            persist_scanned_height(&wallet, last)?;
            set_merkle_tree_height(&wallet, last)?;
            sync_engine.set_scanned_height(last);
            if last < tip {
                sync_engine.set_status(LightSyncStatus::Syncing);
            }
        } else {
            sync_engine.set_status(LightSyncStatus::Syncing);
        }
    } else {
        let wallet = drk.write().await;
        persist_scanned_height(&wallet, window_end)?;
        sync_engine.set_scanned_height(window_end);
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
const PAYMENT_MEMOS_TREE: &str = "payment_memos";
const PAYMENT_RECIPIENTS_TREE: &str = "payment_recipients";

static PAYMENT_META_KEY: LazyLock<RwLock<Option<[u8; 32]>>> = LazyLock::new(|| RwLock::new(None));

/// Install the XChaCha20-Poly1305 key used for payment memo/recipient blobs.
pub(crate) fn set_payment_meta_key(key: [u8; 32]) {
    if let Ok(mut slot) = PAYMENT_META_KEY.write() {
        *slot = Some(key);
    }
}

fn payment_meta_key() -> Option<[u8; 32]> {
    PAYMENT_META_KEY.read().ok().and_then(|g| *g)
}

fn wrap_payment_meta(plaintext: &[u8]) -> Result<Vec<u8>, String> {
    crate::block_cache::wrap_cache_blob(payment_meta_key().as_ref(), plaintext)
}

fn unwrap_payment_meta(data: &[u8]) -> Result<Vec<u8>, String> {
    crate::block_cache::unwrap_cache_blob(payment_meta_key().as_ref(), data)
}
/// Cache flag: Money Merkle tree includes height-0 coins after the dummy ZERO leaf.
const WALLET_MERKLE_META_TREE: &str = "wallet_merkle_meta";
const KEY_MERKLE_FROM_GENESIS: &[u8] = b"merkle_from_genesis";

/// True when this wallet's Money tree was built from genesis (not dummy+post-birthday).
pub(crate) fn merkle_from_genesis(drk: &drk::Drk) -> bool {
    let Ok(tree) = drk.cache.kvdb.open_tree_default(WALLET_MERKLE_META_TREE) else {
        return false;
    };
    matches!(tree.get(KEY_MERKLE_FROM_GENESIS), Ok(Some(v)) if v.as_slice() == b"1")
}

const KEY_MERKLE_TREE_HEIGHT: &[u8] = b"merkle_tree_height";

/// Highest block height whose note commitments are already in the Money tree.
///
/// `None` means this wallet has not recorded a tree position (older wallets,
/// or a tree that was wiped). A snapshot install and every cursor flush set it.
pub(crate) fn merkle_tree_height(drk: &drk::Drk) -> Option<u32> {
    let tree = drk.cache.kvdb.open_tree_default(WALLET_MERKLE_META_TREE).ok()?;
    let raw = tree.get(KEY_MERKLE_TREE_HEIGHT).ok()??;
    let bytes: [u8; 4] = raw.as_slice().try_into().ok()?;
    Some(u32::from_le_bytes(bytes))
}

pub(crate) fn set_merkle_tree_height(drk: &drk::Drk, height: u32) -> Result<(), String> {
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(WALLET_MERKLE_META_TREE)
        .map_err(|e| format!("open merkle meta: {e}"))?;
    tree.insert(KEY_MERKLE_TREE_HEIGHT, &height.to_le_bytes())
        .map_err(|e| format!("set merkle_tree_height: {e}"))?;
    let _ = drk.cache.kvdb.flush_default_mode();
    Ok(())
}

pub(crate) fn clear_merkle_tree_height(drk: &drk::Drk) -> Result<(), String> {
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(WALLET_MERKLE_META_TREE)
        .map_err(|e| format!("open merkle meta: {e}"))?;
    let _ = tree.remove(KEY_MERKLE_TREE_HEIGHT);
    let _ = drk.cache.kvdb.flush_default_mode();
    Ok(())
}

/// Commitments still missing between an installed snapshot and note discovery.
///
/// `scan_start` is the first height note discovery will visit. The snapshot
/// already contains `tree_height`, so the suffix is `(tree_height, scan_start)`.
pub(crate) fn commitment_suffix_after_snapshot(
    tree_height: u32,
    scan_start: u32,
) -> Option<(u32, u32)> {
    if scan_start == 0 {
        return None;
    }
    let start = tree_height.saturating_add(1);
    let end = scan_start.saturating_sub(1);
    if start > end { None } else { Some((start, end)) }
}

/// One unspent-nullifier lookup. Heights outside the scan window never enter the map.
pub(crate) fn nullifier_map_lookup<'a, T>(
    map: &'a std::collections::HashMap<Vec<u8>, T>,
    nullifier: &[u8],
) -> Option<&'a T> {
    map.get(nullifier)
}

async fn commit_coin_inserts(
    drk: &drk::Drk,
    sql: &str,
    rows: &mut Vec<Vec<drk::walletdb::Value>>,
) -> Result<(), String> {
    if rows.is_empty() {
        return Ok(());
    }
    let began = drk.wallet.exec_sql("BEGIN IMMEDIATE", vec![]).await.is_ok();
    for row in rows.drain(..) {
        if let Err(e) = drk.wallet.exec_sql(sql, row).await {
            if began {
                let _ = drk.wallet.exec_sql("ROLLBACK", vec![]).await;
            }
            return Err(format!("batch coin insert: {e}"));
        }
    }
    if began {
        drk.wallet
            .exec_sql("COMMIT", vec![])
            .await
            .map_err(|e| format!("commit coins: {e}"))?;
    }
    Ok(())
}

fn flush_tree_and_cursor(
    drk: &drk::Drk,
    tree: &darkfi_sdk::crypto::MerkleTree,
    height: u32,
) -> Result<(), String> {
    use drk::money::KVDB_MERKLE_TREES_MONEY;
    drk.cache
        .insert_merkle_trees(&[(KVDB_MERKLE_TREES_MONEY, tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    persist_scanned_height(drk, height)?;
    set_merkle_tree_height(drk, height)?;
    Ok(())
}

pub(crate) fn set_merkle_from_genesis(drk: &drk::Drk, complete: bool) -> Result<(), String> {
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(WALLET_MERKLE_META_TREE)
        .map_err(|e| format!("open merkle meta: {e}"))?;
    if complete {
        tree.insert(KEY_MERKLE_FROM_GENESIS, b"1")
            .map_err(|e| format!("set merkle_from_genesis: {e}"))?;
    } else {
        let _ = tree.remove(KEY_MERKLE_FROM_GENESIS);
    }
    let _ = drk.cache.kvdb.flush_default_mode();
    Ok(())
}

/// If the Money tree is not known to include height 0, either rebuild through
/// `tip` (already caught up) or replay `0..=scan_start-1` before the scan window.
///
/// A snapshot sets `merkle_from_genesis` without covering a later birthday.
/// That gap is appended here with decryption skipped. It is not a replay from 0.
async fn ensure_merkle_caught_up(
    drk: &DrkPtr,
    client: &crate::lightwallet_client::LightwalletClient,
    scanned: u32,
    scan_start: u32,
    tip: u32,
) -> Result<(), String> {
    let (from_genesis, tree_height) = {
        let guard = drk.read().await;
        (merkle_from_genesis(&guard), merkle_tree_height(&guard))
    };
    if from_genesis {
        if let Some(tree_height) = tree_height {
            if let Some((start, end)) = commitment_suffix_after_snapshot(tree_height, scan_start) {
                tracing::info!(
                    target: "wallet-sync",
                    "Appending snapshot suffix {start}..={end} before note discovery at {scan_start}"
                );
                let updates = crate::sync_pipeline::fetch_padded_note_commitments(
                    client, start, end, tip.max(end),
                )
                .await?;
                let guard = drk.write().await;
                append_commitment_suffix(&guard, &updates, start, end).await?;
                let (now, _) = guard.get_last_scanned_block().map_err(|e| e.to_string())?;
                if now < end {
                    persist_scanned_height(&guard, end)?;
                }
            }
        }
        return Ok(());
    }
    if scanned >= tip || scan_start > tip {
        if tip == 0 {
            return Ok(());
        }
        tracing::warn!(
            target: "wallet-sync",
            "Money Merkle tree is missing genesis leaves at tip {tip}; rebuilding 0..={tip}"
        );
        return rebuild_money_tree_from_ptr(drk, client, tip).await;
    }
    let Some((_, end)) = crate::birthday::pre_birthday_commitment_range(scan_start) else {
        return Ok(());
    };
    tracing::info!(
        target: "wallet-sync",
        "Backfilling Money Merkle tree 0..={end} before scan_start {scan_start}"
    );
    rebuild_money_tree_from_ptr(drk, client, end).await
}

/// Append `[start, end]` onto the tree already installed by a snapshot.
/// Decryption is intentionally skipped; note discovery begins at `scan_start`.
async fn append_commitment_suffix(
    drk: &drk::Drk,
    updates: &[(u32, Vec<Vec<u8>>)],
    start: u32,
    end: u32,
) -> Result<(), String> {
    use std::collections::BTreeMap;

    use drk::money::KVDB_MERKLE_TREES_MONEY;

    let mut by_h: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
    for (height, coins) in updates {
        if crate::sync_pipeline::in_scan_window(*height, start, end) {
            by_h.entry(*height).or_default().extend(coins.clone());
        }
    }
    let mut tree = drk
        .get_money_tree()
        .await
        .map_err(|e| format!("Failed to load Money Merkle tree: {e}"))?;
    let floor = merkle_tree_height(drk);
    for height in start..=end {
        if let Some(floor) = floor {
            if height <= floor {
                continue;
            }
        }
        for coin_bytes in by_h.get(&height).map(|v| v.as_slice()).unwrap_or(&[]) {
            let Some(node) = merkle_node_from_coin_bytes(coin_bytes) else {
                continue;
            };
            tree.append(node);
        }
    }
    drk.cache
        .insert_merkle_trees(&[(KVDB_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    set_merkle_tree_height(drk, end)?;
    Ok(())
}

fn persist_received_memo_from_tx_hash(drk: &drk::Drk, tx_hash: &[u8], memo_bytes: &[u8]) {
    if memo_bytes.is_empty() || tx_hash.len() != 32 {
        return;
    }
    let b58 = bs58::encode(tx_hash).into_string();
    let hex: String = tx_hash.iter().map(|b| format!("{b:02x}")).collect();
    if let Ok(tree) = drk.cache.kvdb.open_tree_default(PAYMENT_MEMOS_TREE) {
        if let Ok(stored) = wrap_payment_meta(memo_bytes) {
            let _ = tree.insert(b58.as_bytes(), stored.as_slice());
            let _ = tree.insert(hex.as_bytes(), stored.as_slice());
        }
    }
}

pub(crate) fn load_received_memo(drk: &drk::Drk, tx_hash: &str) -> Option<String> {
    let tree = drk.cache.kvdb.open_tree_default(PAYMENT_MEMOS_TREE).ok()?;
    let v = tree.get(tx_hash.as_bytes()).ok()??;
    let plain = unwrap_payment_meta(&v).ok()?;
    String::from_utf8(plain)
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

pub(crate) fn persist_sent_payment_meta(
    drk: &drk::Drk,
    tx_hash: &str,
    memo: Option<&str>,
    recipient: Option<&str>,
) {
    if tx_hash.is_empty() {
        return;
    }
    if let Some(memo) = memo.map(str::trim).filter(|s| !s.is_empty()) {
        if let Ok(tree) = drk.cache.kvdb.open_tree_default(PAYMENT_MEMOS_TREE) {
            if let Ok(stored) = wrap_payment_meta(memo.as_bytes()) {
                let _ = tree.insert(tx_hash.as_bytes(), stored.as_slice());
            }
        }
    }
    if let Some(recipient) = recipient.map(str::trim).filter(|s| !s.is_empty()) {
        if let Ok(tree) = drk.cache.kvdb.open_tree_default(PAYMENT_RECIPIENTS_TREE) {
            if let Ok(stored) = wrap_payment_meta(recipient.as_bytes()) {
                let _ = tree.insert(tx_hash.as_bytes(), stored.as_slice());
            }
        }
    }
}

pub(crate) fn load_sent_recipient(drk: &drk::Drk, tx_hash: &str) -> Option<String> {
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(PAYMENT_RECIPIENTS_TREE)
        .ok()?;
    let v = tree.get(tx_hash.as_bytes()).ok()??;
    let plain = unwrap_payment_meta(&v).ok()?;
    String::from_utf8(plain)
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

pub(crate) fn merkle_node_from_coin_bytes(
    coin_bytes: &[u8],
) -> Option<darkfi_sdk::crypto::MerkleNode> {
    if coin_bytes.len() != 32 {
        return None;
    }
    let mut repr = [0u8; 32];
    repr.copy_from_slice(coin_bytes);
    darkfi_sdk::crypto::MerkleNode::from_bytes(repr)
}

/// Append LWD `GetNoteCommitments` leaves for `[start_height, end_height]`.
///
/// Height 0 is genesis (real coins). Callers must start from an
/// `empty_money_tree()` so the ZERO sentinel is already present.
/// Spend-safe rebuilds go through [`rebuild_money_tree_to_height`] so SQL
/// leaf positions stay aligned; this helper remains for prefix-only backfill.
#[allow(dead_code)]
pub(crate) async fn append_note_commitments(
    tree: &mut darkfi_sdk::crypto::MerkleTree,
    client: &crate::lightwallet_client::LightwalletClient,
    start_height: u32,
    end_height: u32,
    owned: &std::collections::HashSet<Vec<u8>>,
) -> Result<u64, String> {
    use std::collections::BTreeMap;

    if start_height > end_height {
        return Ok(0);
    }

    const CHUNK: u32 = 4096;
    let mut appended = 0u64;
    let mut start = start_height;
    while start <= end_height {
        let chunk_end = end_height.min(start.saturating_add(CHUNK.saturating_sub(1)));
        let updates = crate::sync_pipeline::fetch_padded_note_commitments(
            client,
            start,
            chunk_end,
            end_height,
        )
        .await?;
        let mut by_h: BTreeMap<u32, Vec<Vec<u8>>> = BTreeMap::new();
        for (height, coins) in updates {
            if crate::sync_pipeline::in_scan_window(height, start, chunk_end) {
                by_h.entry(height).or_default().extend(coins);
            }
        }
        for height in start..=chunk_end {
            for coin_bytes in by_h.get(&height).map(|v| v.as_slice()).unwrap_or(&[]) {
                let Some(node) = merkle_node_from_coin_bytes(coin_bytes) else {
                    continue;
                };
                tree.append(node);
                appended += 1;
                if owned.contains(coin_bytes) {
                    let _ = tree.mark();
                }
            }
        }
        start = chunk_end.saturating_add(1);
        if start == 0 {
            break;
        }
    }
    Ok(appended)
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
    let coins_table = drk::money::MONEY_COINS_TABLE.as_str();
    if let Err(e) = drk
        .wallet
        .exec_sql(
            &format!("DELETE FROM {coins_table} WHERE creation_height > ?1"),
            vec![drk::walletdb::Value::from(i64::from(rollback_height))],
        )
        .await
    {
        tracing::error!(target: "wallet-sync", "Failed to delete post-reorg coins: {e}");
    }
    if let Err(e) = drk
        .wallet
        .exec_sql(
            &format!(
                "UPDATE {coins_table} SET is_spent = 0, spent_height = NULL WHERE spent_height > ?1"
            ),
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
            "reset_to_height failed (no inverse diffs); next sync will rebuild the Money tree to {rollback_height}"
        );
        let _ = set_merkle_from_genesis(drk, false);
        let _ = clear_merkle_tree_height(drk);
        let _ = client;
        scan_height = rollback_height;
    }

    // get_last_scanned_block reads the highest key, so drop the stale tail first.
    remove_scanned_blocks_above(&drk.cache.scanned_blocks, scan_height)?;
    persist_scanned_height(drk, scan_height)?;
    crate::transactions::invalidate_transactions_above(drk, rollback_height).await
}

pub async fn rebuild_money_tree_to_height(
    drk: &drk::Drk,
    client: &crate::lightwallet_client::LightwalletClient,
    rollback_height: u32,
) -> Result<(), String> {
    let updates = fetch_commitment_chunks(client, rollback_height).await?;
    install_prefetched_commitments(drk, updates, rollback_height).await
}

async fn install_prefetched_commitments(
    drk: &drk::Drk,
    updates: Vec<(u32, Vec<Vec<u8>>)>,
    rollback_height: u32,
) -> Result<(), String> {
    use std::collections::HashMap;

    use darkfi_serial::serialize;
    use drk::money::{MONEY_COINS_COL_COIN, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_TABLE};

    let coins = drk.get_coins(true).await.map_err(|e| e.to_string())?;
    let mut own: HashMap<Vec<u8>, Vec<u8>> = HashMap::new();
    for (oc, _, _, _, _) in &coins {
        own.insert(
            oc.coin.inner().to_repr().to_vec(),
            oc.coin.to_bytes().to_vec(),
        );
    }

    let mut by_h: std::collections::BTreeMap<u32, Vec<Vec<u8>>> =
        std::collections::BTreeMap::new();
    for (height, coins) in updates {
        if height <= rollback_height {
            by_h.entry(height).or_default().extend(coins);
        }
    }
    let mut tree = empty_money_tree();
    for height in 0..=rollback_height {
            for coin_bytes in by_h.get(&height).map(|v| v.as_slice()).unwrap_or(&[]) {
                let Some(node) = merkle_node_from_coin_bytes(coin_bytes) else {
                    continue;
                };
                tree.append(node);
                if let Some(sql_key) = own.get(coin_bytes) {
                    let pos = tree
                        .mark()
                        .ok_or_else(|| "merkle mark failed".to_string())?;
                    let query = format!(
                        "UPDATE {} SET {} = ?1 WHERE {} = ?2;",
                        *MONEY_COINS_TABLE, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_COL_COIN,
                    );
                    drk.wallet
                        .exec_sql(
                            &query,
                            vec![
                                drk::walletdb::Value::from(serialize(&pos)),
                                drk::walletdb::Value::from(sql_key.clone()),
                            ],
                        )
                        .await
                        .map_err(|e| format!("update leaf_position: {e}"))?;
                }
            }
    }

    drk.cache
        .insert_merkle_trees(&[(drk::money::KVDB_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist rebuilt Money Merkle tree: {e}"))?;
    let _ = drk.cache.kvdb.flush_default_mode();
    set_merkle_from_genesis(drk, true)?;
    set_merkle_tree_height(drk, rollback_height)?;
    Ok(())
}

async fn fetch_commitment_chunks(
    client: &crate::lightwallet_client::LightwalletClient,
    rollback_height: u32,
) -> Result<Vec<(u32, Vec<Vec<u8>>)>, String> {
    const CHUNK: u32 = 4096;
    // A bucket cut at `rollback_height` far below the chain tip is refused by
    // lightwalletd, so pad against the real tip.
    let tip = client.get_chain_tip().await?.height.max(rollback_height);
    let mut start = 0u32;
    let mut all = Vec::new();
    while start <= rollback_height {
        let chunk_end = rollback_height.min(start.saturating_add(CHUNK.saturating_sub(1)));
        let updates = crate::sync_pipeline::fetch_padded_note_commitments(
            client,
            start,
            chunk_end,
            tip,
        )
        .await?;
        all.extend(updates);
        let next = chunk_end.saturating_add(1);
        if next <= start {
            break;
        }
        start = next;
    }
    Ok(all)
}

/// Same replay as [`rebuild_money_tree_to_height`], but the FFI write lock is
/// taken only while the tree and leaf positions are installed.
async fn rebuild_money_tree_from_ptr(
    drk: &DrkPtr,
    client: &crate::lightwallet_client::LightwalletClient,
    rollback_height: u32,
) -> Result<(), String> {
    let updates = fetch_commitment_chunks(client, rollback_height).await?;
    let guard = drk.write().await;
    install_prefetched_commitments(&guard, updates, rollback_height).await
}

/// Birthday/OMR sync only appends the scan window. Spend proofs then use a
/// Merkle root that is not on chain (`tx.calculate_fee` → -32111). Walk every
/// commitment from genesis and rewrite this wallet's leaf positions.
pub(crate) async fn ensure_spendable_money_tree(
    drk: &drk::Drk,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    // A genesis tree that is not the current tip has a root darkfid will
    // reject. Tests set E2E_REBUILD_MERKLE=1 to rebuild before each spend.
    if std::env::var("E2E_REBUILD_MERKLE").ok().as_deref() == Some("1") {
        let _ = set_merkle_from_genesis(drk, false);
    }
    if merkle_from_genesis(drk) {
        return Ok(());
    }
    rebuild_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin).await
}

pub(crate) async fn rebuild_spendable_money_tree(
    drk: &drk::Drk,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    let lw_url = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| "Merkle rebuild requires a lightwallet URL".to_string())?;
    let lw_url = crate::lightwallet_client::normalize_lightwallet_url(lw_url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &lw_url,
        lightwallet_tls_pin,
    );
    let info = client.get_light_info().await?;
    let tip = info.chain_tip_height;
    if tip == 0 {
        return Err("LWD tip is 0".into());
    }
    tracing::info!(
        target: "wallet-tx",
        "Rebuilding Money Merkle tree 0..={tip} for spend proofs"
    );
    rebuild_money_tree_to_height(drk, &client, tip).await?;
    tracing::info!(target: "wallet-tx", "Money Merkle tree rebuild complete");
    Ok(())
}

/// Remove every scanned-block record above `height` (keys are BE `u32`).
pub fn remove_scanned_blocks_above(
    tree: &kvdb_overlay::Tree,
    height: u32,
) -> Result<usize, String> {
    let Some(first) = height.checked_add(1) else {
        return Ok(0);
    };
    let keys = tree
        .range(first.to_be_bytes()..)
        .map(|r| r.map(|(k, _)| k))
        .collect::<Result<Vec<_>, _>>()
        .map_err(|e| format!("Failed to scan scanned blocks: {e}"))?;
    for key in &keys {
        tree.remove(key)
            .map_err(|e| format!("Failed to remove scanned block: {e}"))?;
    }
    Ok(keys.len())
}

pub(crate) fn persist_scanned_height(drk: &drk::Drk, height: u32) -> Result<(), String> {
    use darkfi_serial::serialize;

    let value = serialize(&(String::from("-"), String::from("-")));
    drk.cache
        .scanned_blocks
        .insert(&height.to_be_bytes(), &value)
        .map_err(|e| format!("Failed to persist scanned height: {e}"))?;
    let _ = drk.cache.kvdb.flush_default_mode();
    Ok(())
}

/// Decrypt this wallet's own Money transfer and fee outputs and store them.
///
/// The outgoing UnifOMR clue covers output 0 (the payment), not change or the
/// fee note. Strict sync will not trial-decrypt those, so they have to be
/// recorded at broadcast. Leaf positions are placeholders until the commitment
/// walk rewrites them.
pub async fn remember_own_outputs_from_tx(
    drk: &drk::Drk,
    tx: &darkfi::tx::Transaction,
) -> Result<(), String> {
    use darkfi_money_contract::{
        client::MoneyNote,
        model::{MoneyFeeParamsV1, MoneyTransferParamsV1, Output},
        MoneyFunction,
    };
    use darkfi_sdk::crypto::contract_id::MONEY_CONTRACT_ID;
    use darkfi_serial::deserialize_async;

    let secrets = drk.get_money_secrets().await.map_err(|e| e.to_string())?;
    if secrets.is_empty() {
        return Ok(());
    }
    let (height, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;

    for call in &tx.calls {
        if call.data.contract_id != *MONEY_CONTRACT_ID {
            continue;
        }
        let data = &call.data.data;
        if data.is_empty() {
            continue;
        }
        let outputs: Vec<Output> = match MoneyFunction::try_from(data[0]) {
            Ok(MoneyFunction::TransferV1) => {
                let Ok(params) = deserialize_async::<MoneyTransferParamsV1>(&data[1..]).await
                else {
                    continue;
                };
                params.outputs
            }
            Ok(MoneyFunction::FeeV1) => {
                if data.len() < 10 {
                    continue;
                }
                let Ok(params) = deserialize_async::<MoneyFeeParamsV1>(&data[9..]).await else {
                    continue;
                };
                vec![params.output]
            }
            _ => continue,
        };
        for output in outputs {
            if output.tx_local {
                continue;
            }
            let mut matched = None;
            for secret in &secrets {
                if let Ok(note) = output.note.decrypt::<MoneyNote>(secret) {
                    matched = Some((note, *secret));
                    break;
                }
            }
            if let Some((note, secret)) = matched {
                if let Err(e) = remember_own_output(drk, height, &output.coin, &note, &secret).await
                {
                    tracing::warn!(target: "wallet-tx", "remember own output: {e}");
                }
            }
        }
    }
    Ok(())
}

/// Record a note we already decrypted. Leaf position is a placeholder;
/// the commitment walk rewrites it from the chain.
pub async fn remember_own_output(
    drk: &drk::Drk,
    height: u32,
    coin: &darkfi_money_contract::model::Coin,
    note: &darkfi_money_contract::client::MoneyNote,
    secret: &darkfi_sdk::crypto::SecretKey,
) -> Result<(), String> {
    use darkfi_serial::serialize;
    use drk::money::{
        MONEY_COINS_COL_COIN, MONEY_COINS_COL_COIN_BLIND, MONEY_COINS_COL_CREATION_HEIGHT,
        MONEY_COINS_COL_IS_SPENT, MONEY_COINS_COL_LEAF_POSITION, MONEY_COINS_COL_MEMO,
        MONEY_COINS_COL_SECRET, MONEY_COINS_COL_SPEND_HOOK, MONEY_COINS_COL_SPENT_HEIGHT,
        MONEY_COINS_COL_TOKEN_BLIND, MONEY_COINS_COL_TOKEN_ID, MONEY_COINS_COL_USER_DATA,
        MONEY_COINS_COL_VALUE, MONEY_COINS_COL_VALUE_BLIND, MONEY_COINS_TABLE,
    };

    let leaf_position = {
        let mut placeholder = empty_money_tree();
        placeholder
            .mark()
            .ok_or_else(|| "placeholder merkle mark failed".to_string())?
    };
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
        drk::walletdb::Value::from(serialize(secret)),
        drk::walletdb::Value::from(serialize(&leaf_position)),
        drk::walletdb::Value::from(serialize(&note.memo)),
        drk::walletdb::Value::from(i64::from(height)),
        drk::walletdb::Value::from(0i64),
        drk::walletdb::Value::from(spent_height),
    ];
    drk.wallet
        .exec_sql(&query, params)
        .await
        .map_err(|e| format!("remember_own_output: {e}"))?;
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
    process_compact_block(drk, block, true, true).await
}

async fn process_compact_block(
    drk: &drk::Drk,
    block: &crate::lightwallet_client::LightCompactBlock,
    trial_decrypt: bool,
    append_commitments: bool,
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
        KVDB_MERKLE_TREES_MONEY, MONEY_COINS_COL_COIN, MONEY_COINS_COL_COIN_BLIND,
        MONEY_COINS_COL_CREATION_HEIGHT, MONEY_COINS_COL_IS_SPENT, MONEY_COINS_COL_LEAF_POSITION,
        MONEY_COINS_COL_MEMO, MONEY_COINS_COL_SECRET, MONEY_COINS_COL_SPEND_HOOK,
        MONEY_COINS_COL_SPENT_HEIGHT, MONEY_COINS_COL_TOKEN_BLIND, MONEY_COINS_COL_TOKEN_ID,
        MONEY_COINS_COL_USER_DATA, MONEY_COINS_COL_VALUE, MONEY_COINS_COL_VALUE_BLIND,
        MONEY_COINS_TABLE,
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
                    return Err(format!(
                        "Invalid note commitment at height {}: not a canonical pallas::Base",
                        block.height
                    ));
                };
                Coin::from(base)
            } else {
                continue;
            };

            if append_commitments {
                tree.append(MerkleNode::from(coin.inner()));
            }

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

            let leaf_position = if append_commitments {
                tree.mark().unwrap()
            } else {
                continue;
            };
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
        .insert_merkle_trees(&[(KVDB_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist Money Merkle tree: {e}"))?;
    let _ = drk.cache.kvdb.flush_default_mode();

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
            needed_limbs = Some(crate::batch_pir::padded_limb_count(max_need));
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
/// `slot_heights` is the response's packed slot → height map (same for every
/// key); slot `i` decrypts to a match iff its clue is pertinent, mapping to
/// `slot_heights[i]`. Single-key responses are unframed; multi-key responses are
/// length-prefixed frames (one digest per detection key), matching lightwalletd.
fn decrypt_unif_omr_heights(
    clients: &[crate::unifomr::UnifOmrClient],
    encrypted_digest: &[u8],
    slot_heights: &[u32],
) -> Result<Vec<u32>, String> {
    use std::collections::BTreeSet;

    if clients.is_empty() {
        return Err("No UnifOMR clients for digest decrypt".into());
    }
    if clients.len() == 1 {
        let slots = clients[0]
            .decrypt_digest_slots(encrypted_digest)
            .map_err(|e| format!("UnifOMR digest decrypt failed: {e}"))?;
        return crate::unifomr::UnifOmrClient::range_check_matches(&slots, slot_heights)
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
        for h in crate::unifomr::UnifOmrClient::range_check_matches(&slots, slot_heights)? {
            heights.insert(h);
        }
    }
    Ok(heights.into_iter().collect())
}

pub fn redact_sync_error(error: &str) -> String {
    // Remove IP addresses (IPv4)
    let redacted = regex_lite::Regex::new(r"\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?")
        .map(|re| re.replace_all(error, "[redacted-addr]").to_string())
        .unwrap_or_else(|_| error.to_string());

    // IPv6 (bracketed or bare)
    let redacted = regex_lite::Regex::new(
        r"\[(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\](?::\d+)?|(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}",
    )
    .map(|re| re.replace_all(&redacted, "[redacted-addr]").to_string())
    .unwrap_or(redacted);

    // .onion hosts
    let redacted = regex_lite::Regex::new(r"[a-zA-Z2-7]{16,56}\.onion(?::\d+)?")
        .map(|re| re.replace_all(&redacted, "[redacted-addr]").to_string())
        .unwrap_or(redacted);

    // Remove hostnames that look like server URLs
    let redacted =
        regex_lite::Regex::new(r"(https?://|tcp://|tcp\+tls://|socks5://|grpc://|wss?://)[^\s]+")
            .map(|re| re.replace_all(&redacted, "[redacted-url]").to_string())
            .unwrap_or(redacted);

    redacted
}

/// Clamp a truncated UnifOMR digest so the scan cursor never rewinds.
///
/// `Ok(None)` ⇒ nothing was covered (empty `slot_heights`): no progress this
/// cycle, leave the cursor where it is.
pub(crate) fn clamp_omr_truncated_tip(
    scan_start: u32,
    window_tip: u32,
    covered_end: Option<u32>,
) -> Result<Option<u32>, String> {
    let Some(covered_end) = covered_end else {
        return Ok(None);
    };
    if covered_end < scan_start {
        return Err("UnifOMR digest truncated below scan cursor; retry with smaller window".into());
    }
    if covered_end < window_tip {
        tracing::warn!(
            target: "wallet-sync",
            "UnifOMR digest truncated at DoS cap: covered up to \
             height {covered_end} (< window_tip {window_tip}); \
             clamping scan cursor"
        );
        Ok(Some(covered_end))
    } else {
        Ok(Some(window_tip))
    }
}

/// Append unique commitments once. Used to lock the M1 two-pass invariant.
pub(crate) fn append_commitment_leaves(
    tree: &mut darkfi_sdk::crypto::MerkleTree,
    coins: &[[u8; 32]],
) -> Result<usize, String> {
    let mut n = 0usize;
    for coin in coins {
        let node =
            merkle_node_from_coin_bytes(coin).ok_or_else(|| "invalid commitment".to_string())?;
        tree.append(node);
        n += 1;
    }
    Ok(n)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_money_tree_root_matches_zero_leaf_only() {
        use darkfi_sdk::crypto::{MerkleNode, MerkleTree};
        use darkfi_sdk::pasta::group::ff::Field;
        use darkfi_sdk::pasta::pallas;
        let tree = empty_money_tree();
        let mut expected = MerkleTree::new(u32::MAX as usize);
        expected.append(MerkleNode::from(pallas::Base::ZERO));
        assert_eq!(tree.root(0).unwrap(), expected.root(0).unwrap());
    }

    #[test]
    fn merkle_node_from_coin_bytes_rejects_short() {
        assert!(merkle_node_from_coin_bytes(&[1, 2, 3]).is_none());
    }

    #[test]
    fn merkle_node_from_coin_bytes_accepts_zero() {
        assert!(merkle_node_from_coin_bytes(&[0u8; 32]).is_some());
    }

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
            MAX_SYNC_CYCLE_SECS > 1800,
            "Cycle timeout must outlast the 1800s UnifOMR digest RPC timeout"
        );
        assert!(
            MAX_SYNC_CYCLE_SECS <= 3600,
            "Cycle timeout too long — 1h+ stalls are unacceptable"
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

    #[test]
    fn two_pass_window_does_not_double_append() {
        let mut c1 = [0u8; 32];
        c1[0] = 1;
        let mut c2 = [0u8; 32];
        c2[0] = 2;
        let coins = [c1, c2];
        let mut once = empty_money_tree();
        append_commitment_leaves(&mut once, &coins).unwrap();
        let root_once = once.root(0).unwrap();

        let mut twice = empty_money_tree();
        append_commitment_leaves(&mut twice, &coins).unwrap();
        append_commitment_leaves(&mut twice, &coins).unwrap();
        assert_ne!(
            twice.root(0).unwrap(),
            root_once,
            "appending the same window twice must change the Money tree"
        );

        let mut supplemental = empty_money_tree();
        append_commitment_leaves(&mut supplemental, &coins).unwrap();
        // Supplemental trial-decrypt (append=false) must leave size/root unchanged.
        assert_eq!(supplemental.root(0).unwrap(), root_once);
    }

    #[test]
    fn remove_scanned_blocks_above_rewinds_last() {
        let (db, _dir) = kvdb_overlay::Database::open_temp().unwrap();
        let tree = db.open_tree_default("_scanned_blocks").unwrap();
        for h in [5u32, 100, 255, 256, 1_000, 70_000] {
            tree.insert(&h.to_be_bytes(), b"-").unwrap();
        }
        assert_eq!(remove_scanned_blocks_above(&tree, 255).unwrap(), 3);
        let (last, _) = tree.last().unwrap().unwrap();
        assert_eq!(last, 255u32.to_be_bytes().to_vec());
        tree.insert(&200u32.to_be_bytes(), b"-").unwrap();
        assert_eq!(remove_scanned_blocks_above(&tree, 200).unwrap(), 1);
        let (last, _) = tree.last().unwrap().unwrap();
        assert_eq!(last, 200u32.to_be_bytes().to_vec());
        assert_eq!(remove_scanned_blocks_above(&tree, u32::MAX).unwrap(), 0);
        assert_eq!(tree.len().unwrap(), 3);
    }

    #[test]
    fn clamp_omr_truncated_tip_errors_below_scan_start() {
        let err = clamp_omr_truncated_tip(100, 200, Some(50)).unwrap_err();
        assert!(err.contains("truncated below scan cursor"));
    }

    #[test]
    fn clamp_omr_truncated_tip_no_progress_on_empty_slots() {
        assert_eq!(clamp_omr_truncated_tip(100, 200, None).unwrap(), None);
    }

    #[test]
    fn clamp_omr_truncated_tip_clamps_within_window() {
        assert_eq!(
            clamp_omr_truncated_tip(100, 200, Some(150)).unwrap(),
            Some(150)
        );
        assert_eq!(
            clamp_omr_truncated_tip(100, 200, Some(200)).unwrap(),
            Some(200)
        );
    }

    #[test]
    fn test_redact_sync_error_strips_ipv6_onion_and_proxy_schemes() {
        let input = "fail socks5://127.0.0.1:9050/abc.onion:9601 grpc://[2001:db8::1]:9067 wss://lw.example";
        let output = redact_sync_error(input);
        assert!(!output.contains("127.0.0.1"), "{output}");
        assert!(!output.contains("abc.onion"), "{output}");
        assert!(!output.contains("2001:db8::1"), "{output}");
        assert!(!output.contains("lw.example"), "{output}");
    }

    #[test]
    fn commitment_suffix_fills_only_the_gap_above_the_snapshot() {
        assert_eq!(commitment_suffix_after_snapshot(4000, 5000), Some((4001, 4999)));
        assert_eq!(commitment_suffix_after_snapshot(5000, 5001), None);
        assert_eq!(commitment_suffix_after_snapshot(0, 0), None);
        assert_eq!(commitment_suffix_after_snapshot(10, 10), None);
    }

    #[test]
    fn nullifier_map_lookup_uses_raw_bytes() {
        let mut map = std::collections::HashMap::new();
        let nf = vec![7u8; 32];
        map.insert(nf.clone(), (nf.clone(), 4u64));
        assert!(nullifier_map_lookup(&map, &nf).is_some());
        assert!(nullifier_map_lookup(&map, &[8u8; 32]).is_none());
        assert!(nullifier_map_lookup(&map, &nf[..16]).is_none());
    }
}
