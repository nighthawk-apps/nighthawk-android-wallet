//! Transfer build/broadcast and wallet transaction history for UniFFI.

use std::collections::HashMap;
use std::str::FromStr;
use std::sync::LazyLock;
use std::sync::RwLock;

use darkfi::tx::Transaction;
use darkfi_money_contract::{client::MoneyNote, model::MoneyTransferParamsV1, MoneyFunction};
use darkfi_sdk::crypto::contract_id::MONEY_CONTRACT_ID;
use darkfi_sdk::crypto::keypair::Address;
use darkfi_sdk::pasta::group::ff::PrimeField;
use darkfi_serial::{deserialize_async, serialize_async};
use drk::Drk;

use crate::{
    omr_envelope::{parse_envelope, strip_envelope, wrap_envelope},
    tx_inspect::{contract_summary_for_tx, net_value_atomic, outgoing_recipient},
    DrkTransactionRecord, SyncMethod,
};

/// Per-session record of the OMR scheme byte embedded in each outgoing
/// transaction's clue, keyed by tx hash.
static SENT_SYNC_SCHEMES: LazyLock<RwLock<HashMap<String, u8>>> =
    LazyLock::new(|| RwLock::new(HashMap::new()));

/// Per-session payment memo + recipient for txs we broadcast this process.
#[allow(clippy::type_complexity)]
static SENT_PAYMENT_META: LazyLock<RwLock<HashMap<String, (Option<String>, Option<String>)>>> =
    LazyLock::new(|| RwLock::new(HashMap::new()));

const MAX_SENT_CACHE_ENTRIES: usize = 10_000;

/// Clear session caches (call on wallet close / re-bootstrap).
pub fn clear_sent_session_cache() {
    if let Ok(mut m) = SENT_SYNC_SCHEMES.write() {
        m.clear();
    }
    if let Ok(mut m) = SENT_PAYMENT_META.write() {
        m.clear();
    }
}

fn insert_bounded_meta(
    map: &mut HashMap<String, (Option<String>, Option<String>)>,
    key: String,
    val: (Option<String>, Option<String>),
) {
    if map.len() >= MAX_SENT_CACHE_ENTRIES && !map.contains_key(&key) {
        if let Some(oldest) = map.keys().next().cloned() {
            map.remove(&oldest);
        }
    }
    map.insert(key, val);
}

/// Look up a recipient address persisted at broadcast time (session cache).
pub fn sent_recipient_address(tx_hash: &str) -> Option<String> {
    SENT_PAYMENT_META
        .read()
        .ok()
        .and_then(|m| m.get(tx_hash).and_then(|(_, r)| r.clone()))
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
    let secret_bytes: [u8; 32] = secret.inner().to_repr();
    let recipient_pk_bytes: [u8; 32] = recipient.public_key().to_bytes();

    let network_byte = match drk.network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => 0x00u8,
        darkfi_sdk::crypto::keypair::Network::Testnet => 0x01u8,
    };

    // Fail-closed: require a registered UnifOMR clue public key (verified ownership).
    let (omr_scheme, omr_clue) = resolve_outgoing_omr_clue(
        &recipient_pk_bytes,
        network_byte,
        lightwallet_server_url,
        lightwallet_tls_pin,
    )
    .await?;

    // Per-tx memo key (domain-separated) — never reuse raw spending key bytes.
    let mut nonce = [0u8; 16];
    rand::RngCore::fill_bytes(&mut rand::thread_rng(), &mut nonce);
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

    let _plain_memo = payment_memo
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.as_bytes().to_vec());

    let tx = drk
        .transfer(amount, token, *recipient.public_key(), None, None, false)
        .await
        .map_err(|e| format!("transfer: {e}"))?;

    let tx_bytes = serialize_async(&tx).await;
    wrap_envelope(&omr_metadata_enc, &omr_clue, &tx_bytes)
}

/// Look up recipient UnifOMR clue PK and build a paper clue.
/// Ownership proof from LWD MUST verify; otherwise treat as unregistered.
async fn resolve_outgoing_omr_clue(
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
    crate::unifomr::verify_clue_pk_ownership(
        network_byte,
        key_version,
        recipient_pk,
        &clue_pk,
        &ownership_proof,
    )
    .map_err(|e| {
        format!(
            "GetCluePublicKey ownership verify failed ({e}); treating as unregistered \
             (possible MITM or decoy)"
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

    // Idempotency: already broadcast this session.
    if let Ok(map) = SENT_PAYMENT_META.read() {
        if map.contains_key(&tx_hash) {
            tracing::warn!(
                target: "wallet-tx",
                "broadcast_transfer: txid {tx_hash} already submitted this session"
            );
            return Ok(tx_hash);
        }
    }

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

    // Mark pending before send to prevent concurrent duplicate.
    {
        let memo = payment_memo
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(str::to_string);
        let recipient = recipient_address
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(str::to_string);
        if let Ok(mut map) = SENT_PAYMENT_META.write() {
            insert_bounded_meta(&mut map, tx_hash.clone(), (memo, recipient));
        }
    }

    let send_result = client
        .send_transaction(raw_tx.to_vec(), unifomr_clue.clone(), omr_metadata_enc)
        .await;

    match send_result {
        Ok(_tx_hash_bytes) => {}
        Err(e) => {
            let is_timeout = e.to_lowercase().contains("timeout")
                || e.to_lowercase().contains("timed out")
                || e.to_lowercase().contains("deadline");
            if !is_timeout {
                if let Ok(mut map) = SENT_PAYMENT_META.write() {
                    map.remove(&tx_hash);
                }
            }
            return Err(format!(
                "SendTransaction via lightwalletd failed ({e}). \
                 UnifOMR clue hint was not stored — refusing darkfid fallback so \
                 receivers can still detect within the 24h TTL."
            ));
        }
    }

    let mut output = Vec::new();
    drk.mark_tx_spend(&tx, &mut output)
        .await
        .map_err(|e| format!("mark_tx_spend after broadcast: {e}"))?;

    if let Some(scheme) = sent_scheme {
        if let Ok(mut map) = SENT_SYNC_SCHEMES.write() {
            if map.len() >= MAX_SENT_CACHE_ENTRIES && !map.contains_key(&tx_hash) {
                if let Some(oldest) = map.keys().next().cloned() {
                    map.remove(&oldest);
                }
            }
            map.insert(tx_hash.clone(), scheme);
        }
    }

    Ok(tx_hash)
}

pub async fn estimate_transfer_fee(
    drk: &Drk,
    recipient_address: &str,
    amount: &str,
    token_id: Option<&str>,
    payment_memo: Option<&str>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<i64, String> {
    let envelope = build_transfer(
        drk,
        recipient_address,
        amount,
        token_id,
        payment_memo,
        lightwallet_server_url,
        lightwallet_tls_pin,
    )
    .await?;
    let raw_tx = strip_omr_envelope(&envelope)?;
    let tx: Transaction = deserialize_async(raw_tx)
        .await
        .map_err(|e| format!("decode tx: {e}"))?;
    let fee = drk
        .get_tx_fee(&tx, true)
        .await
        .map_err(|e| format!("get_tx_fee: {e}"))?;
    i64::try_from(fee).map_err(|_| format!("fee out of range: {fee}"))
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
    Ok(outgoing_recipient(drk, tx_hash))
}

async fn decrypt_user_memo_from_tx(drk: &Drk, tx: &Transaction) -> Result<Option<String>, String> {
    let secret = drk.default_secret().await.map_err(|e| e.to_string())?;
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
            if let Ok(note) = output.note.decrypt::<MoneyNote>(&secret) {
                // MoneyNote::memo is plain user text (OMR metadata is in omr_metadata_enc).
                if !note.memo.is_empty() {
                    if let Ok(s) = String::from_utf8(note.memo) {
                        let t = s.trim();
                        if !t.is_empty() {
                            return Ok(Some(t.to_string()));
                        }
                    }
                }
            }
        }
    }
    Ok(None)
}

pub async fn list_transaction_history(drk: &Drk) -> Result<Vec<DrkTransactionRecord>, String> {
    let rows = drk.get_txs_history().await.map_err(|e| e.to_string())?;
    let mut records = Vec::with_capacity(rows.len());

    for (tx_hash, status, block_height) in rows {
        let is_sent = status == "Broadcasted";
        let mut fee_atomic = 0i64;
        let mut net_atomic = 0i64;
        let contract_summary = match drk.get_tx_history_record(&tx_hash).await {
            Ok((_, _, _, tx)) => {
                fee_atomic = drk
                    .get_tx_fee(&tx, true)
                    .await
                    .ok()
                    .and_then(|f| i64::try_from(f).ok())
                    .unwrap_or(0);
                net_atomic = net_value_atomic(drk, &tx).await.unwrap_or(0);
                contract_summary_for_tx(&tx)
            }
            Err(_) => {
                if is_sent {
                    "Outgoing transfer".to_string()
                } else {
                    "Transaction".to_string()
                }
            }
        };
        let recipient_address = outgoing_recipient(drk, &tx_hash);

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
    let mut output = Vec::new();
    drk.revert_transactions_after(&rewind_height, &mut output)
        .await
        .map_err(|e| format!("revert_transactions_after: {e}"))?;
    tracing::debug!(
        target: "reorg",
        "Reverted transactions above height {rewind_height}: {}",
        output.join("; ")
    );
    Ok(1)
}

#[cfg(test)]
mod tests {
    use super::*;

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
}
