//! Lightwallet sync engine for mobile clients.
//!
//! This module provides the client-side sync logic that talks to a
//! `darkfi-lightwalletd` gRPC server instead of connecting directly
//! to a `darkfid` full node.
//!
//! ## Sync modes
//!
//! 1. **OMR (Oblivious Message Retrieval)** — default when server supports it.
//!    The server performs FHE-based filtering so only relevant compact blocks
//!    are streamed. Faster sync, lower bandwidth.
//!
//! 2. **Trial Decryption** — automatic fallback. Client downloads all compact
//!    blocks and attempts ChaCha20Poly1305 decryption of each encrypted note
//!    with its secret keys.
//!
//! The sync engine transparently switches between modes based on server
//! capability and health.

use std::fmt;
use std::sync::{Arc, Mutex};

/// The current sync status, exposed to the mobile UI.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LightSyncStatus {
    /// Not connected to lightwallet server
    Disconnected,
    /// Establishing connection
    Connecting,
    /// Actively downloading and processing blocks
    Syncing,
    /// Fully synced to chain tip
    Synced,
    /// Retrying after a transient failure
    Retrying,
    /// Connected but degraded (e.g. OMR unavailable, using fallback)
    Degraded,
    /// Unrecoverable error
    Error,
}

impl fmt::Display for LightSyncStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Disconnected => write!(f, "Disconnected"),
            Self::Connecting => write!(f, "Connecting"),
            Self::Syncing => write!(f, "Syncing"),
            Self::Synced => write!(f, "Synced"),
            Self::Retrying => write!(f, "Retrying"),
            Self::Degraded => write!(f, "Degraded"),
            Self::Error => write!(f, "Error"),
        }
    }
}

/// The method being used for note discovery.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LightSyncType {
    /// OMR-based filtering (server-side FHE detection)
    Omr,
    /// Standard trial decryption of all compact blocks
    TrialDecryption,
    /// Recovering from OMR failure using trial decryption
    TrialDecryptionFallback,
    /// Mixed: catching up missed blocks with trial decryption
    /// while OMR handles new blocks
    MixedRecovery,
    /// Initial catch-up sync from birthday height
    CatchUpSync,
    /// Not actively syncing
    Idle,
}

impl fmt::Display for LightSyncType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Omr => write!(f, "OMR"),
            Self::TrialDecryption => write!(f, "Trial decryption"),
            Self::TrialDecryptionFallback => write!(f, "Trial decryption (fallback)"),
            Self::MixedRecovery => write!(f, "Mixed recovery"),
            Self::CatchUpSync => write!(f, "Catch-up sync"),
            Self::Idle => write!(f, "Idle"),
        }
    }
}

/// Full sync state snapshot exposed to mobile UIs.
#[derive(Debug, Clone)]
pub struct LightSyncState {
    pub status: LightSyncStatus,
    pub sync_type: LightSyncType,
    pub scanned_height: u32,
    pub chain_tip: u32,
    /// Human-readable status message for the UI
    pub status_message: String,
    /// Human-readable sync type label for the UI
    pub sync_type_message: String,
    /// Whether OMR is available on the server
    pub omr_available: bool,
    /// Number of consecutive OMR failures (resets on success)
    pub omr_failure_count: u32,
    /// Number of sync cycles remaining before retrying OMR after a failure.
    /// When >0, the engine uses trial decryption temporarily.
    /// Decremented each time `choose_sync_type()` is called during backoff.
    pub omr_backoff_remaining: u32,
    /// Best-known chain tip hash from the last GetLightInfo response (finding 5.3).
    /// Used to detect silent reorgs where the tip height stays the same
    /// but the hash changes.
    pub chain_tip_hash: Vec<u8>,
    /// True when an OMR downgrade was detected this session (security audit R-S2).
    /// UI should surface a warning banner when this is set.
    pub omr_downgrade_warning: bool,
    /// Count of OMR→non-OMR transitions this session (security audit R-S2).
    /// If >3, the server may be adversarially toggling OMR.
    pub omr_downgrade_count: u32,
    /// True when a chain reorg was detected and the wallet needs a re-scan
    /// (security audit R1). The UI should prompt the user or auto-trigger rescan.
    pub reorg_detected: bool,
    /// Last hard error message (e.g. strict OMR downgrade refusal).
    pub last_error: Option<String>,
}

impl Default for LightSyncState {
    fn default() -> Self {
        Self {
            status: LightSyncStatus::Disconnected,
            sync_type: LightSyncType::Idle,
            scanned_height: 0,
            chain_tip: 0,
            // These must match what refresh_messages() would produce
            // for the default status/sync_type values above.
            status_message: "Server unreachable".to_string(),
            sync_type_message: "Idle".to_string(),
            omr_available: false,
            omr_failure_count: 0,
            omr_backoff_remaining: 0,
            chain_tip_hash: Vec::new(),
            omr_downgrade_warning: false,
            omr_downgrade_count: 0,
            reorg_detected: false,
            last_error: None,
        }
    }
}

impl LightSyncState {
    /// Update the human-readable messages from the current state.
    pub fn refresh_messages(&mut self) {
        self.status_message = match &self.status {
            LightSyncStatus::Disconnected => "Server unreachable".to_string(),
            LightSyncStatus::Connecting => "Connecting to lightwallet server…".to_string(),
            LightSyncStatus::Syncing => {
                if self.chain_tip > 0 {
                    format!(
                        "Syncing block {} of {}",
                        self.scanned_height, self.chain_tip
                    )
                } else {
                    "Syncing…".to_string()
                }
            }
            LightSyncStatus::Synced => format!("Synced · Block {}", self.chain_tip),
            LightSyncStatus::Retrying => "Connection lost, retrying…".to_string(),
            LightSyncStatus::Degraded => {
                "Synced (UnifOMR unavailable — using trial decryption. Prefer Nighthawk or Moonshine for fastest private sync)".to_string()
            }
            LightSyncStatus::Error => "Sync error".to_string(),
        };

        self.sync_type_message = match &self.sync_type {
            LightSyncType::Omr => "OMR".to_string(),
            LightSyncType::TrialDecryption => "Trial decryption".to_string(),
            LightSyncType::TrialDecryptionFallback => "Trial decryption fallback".to_string(),
            LightSyncType::MixedRecovery => "Mixed recovery".to_string(),
            LightSyncType::CatchUpSync => "Catch-up sync".to_string(),
            LightSyncType::Idle => "Idle".to_string(),
        };
    }
}

/// Thread-safe sync state container.
pub struct SyncEngine {
    state: Arc<Mutex<LightSyncState>>,
    /// Lightwallet server endpoint (e.g. "https://lightwalletd.dark.fi:9067")
    server_endpoint: String,
    /// Optional SHA-256 of lightwalletd leaf cert DER (S8).
    tls_pin_sha256: Option<[u8; 32]>,
    /// Maximum consecutive OMR failures before halting (strict mode) or
    /// continuing with backoff-based retry (default mode).
    max_omr_failures: u32,
    /// If true, halt sync entirely instead of falling back to trial decryption
    /// when max failures is exceeded. This prevents a downgrade attack from
    /// forcing the client into a less private sync mode.
    /// Production default: enabled (`true`).
    strict_omr_only: std::sync::atomic::AtomicBool,
    /// Previous chain tip seen by the sync loop (finding 5.6).
    /// Used together with `last_tip_update` to detect rapid tip advancement.
    prev_chain_tip: std::sync::atomic::AtomicU32,
    /// Epoch-seconds of the last chain tip update (finding 5.6).
    last_tip_update: std::sync::atomic::AtomicU64,
    /// Optional callback for chain reorg events — fires to notify mobile UI.
    pub reorg_callback: std::sync::Mutex<Option<Box<dyn crate::ReorgEventCallback>>>,
}

/// Threshold: a tip advance of >100 blocks between two GetLightInfo calls
/// indicates the backend (darkfid) is still catching up.
const BACKEND_CATCHUP_THRESHOLD: u32 = 100;

/// Threshold: after this many consecutive OMR failures, apply exponential
/// backoff before retrying. Slightly raised (S14) so transient OMR blips
/// do not immediately force trial-decrypt downgrade.
///
/// UI should surface when sync enters `TrialDecryptionFallback` / `Degraded`
/// so the user knows privacy mode was downgraded.
const DEFAULT_MAX_OMR_FAILURES: u32 = 5;

/// Maximum backoff cycles (2^MAX_BACKOFF_EXPONENT = 64 cycles).
const MAX_BACKOFF_EXPONENT: u32 = 6;

impl SyncEngine {
    /// Create a new sync engine targeting the given lightwallet server.
    pub fn new(server_endpoint: String) -> Self {
        Self::with_tls_pin(server_endpoint, None)
    }

    /// Create a sync engine with an optional TLS certificate pin (S8).
    pub fn with_tls_pin(server_endpoint: String, tls_pin_sha256: Option<[u8; 32]>) -> Self {
        Self {
            state: Arc::new(Mutex::new(LightSyncState::default())),
            server_endpoint,
            tls_pin_sha256,
            max_omr_failures: DEFAULT_MAX_OMR_FAILURES,
            // Production default: no trial-decrypt fallback (privacy).
            // Use SyncEngine::set_strict_omr_only(false) for recovery / tests.
            strict_omr_only: std::sync::atomic::AtomicBool::new(true),
            prev_chain_tip: std::sync::atomic::AtomicU32::new(0),
            last_tip_update: std::sync::atomic::AtomicU64::new(0),
            reorg_callback: std::sync::Mutex::new(None),
        }
    }

    /// Create a new sync engine in strict OMR-only mode.
    ///
    /// In this mode, if OMR fails `max_omr_failures` consecutive times,
    /// the engine halts with `LightSyncStatus::Error` instead of falling
    /// back to trial decryption. This is useful for privacy-sensitive
    /// deployments that prefer no sync over a less private sync.
    pub fn new_strict(server_endpoint: String) -> Self {
        Self {
            state: Arc::new(Mutex::new(LightSyncState::default())),
            server_endpoint,
            tls_pin_sha256: None,
            max_omr_failures: DEFAULT_MAX_OMR_FAILURES,
            strict_omr_only: std::sync::atomic::AtomicBool::new(true),
            prev_chain_tip: std::sync::atomic::AtomicU32::new(0),
            last_tip_update: std::sync::atomic::AtomicU64::new(0),
            reorg_callback: std::sync::Mutex::new(None),
        }
    }

    /// When true, never fall back to full-window / gap trial decrypt (privacy mode).
    pub fn strict_omr_only(&self) -> bool {
        self.strict_omr_only.load(std::sync::atomic::Ordering::Relaxed)
    }

    /// Enable or disable strict OMR-only mode (production default is enabled).
    pub fn set_strict_omr_only(&self, strict: bool) {
        self.strict_omr_only
            .store(strict, std::sync::atomic::Ordering::Relaxed);
    }

    /// Get a snapshot of the current sync state.
    pub fn snapshot(&self) -> LightSyncState {
        self.state.lock().unwrap().clone()
    }

    /// Update sync status.
    pub fn set_status(&self, status: LightSyncStatus) {
        let mut state = self.state.lock().unwrap();
        state.status = status;
        state.refresh_messages();
    }

    /// Update sync type.
    pub fn set_sync_type(&self, sync_type: LightSyncType) {
        let mut state = self.state.lock().unwrap();
        state.sync_type = sync_type;
        state.refresh_messages();
    }

    /// Override the status message with a custom string.
    ///
    /// Used when entering degraded mode to provide context-specific messages
    /// (e.g. "Some transactions were sent from a non-OMR wallet...").
    pub fn set_status_message(&self, msg: &str) {
        let mut state = self.state.lock().unwrap();
        state.status_message = msg.to_string();
    }

    /// Get current sync status.
    pub fn status(&self) -> LightSyncStatus {
        self.state.lock().unwrap().status.clone()
    }

    /// Update scanned height.
    pub fn set_scanned_height(&self, height: u32) {
        let mut state = self.state.lock().unwrap();
        state.scanned_height = height;
        state.refresh_messages();
    }

    /// Update chain tip.
    pub fn set_chain_tip(&self, tip: u32) {
        let mut state = self.state.lock().unwrap();
        state.chain_tip = tip;
        state.refresh_messages();
    }

    /// Get current chain tip height.
    pub fn chain_tip(&self) -> u32 {
        self.state.lock().unwrap().chain_tip
    }

    /// Get the last locally scanned height.
    pub fn scanned_height(&self) -> u32 {
        self.state.lock().unwrap().scanned_height
    }

    /// Whether the wallet is still behind the chain tip (more blocks to scan).
    pub fn is_behind_tip(&self) -> bool {
        let state = self.state.lock().unwrap();
        state.chain_tip > 0 && state.scanned_height < state.chain_tip
    }

    /// Update the chain tip hash from the latest GetLightInfo response (finding 5.3).
    ///
    /// Returns `true` if a potential reorg is detected. Detection triggers:
    /// 1. Same height, different hash (silent reorg)
    /// 2. Tip regression: new height < previous height (chain rolled back)
    ///
    /// Security audit R1: on reorg detection, sets `reorg_detected` flag
    /// so the sync loop can call `rewind_to_height()` to roll back state.
    pub fn update_chain_tip_hash(&self, height: u32, hash: &[u8]) -> bool {
        let mut state = self.state.lock().unwrap();

        let prev_height = state.chain_tip;

        // Detect reorg case 1: same height, different hash
        let same_height_reorg = !state.chain_tip_hash.is_empty()
            && prev_height == height
            && state.chain_tip_hash != hash;

        // Detect reorg case 2: tip regression (new tip < previous tip)
        // This catches reorgs where the chain temporarily shortens.
        let tip_regression =
            prev_height > 0 && height < prev_height && !state.chain_tip_hash.is_empty();

        let reorg_detected = same_height_reorg || tip_regression;

        if reorg_detected {
            if tip_regression {
                tracing::warn!(
                    target: "sync-engine",
                    "Chain tip regressed from {} to {} — reorg detected",
                    prev_height, height,
                );
            } else {
                tracing::warn!(
                    target: "sync-engine",
                    "Tip hash changed at height {} — possible reorg or chain reorganization",
                    height,
                );
            }
            state.reorg_detected = true;
        }

        state.chain_tip = height;
        state.chain_tip_hash = hash.to_vec();
        state.refresh_messages();
        reorg_detected
    }

    /// Detect if the backend (darkfid) is still catching up (finding 5.6).
    ///
    /// Adopted from zcash/lightwalletd's "graceful handling of syncing zcashd":
    /// when the backend is syncing after a restart, the tip advances rapidly.
    /// This should NOT trigger OMR failure counting or degradation — it's
    /// normal catch-up behavior, not an error.
    ///
    /// Returns `true` if the tip advanced by more than `BACKEND_CATCHUP_THRESHOLD`
    /// blocks since the last call.
    pub fn is_backend_catching_up(&self, new_tip: u32) -> bool {
        use std::sync::atomic::Ordering;

        let prev = self.prev_chain_tip.swap(new_tip, Ordering::Relaxed);
        let now_secs = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let prev_time = self.last_tip_update.swap(now_secs, Ordering::Relaxed);

        if prev == 0 {
            return false;
        }

        let delta = new_tip.saturating_sub(prev);
        let elapsed = now_secs.saturating_sub(prev_time);

        // Rapid advance: >100 blocks in <5 minutes
        delta > BACKEND_CATCHUP_THRESHOLD && elapsed < 300
    }

    /// Record an OMR failure. Returns `true` only when the failure count
    /// reaches `max_omr_failures` (same-cycle trial-decrypt fallback) or
    /// when strict mode halts with `Error`.
    ///
    /// Failures below the threshold still apply exponential backoff and
    /// `Degraded` status, but return `false` so the sync loop retries OMR
    /// on the next cycle instead of falling through immediately.
    ///
    /// ## Exponential backoff
    ///
    /// Instead of permanently falling back to trial decryption after
    /// `max_omr_failures`, the engine uses exponential backoff:
    /// - After failure N, wait `2^min(N, 6)` sync cycles before retrying OMR.
    /// - During the backoff window, `choose_sync_type()` returns
    ///   `TrialDecryptionFallback` and decrements the counter.
    /// - Once the counter reaches 0, OMR is retried.
    ///
    /// ## Strict mode
    ///
    /// If `strict_omr_only` is enabled and `max_omr_failures` is exceeded,
    /// the engine enters `LightSyncStatus::Error` and halts sync entirely.
    pub fn record_omr_failure(&self) -> bool {
        let mut state = self.state.lock().unwrap();
        state.omr_failure_count += 1;

        // Strict mode: halt instead of degrade
        if self.strict_omr_only() && state.omr_failure_count >= self.max_omr_failures {
            state.status = LightSyncStatus::Error;
            state.sync_type = LightSyncType::Idle;
            state.refresh_messages();
            return true;
        }

        // Exponential backoff: wait 2^n cycles (capped at 2^6 = 64)
        let exponent = state.omr_failure_count.min(MAX_BACKOFF_EXPONENT);
        state.omr_backoff_remaining = 1u32 << exponent;
        state.sync_type = LightSyncType::TrialDecryptionFallback;
        state.status = LightSyncStatus::Degraded;
        state.refresh_messages();

        // Same-cycle trial-decrypt fallback only at/above the threshold.
        state.omr_failure_count >= self.max_omr_failures
    }

    /// Record an OMR success.
    ///
    /// Security audit fix: uses windowed failure rate instead of full reset.
    /// Halves the failure count on success instead of zeroing it, so that
    /// intermittent successes from a malicious server can't prevent the
    /// counter from ever reaching the threshold.
    pub fn record_omr_success(&self) {
        let mut state = self.state.lock().unwrap();
        // Windowed decay: halve instead of zero to prevent gaming.
        // A consistently healthy server will still converge to 0 quickly
        // (e.g., 4 → 2 → 1 → 0 over 3 successes).
        state.omr_failure_count /= 2;
        state.omr_backoff_remaining = 0;
        state.omr_available = true;
        state.refresh_messages();
    }

    /// Set OMR availability based on server info response.
    ///
    /// Security audit R-S2: tracks downgrade events per session and sets
    /// `omr_downgrade_warning` for UI surfacing.
    pub fn set_omr_available(&self, available: bool) {
        let mut state = self.state.lock().unwrap();
        if state.omr_available && !available {
            state.omr_downgrade_count += 1;
            state.omr_downgrade_warning = true;
            tracing::warn!(
                target: "sync-engine",
                "SECURITY WARNING: Server suddenly reported OMR is not available! \
                 Potential downgrade attack detected (session count: {})",
                state.omr_downgrade_count,
            );
            if self.strict_omr_only() {
                state.omr_available = false;
                state.status = LightSyncStatus::Error;
                state.sync_type = LightSyncType::Idle;
                state.last_error = Some(
                    "OMR capability downgrade refused (strict_omr_only); \
                     refusing trial-decrypt fallback"
                        .into(),
                );
                state.refresh_messages();
                return;
            }
            if state.omr_downgrade_count > 3 {
                tracing::error!(
                    target: "sync-engine",
                    "CRITICAL: OMR toggled off {} times this session — \
                     server may be adversarially toggling OMR availability",
                    state.omr_downgrade_count,
                );
            }
        }
        state.omr_available = available;
        if available && state.sync_type == LightSyncType::Idle {
            state.sync_type = LightSyncType::Omr;
        } else if !available && state.sync_type == LightSyncType::Omr {
            if self.strict_omr_only() {
                state.status = LightSyncStatus::Error;
                state.sync_type = LightSyncType::Idle;
                state.last_error = Some(
                    "Server reports OMR unsupported; strict_omr_only refuses trial decrypt"
                        .into(),
                );
            } else {
                state.sync_type = LightSyncType::TrialDecryption;
            }
        }
        state.refresh_messages();
    }

    /// Determine the best sync type for a sync operation.
    ///
    /// Logic:
    /// 1. If server doesn't support OMR → TrialDecryption
    /// 2. If in backoff (omr_backoff_remaining > 0) → TrialDecryptionFallback,
    ///    and decrement the counter. When it reaches 0, OMR is retried.
    /// 3. If OMR is available and no active backoff → Omr
    pub fn choose_sync_type(&self) -> LightSyncType {
        let mut state = self.state.lock().unwrap();

        // Strict mode halt
        if self.strict_omr_only() && state.status == LightSyncStatus::Error {
            return LightSyncType::Idle;
        }

        if !state.omr_available {
            return LightSyncType::TrialDecryption;
        }

        // Backoff in progress: use trial decryption but count down
        if state.omr_backoff_remaining > 0 {
            state.omr_backoff_remaining -= 1;
            if state.omr_backoff_remaining == 0 {
                // Backoff expired — next call will try OMR again
                state.sync_type = LightSyncType::Omr;
                state.status = LightSyncStatus::Syncing;
                state.refresh_messages();
            }
            return LightSyncType::TrialDecryptionFallback;
        }

        LightSyncType::Omr
    }

    /// Get the server endpoint as a string reference.
    pub fn endpoint(&self) -> &str {
        &self.server_endpoint
    }

    /// Get the server URL (owned) for client construction.
    pub fn server_url(&self) -> String {
        self.server_endpoint.clone()
    }

    /// Optional TLS pin for lightwalletd connections (S8).
    pub fn tls_pin(&self) -> Option<[u8; 32]> {
        self.tls_pin_sha256
    }

    /// Build a lightwallet client using this engine's endpoint + pin policy.
    pub fn lightwallet_client(&self) -> crate::lightwallet_client::LightwalletClient {
        crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
            &self.server_url(),
            self.tls_pin(),
        )
    }

    /// Rewind sync state after a chain reorg is detected (security audit R1).
    ///
    /// Rolls back `scanned_height` to `target_height`, clears the chain tip
    /// hash (so the next cycle re-establishes it), and marks `reorg_detected`
    /// as handled. The caller is responsible for:
    /// - Rolling back the Merkle tree to the target height
    /// - Deleting coins with `creation_height > target_height`
    /// - Un-spending coins with `spent_height > target_height`
    /// - Invalidating the block cache for heights above target
    ///
    /// Returns the previous scanned height (before rewind) for logging.
    pub fn rewind_to_height(&self, target_height: u32) -> u32 {
        let mut state = self.state.lock().unwrap();
        let prev = state.scanned_height;
        state.scanned_height = target_height;
        state.chain_tip_hash.clear();
        state.reorg_detected = false;
        state.status = LightSyncStatus::Syncing;
        state.refresh_messages();

        tracing::info!(
            target: "sync-engine",
            "Rewound scan cursor from {} to {} for reorg recovery",
            prev, target_height,
        );

        prev
    }

    /// Whether a reorg has been detected and needs handling.
    pub fn needs_reorg_recovery(&self) -> bool {
        self.state.lock().unwrap().reorg_detected
    }

    /// Clear the reorg flag without rewinding (e.g. after manual rescan).
    pub fn clear_reorg_flag(&self) {
        self.state.lock().unwrap().reorg_detected = false;
    }

    /// Reset all server-dependent state when switching lightwalletd servers
    /// (security audit R-S3/S4).
    ///
    /// This must be called when the user changes the lightwalletd endpoint.
    /// Without it, stale state from the previous server (tip hash, OMR
    /// availability, failure counters) can cause false reorg detection,
    /// wrong OMR mode selection, or stale backend catch-up signals.
    pub fn reset_for_server_switch(&self) {
        use std::sync::atomic::Ordering;

        let mut state = self.state.lock().unwrap();
        state.chain_tip_hash.clear();
        state.chain_tip = 0;
        state.omr_available = false;
        state.omr_failure_count = 0;
        state.omr_backoff_remaining = 0;
        state.omr_downgrade_warning = false;
        // Don't reset omr_downgrade_count — it tracks across the session
        state.reorg_detected = false;
        state.status = LightSyncStatus::Disconnected;
        state.sync_type = LightSyncType::Idle;
        state.refresh_messages();

        // Reset atomic counters for backend catch-up detection
        self.prev_chain_tip.store(0, Ordering::Relaxed);
        self.last_tip_update.store(0, Ordering::Relaxed);

        tracing::info!(
            target: "sync-engine",
            "Sync engine state reset for server switch",
        );
    }

    /// Whether the OMR downgrade warning should be shown in the UI.
    pub fn has_omr_downgrade_warning(&self) -> bool {
        self.state.lock().unwrap().omr_downgrade_warning
    }

    /// Acknowledge the OMR downgrade warning (user dismissed the banner).
    pub fn acknowledge_omr_downgrade(&self) {
        self.state.lock().unwrap().omr_downgrade_warning = false;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_engine_default_state() {
        let engine = SyncEngine::new("https://localhost:9067".to_string());
        let snap = engine.snapshot();
        assert_eq!(snap.status, LightSyncStatus::Disconnected);
        assert_eq!(snap.sync_type, LightSyncType::Idle);
        assert_eq!(snap.scanned_height, 0);
        assert_eq!(snap.chain_tip, 0);
        assert!(!snap.omr_available);
        assert_eq!(snap.omr_failure_count, 0);
        assert_eq!(snap.status_message, "Server unreachable");
        assert_eq!(snap.sync_type_message, "Idle");
    }

    #[test]
    fn test_server_url_and_endpoint() {
        let engine = SyncEngine::new("tcp://127.0.0.1:9067".to_string());
        assert_eq!(engine.server_url(), "tcp://127.0.0.1:9067");
        assert_eq!(engine.endpoint(), "tcp://127.0.0.1:9067");
    }

    #[test]
    fn test_status_connecting_message() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Connecting);
        assert!(engine.snapshot().status_message.contains("Connecting"));
    }

    #[test]
    fn test_status_syncing_with_progress() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Syncing);
        engine.set_chain_tip(1000);
        engine.set_scanned_height(500);
        assert_eq!(
            engine.snapshot().status_message,
            "Syncing block 500 of 1000"
        );
    }

    #[test]
    fn test_status_syncing_no_tip() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Syncing);
        assert_eq!(engine.snapshot().status_message, "Syncing…");
    }

    #[test]
    fn test_status_synced_message() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_chain_tip(42000);
        engine.set_status(LightSyncStatus::Synced);
        assert!(engine.snapshot().status_message.contains("42000"));
    }

    #[test]
    fn test_status_retrying_message() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Retrying);
        assert!(engine.snapshot().status_message.contains("retrying"));
    }

    #[test]
    fn test_status_degraded_message() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Degraded);
        assert!(engine.snapshot().status_message.contains("OMR unavailable"));
    }

    #[test]
    fn test_status_error_message() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_status(LightSyncStatus::Error);
        assert_eq!(engine.snapshot().status_message, "Sync error");
    }

    #[test]
    fn test_display_impls() {
        assert_eq!(LightSyncStatus::Disconnected.to_string(), "Disconnected");
        assert_eq!(LightSyncStatus::Connecting.to_string(), "Connecting");
        assert_eq!(LightSyncStatus::Syncing.to_string(), "Syncing");
        assert_eq!(LightSyncStatus::Synced.to_string(), "Synced");
        assert_eq!(LightSyncStatus::Retrying.to_string(), "Retrying");
        assert_eq!(LightSyncStatus::Degraded.to_string(), "Degraded");
        assert_eq!(LightSyncStatus::Error.to_string(), "Error");
        assert_eq!(LightSyncType::Omr.to_string(), "OMR");
        assert_eq!(
            LightSyncType::TrialDecryption.to_string(),
            "Trial decryption"
        );
        assert_eq!(
            LightSyncType::TrialDecryptionFallback.to_string(),
            "Trial decryption (fallback)"
        );
        assert_eq!(LightSyncType::Idle.to_string(), "Idle");
    }

    #[test]
    fn test_set_omr_available_switches_idle_to_omr() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_omr_available(true);
        assert_eq!(engine.snapshot().sync_type, LightSyncType::Omr);
        assert!(engine.snapshot().omr_available);
    }

    #[test]
    fn test_set_omr_unavailable_strict_refuses_trial() {
        let engine = SyncEngine::new("x".to_string());
        // strict_omr_only defaults true
        engine.set_omr_available(true);
        engine.set_omr_available(false);
        assert_eq!(engine.snapshot().status, LightSyncStatus::Error);
        assert_ne!(engine.snapshot().sync_type, LightSyncType::TrialDecryption);
    }

    #[test]
    fn test_set_omr_unavailable_switches_omr_to_trial_when_not_strict() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);
        engine.set_omr_available(false);
        assert_eq!(engine.snapshot().sync_type, LightSyncType::TrialDecryption);
    }

    #[test]
    fn test_set_omr_preserves_non_idle_type() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_sync_type(LightSyncType::CatchUpSync);
        engine.set_omr_available(true);
        assert_eq!(engine.snapshot().sync_type, LightSyncType::CatchUpSync);
    }

    #[test]
    fn test_omr_backoff_after_failure() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);

        // First failure (below threshold): backoff applied, no same-cycle fallback
        assert!(!engine.record_omr_failure());
        let snap = engine.snapshot();
        assert_eq!(snap.sync_type, LightSyncType::TrialDecryptionFallback);
        assert_eq!(snap.status, LightSyncStatus::Degraded);
        assert_eq!(snap.omr_failure_count, 1);
        assert_eq!(snap.omr_backoff_remaining, 2); // 2^1 = 2

        // Backoff: choose_sync_type returns Fallback and decrements
        assert_eq!(
            engine.choose_sync_type(),
            LightSyncType::TrialDecryptionFallback
        );
        assert_eq!(engine.snapshot().omr_backoff_remaining, 1);
        assert_eq!(
            engine.choose_sync_type(),
            LightSyncType::TrialDecryptionFallback
        );
        assert_eq!(engine.snapshot().omr_backoff_remaining, 0);

        // Backoff expired: next call returns Omr
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);
    }

    #[test]
    fn test_omr_failure_returns_true_only_at_max() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);

        // Failures 1..max-1: false (retry OMR next cycle)
        assert!(!engine.record_omr_failure());
        assert!(!engine.record_omr_failure());
        assert!(!engine.record_omr_failure());
        assert!(!engine.record_omr_failure());
        // Failure at max (5): true (same-cycle trial-decrypt fallback)
        assert!(engine.record_omr_failure());
        assert_eq!(engine.snapshot().omr_failure_count, 5);
        assert_eq!(engine.snapshot().status, LightSyncStatus::Degraded);
    }

    #[test]
    fn test_omr_exponential_backoff_growth() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);

        // Failure 1: backoff = 2^1 = 2
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().omr_backoff_remaining, 2);

        // Failure 2: backoff = 2^2 = 4
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().omr_backoff_remaining, 4);

        // Failure 3: backoff = 2^3 = 8
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().omr_backoff_remaining, 8);

        // Failure 6: backoff = 2^6 = 64 (cap)
        engine.record_omr_failure();
        engine.record_omr_failure();
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().omr_backoff_remaining, 64);

        // Failure 7: still capped at 64
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().omr_backoff_remaining, 64);
    }

    #[test]
    fn test_omr_success_resets_backoff() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);
        engine.record_omr_failure();
        engine.record_omr_failure();
        assert!(engine.snapshot().omr_backoff_remaining > 0);

        engine.record_omr_success();
        assert_eq!(engine.snapshot().omr_failure_count, 1);
        assert_eq!(engine.snapshot().omr_backoff_remaining, 0);
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);
    }

    #[test]
    fn test_choose_sync_type_without_omr() {
        let engine = SyncEngine::new("x".to_string());
        assert_eq!(engine.choose_sync_type(), LightSyncType::TrialDecryption);
    }

    #[test]
    fn test_choose_sync_type_with_omr() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_omr_available(true);
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);
    }

    #[test]
    fn test_strict_mode_halts_on_max_failures() {
        let engine = SyncEngine::new_strict("x".to_string());
        engine.set_omr_available(true);

        // Failures below threshold use backoff
        for _ in 0..4 {
            engine.record_omr_failure();
            assert_eq!(engine.snapshot().status, LightSyncStatus::Degraded);
        }

        // Fifth failure halts in strict mode (DEFAULT_MAX_OMR_FAILURES = 5)
        engine.record_omr_failure();
        assert_eq!(engine.snapshot().status, LightSyncStatus::Error);
        assert_eq!(engine.choose_sync_type(), LightSyncType::Idle);
    }

    #[test]
    fn test_concurrent_access() {
        use std::thread;
        let engine = Arc::new(SyncEngine::new("x".to_string()));
        let mut handles = vec![];
        for i in 0u32..10 {
            let e = Arc::clone(&engine);
            handles.push(thread::spawn(move || {
                e.set_scanned_height(i * 100);
                e.set_chain_tip(10_000);
                e.set_status(LightSyncStatus::Syncing);
                let _ = e.snapshot();
            }));
        }
        for h in handles {
            h.join().unwrap();
        }
        assert_eq!(engine.snapshot().chain_tip, 10_000);
    }

    #[test]
    fn test_full_sync_lifecycle() {
        let engine = SyncEngine::new("tcp://127.0.0.1:9067".to_string());
        assert_eq!(engine.snapshot().status, LightSyncStatus::Disconnected);

        engine.set_status(LightSyncStatus::Connecting);
        engine.set_omr_available(true);
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);

        engine.set_status(LightSyncStatus::Syncing);
        engine.set_sync_type(LightSyncType::Omr);
        engine.set_chain_tip(50_000);
        engine.set_scanned_height(0);

        // First failure triggers backoff
        engine.record_omr_failure();
        assert_eq!(
            engine.choose_sync_type(),
            LightSyncType::TrialDecryptionFallback
        );

        engine.set_sync_type(LightSyncType::TrialDecryption);
        engine.set_scanned_height(25_000);

        engine.record_omr_success();
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);

        engine.set_scanned_height(50_000);
        engine.set_status(LightSyncStatus::Synced);
        let snap = engine.snapshot();
        assert!(snap.status_message.contains("Synced"));
        assert!(snap.status_message.contains("50000"));
    }

    #[test]
    fn test_backoff_recovery_cycle() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_omr_available(true);
        engine.set_status(LightSyncStatus::Syncing);
        engine.set_chain_tip(100);

        // First failure: backoff = 2 cycles
        engine.record_omr_failure();
        assert_eq!(
            engine.choose_sync_type(),
            LightSyncType::TrialDecryptionFallback
        );
        assert_eq!(
            engine.choose_sync_type(),
            LightSyncType::TrialDecryptionFallback
        );
        // Backoff expired, OMR retried
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);

        // State should still preserve chain tip
        assert_eq!(engine.snapshot().chain_tip, 100);
    }

    #[test]
    fn test_omr_degraded_state_recovery_after_backoff() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_strict_omr_only(false);
        engine.set_omr_available(true);

        // Fail 3 times to build up backoff
        engine.record_omr_failure();
        engine.record_omr_failure();
        engine.record_omr_failure();

        // Should be in fallback with backoff = 2^3 = 8
        assert_eq!(engine.snapshot().omr_backoff_remaining, 8);
        for _ in 0..8 {
            assert_eq!(
                engine.choose_sync_type(),
                LightSyncType::TrialDecryptionFallback
            );
        }

        // After backoff expires, OMR is retried (NOT permanent fallback)
        assert_eq!(engine.choose_sync_type(), LightSyncType::Omr);
    }

    // =========================================================================
    // Chain tip hash tracking (finding 5.3)
    // =========================================================================

    #[test]
    fn test_chain_tip_hash_default_empty() {
        let engine = SyncEngine::new("x".to_string());
        assert!(engine.snapshot().chain_tip_hash.is_empty());
    }

    #[test]
    fn test_update_chain_tip_hash_first_time_no_reorg() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_chain_tip(100);
        let reorg = engine.update_chain_tip_hash(100, &[0xABu8; 32]);
        assert!(!reorg, "First hash update should not detect reorg");
        assert_eq!(engine.snapshot().chain_tip_hash.len(), 32);
    }

    #[test]
    fn test_update_chain_tip_hash_same_hash_no_reorg() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_chain_tip(100);
        engine.update_chain_tip_hash(100, &[0xAB; 32]);
        let reorg = engine.update_chain_tip_hash(100, &[0xAB; 32]);
        assert!(!reorg, "Same hash at same height should not be a reorg");
    }

    #[test]
    fn test_update_chain_tip_hash_different_hash_same_height_reorg() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_chain_tip(100);
        engine.update_chain_tip_hash(100, &[0xAA; 32]);
        let reorg = engine.update_chain_tip_hash(100, &[0xBB; 32]);
        assert!(reorg, "Different hash at same height should detect reorg");
    }

    #[test]
    fn test_update_chain_tip_hash_different_height_no_reorg() {
        let engine = SyncEngine::new("x".to_string());
        // First call sets height=100 and hash=0xAA
        engine.update_chain_tip_hash(100, &[0xAA; 32]);
        // Second call advances to height=101 with hash=0xBB
        let reorg = engine.update_chain_tip_hash(101, &[0xBB; 32]);
        assert!(
            !reorg,
            "Different hash at different height is normal progression"
        );
    }

    // =========================================================================
    // Backend catching up detection (finding 5.6)
    // =========================================================================

    #[test]
    fn test_backend_catching_up_first_call_false() {
        let engine = SyncEngine::new("x".to_string());
        assert!(
            !engine.is_backend_catching_up(1000),
            "First call should return false (no previous tip)"
        );
    }

    #[test]
    fn test_backend_catching_up_small_advance_false() {
        let engine = SyncEngine::new("x".to_string());
        engine.is_backend_catching_up(1000);
        assert!(
            !engine.is_backend_catching_up(1010),
            "Small advance (10 blocks) should not trigger catch-up"
        );
    }

    #[test]
    fn test_backend_catching_up_large_advance_true() {
        let engine = SyncEngine::new("x".to_string());
        engine.is_backend_catching_up(1000);
        assert!(
            engine.is_backend_catching_up(1200),
            "200 blocks in rapid succession should trigger catch-up"
        );
    }

    #[test]
    fn test_backend_catchup_threshold_reasonable() {
        assert!(
            BACKEND_CATCHUP_THRESHOLD >= 50,
            "Threshold too low — normal sync advances 50+ blocks per cycle"
        );
        assert!(
            BACKEND_CATCHUP_THRESHOLD <= 1000,
            "Threshold too high — would miss real catch-up behavior"
        );
    }

    #[test]
    fn test_rewind_to_height_resets_scan_cursor() {
        let engine = SyncEngine::new("x".to_string());
        engine.set_scanned_height(500);
        engine.set_chain_tip(500);

        let prev = engine.rewind_to_height(200);
        assert_eq!(prev, 500);

        let snap = engine.snapshot();
        assert_eq!(snap.scanned_height, 200);
        // chain_tip_hash should be cleared after rewind
        assert!(!engine.needs_reorg_recovery());
    }

    #[test]
    fn test_needs_reorg_recovery_after_hash_change() {
        let engine = SyncEngine::new("x".to_string());
        // First update — no reorg (first time)
        let reorg1 = engine.update_chain_tip_hash(100, &[0xAA; 32]);
        assert!(!reorg1, "First tip hash should not trigger reorg");
        assert!(!engine.needs_reorg_recovery());

        // Same height, different hash — reorg
        let reorg2 = engine.update_chain_tip_hash(100, &[0xBB; 32]);
        assert!(reorg2, "Same height + different hash = reorg");
        assert!(engine.needs_reorg_recovery());
    }

    #[test]
    fn test_reorg_recovery_clears_flag() {
        let engine = SyncEngine::new("x".to_string());
        engine.update_chain_tip_hash(100, &[0xAA; 32]);
        engine.update_chain_tip_hash(100, &[0xBB; 32]);
        assert!(engine.needs_reorg_recovery());

        // Rewind clears the flag
        engine.rewind_to_height(50);
        assert!(!engine.needs_reorg_recovery(), "rewind should clear reorg flag");

        // Re-detect and test clear_reorg_flag
        engine.update_chain_tip_hash(100, &[0xCC; 32]);
        engine.update_chain_tip_hash(100, &[0xDD; 32]);
        assert!(engine.needs_reorg_recovery());
        engine.clear_reorg_flag();
        assert!(!engine.needs_reorg_recovery(), "clear_reorg_flag should reset");
    }
}
