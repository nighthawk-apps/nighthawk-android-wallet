//! Transfer build/broadcast and wallet transaction history for UniFFI.

use std::collections::{HashMap, HashSet, VecDeque};
use std::str::FromStr;
use std::sync::LazyLock;
use std::sync::{Mutex, RwLock};

use darkfi::tx::Transaction;
use darkfi_money_contract::{client::MoneyNote, model::MoneyTransferParamsV1, MoneyFunction};
use darkfi_sdk::crypto::contract_id::MONEY_CONTRACT_ID;
use darkfi_sdk::crypto::keypair::Address;
use darkfi_sdk::crypto::ContractId;
use darkfi_sdk::pasta::group::ff::PrimeField;
use darkfi_serial::{deserialize_async, serialize_async};
use drk::Drk;

use crate::{
    omr_envelope::{parse_envelope, strip_envelope, wrap_envelope},
    tx_inspect::{contract_summary_for_tx, net_value_atomic, outgoing_recipient},
    DrkTransactionRecord, SyncMethod,
};

/// Session-scoped bounded cache with FIFO eviction.
///
/// Oldest *inserted* key is dropped when [`BoundedCache::insert`] would
/// exceed `cap`. Updating an existing key does not refresh its position.
struct BoundedCache<V> {
    map: HashMap<String, V>,
    insertion_order: VecDeque<String>,
    cap: usize,
}

impl<V> BoundedCache<V> {
    fn with_capacity(cap: usize) -> Self {
        Self {
            map: HashMap::new(),
            insertion_order: VecDeque::new(),
            cap: cap.max(1),
        }
    }

    fn new() -> Self {
        Self::with_capacity(MAX_SENT_CACHE_ENTRIES)
    }

    #[cfg(test)]
    fn len(&self) -> usize {
        self.map.len()
    }

    fn get(&self, key: &str) -> Option<&V> {
        self.map.get(key)
    }

    fn contains_key(&self, key: &str) -> bool {
        self.map.contains_key(key)
    }

    fn insert(&mut self, key: String, val: V) {
        if self.map.len() >= self.cap && !self.map.contains_key(&key) {
            if let Some(oldest) = self.insertion_order.pop_front() {
                self.map.remove(&oldest);
            }
        }
        if !self.map.contains_key(&key) {
            self.insertion_order.push_back(key.clone());
        }
        self.map.insert(key, val);
    }

    fn remove(&mut self, key: &str) -> Option<V> {
        self.insertion_order.retain(|k| k != key);
        self.map.remove(key)
    }

    fn clear(&mut self) {
        self.map.clear();
        self.insertion_order.clear();
    }
}

/// Per-session record of the OMR scheme byte embedded in each outgoing
/// transaction's clue, keyed by tx hash.
static SENT_SYNC_SCHEMES: LazyLock<RwLock<BoundedCache<u8>>> =
    LazyLock::new(|| RwLock::new(BoundedCache::new()));

/// Per-session payment memo + recipient for txs we broadcast this process.
static SENT_PAYMENT_META: LazyLock<RwLock<BoundedCache<(Option<String>, Option<String>)>>> =
    LazyLock::new(|| RwLock::new(BoundedCache::new()));

/// Session set of tx hashes confirmed to have landed in lightwalletd mempool.
static CONFIRMED_BROADCASTS: LazyLock<RwLock<BoundedCache<()>>> =
    LazyLock::new(|| RwLock::new(BoundedCache::new()));

/// Prevents concurrent duplicate broadcasts of the same tx while the first is in flight
/// without ever returning `Ok` before mempool confirmation succeeds.
static IN_FLIGHT_BROADCASTS: LazyLock<Mutex<HashSet<String>>> =
    LazyLock::new(|| Mutex::new(HashSet::new()));

struct InFlightBroadcastGuard {
    tx_hash: String,
}

impl InFlightBroadcastGuard {
    fn try_acquire(tx_hash: &str) -> Result<Self, String> {
        let mut guard = IN_FLIGHT_BROADCASTS
            .lock()
            .map_err(|_| "broadcast guard mutex poisoned".to_string())?;
        if !guard.insert(tx_hash.to_string()) {
            return Err(format!("broadcast already in progress for {tx_hash}"));
        }
        Ok(Self {
            tx_hash: tx_hash.to_string(),
        })
    }
}

impl Drop for InFlightBroadcastGuard {
    fn drop(&mut self) {
        if let Ok(mut guard) = IN_FLIGHT_BROADCASTS.lock() {
            guard.remove(&self.tx_hash);
        }
    }
}

const MAX_SENT_CACHE_ENTRIES: usize = 10_000;

fn zkas_lookup_fallback(contract_id: &ContractId) -> darkfi::Result<Vec<(String, Vec<u8>)>> {
    let entries =
        crate::zkas_cache::global_zkas_cache().entries_for_contract(&contract_id.to_string());
    if entries.is_empty() {
        return Err(darkfi::Error::Custom(format!(
            "zkas cache empty for {contract_id}; LookupZkas prefetch required"
        )));
    }
    Ok(entries)
}

async fn prefetch_money_zkas(
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    let money_id = MONEY_CONTRACT_ID.to_string();
    prefetch_contract_zkas(lightwallet_server_url, lightwallet_tls_pin, &[&money_id]).await
}

/// Fetch circuit bincodes before a contract call. `dao_mint` reads this cache
/// and does not dial `LookupZkas` itself.
pub async fn prefetch_contract_zkas(
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
    contract_ids: &[&str],
) -> Result<(), String> {
    drk::rpc::set_zkas_lookup_fallback(Some(zkas_lookup_fallback));
    let Some(url) = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        return Ok(());
    };
    let lw_url = crate::lightwallet_client::normalize_lightwallet_url(url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &lw_url,
        lightwallet_tls_pin,
    );
    let warmed = crate::zkas_cache::warm_zkas_cache(&client, contract_ids).await?;
    if warmed == 0 {
        let missing = contract_ids.iter().any(|id| {
            crate::zkas_cache::global_zkas_cache()
                .entries_for_contract(id)
                .is_empty()
        });
        if missing {
            return Err(format!(
                "LookupZkas returned no circuits for {}",
                contract_ids.join(", ")
            ));
        }
    }
    Ok(())
}

/// Conservative TransferV1+FeeV1 schedule used by estimate (no Halo2 / darkfid).
/// Matches Moonshine's default `--fee` overpay on testnet.
pub const SCHEDULE_TRANSFER_FEE_ATOMIC: i64 = 5_000_000;

/// Clear session caches (call on wallet close / re-bootstrap).
pub fn clear_sent_session_cache() {
    if let Ok(mut m) = SENT_SYNC_SCHEMES.write() {
        m.clear();
    }
    if let Ok(mut m) = SENT_PAYMENT_META.write() {
        m.clear();
    }
}

/// Rows `revert_transactions_after` will match: `block_height > rewind_height`.
/// Null heights (mempool / already reverted) are excluded — same as the SQL WHERE.
fn count_history_rows_above_height(
    history: &[(String, String, Option<u32>)],
    rewind_height: u32,
) -> u32 {
    history
        .iter()
        .filter(|(_, _, h)| h.is_some_and(|hh| hh > rewind_height))
        .count() as u32
}

/// Look up a recipient address persisted at broadcast time (session cache).
pub fn sent_recipient_address(tx_hash: &str) -> Option<String> {
    SENT_PAYMENT_META
        .read()
        .ok()
        .and_then(|m| m.get(tx_hash).and_then(|(_, r)| r.clone()))
}

fn cache_and_persist_payment_meta(
    drk: &Drk,
    tx_hash: &str,
    memo: Option<String>,
    recipient: Option<String>,
) {
    if let Ok(mut cache) = SENT_PAYMENT_META.write() {
        cache.insert(tx_hash.to_string(), (memo.clone(), recipient.clone()));
    }
    crate::sync::persist_sent_payment_meta(drk, tx_hash, memo.as_deref(), recipient.as_deref());
}

fn derive_omr_memo_key(secret_bytes: &[u8; 32], recipient_pk: &[u8; 32], nonce: &[u8]) -> [u8; 32] {
    let mut out = [0u8; 32];
    let mut hasher = blake3::Hasher::new_derive_key("DarkFi-OMR-MemoKey-v1");
    hasher.update(secret_bytes);
    hasher.update(recipient_pk);
    hasher.update(nonce);
    out.copy_from_slice(hasher.finalize().as_bytes());
    out
}

pub async fn build_transfer(
    drk: &Drk,
    recipient_address: &str,
    amount: &str,
    token_id: Option<&str>,
    payment_memo: Option<&str>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
    experimental_unifomr: bool,
) -> Result<Vec<u8>, String> {
    let recipient = Address::from_str(recipient_address.trim())
        .map_err(|e| format!("recipient address: {e}"))?;
    let token_input = token_id
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("DRK")
        .to_string();
    let token = drk
        .get_token(token_input)
        .await
        .map_err(|e| format!("token: {e}"))?;

    let secret = drk
        .default_secret()
        .await
        .map_err(|e| format!("Failed to get wallet secret for OMR clue: {e}"))?;
    let secret_bytes = zeroize::Zeroizing::new(secret.inner().to_repr());
    let recipient_pk_bytes: [u8; 32] = recipient.public_key().to_bytes();

    let network_byte = match drk.network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => 0x00u8,
        darkfi_sdk::crypto::keypair::Network::Testnet => 0x01u8,
    };

    if !experimental_unifomr {
        crate::sync::ensure_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin)
            .await
            .map_err(|e| format!("merkle rebuild for spend: {e}"))?;
        prefetch_money_zkas(lightwallet_server_url, lightwallet_tls_pin).await?;
        let memo_bytes = crate::memo::parse_payment_memo(payment_memo)?.unwrap_or_default();
        let tx = drk
            .transfer(
                amount,
                token,
                *recipient.public_key(),
                None,
                None,
                false,
                memo_bytes,
            )
            .await
            .map_err(|e| format!("transfer: {e}"))?;
        return Ok(serialize_async(&tx).await);
    }

    // Experimental UnifOMR: require a registered clue public key.
    let (omr_scheme, omr_clue) = resolve_outgoing_omr_clue(
        &recipient_pk_bytes,
        network_byte,
        lightwallet_server_url,
        lightwallet_tls_pin,
    )
    .await?;

    // Per-tx memo key (domain-separated) — never reuse raw spending key bytes.
    let mut nonce = [0u8; 16];
    rand::RngCore::fill_bytes(&mut rand::rngs::OsRng, &mut nonce);
    let memo_key = derive_omr_memo_key(&secret_bytes, &recipient_pk_bytes, &nonce);

    let omr_memo = crate::memo::build_omr_memo(
        &memo_key,
        &recipient_pk_bytes,
        payment_memo,
        Some(omr_scheme),
    )?;

    // Encrypt the OMR metadata for the recipient (LWD cannot read it).
    // Bind clue hash into AEAD plaintext so clue swap is detectable by recipient.
    let mut omr_memo_bound = omr_memo;
    let clue_hash = *blake3::hash(&omr_clue).as_bytes();
    omr_memo_bound.extend_from_slice(b"|CLUE|");
    omr_memo_bound.extend_from_slice(&clue_hash);
    let omr_metadata_enc =
        crate::memo::encrypt_omr_metadata(&omr_memo_bound, recipient.public_key())?;

    tracing::info!(
        target: "wallet-tx",
        "OMR clue: scheme=0x{omr_scheme:02x}, clue_len={}, metadata_enc_len={}, user_memo_present={}",
        omr_clue.len(),
        omr_metadata_enc.len(),
        payment_memo.map(str::trim).filter(|s| !s.is_empty()).is_some()
    );

    let memo_bytes = crate::memo::parse_payment_memo(payment_memo)?.unwrap_or_default();

    crate::sync::ensure_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin)
        .await
        .map_err(|e| format!("merkle rebuild for spend: {e}"))?;

    prefetch_money_zkas(lightwallet_server_url, lightwallet_tls_pin).await?;

    let tx = drk
        .transfer(
            amount,
            token,
            *recipient.public_key(),
            None,
            None,
            false,
            memo_bytes,
        )
        .await
        .map_err(|e| format!("transfer: {e}"))?;

    let tx_bytes = serialize_async(&tx).await;
    wrap_envelope(&omr_metadata_enc, &omr_clue, &tx_bytes)
}

/// Look up recipient UnifOMR clue PK and build a paper clue.
/// Ownership proof from LWD MUST verify; otherwise treat as unregistered.
pub(crate) async fn resolve_outgoing_omr_clue(
    recipient_pk: &[u8; 32],
    network_byte: u8,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(u8, Vec<u8>), String> {
    const SCHEME_UNIFOMR: u8 = crate::memo::SCHEME_UNIFOMR;

    let lw_url = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| {
            "UnifOMR clue requires a lightwallet URL (GetCluePublicKey); no PerfOMR fallback"
                .to_string()
        })?;
    let lw_url = crate::lightwallet_client::normalize_lightwallet_url(lw_url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &lw_url,
        lightwallet_tls_pin,
    );

    let info = client
        .get_light_info()
        .await
        .map_err(|e| format!("GetLightInfo failed: {e}"))?;
    if info.directory_attest_pubkey.len() != 32 {
        return Err("GetLightInfo missing directory_attest_pubkey; upgrade lightwalletd".into());
    }
    let (_found, clue_pk, ownership_proof, key_version) = client
        .get_clue_public_key(recipient_pk.to_vec())
        .await
        .map_err(|e| format!("GetCluePublicKey failed: {e}"))?;
    if clue_pk.is_empty() {
        return Err(
            "GetCluePublicKey returned empty clue public key; recipient must register UnifOMR"
                .into(),
        );
    }
    crate::unifomr::verify_directory_attestation(
        &info.directory_attest_pubkey,
        network_byte,
        key_version,
        recipient_pk,
        &clue_pk,
        &ownership_proof,
    )
    .map_err(|e| {
        format!(
            "GetCluePublicKey directory attestation failed ({e}); treating as unregistered \
             or MITM"
        )
    })?;
    let pk = crate::unifomr::deserialize_public_key(&clue_pk)
        .map_err(|e| format!("Invalid UnifOMR clue public key: {e}"))?;
    let clue = crate::unifomr::build_omr_clue_from_pk(&pk);
    tracing::info!(
        target: "wallet-tx",
        "Using verified UnifOMR clue public key for recipient"
    );
    Ok((SCHEME_UNIFOMR, clue))
}

/// Compact output that this wallet cannot decrypt. That is the payment.
/// Several foreign outputs, or none, keep the historical index 0.
async fn payment_output_index(drk: &Drk, tx: &Transaction) -> Result<u32, String> {
    use darkfi_money_contract::client::MoneyNote;

    let secrets = drk.get_money_secrets().await.map_err(|e| e.to_string())?;
    let visible = crate::lightwallet_client::visible_money_outputs(tx).await?;
    let mut foreign = Vec::new();
    for output in &visible {
        let ours = secrets
            .iter()
            .any(|secret| output.note.decrypt::<MoneyNote>(secret).is_ok());
        if !ours {
            foreign.push(output.index);
        }
    }
    Ok(if foreign.len() == 1 { foreign[0] } else { 0 })
}

/// Inputs marked spent before `SendTransaction`. Released only when the RPC
/// fails in a way that means the server never accepted the transaction.
struct PendingBroadcast {
    coins: Vec<darkfi_money_contract::model::Coin>,
}

impl PendingBroadcast {
    async fn reserve(drk: &Drk, tx: &Transaction) -> Result<Self, String> {
        let tx_hash = tx.hash().to_string();
        let mut output = Vec::new();
        drk.mark_tx_spend(tx, &mut output)
            .await
            .map_err(|e| format!("PendingBroadcast reservation: {e}"))?;
        let rows = drk
            .get_coins(true)
            .await
            .map_err(|e| format!("PendingBroadcast reservation: {e}"))?;
        let coins = rows
            .into_iter()
            .filter(|(_, _, spent, height, hash)| *spent && height.is_none() && hash == &tx_hash)
            .map(|(own, _, _, _, _)| own.coin)
            .collect();
        Ok(Self { coins })
    }

    async fn release(self, drk: &Drk) {
        for coin in &self.coins {
            if let Err(e) = drk.unspend_coin(coin).await {
                tracing::warn!(target: "wallet-tx", "PendingBroadcast release failed: {e}");
            }
        }
    }
}

/// Timeouts and transport drops can happen after lightwalletd accepted the tx.
fn send_may_have_landed(err: &str) -> bool {
    let e = err.to_ascii_lowercase();
    e.contains("timeout")
        || e.contains("timed out")
        || e.contains("deadline")
        || e.contains("unavailable")
        || e.contains("transport")
        || e.contains("connection")
        || e.contains("reset")
        || e.contains("broken pipe")
}

pub async fn broadcast_transfer(
    drk: &Drk,
    tx_bytes: &[u8],
    payment_memo: Option<&str>,
    recipient_address: Option<&str>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    let raw_tx = strip_omr_envelope(tx_bytes)?;

    let sent_scheme = extract_envelope_scheme(tx_bytes);

    let tx: Transaction = deserialize_async(raw_tx)
        .await
        .map_err(|e| format!("decode tx: {e}"))?;

    let tx_hash = tx.hash().to_string();

    // Idempotency: return Ok only if this tx was already confirmed in lightwalletd mempool.
    if let Ok(map) = CONFIRMED_BROADCASTS.read() {
        if map.contains_key(&tx_hash) {
            tracing::warn!(
                target: "wallet-tx",
                "broadcast_transfer: txid {tx_hash} already confirmed in mempool this session"
            );
            return Ok(tx_hash);
        }
    }
    let _in_flight_guard = InFlightBroadcastGuard::try_acquire(&tx_hash)?;

    drk.simulate_tx(&tx)
        .await
        .map_err(|e| format!("simulate_tx: {e}"))?;

    let unifomr_clue = extract_envelope_fhe_clue(tx_bytes).unwrap_or_default();
    let omr_metadata_enc = extract_envelope_omr_memo(tx_bytes).unwrap_or_default();

    let lw_url = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or_else(|| {
            "UnifOMR broadcast requires lightwalletd SendTransaction (no darkfid fallback)"
                .to_string()
        })?;
    let lw_url = crate::lightwallet_client::normalize_lightwallet_url(lw_url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &lw_url,
        lightwallet_tls_pin,
    );

    let secret = drk
        .default_secret()
        .await
        .map_err(|e| format!("wallet secret for change clues: {e}"))?;
    let secret_bytes = zeroize::Zeroizing::new(secret.inner().to_repr());
    let network_byte = match drk.network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => 0x00u8,
        darkfi_sdk::crypto::keypair::Network::Testnet => 0x01u8,
    };
    let payment_index = payment_output_index(drk, &tx).await?;
    // Reserve before SendTransaction. A timeout on that RPC, or on the
    // GetTransaction confirm below, must not leave the inputs spendable.
    let pending = PendingBroadcast::reserve(drk, &tx).await?;
    let tx_hash_bytes = match client
        .send_transaction_with_wallet_clues(
            raw_tx.to_vec(),
            unifomr_clue.clone(),
            omr_metadata_enc,
            &*secret_bytes,
            network_byte,
            payment_index,
        )
        .await
    {
        Ok(bytes) => bytes,
        Err(e) => {
            if send_may_have_landed(&e) {
                let _ = drk
                    .put_tx_history_record(&tx, "PendingBroadcast", None)
                    .await;
            } else {
                pending.release(drk).await;
            }
            return Err(format!(
                "SendTransaction via lightwalletd failed ({e}). \
                 UnifOMR clue hint was not stored — refusing darkfid fallback so \
                 receivers can still detect within the 24h TTL."
            ));
        }
    };
    let _ = drk
        .put_tx_history_record(&tx, "PendingBroadcast", None)
        .await;
    client
        .confirm_transaction_in_mempool(&tx_hash_bytes)
        .await
        .map_err(|e| {
            format!(
                "Transaction was not confirmed in lightwalletd mempool ({e}). \
                 Inputs stay reserved as PendingBroadcast and are not spendable."
            )
        })?;

    if let Ok(mut map) = CONFIRMED_BROADCASTS.write() {
        map.insert(tx_hash.clone(), ());
    }

    let memo = payment_memo
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string);
    let recipient = recipient_address
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string);
    cache_and_persist_payment_meta(drk, &tx_hash, memo, recipient);

    drk.put_tx_history_record(&tx, "Broadcasted", None)
        .await
        .map_err(|e| format!("put_tx_history_record: {e}"))?;

    if let Err(e) = crate::sync::remember_own_outputs_from_tx(drk, &tx).await {
        tracing::warn!(
            target: "wallet-tx",
            "own change/fee notes not recorded yet: {e}"
        );
    }

    if let Some(scheme) = sent_scheme {
        if let Ok(mut cache) = SENT_SYNC_SCHEMES.write() {
            cache.insert(tx_hash.clone(), scheme);
        }
    }

    Ok(tx_hash)
}

/// Validates transfer inputs and returns the fixed schedule fee constant
/// (`SCHEDULE_TRANSFER_FEE_ATOMIC`, 5_000_000 atomic = 0.05 DRK).
///
/// Lightwalletd clients do not have a `darkfid` RPC for `tx.calculate_fee`
/// gas simulation and do not run Halo2 proving during fee preview, so callers
/// must label this as the fixed scheduled fee rather than a measured gas fee.
pub async fn estimate_transfer_fee(
    _drk: &Drk,
    recipient_address: &str,
    amount: &str,
    token_id: Option<&str>,
    payment_memo: Option<&str>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<i64, String> {
    let _ = Address::from_str(recipient_address.trim())
        .map_err(|e| format!("invalid recipient address: {e}"))?;
    darkfi::util::parse::decode_base10(amount.trim(), 8, false)
        .map_err(|e| format!("invalid amount: {e}"))?;
    crate::memo::parse_payment_memo(payment_memo)?;
    let _ = (token_id, lightwallet_server_url, lightwallet_tls_pin);
    Ok(SCHEDULE_TRANSFER_FEE_ATOMIC)
}

pub async fn get_transaction_memo(drk: &Drk, tx_hash: &str) -> Result<Option<String>, String> {
    if let Some(memo) = SENT_PAYMENT_META
        .read()
        .ok()
        .and_then(|m| m.get(tx_hash).and_then(|(memo, _)| memo.clone()))
    {
        return Ok(Some(memo));
    }

    if let Some(memo) = crate::sync::load_received_memo(drk, tx_hash) {
        return Ok(Some(memo));
    }

    // Received payments: decrypt own notes and extract user memo from OMR header.
    if let Ok((_, _, _, tx)) = drk.get_tx_history_record(tx_hash).await {
        if let Some(memo) = decrypt_user_memo_from_tx(drk, &tx).await? {
            return Ok(Some(memo));
        }
    }
    Ok(None)
}

pub async fn get_transaction_recipient(drk: &Drk, tx_hash: &str) -> Result<Option<String>, String> {
    if let Some(addr) = sent_recipient_address(tx_hash) {
        return Ok(Some(addr));
    }
    if let Some(addr) = crate::sync::load_sent_recipient(drk, tx_hash) {
        return Ok(Some(addr));
    }
    Ok(outgoing_recipient(drk, tx_hash))
}

async fn decrypt_user_memo_from_secrets(
    secrets: &[darkfi_sdk::crypto::SecretKey],
    tx: &Transaction,
) -> Result<Option<String>, String> {
    for call in &tx.calls {
        if call.data.contract_id != *MONEY_CONTRACT_ID || call.data.data.is_empty() {
            continue;
        }
        let Ok(func) = MoneyFunction::try_from(call.data.data[0]) else {
            continue;
        };
        if !matches!(func, MoneyFunction::TransferV1) {
            continue;
        }
        let params: MoneyTransferParamsV1 = deserialize_async(&call.data.data[1..])
            .await
            .map_err(|e| e.to_string())?;
        for output in params.outputs {
            if output.tx_local {
                continue;
            }
            for secret in secrets {
                if let Ok(note) = output.note.decrypt::<MoneyNote>(secret) {
                    // MoneyNote::memo is plain user text (OMR metadata is in omr_metadata_enc).
                    if !note.memo.is_empty() {
                        if let Ok(s) = String::from_utf8(note.memo) {
                            let t = s.trim();
                            if !t.is_empty() {
                                return Ok(Some(t.to_string()));
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
    Ok(None)
}

async fn decrypt_user_memo_from_tx(drk: &Drk, tx: &Transaction) -> Result<Option<String>, String> {
    let mut secrets = drk.get_money_secrets().await.unwrap_or_default();
    if secrets.is_empty() {
        secrets.push(drk.default_secret().await.map_err(|e| e.to_string())?);
    }
    decrypt_user_memo_from_secrets(&secrets, tx).await
}

pub(crate) fn is_outgoing_transaction(
    net_atomic: i64,
    status: &str,
    has_recipient: bool,
    has_sent_scheme: bool,
) -> bool {
    net_atomic < 0
        || (net_atomic == 0 && (status == "Broadcasted" || has_recipient || has_sent_scheme))
}

pub async fn list_transaction_history(drk: &Drk) -> Result<Vec<DrkTransactionRecord>, String> {
    let rows = drk.get_txs_history().await.map_err(|e| e.to_string())?;
    let mut records = Vec::with_capacity(rows.len());

    for (tx_hash, status, block_height) in rows {
        let recipient_address = outgoing_recipient(drk, &tx_hash);
        let has_sent_scheme = SENT_SYNC_SCHEMES
            .read()
            .ok()
            .map(|map| map.contains_key(&tx_hash))
            .unwrap_or(false);

        let mut fee_atomic = 0i64;
        let mut net_atomic = 0i64;
        let mut summary_opt = None;

        if let Ok((_, _, _, tx)) = drk.get_tx_history_record(&tx_hash).await {
            fee_atomic = drk
                .get_tx_fee(&tx, true)
                .await
                .ok()
                .and_then(|f| i64::try_from(f).ok())
                .unwrap_or(0);
            net_atomic = net_value_atomic(drk, &tx).await.unwrap_or(0);
            summary_opt = Some(contract_summary_for_tx(&tx));
        }

        let is_sent = is_outgoing_transaction(
            net_atomic,
            &status,
            recipient_address.is_some(),
            has_sent_scheme,
        );

        let contract_summary = summary_opt.unwrap_or_else(|| {
            if is_sent {
                "Outgoing transfer".to_string()
            } else {
                "Transaction".to_string()
            }
        });

        // Resolve how this tx was discovered/built: sent txs carry the OMR
        // scheme we embedded (UnifOMR); received txs may have no
        // persisted discovery metadata yet.
        let sync_method = SENT_SYNC_SCHEMES
            .read()
            .ok()
            .and_then(|map| map.get(&tx_hash).copied())
            .map(SyncMethod::from_scheme_byte)
            .unwrap_or(if is_sent {
                SyncMethod::UnifOmr
            } else {
                SyncMethod::Unknown
            });

        records.push(DrkTransactionRecord {
            tx_hash,
            status,
            block_height: block_height.map(i64::from).unwrap_or(-1),
            fee_atomic,
            is_sent,
            net_value_atomic: net_atomic,
            contract_summary,
            recipient_address,
            sync_method,
        });
    }

    Ok(records)
}

/// Strip the OMR envelope prefix from transaction bytes.
///
/// If the bytes start with `b"O2"`, parse memo/clue lengths and return
/// only the raw tx bytes. Otherwise returns the input unchanged.
fn strip_omr_envelope(data: &[u8]) -> Result<&[u8], String> {
    strip_envelope(data)
}

fn extract_envelope_scheme(data: &[u8]) -> Option<u8> {
    // If data has an OMR envelope, the scheme is always UnifOMR (the only supported scheme).
    // The metadata field is now recipient-encrypted, so we can't parse the scheme byte.
    let _env = parse_envelope(data)?;
    Some(crate::memo::SCHEME_UNIFOMR)
}

fn extract_envelope_fhe_clue(data: &[u8]) -> Option<Vec<u8>> {
    let env = parse_envelope(data)?;
    if env.fhe_clue.is_empty() {
        None
    } else {
        Some(env.fhe_clue.to_vec())
    }
}

fn extract_envelope_omr_memo(data: &[u8]) -> Option<Vec<u8>> {
    let env = parse_envelope(data)?;
    if env.omr_memo.is_empty() {
        None
    } else {
        Some(env.omr_memo.to_vec())
    }
}

/// Invalidate transactions confirmed at heights above `rewind_height`.
///
/// Uses upstream `drk.revert_transactions_after`, which matches the
/// `"Confirmed"` / `"Broadcasted"` status strings stored by `drk`.
pub async fn invalidate_transactions_above(drk: &Drk, rewind_height: u32) -> Result<u32, String> {
    // Count matching history rows *before* revert. `revert_transactions_after`
    // only writes two CLI log lines into `output` — never a per-tx list.
    let history = drk
        .get_txs_history()
        .await
        .map_err(|e| format!("get_txs_history: {e}"))?;
    let to_revert = count_history_rows_above_height(&history, rewind_height);

    let mut output = Vec::new();
    drk.revert_transactions_after(&rewind_height, &mut output)
        .await
        .map_err(|e| format!("revert_transactions_after: {e}"))?;
    tracing::debug!(
        target: "reorg",
        "Reverted {to_revert} transaction(s) above height {rewind_height}: {}",
        output.join("; ")
    );
    Ok(to_revert)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_send_memo_is_plain_utf8() {
        let bytes = crate::memo::parse_payment_memo(Some("  lunch  "))
            .unwrap()
            .unwrap();
        assert_eq!(bytes, b"lunch");
        assert!(crate::memo::parse_payment_memo(None).unwrap().is_none());
        assert!(crate::memo::parse_payment_memo(Some("   "))
            .unwrap()
            .is_none());
    }

    #[test]
    fn bounded_cache_fifo_evicts_oldest_insert_not_hash_order() {
        let mut cache = BoundedCache::with_capacity(2);
        cache.insert("a".into(), 1u8);
        cache.insert("b".into(), 2u8);
        cache.insert("c".into(), 3u8);
        assert_eq!(cache.len(), 2);
        assert!(!cache.contains_key("a"));
        assert_eq!(cache.get("b"), Some(&2));
        assert_eq!(cache.get("c"), Some(&3));
    }

    #[test]
    fn bounded_cache_update_does_not_refresh_fifo_position() {
        let mut cache = BoundedCache::with_capacity(2);
        cache.insert("a".into(), 1u8);
        cache.insert("b".into(), 2u8);
        cache.insert("a".into(), 9u8);
        cache.insert("c".into(), 3u8);
        assert!(
            !cache.contains_key("a"),
            "oldest insert was a, even after update"
        );
        assert_eq!(cache.get("b"), Some(&2));
        assert_eq!(cache.get("c"), Some(&3));
    }

    #[test]
    fn count_history_rows_above_height_matches_sql_where() {
        let rows = vec![
            ("t1".into(), "Confirmed".into(), Some(10u32)),
            ("t2".into(), "Confirmed".into(), Some(20)),
            ("t3".into(), "Broadcasted".into(), None),
            ("t4".into(), "Reverted".into(), None),
            ("t5".into(), "Confirmed".into(), Some(15)),
        ];
        assert_eq!(count_history_rows_above_height(&rows, 15), 1);
        assert_eq!(count_history_rows_above_height(&rows, 9), 3);
        assert_eq!(count_history_rows_above_height(&rows, 20), 0);
    }

    #[test]
    fn test_strip_omr_envelope_with_tag() {
        let omr_memo = vec![0x4F, 0x05, 0x03]; // 3-byte UnifOMR memo
        let raw_tx = b"raw_transaction_data";
        let envelope = wrap_envelope(&omr_memo, &[], raw_tx).unwrap();

        let stripped = strip_omr_envelope(&envelope).unwrap();
        assert_eq!(stripped, raw_tx);
    }

    #[test]
    fn test_strip_omr_envelope_no_tag() {
        let raw_tx = b"just_a_raw_transaction";
        let stripped = strip_omr_envelope(raw_tx).unwrap();
        assert_eq!(stripped, raw_tx);
    }

    #[test]
    fn test_strip_omr_envelope_empty() {
        let stripped = strip_omr_envelope(&[]).unwrap();
        assert_eq!(stripped, &[] as &[u8]);
    }

    #[test]
    fn test_strip_omr_envelope_too_short() {
        let stripped = strip_omr_envelope(b"OM").unwrap();
        assert_eq!(stripped, b"OM");
    }

    #[test]
    fn test_envelope_preserves_large_unifomr_clue() {
        let memo = crate::memo::build_omr_memo(&[1u8; 32], &[2u8; 32], None, Some(0x05)).unwrap();
        // In production, memo would be encrypted. For this test, use plaintext as the metadata blob.
        let (_sk, pk) = crate::unifomr::clue_keypair_from_wallet(&[9u8; 32], 0x01).unwrap();
        let clue = crate::unifomr::build_omr_clue_from_pk(&pk);
        assert!(
            clue.len() > 255,
            "UnifOMR clue must use O2 u32 length (multi-KB)"
        );
        let env = wrap_envelope(&memo, &clue, b"txbytes").unwrap();
        let parsed = parse_envelope(&env).expect("O2 parse");
        assert_eq!(parsed.fhe_clue, clue.as_slice());
        // extract_envelope_scheme returns UNIFOMR when any envelope is present
        assert_eq!(extract_envelope_scheme(&env), Some(0x05));
    }

    #[test]
    fn test_registered_clue_matches_receiver_detection_sk() {
        // Receiver wallet secret → registerable pk → sender clue → decrypt small error.
        //
        // NOTE: decrypt_error returns center_lift(b) - center_lift(a·s) which
        // lies in (-q, q), NOT (-q/2, q/2]. We must reduce mod q and re-center
        // before measuring the error magnitude.
        //
        // Paper Param2 r'=149 assumes digest mod-switch (Q→Q'=q) which is not
        // yet wired. The interim R_PRIME=32768 can be exceeded by raw BFV noise.
        // See docs/unifomr_mvp_limits.md SHIP NOTICE.
        let receiver_secret = [7u8; 32];
        let (sk, pk) = crate::unifomr::clue_keypair_from_wallet(&receiver_secret, 0x01).unwrap();
        let clue_bytes = crate::unifomr::build_omr_clue_from_pk(&pk);
        let ct = crate::unifomr::deserialize_clue(&clue_bytes).unwrap();
        let err = sk.decrypt_error(&ct);
        let q = crate::unifomr::CLUE_Q as i64;
        let max = err
            .iter()
            .copied()
            .map(|e| {
                // Reduce into [0, q) then center-lift into (-q/2, q/2].
                let r = ((e % q) + q) % q;
                if r > q / 2 {
                    (r - q).unsigned_abs()
                } else {
                    r as u64
                }
            })
            .max()
            .unwrap_or(0);
        assert!(
            max <= crate::unifomr::R_PRIME,
            "paper clue under registered pk must decrypt within R_PRIME (got max={max}, R_PRIME={})",
            crate::unifomr::R_PRIME
        );
    }

    #[test]
    fn test_is_outgoing_transaction_determination() {
        // Confirmed transaction with negative net balance is outgoing regardless of status string.
        assert!(is_outgoing_transaction(-5000, "Confirmed", false, false));
        assert!(is_outgoing_transaction(-1, "Mined", false, false));

        // Incoming transaction with positive net balance is NOT outgoing even if broadcasted.
        assert!(!is_outgoing_transaction(10000, "Confirmed", false, false));
        assert!(!is_outgoing_transaction(10000, "Broadcasted", false, false));

        // Net zero but has recipient or sent scheme or broadcasted pending.
        assert!(is_outgoing_transaction(0, "Broadcasted", false, false));
        assert!(is_outgoing_transaction(0, "Confirmed", true, false));
        assert!(is_outgoing_transaction(0, "Confirmed", false, true));

        // Net zero without outgoing markers is not outgoing.
        assert!(!is_outgoing_transaction(0, "Confirmed", false, false));
    }

    #[test]
    fn in_flight_broadcast_guard_prevents_concurrent_duplicate_and_releases_on_drop() {
        let txid = "test_tx_guard_1";
        let g1 = InFlightBroadcastGuard::try_acquire(txid).expect("first acquire");
        assert!(InFlightBroadcastGuard::try_acquire(txid).is_err());
        drop(g1);
        let g2 = InFlightBroadcastGuard::try_acquire(txid).expect("reacquire after failure drop");
        drop(g2);
        assert!(CONFIRMED_BROADCASTS
            .read()
            .map(|m| !m.contains_key(txid))
            .unwrap_or(false));
    }

    #[test]
    fn transfer_note_memo_encrypts_and_decrypts_from_tx() {
        use darkfi::tx::Transaction;
        use darkfi::zk::halo2::Field;
        use darkfi_money_contract::model::{Coin, Output, DARK_TOKEN_ID};
        use darkfi_sdk::{
            crypto::{
                note::AeadEncryptedNote, BaseBlind, FuncId, PublicKey, ScalarBlind, SecretKey,
            },
            dark_tree::DarkLeaf,
            pasta::pallas,
            ContractCall,
        };
        use darkfi_serial::AsyncEncodable;
        use rand::rngs::OsRng;

        let recipient_sk = SecretKey::random(&mut OsRng);
        let recipient_pk = PublicKey::from_secret(recipient_sk);
        let sender_sk = SecretKey::random(&mut OsRng);
        let sender_pk = PublicKey::from_secret(sender_sk);

        let memo_bytes = crate::memo::parse_payment_memo(Some("  invoice #42  "))
            .unwrap()
            .unwrap();
        let recipient_note = MoneyNote {
            value: 100_000_000,
            token_id: *DARK_TOKEN_ID,
            spend_hook: FuncId::none(),
            user_data: pallas::Base::ZERO,
            coin_blind: BaseBlind::random(&mut OsRng),
            value_blind: ScalarBlind::random(&mut OsRng),
            token_blind: BaseBlind::random(&mut OsRng),
            memo: memo_bytes,
        };
        let change_note = MoneyNote {
            memo: Vec::new(),
            ..recipient_note.clone()
        };

        let enc_recipient =
            AeadEncryptedNote::encrypt(&recipient_note, &recipient_pk, &mut OsRng).unwrap();
        let enc_change = AeadEncryptedNote::encrypt(&change_note, &sender_pk, &mut OsRng).unwrap();
        let dummy_coin = Coin::from(pallas::Base::ZERO);

        let params = MoneyTransferParamsV1 {
            inputs: Vec::new(),
            outputs: vec![
                Output {
                    value_commit: pallas::Point::default(),
                    token_commit: pallas::Base::ZERO,
                    coin: dummy_coin,
                    note: enc_change,
                    tx_local: false,
                },
                Output {
                    value_commit: pallas::Point::default(),
                    token_commit: pallas::Base::ZERO,
                    coin: dummy_coin,
                    note: enc_recipient,
                    tx_local: false,
                },
            ],
        };
        let mut data = vec![MoneyFunction::TransferV1 as u8];
        smol::block_on(params.encode_async(&mut data)).unwrap();
        let tx = Transaction {
            calls: vec![DarkLeaf {
                data: ContractCall {
                    contract_id: *MONEY_CONTRACT_ID,
                    data,
                },
                parent_index: None,
                children_indexes: Vec::new(),
            }],
            proofs: Vec::new(),
            signatures: Vec::new(),
        };

        let found = smol::block_on(decrypt_user_memo_from_secrets(&[recipient_sk], &tx)).unwrap();
        assert_eq!(found.as_deref(), Some("invoice #42"));
        let sender_found =
            smol::block_on(decrypt_user_memo_from_secrets(&[sender_sk], &tx)).unwrap();
        assert_eq!(sender_found, None);
    }
}
