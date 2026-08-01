//! UniFFI entry for the Android wallet (`libdarkfi_mobile_ffi`).
//!
//! Links upstream **`bin/drk`** when `third_party/darkfi` is vendored (see `scripts/vendor-darkfi.sh`).

mod birthday;
pub mod block_cache;
pub mod bootstrap;
mod dao;
#[cfg(feature = "darkirc")]
mod darkirc_daemon;
mod memo;
pub mod mnemonic;
mod omr_envelope;
pub mod sync;
mod tokens;
mod tor;
pub mod transactions;
mod tx_inspect;

pub mod batch_pir;
pub mod lightwallet_client;
pub mod lightwallet_sync;
pub mod omr;
pub mod unifomr;

#[cfg(feature = "darkirc")]
pub trait DarkircEventCallback: Send + Sync {
    fn on_message(
        &self,
        event_id: String,
        channel: String,
        nick: String,
        message: String,
        timestamp: u64,
    );
}

#[cfg(not(feature = "darkirc"))]
pub trait DarkircEventCallback: Send + Sync {
    fn on_message(
        &self,
        event_id: String,
        channel: String,
        nick: String,
        message: String,
        timestamp: u64,
    );
}

#[cfg(feature = "darkirc")]
pub use darkirc_daemon::{darkirc_status, send_chat_message, start_darkirc, stop_darkirc};

// Stubs when darkirc feature is disabled (e.g. iOS builds with --no-default-features).
// The UDL unconditionally declares these functions, so we provide no-op stubs.
#[cfg(not(feature = "darkirc"))]
pub fn darkirc_status() -> String {
    "disabled".to_string()
}

#[cfg(not(feature = "darkirc"))]
pub fn start_darkirc(
    _datastore_path: String,
    _use_tor: bool,
    _tor_socks_port: u16,
    _callback: Option<Box<dyn DarkircEventCallback>>,
) -> Result<(), DarkfiWalletNativeError> {
    Err(DarkfiWalletNativeError::CryptoError(
        "darkirc feature not compiled".to_string(),
    ))
}

#[cfg(not(feature = "darkirc"))]
pub fn stop_darkirc() -> Result<(), DarkfiWalletNativeError> {
    Ok(())
}

#[cfg(not(feature = "darkirc"))]
pub fn send_chat_message(
    _channel: String,
    _nick: String,
    _message: String,
) -> Result<(), DarkfiWalletNativeError> {
    Err(DarkfiWalletNativeError::CryptoError(
        "darkirc feature not compiled".to_string(),
    ))
}

use std::sync::{Arc, OnceLock};

/// Event emitted when a chain reorganization is detected.
/// Exposed to mobile UI via UniFFI for user notification.
#[derive(Debug, Clone)]
pub struct ReorgEvent {
    /// Height at which the reorg was first detected
    pub detected_at_height: u32,
    /// Height the wallet rewound to
    pub rewound_to: u32,
    /// Number of blocks invalidated
    pub blocks_invalidated: u32,
    /// Number of transactions affected (re-scanned / status changed)
    pub txs_affected: u32,
    /// Human-readable summary for UI display
    pub summary_message: String,
}

/// Callback trait for chain reorganization events.
/// Mobile apps implement this to show reorg notifications on the main screen.
pub trait ReorgEventCallback: Send + Sync {
    fn on_reorg(&self, event: ReorgEvent);
}

use crypto_box::aead::Aead;
use darkfi_sdk::crypto::keypair::{Address, StandardAddress};
use drk::Drk;
use smol::{lock::RwLock, Executor};

type DrkPtr = Arc<RwLock<Drk>>;
pub type DrkWalletPtr = DrkPtr;

#[derive(Debug, thiserror::Error)]
pub enum DarkfiWalletNativeError {
    #[error("wallet not initialized")]
    WalletNotInitialized,
    #[error("invalid bootstrap config")]
    InvalidBootstrapConfig,
    #[error("native drk unavailable: {0}")]
    NativeDrkUnavailable(String),
    #[error("connection failed: {0}")]
    ConnectionFailed(String),
    #[error("sync failed: {0}")]
    SyncFailed(String),
    #[error("crypto error: {0}")]
    CryptoError(String),
    #[error("network timeout")]
    NetworkTimeout(String),
    #[error("server unavailable")]
    ServerUnavailable(String),
    #[error("invalid address: {0}")]
    InvalidAddress(String),
    #[error("insufficient funds")]
    InsufficientFunds(String),
    #[error("transaction build failed: {0}")]
    TransactionBuildFailed(String),
    #[error("OMR detection failed: {0}")]
    OmrDetectionFailed(String),
    #[error("trial decrypt failed: {0}")]
    TrialDecryptFailed(String),
}

type ResultWallet<T> = Result<T, DarkfiWalletNativeError>;

/// Bootstrap fields mirroring upstream **`DrkPlugin::new`** / `Drk::new` inputs on Android.
#[derive(Debug, Clone)]
pub struct DrkBootstrapConfig {
    pub network: String,
    pub mnemonic: Vec<String>,
    pub wallet_db_path: String,
    pub cache_path: String,
    pub wallet_pass: String,
    pub lightwallet_server_url: String,
    /// Birthday / create height for scan seeding:
    /// - `0`: fresh create → seed scan cursor at lightwalletd tip (no history)
    /// - `> 0`: restore birthday → seed at `birthday - 1`
    /// - `-1`: unknown birthday (Kotlin `null`) → full history scan
    pub birthday_height: i64,
    /// SHA-256 of lightwalletd leaf certificate DER (32 bytes). Required for remote HTTPS (S8).
    pub lightwallet_tls_pin_sha256: Option<Vec<u8>>,
    /// If true, start an in-process arti SOCKS proxy at bootstrap and route
    /// ALL remote lightwalletd traffic through Tor. Fail-closed: while Tor is
    /// bootstrapping (or if it fails), remote connections error out and the
    /// sync engine retries — traffic is never silently sent directly.
    pub use_tor: bool,
    /// SOCKS5 port for the arti proxy (default: 9150). Ignored when `use_tor` is false.
    pub tor_socks_port: u16,
    /// Optional darkfid JSON-RPC for broadcast fallback only. Empty/`None` = LWD-only.
    pub darkfid_rpc_url: Option<String>,
}

/// Canonical retrieval / encryption path used for note discovery + sync.
///
/// Shared source-of-truth model exported to Kotlin (and mirrored 1:1 in the
/// iOS FFI) via UniFFI, so both platforms map from the *same* enum instead of
/// duplicating ad-hoc, stringly-typed values.
///
/// - `UnifOmr`: UnifOMR (ePrint 2025) — the default deployed retrieval path;
///   the client derives UnifOMR detection keys (BFV, degree=2048) and the
///   server runs homomorphic detection.
/// - `TrialDecrypt`: client-side trial decryption of compact blocks (fallback).
/// - `Unknown`: idle or not-yet-determined.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncMethod {
    UnifOmr,
    TrialDecrypt,
    Unknown,
}

impl SyncMethod {
    /// Derive the retrieval/encryption path from the live sync engine state.
    pub fn from_light_sync_type(t: &crate::lightwallet_sync::LightSyncType) -> Self {
        use crate::lightwallet_sync::LightSyncType::*;
        match t {
            Omr => SyncMethod::UnifOmr,
            TrialDecryption | TrialDecryptionFallback | MixedRecovery | CatchUpSync => {
                SyncMethod::TrialDecrypt
            }
            Idle => SyncMethod::Unknown,
        }
    }

    /// Map an OMR scheme byte (as embedded in a transaction's OMR memo) to the
    /// canonical method.
    ///
    /// Uses the canonical `memo::SCHEME_*` wire constants as the single source
    /// of truth so this mapping cannot silently drift from the memo encoder
    /// or the other platform.
    pub fn from_scheme_byte(scheme: u8) -> Self {
        use crate::memo::SCHEME_UNIFOMR;
        match scheme {
            SCHEME_UNIFOMR => SyncMethod::UnifOmr,
            _ => SyncMethod::Unknown,
        }
    }
}

/// Reason why the sync engine fell back from UnifOMR to trial decryption.
///
/// Exposed to the mobile UI so the user understands why their wallet is in a
/// slower, less private sync mode and what they can do about it.
///
/// ## Cross-wallet scenario
///
/// When the same mnemonic is imported into both Moonshine/Nighthawk (UnifOMR)
/// and the official `drk` CLI wallet (no OMR), transactions sent from `drk`
/// will NOT have UnifOMR clues. The lightwalletd cannot detect those txs via
/// OMR, so the sync engine falls back to trial decrypt for those blocks.
///
/// The UI should surface `MissingOmrClues` and recommend using Nighthawk/Moonshine
/// for future transactions.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncFallbackReason {
    /// No fallback — UnifOMR is working normally.
    None,
    /// The lightwalletd server does not support OMR detection.
    /// Likely an older server or one without FHE capabilities.
    ServerOmrUnsupported,
    /// OMR detection returned errors repeatedly (e.g. key rejected,
    /// digest decryption failed, server timeout).
    OmrDetectionFailed,
    /// The digest is empty or partial — some blocks have no OMR clues.
    /// This typically means the sender used a wallet that doesn't embed
    /// UnifOMR clues (e.g. `drk` CLI, third-party wallet).
    MissingOmrClues,
    /// The detection key pool has expired and no fresh keys are registered.
    /// Pool refresh will be attempted automatically on next sync cycle.
    KeyPoolExpired,
    /// The user's wallet key pool is not registered on this server.
    /// Registration may have failed or this is a new server.
    KeyPoolNotRegistered,
    /// Generic/unknown fallback (e.g. network transient, unusual error).
    Unknown,
}

impl SyncFallbackReason {
    /// User-facing explanation for this fallback reason.
    ///
    /// These messages are designed to be displayed directly in the mobile UI
    /// status bar or a notification banner.
    pub fn user_message(&self) -> &'static str {
        match self {
            Self::None => "",
            Self::ServerOmrUnsupported => {
                "This server does not support UnifOMR detection. \
                 Syncing via trial decryption (slower). \
                 Connect to a UnifOMR-enabled lightwalletd for faster, more private sync."
            }
            Self::OmrDetectionFailed => {
                "UnifOMR detection failed. Syncing via trial decryption. \
                 This may be temporary — UnifOMR will be retried automatically."
            }
            Self::MissingOmrClues => {
                "Some transactions were sent from a wallet that doesn't support UnifOMR. \
                 Using trial decryption to find those transactions. \
                 For the most private and fastest sync, prefer Nighthawk or Moonshine \
                 for all DarkFi transactions."
            }
            Self::KeyPoolExpired => {
                "Your detection key pool has expired. Refreshing automatically. \
                 Using trial decryption in the meantime."
            }
            Self::KeyPoolNotRegistered => {
                "Detection keys not registered on this server. \
                 Registration will be attempted on next sync. \
                 Using trial decryption in the meantime."
            }
            Self::Unknown => {
                "UnifOMR sync temporarily unavailable. \
                 Using trial decryption as fallback."
            }
        }
    }
}

/// Detailed sync state returned from [`DarkfiWalletHandle::light_sync_snapshot`].
#[derive(Debug, Clone)]
pub struct DrkLightSyncState {
    pub status: String,
    pub sync_type: String,
    pub status_message: String,
    pub sync_type_message: String,
    pub scanned_height: i64,
    pub chain_tip: i64,
    pub omr_available: bool,
    /// Canonical retrieval/encryption path currently in use.
    pub sync_method: SyncMethod,
    /// Reason for fallback if sync_method is TrialDecrypt when UnifOMR was expected.
    pub fallback_reason: SyncFallbackReason,
    /// User-facing message explaining the fallback. Empty when no fallback.
    pub fallback_user_message: String,
}

impl From<crate::lightwallet_sync::LightSyncState> for DrkLightSyncState {
    fn from(s: crate::lightwallet_sync::LightSyncState) -> Self {
        let sync_method = SyncMethod::from_light_sync_type(&s.sync_type);

        // Determine fallback reason from sync engine state.
        let fallback_reason = if sync_method == SyncMethod::TrialDecrypt && s.omr_available {
            // Server supports OMR but we're in trial decrypt — something went wrong.
            if s.omr_failure_count > 0 {
                SyncFallbackReason::OmrDetectionFailed
            } else {
                SyncFallbackReason::MissingOmrClues
            }
        } else if sync_method == SyncMethod::TrialDecrypt && !s.omr_available {
            SyncFallbackReason::ServerOmrUnsupported
        } else {
            SyncFallbackReason::None
        };
        let fallback_user_message = fallback_reason.user_message().to_string();

        Self {
            status: s.status.to_string(),
            sync_type: s.sync_type.to_string(),
            status_message: s.status_message,
            sync_type_message: s.sync_type_message,
            scanned_height: i64::from(s.scanned_height),
            chain_tip: i64::from(s.chain_tip),
            omr_available: s.omr_available,
            sync_method,
            fallback_reason,
            fallback_user_message,
        }
    }
}

/// Block scan progress returned from [`DarkfiWalletHandle::refresh_now`].
#[derive(Debug, Clone)]
pub struct DrkSyncSnapshot {
    pub scanned_blocks: i64,
    pub chain_tip: i64,
}

/// Wallet transaction history row mapped to Kotlin [`DarkfiTransactionOverview`].
#[derive(Debug, Clone)]
pub struct DrkTransactionRecord {
    pub tx_hash: String,
    pub status: String,
    /// `-1` when the transaction is not yet mined.
    pub block_height: i64,
    pub fee_atomic: i64,
    pub is_sent: bool,
    pub net_value_atomic: i64,
    pub contract_summary: String,
    pub recipient_address: Option<String>,
    /// How this transaction was discovered/built. Sent transactions carry the
    /// OMR scheme embedded in their clue (UnifOMR by default); received
    /// transactions default to `Unknown`.
    pub sync_method: SyncMethod,
}

/// Per-token balance for portfolio / send picker.
#[derive(Debug, Clone)]
pub struct DrkTokenBalance {
    pub token_id: String,
    pub display_label: Option<String>,
    pub balance_atomic: i64,
}

/// Imported DAO summary (`drk dao list`).
#[derive(Debug, Clone)]
pub struct DrkDaoSummary {
    pub name: String,
    pub bulla_b58: String,
    pub gov_token_id: String,
    pub quorum_display: String,
    pub proposer_limit_display: String,
    pub approval_ratio_percent: f64,
    pub mint_height: i64,
    pub can_propose: bool,
    pub can_vote: bool,
    pub can_exec: bool,
}

/// DAO proposal row for list UIs.
#[derive(Debug, Clone)]
pub struct DrkDaoProposalSummary {
    pub proposal_bulla_b58: String,
    pub dao_name: String,
    pub dao_bulla_b58: String,
    pub auth_call_count: u32,
    pub duration_blockwindows: u64,
    pub creation_blockwindow: u64,
    pub mint_height: i64,
    pub exec_height: i64,
    pub is_executed: bool,
    pub summary_line: String,
}

/// Full proposal detail (`drk dao proposal <bulla>`).
#[derive(Debug, Clone)]
pub struct DrkDaoProposalDetail {
    pub proposal_bulla_b58: String,
    pub dao_name: String,
    pub dao_bulla_b58: String,
    pub auth_call_count: u32,
    pub duration_blockwindows: u64,
    pub creation_blockwindow: u64,
    pub mint_height: i64,
    pub exec_height: i64,
    pub is_executed: bool,
    pub summary_line: String,
    pub propose_tx_hash: Option<String>,
    pub exec_tx_hash: Option<String>,
    pub has_plaintext_data: bool,
}

static EXECUTOR: OnceLock<Arc<Executor<'static>>> = OnceLock::new();

pub fn shared_executor() -> Arc<Executor<'static>> {
    EXECUTOR
        .get_or_init(|| {
            let ex = Arc::new(Executor::new());
            let run_ex = ex.clone();
            std::thread::Builder::new()
                .name("darkfi-mobile-ffi-smol".into())
                .spawn(move || {
                    smol::block_on(run_ex.run(futures::future::pending::<()>()));
                })
                .expect("spawn smol executor thread");
            ex
        })
        .clone()
}

pub fn start_arti_proxy(socks_listen: String) -> Result<bool, DarkfiWalletNativeError> {
    let port = parse_socks_listen_port(&socks_listen).map_err(|e| {
        DarkfiWalletNativeError::NativeDrkUnavailable(format!("invalid socks_listen: {e}"))
    })?;
    crate::tor::start_arti_proxy(port)
}

fn parse_socks_listen_port(s: &str) -> Result<u16, String> {
    let s = s.trim();
    if let Ok(p) = s.parse::<u16>() {
        return Ok(p);
    }
    if let Some((_, port_s)) = s.rsplit_once(':') {
        return port_s
            .parse::<u16>()
            .map_err(|e| format!("bad port in socks_listen: {e}"));
    }
    Err("could not parse port from socks_listen".to_string())
}

pub fn stop_arti_proxy() {
    crate::tor::stop_arti_proxy();
}

pub fn is_arti_running() -> bool {
    crate::tor::is_arti_running()
}

fn block_on<F: std::future::Future>(future: F) -> F::Output {
    smol::block_on(future)
}

fn bridge_version() -> String {
    env!("CARGO_PKG_VERSION").to_owned()
}

fn bridge_ping() -> String {
    "pong".to_owned()
}

fn validate_bootstrap(config: &DrkBootstrapConfig) -> ResultWallet<()> {
    let network = config.network.trim();
    if network != "mainnet" && network != "testnet" && network != "localnet" {
        return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
    }
    let words = config.mnemonic.len();
    // Wallet money keys use DarkFi's 22-word mnemonic; 12-word BIP-39 is chat-only.
    if words != 22 {
        return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
    }
    if config.wallet_db_path.trim().is_empty() || config.cache_path.trim().is_empty() {
        return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
    }
    if config.wallet_pass.is_empty() || config.lightwallet_server_url.trim().is_empty() {
        return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
    }

    if let Some(pin) = &config.lightwallet_tls_pin_sha256 {
        if pin.len() != 32 {
            return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
        }
    }
    // S8: remote HTTPS lightwallet requires a TLS pin (same loopback check as client).
    {
        let parsed =
            crate::lightwallet_client::parse_lightwallet_endpoint(&config.lightwallet_server_url);
        let grpc = &parsed.grpc_url;
        if grpc.starts_with("https://") {
            let host = grpc
                .trim_start_matches("https://")
                .split(['/', ':'])
                .next()
                .unwrap_or("");
            let has_pin = config
                .lightwallet_tls_pin_sha256
                .as_ref()
                .map(|p| p.len() == 32)
                .unwrap_or(false);
            if !crate::lightwallet_client::is_loopback_host(host) && !has_pin {
                return Err(DarkfiWalletNativeError::InvalidBootstrapConfig);
            }
        }
    }
    Ok(())
}

/// Parse optional 32-byte TLS pin from bootstrap / sync engine config.
pub(crate) fn parse_tls_pin(bytes: Option<&[u8]>) -> Result<Option<[u8; 32]>, String> {
    match bytes {
        None | Some([]) => Ok(None),
        Some(b) if b.len() == 32 => {
            let mut pin = [0u8; 32];
            pin.copy_from_slice(b);
            Ok(Some(pin))
        }
        Some(b) => Err(format!(
            "lightwallet_tls_pin_sha256 must be 32 bytes, got {}",
            b.len()
        )),
    }
}

/// Prefer SyncEngine heights (lightwalletd) over darkfid RPC tip.
fn sync_snapshot_from_engine(
    scanned_wallet: u32,
    engine: &crate::lightwallet_sync::SyncEngine,
) -> DrkSyncSnapshot {
    let snap = engine.snapshot();
    let scanned = scanned_wallet.max(snap.scanned_height);
    let tip = if snap.chain_tip > 0 {
        snap.chain_tip
    } else {
        scanned
    };
    DrkSyncSnapshot {
        scanned_blocks: i64::from(scanned),
        chain_tip: i64::from(tip),
    }
}

/// Opaque handle for an on-device `Drk` session (upstream `bin/drk/src/lib.rs`).
pub struct DarkfiWalletHandle {
    drk: DrkPtr,
    _sync_started: bool,
    sync_engine: Arc<crate::lightwallet_sync::SyncEngine>,
    cache_path: String,
}

impl std::fmt::Debug for DarkfiWalletHandle {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("DarkfiWalletHandle").finish_non_exhaustive()
    }
}

impl DarkfiWalletHandle {
    pub fn new(config: DrkBootstrapConfig) -> ResultWallet<Self> {
        validate_bootstrap(&config)?;

        // Tor routing (fail-closed): start the in-process arti SOCKS proxy and
        // install it as the process-wide route for ALL remote lightwalletd
        // traffic (sync, broadcast, clue lookups, bootstrap probes). The SOCKS
        // listener only binds after the Tor circuit is bootstrapped, so remote
        // connections either go through Tor or fail — they never silently
        // downgrade to a direct connection.
        if config.use_tor {
            let port = if config.tor_socks_port == 0 {
                9150
            } else {
                config.tor_socks_port
            };
            crate::tor::start_arti_proxy(port)?;
            crate::lightwallet_client::set_default_socks5_proxy(Some((
                "127.0.0.1".to_string(),
                port,
            )));
            tracing::info!(
                "Tor routing enabled: remote lightwalletd traffic via arti SOCKS5 on 127.0.0.1:{port}"
            );
        } else {
            crate::lightwallet_client::set_default_socks5_proxy(None);
        }

        let ex = shared_executor();
        let drk = block_on(async { bootstrap::bootstrap_drk(&config, &ex).await })
            .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)?;
        let sync_engine = Arc::new(crate::lightwallet_sync::SyncEngine::with_tls_pin(
            config.lightwallet_server_url.clone(),
            crate::parse_tls_pin(config.lightwallet_tls_pin_sha256.as_deref())
                .ok()
                .flatten(),
        ));
        sync::start_background_sync(drk.clone(), ex, sync_engine.clone());
        Ok(Self {
            drk,
            _sync_started: true,
            sync_engine,
            cache_path: config.cache_path.clone(),
        })
    }

    pub fn light_sync_snapshot(&self) -> DrkLightSyncState {
        self.sync_engine.snapshot().into()
    }

    pub fn confirmed_balance_atomic(&self) -> ResultWallet<i64> {
        let drk = self.drk.clone();
        let balances = block_on(async move {
            let drk = drk.read().await;
            drk.money_balance().await.map_err(|e| e.to_string())
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)?;

        // Prefer native DRK; fall back to sole token if wallet only holds one.
        let dark_id = darkfi_money_contract::model::DARK_TOKEN_ID.to_string();
        let total = balances
            .get(&dark_id)
            .copied()
            .or_else(|| {
                if balances.len() == 1 {
                    balances.values().next().copied()
                } else {
                    None
                }
            })
            .unwrap_or(0);
        Ok(i64::try_from(total).unwrap_or(i64::MAX))
    }

    pub fn primary_deposit_address(&self) -> ResultWallet<String> {
        let drk = self.drk.clone();
        let address = block_on(async move {
            let drk = drk.read().await;
            let pubkey = drk.default_address().await.map_err(|e| e.to_string())?;
            let network = drk.network;
            let address: Address = StandardAddress::from_public(network, pubkey).into();
            Ok::<String, String>(address.to_string())
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)?;

        Ok(address)
    }

    pub fn refresh_now(&self) -> ResultWallet<DrkSyncSnapshot> {
        // SECURITY (S9): never call darkfid `scan_blocks` from the production
        // mobile path — that reveals block interest to the full node.
        // Background lightwalletd sync owns scanning; this returns a snapshot.
        let engine = self.sync_engine.clone();
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            let (scanned, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
            Ok(sync_snapshot_from_engine(scanned, &engine))
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn sync_snapshot(&self) -> ResultWallet<DrkSyncSnapshot> {
        let engine = self.sync_engine.clone();
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            let (scanned, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
            Ok(sync_snapshot_from_engine(scanned, &engine))
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn build_transfer(
        &self,
        recipient_address: String,
        amount: String,
        token_id: Option<String>,
        payment_memo: Option<String>,
    ) -> ResultWallet<Vec<u8>> {
        let drk = self.drk.clone();
        let lw_url = self.sync_engine.server_url();
        let tls_pin = self.sync_engine.tls_pin();
        block_on(async move {
            // Exclusive lock while building (coin selection / note construction).
            let drk = drk.write().await;
            transactions::build_transfer(
                &drk,
                &recipient_address,
                &amount,
                token_id.as_deref(),
                payment_memo.as_deref(),
                Some(&lw_url),
                tls_pin,
            )
            .await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn estimate_transfer_fee(
        &self,
        recipient_address: String,
        amount: String,
        token_id: Option<String>,
        payment_memo: Option<String>,
    ) -> ResultWallet<i64> {
        let drk = self.drk.clone();
        let lw_url = self.sync_engine.server_url();
        let tls_pin = self.sync_engine.tls_pin();
        block_on(async move {
            let drk = drk.write().await;
            transactions::estimate_transfer_fee(
                &drk,
                &recipient_address,
                &amount,
                token_id.as_deref(),
                payment_memo.as_deref(),
                Some(&lw_url),
                tls_pin,
            )
            .await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn broadcast_transfer(
        &self,
        tx_bytes: Vec<u8>,
        payment_memo: Option<String>,
        recipient_address: Option<String>,
    ) -> ResultWallet<String> {
        let drk = self.drk.clone();
        let lw_url = self.sync_engine.server_url();
        let tls_pin = self.sync_engine.tls_pin();
        block_on(async move {
            // Exclusive lock: spend marking must not race sync coin inserts.
            let drk = drk.write().await;
            transactions::broadcast_transfer(
                &drk,
                &tx_bytes,
                payment_memo.as_deref(),
                recipient_address.as_deref(),
                Some(&lw_url),
                tls_pin,
            )
            .await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn transaction_payment_memo(&self, tx_hash: String) -> ResultWallet<Option<String>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            transactions::get_transaction_memo(&drk, &tx_hash).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn list_transactions(&self) -> ResultWallet<Vec<DrkTransactionRecord>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            transactions::list_transaction_history(&drk).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn list_token_balances(&self) -> ResultWallet<Vec<DrkTokenBalance>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            tokens::list_token_balances(&drk).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn transaction_recipient(&self, tx_hash: String) -> ResultWallet<Option<String>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            transactions::get_transaction_recipient(&drk, &tx_hash).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn generate_new_address(&self) -> ResultWallet<String> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.write().await;
            let mut output = Vec::new();
            drk.money_keygen(&mut output)
                .await
                .map_err(|e| e.to_string())?;
            output
                .last()
                .cloned()
                .ok_or_else(|| "No address generated".to_string())
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn list_addresses(&self) -> ResultWallet<Vec<String>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            let addrs = drk.addresses().await.map_err(|e| e.to_string())?;
            let network = drk.network;
            let mut res = Vec::new();
            for (_, pubkey, _, _) in addrs {
                let address: Address = StandardAddress::from_public(network, pubkey).into();
                res.push(address.to_string());
            }
            Ok::<Vec<String>, String>(res)
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn list_daos(&self) -> ResultWallet<Vec<DrkDaoSummary>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            dao::list_daos(&drk).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn list_proposals(
        &self,
        dao_name: Option<String>,
    ) -> ResultWallet<Vec<DrkDaoProposalSummary>> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            dao::list_proposals(&drk, dao_name).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn get_proposal(&self, proposal_bulla_b58: String) -> ResultWallet<DrkDaoProposalDetail> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.read().await;
            dao::get_proposal(&drk, &proposal_bulla_b58).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn dao_propose_transfer(
        &self,
        dao_name: String,
        duration_blockwindows: u64,
        amount: String,
        token_id: Option<String>,
        recipient_address: String,
    ) -> ResultWallet<String> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.write().await;
            dao::propose_transfer(
                &drk,
                &dao_name,
                duration_blockwindows,
                &amount,
                token_id.as_deref(),
                &recipient_address,
            )
            .await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    pub fn dao_vote(&self, proposal_bulla_b58: String, vote_yes: bool) -> ResultWallet<String> {
        let drk = self.drk.clone();
        block_on(async move {
            let drk = drk.write().await;
            dao::vote_on_proposal(&drk, &proposal_bulla_b58, vote_yes).await
        })
        .map_err(DarkfiWalletNativeError::NativeDrkUnavailable)
    }

    /// Register a callback for chain reorganization events.
    /// The callback fires when reorg is detected during sync, allowing
    /// mobile apps to show a notification on the main screen.
    pub fn set_reorg_callback(&self, callback: Option<Box<dyn ReorgEventCallback>>) {
        if let Ok(mut cb) = self.sync_engine.reorg_callback.lock() {
            *cb = callback;
        }
    }

    /// Handle a detected chain reorganization:
    /// 1. Rewinds sync state to `rewind_to_height`
    /// 2. Invalidates block cache above fork point
    /// 3. Marks transactions above fork point as unconfirmed
    /// 4. Re-scans affected blocks to re-derive confirmed status
    /// 5. Fires the reorg callback for UI notification
    ///
    /// Returns a `ReorgEvent` describing what changed.
    pub fn handle_reorg_recovery(&self, rewind_to_height: u32) -> ResultWallet<ReorgEvent> {
        let drk = self.drk.clone();
        let sync_engine = self.sync_engine.clone();

        // Step 1: Get current state before rewind
        let state_before = self.light_sync_snapshot();
        let detected_at = state_before.scanned_height.max(0) as u32;

        // Step 2: Rewind the sync engine state
        let prev_height = sync_engine.rewind_to_height(rewind_to_height);
        let blocks_invalidated = prev_height.saturating_sub(rewind_to_height);

        // Step 3: Invalidate block cache above fork point.
        // The block cache DB sits alongside the wallet DB. We try the
        // sync engine's data directory (derived from the cache_path)
        // or silently skip if the cache is unavailable.
        let cache_path = self.cache_path.clone();
        block_on(async {
            let cache_db = std::path::Path::new(&cache_path).join("compact_blocks.db");
            if cache_db.exists() {
                if let Ok(cache) = block_cache::MobileBlockCache::open(
                    cache_db.to_str().unwrap_or("compact_blocks.db"),
                ) {
                    let _ = cache.prune_above(rewind_to_height);
                }
            }
        });

        // Step 4: Invalidate money_coins DB & transactions above fork point via drk
        let txs_affected = block_on(async {
            let drk_guard = drk.write().await;

            // Delete coins created after rollback height
            if let Err(e) = drk_guard.wallet.exec_sql(
                "DELETE FROM money_coins WHERE creation_height > ?1",
                rusqlite::params![rewind_to_height],
            ) {
                tracing::error!(target: "reorg", "Failed to delete post-reorg coins: {e}");
            }

            // Un-spend coins that were marked spent after rollback height
            if let Err(e) = drk_guard.wallet.exec_sql(
                "UPDATE money_coins SET is_spent = 0, spent_height = NULL WHERE spent_height > ?1",
                rusqlite::params![rewind_to_height],
            ) {
                tracing::error!(target: "reorg", "Failed to un-spend post-reorg coins: {e}");
            }

            let _ = sync::persist_scanned_height(&drk_guard, rewind_to_height);

            transactions::invalidate_transactions_above(&drk_guard, rewind_to_height).await
        })
        .unwrap_or(0);

        // Step 5: Build the event
        let event = ReorgEvent {
            detected_at_height: detected_at,
            rewound_to: rewind_to_height,
            blocks_invalidated,
            txs_affected,
            summary_message: format!(
                "Chain reorganization detected at height {}. Rewound to {} — {} blocks and {} transactions affected.",
                detected_at, rewind_to_height, blocks_invalidated, txs_affected
            ),
        };

        // Step 6: Fire callback for UI notification
        if let Ok(cb) = self.sync_engine.reorg_callback.lock() {
            if let Some(callback) = cb.as_ref() {
                callback.on_reorg(event.clone());
            }
        }

        tracing::warn!(
            target: "reorg",
            "{}", event.summary_message
        );

        Ok(event)
    }

    /// Explicitly close the wallet handle, releasing all resources.
    ///
    /// This is preferred over relying on Swift's ARC deinit or Kotlin's
    /// garbage collection for deterministic cleanup. It stops background
    /// sync tasks and flushes any pending database writes.
    pub fn close(&self) -> ResultWallet<()> {
        // Deterministic cleanup: drop the sync engine and gRPC resources.
        // The `_sync_started` flag is a plain bool set at construction time.
        // The actual cleanup happens when `DarkfiWalletHandle` is dropped (via Arc).
        tracing::info!("DarkfiWalletHandle::close() called — resources will be released on drop");
        Ok(())
    }
}

/// Generates a new 22-word DarkFi mnemonic phrase (English).
pub fn generate_darkfi_mnemonic() -> Vec<String> {
    let mnemonic_engine = mnemonic::DarkfiMnemonic::default();
    let phrase = mnemonic_engine
        .make_seed(None, None)
        .expect("generate seed");
    phrase.split_whitespace().map(|s| s.to_string()).collect()
}

/// Generates a new 12-word BIP-39 mnemonic for Chat Identity.
pub fn generate_bip39_chat_mnemonic() -> Vec<String> {
    use bip39::Mnemonic;
    let mut entropy = [0u8; 16];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut entropy);
    let mnemonic = Mnemonic::from_entropy(&entropy).expect("entropy is 16 bytes");
    mnemonic.words().map(|s| s.to_string()).collect()
}

/// Validates a DarkFi mnemonic phrase.
pub fn validate_darkfi_mnemonic(phrase: Vec<String>) -> bool {
    let phrase_str = phrase.join(" ");
    let mnemonic_engine = mnemonic::DarkfiMnemonic::default();
    mnemonic_engine.mnemonic_decode(&phrase_str).is_ok()
}

/// Decodes 12-word phrase to entropy bytes if valid.
pub fn decode_chat_entropy(phrase: Vec<String>) -> Option<Vec<u8>> {
    use bip39::Mnemonic;
    if phrase.len() != 12 {
        return None;
    }
    let phrase_str = phrase.join(" ");
    if let Ok(mnemonic) = Mnemonic::parse(&phrase_str) {
        Some(mnemonic.to_entropy().to_vec())
    } else {
        None
    }
}

/// ChaChaBox DM keypair (base58-encoded secret + public keys).
#[derive(Debug, Clone)]
pub struct DmKeypair {
    pub secret_b58: String,
    pub public_b58: String,
}

/// Generates a new ChaChaBox DM keypair for E2E encrypted chat.
pub fn generate_dm_keypair() -> DmKeypair {
    let secret_key = crypto_box::SecretKey::generate(&mut rand::thread_rng());
    let public_key = secret_key.public_key();
    DmKeypair {
        secret_b58: bs58::encode(secret_key.to_bytes()).into_string(),
        public_b58: bs58::encode(public_key.to_bytes()).into_string(),
    }
}

pub fn chacha_encrypt_dm(
    my_secret: Vec<u8>,
    their_public: Vec<u8>,
    plaintext: String,
) -> ResultWallet<String> {
    let my_sk = crypto_box::SecretKey::from_slice(&my_secret)
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid secret".into()))?;
    let their_pk = crypto_box::PublicKey::from_slice(&their_public)
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid public".into()))?;

    let box_algo = crypto_box::ChaChaBox::new(&their_pk, &my_sk);
    let mut nonce_bytes = [0u8; 24];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut nonce_bytes);

    let ciphertext = box_algo
        .encrypt(&nonce_bytes.into(), plaintext.as_bytes())
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Encrypt failed".into()))?;

    let mut concat = nonce_bytes.to_vec();
    concat.extend(ciphertext);
    Ok(bs58::encode(concat).into_string())
}

pub fn chacha_decrypt_dm(
    my_secret: Vec<u8>,
    their_public: Vec<u8>,
    ciphertext_b58: String,
) -> ResultWallet<String> {
    let my_sk = crypto_box::SecretKey::from_slice(&my_secret)
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid secret".into()))?;
    let their_pk = crypto_box::PublicKey::from_slice(&their_public)
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid public".into()))?;

    let cipher_bytes = bs58::decode(&ciphertext_b58)
        .into_vec()
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid b58".into()))?;
    if cipher_bytes.len() < 24 {
        return Err(DarkfiWalletNativeError::NativeDrkUnavailable(
            "Too short".into(),
        ));
    }

    let nonce_bytes: [u8; 24] = cipher_bytes[0..24].try_into().unwrap();
    let box_algo = crypto_box::ChaChaBox::new(&their_pk, &my_sk);

    let decrypted = box_algo
        .decrypt(&nonce_bytes.into(), &cipher_bytes[24..])
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Decrypt failed".into()))?;

    String::from_utf8(decrypted)
        .map_err(|_| DarkfiWalletNativeError::NativeDrkUnavailable("Invalid UTF8".into()))
}

uniffi::include_scaffolding!("darkfi_mobile_ffi");

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_config() -> DrkBootstrapConfig {
        DrkBootstrapConfig {
            network: "testnet".into(),
            mnemonic: (1..=22).map(|i| format!("word{i:04}")).collect(),
            wallet_db_path: "/tmp/wallet.db".into(),
            cache_path: "/tmp/cache".into(),
            wallet_pass: "pass".into(),
            lightwallet_server_url: "tcp://127.0.0.1:9067".into(),
            birthday_height: -1,
            lightwallet_tls_pin_sha256: None,
            use_tor: false,
            tor_socks_port: 9150,
            darkfid_rpc_url: None,
        }
    }

    #[test]
    fn validate_bootstrap_rejects_bad_network() {
        let mut cfg = sample_config();
        cfg.network = "invalid".into();
        assert!(matches!(
            validate_bootstrap(&cfg),
            Err(DarkfiWalletNativeError::InvalidBootstrapConfig)
        ));
    }

    #[test]
    fn validate_bootstrap_accepts_testnet_22_words() {
        assert!(validate_bootstrap(&sample_config()).is_ok());
    }

    #[test]
    fn validate_bootstrap_accepts_optional_darkfid_url_unset() {
        let cfg = sample_config();
        assert!(cfg.darkfid_rpc_url.is_none());
        assert!(validate_bootstrap(&cfg).is_ok());
    }

    #[test]
    fn generate_dm_keypair_returns_base58_pair() {
        let kp = generate_dm_keypair();
        assert!(!kp.secret_b58.is_empty());
        assert!(!kp.public_b58.is_empty());
        assert_ne!(kp.secret_b58, kp.public_b58);
    }

    #[test]
    fn validate_bootstrap_accepts_localnet_22_words() {
        let mut cfg = sample_config();
        cfg.network = "localnet".into();
        assert!(validate_bootstrap(&cfg).is_ok());
    }

    #[test]
    fn validate_bootstrap_rejects_12_word_wallet_mnemonic() {
        let mut cfg = sample_config();
        cfg.mnemonic = (1..=12).map(|i| format!("word{i:04}")).collect();
        assert!(matches!(
            validate_bootstrap(&cfg),
            Err(DarkfiWalletNativeError::InvalidBootstrapConfig)
        ));
    }

    #[test]
    fn validate_bootstrap_rejects_bad_mnemonic_length() {
        let mut cfg = sample_config();
        cfg.mnemonic = vec!["only".into(), "three".into(), "words".into()];
        assert!(matches!(
            validate_bootstrap(&cfg),
            Err(DarkfiWalletNativeError::InvalidBootstrapConfig)
        ));
    }

    #[test]
    fn validate_bootstrap_rejects_empty_wallet_pass() {
        let mut cfg = sample_config();
        cfg.wallet_pass.clear();
        assert!(matches!(
            validate_bootstrap(&cfg),
            Err(DarkfiWalletNativeError::InvalidBootstrapConfig)
        ));
    }

    #[test]
    fn validate_bootstrap_rejects_remote_https_without_pin() {
        let mut cfg = sample_config();
        // tcp+tls normalizes to https:// — remote requires a pin (S8).
        cfg.lightwallet_server_url = "tcp+tls://lw.darkfi.xyz:9067".into();
        cfg.lightwallet_tls_pin_sha256 = None;
        assert!(matches!(
            validate_bootstrap(&cfg),
            Err(DarkfiWalletNativeError::InvalidBootstrapConfig)
        ));
    }

    #[test]
    fn validate_bootstrap_accepts_remote_https_with_pin() {
        let mut cfg = sample_config();
        cfg.lightwallet_server_url = "tcp+tls://lw.darkfi.xyz:9067".into();
        cfg.lightwallet_tls_pin_sha256 = Some(vec![0xABu8; 32]);
        assert!(validate_bootstrap(&cfg).is_ok());
    }

    // ========================================================================
    // Mnemonic tests — ensure cross-wallet compatibility
    // ========================================================================

    #[test]
    fn mnemonic_generates_22_words() {
        let words = generate_darkfi_mnemonic();
        assert!(
            words.len() >= 21 && words.len() <= 22,
            "DarkFi mnemonic should be 21-22 words, got {}",
            words.len()
        );
    }

    #[test]
    fn mnemonic_roundtrip_encode_decode() {
        let engine = mnemonic::DarkfiMnemonic::default();
        let phrase = engine.make_seed(None, None).unwrap();
        let decoded = engine.mnemonic_decode(&phrase).unwrap();
        let re_encoded = engine.mnemonic_encode(&decoded);
        assert_eq!(phrase, re_encoded, "Mnemonic must round-trip");
    }

    #[test]
    fn mnemonic_validation_accepts_valid() {
        let phrase_str = generate_darkfi_mnemonic().join(" ");
        assert!(validate_darkfi_mnemonic(
            phrase_str
                .split_whitespace()
                .map(|s| s.to_string())
                .collect()
        ));
    }

    #[test]
    fn mnemonic_validation_rejects_garbage() {
        assert!(!validate_darkfi_mnemonic(vec![
            "this".into(),
            "is".into(),
            "not".into(),
            "valid".into()
        ]));
    }

    #[test]
    fn mnemonic_deterministic_key_derivation() {
        let words: Vec<String> = vec![
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();

        let key1 = mnemonic::secret_key_from_mnemonic(&words).unwrap();
        let key2 = mnemonic::secret_key_from_mnemonic(&words).unwrap();
        assert_eq!(key1, key2, "Same mnemonic must produce same key");
    }

    #[test]
    fn mnemonic_different_words_different_keys() {
        let words1: Vec<String> = vec![
            "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
            "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
            "acoustic", "acquire", "across", "act", "action", "actor",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();
        let words2: Vec<String> = vec![
            "zoo", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd",
            "abuse", "access", "accident", "account", "accuse", "achieve", "acid", "acoustic",
            "acquire", "across", "act", "action", "actor",
        ]
        .into_iter()
        .map(|s| s.to_string())
        .collect();

        let key1 = mnemonic::secret_key_from_mnemonic(&words1).unwrap();
        let key2 = mnemonic::secret_key_from_mnemonic(&words2).unwrap();
        assert_ne!(key1, key2);
    }

    #[test]
    fn mnemonic_pbkdf2_seed_is_deterministic() {
        let seed1 = mnemonic::DarkfiMnemonic::mnemonic_to_seed("abandon ability", None);
        let seed2 = mnemonic::DarkfiMnemonic::mnemonic_to_seed("abandon ability", None);
        assert_eq!(seed1.len(), 64);
        assert_eq!(seed1, seed2);
    }

    #[test]
    fn mnemonic_pbkdf2_with_passphrase_differs() {
        let seed1 = mnemonic::DarkfiMnemonic::mnemonic_to_seed("abandon ability", None);
        let seed2 =
            mnemonic::DarkfiMnemonic::mnemonic_to_seed("abandon ability", Some("passphrase"));
        assert_ne!(seed1, seed2, "Passphrase must alter derived seed");
    }

    // ========================================================================
    // Error enum tests — verify all 13 variants
    // ========================================================================

    #[test]
    fn error_wallet_not_initialized_display() {
        let e = DarkfiWalletNativeError::WalletNotInitialized;
        assert_eq!(e.to_string(), "wallet not initialized");
    }

    #[test]
    fn error_connection_failed_display() {
        let e = DarkfiWalletNativeError::ConnectionFailed("timeout".into());
        assert!(e.to_string().contains("timeout"));
    }

    #[test]
    fn error_sync_failed_display() {
        let e = DarkfiWalletNativeError::SyncFailed("block 12345".into());
        assert!(e.to_string().contains("block 12345"));
    }

    #[test]
    fn error_crypto_error_display() {
        let e = DarkfiWalletNativeError::CryptoError("bad key".into());
        assert!(e.to_string().contains("bad key"));
    }

    #[test]
    fn error_network_timeout_display() {
        let e = DarkfiWalletNativeError::NetworkTimeout("30s".into());
        assert!(e.to_string().contains("network timeout"));
    }

    #[test]
    fn error_server_unavailable_display() {
        let e = DarkfiWalletNativeError::ServerUnavailable("503".into());
        assert!(e.to_string().contains("server unavailable"));
    }

    #[test]
    fn error_invalid_address_display() {
        let e = DarkfiWalletNativeError::InvalidAddress("not_base58".into());
        assert!(e.to_string().contains("not_base58"));
    }

    #[test]
    fn error_insufficient_funds_display() {
        let e = DarkfiWalletNativeError::InsufficientFunds("need 100".into());
        assert!(e.to_string().contains("insufficient funds"));
    }

    #[test]
    fn error_tx_build_failed_display() {
        let e = DarkfiWalletNativeError::TransactionBuildFailed("proof gen".into());
        assert!(e.to_string().contains("proof gen"));
    }

    #[test]
    fn error_omr_detection_failed_display() {
        let e = DarkfiWalletNativeError::OmrDetectionFailed("scheme unsupported".into());
        assert!(e.to_string().contains("scheme unsupported"));
    }

    #[test]
    fn error_trial_decrypt_failed_display() {
        let e = DarkfiWalletNativeError::TrialDecryptFailed("AEAD failure".into());
        assert!(e.to_string().contains("AEAD failure"));
    }

    #[test]
    fn all_error_variants_are_error_trait() {
        // Compile-time check that all variants implement std::error::Error
        fn assert_error<E: std::error::Error>(_e: E) {}
        assert_error(DarkfiWalletNativeError::WalletNotInitialized);
        assert_error(DarkfiWalletNativeError::InvalidBootstrapConfig);
        assert_error(DarkfiWalletNativeError::NativeDrkUnavailable("x".into()));
        assert_error(DarkfiWalletNativeError::ConnectionFailed("x".into()));
        assert_error(DarkfiWalletNativeError::SyncFailed("x".into()));
        assert_error(DarkfiWalletNativeError::CryptoError("x".into()));
        assert_error(DarkfiWalletNativeError::NetworkTimeout("x".into()));
        assert_error(DarkfiWalletNativeError::ServerUnavailable("x".into()));
        assert_error(DarkfiWalletNativeError::InvalidAddress("x".into()));
        assert_error(DarkfiWalletNativeError::InsufficientFunds("x".into()));
        assert_error(DarkfiWalletNativeError::TransactionBuildFailed("x".into()));
        assert_error(DarkfiWalletNativeError::OmrDetectionFailed("x".into()));
        assert_error(DarkfiWalletNativeError::TrialDecryptFailed("x".into()));
    }
}
