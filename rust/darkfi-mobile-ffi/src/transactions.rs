//! Transfer build/broadcast and wallet transaction history for UniFFI.

use std::str::FromStr;

use darkfi::tx::Transaction;
use darkfi_sdk::crypto::keypair::Address;
use darkfi_serial::{deserialize_async, serialize_async};
use drk::Drk;

use crate::DrkTransactionRecord;

pub async fn build_transfer(
    drk: &Drk,
    recipient_address: &str,
    amount: &str,
    token_id: Option<&str>,
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

    let tx = drk
        .transfer(amount, token, *recipient.public_key(), None, None, false)
        .await
        .map_err(|e| format!("transfer: {e}"))?;

    Ok(serialize_async(&tx).await)
}

pub async fn broadcast_transfer(drk: &Drk, tx_bytes: &[u8]) -> Result<String, String> {
    let tx: Transaction = deserialize_async(tx_bytes)
        .await
        .map_err(|e| format!("decode tx: {e}"))?;

    drk.simulate_tx(&tx)
        .await
        .map_err(|e| format!("simulate_tx: {e}"))?;

    let mut output = Vec::new();
    drk.mark_tx_spend(&tx, &mut output)
        .await
        .map_err(|e| format!("mark_tx_spend: {e}"))?;

    drk.broadcast_tx(&tx, &mut output)
        .await
        .map_err(|e| format!("broadcast_tx: {e}"))
}

pub async fn estimate_transfer_fee(
    drk: &Drk,
    recipient_address: &str,
    amount: &str,
    token_id: Option<&str>,
) -> Result<i64, String> {
    let tx_bytes = build_transfer(drk, recipient_address, amount, token_id).await?;
    let tx: Transaction = deserialize_async(&tx_bytes)
        .await
        .map_err(|e| format!("decode tx: {e}"))?;
    let fee = drk
        .get_tx_fee(&tx, true)
        .await
        .map_err(|e| format!("get_tx_fee: {e}"))?;
    i64::try_from(fee).map_err(|_| format!("fee out of range: {fee}"))
}

pub fn list_transaction_history(drk: &Drk) -> Result<Vec<DrkTransactionRecord>, String> {
    let rows = drk.get_txs_history().map_err(|e| e.to_string())?;
    Ok(rows
        .into_iter()
        .map(|(tx_hash, status, block_height)| {
            let is_sent = status == "Broadcasted";
            DrkTransactionRecord {
                tx_hash,
                status,
                block_height: block_height.map(i64::from).unwrap_or(-1),
                fee_atomic: 0,
                is_sent,
                net_value_atomic: 0,
            }
        })
        .collect())
}
