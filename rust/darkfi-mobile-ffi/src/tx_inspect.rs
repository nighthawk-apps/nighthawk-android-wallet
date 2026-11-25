//! Transaction inspection: contract labels, net value, and recipient hints.

use darkfi::tx::Transaction;
use darkfi_dao_contract::DaoFunction;
use darkfi_money_contract::{client::MoneyNote, MoneyFunction};
use darkfi_sdk::crypto::contract_id::{DAO_CONTRACT_ID, MONEY_CONTRACT_ID};
use drk::Drk;

pub fn contract_summary_for_tx(tx: &Transaction) -> String {
    let mut parts = Vec::new();
    for call in &tx.calls {
        let contract_id = call.data.contract_id;
        let data = &call.data.data;
        if data.is_empty() {
            parts.push(format!("Contract {}", short_contract_id(&contract_id)));
            continue;
        }

        let label = if contract_id == *MONEY_CONTRACT_ID {
            money_function_label(data[0])
        } else if contract_id == *DAO_CONTRACT_ID {
            dao_function_label(data[0])
        } else {
            format!("Contract {}", short_contract_id(&contract_id))
        };
        parts.push(label);
    }

    if parts.is_empty() {
        "Transaction".to_string()
    } else {
        parts.join(", ")
    }
}

fn short_contract_id(id: &darkfi_sdk::crypto::ContractId) -> String {
    let s = id.to_string();
    if s.len() > 12 {
        format!("{}…", &s[..12])
    } else {
        s
    }
}

fn money_function_label(code: u8) -> String {
    match MoneyFunction::try_from(code) {
        Ok(MoneyFunction::FeeV1) => "Money: Fee".into(),
        Ok(MoneyFunction::GenesisMintV1) => "Money: Genesis mint".into(),
        Ok(MoneyFunction::PoWRewardV1) => "Money: PoW reward".into(),
        Ok(MoneyFunction::TransferV1) => "Money: Transfer".into(),
        Ok(MoneyFunction::AuthTokenMintV1) => "Money: Auth token mint".into(),
        Ok(MoneyFunction::AuthTokenFreezeV1) => "Money: Auth token freeze".into(),
        Ok(MoneyFunction::TokenMintV1) => "Money: Token mint".into(),
        Ok(MoneyFunction::BurnV1) => "Money: Burn".into(),
        Err(_) => format!("Money: call 0x{code:02x}"),
    }
}

fn dao_function_label(code: u8) -> String {
    match DaoFunction::try_from(code) {
        Ok(DaoFunction::Mint) => "DAO: Mint".into(),
        Ok(DaoFunction::Propose) => "DAO: Propose".into(),
        Ok(DaoFunction::Vote) => "DAO: Vote".into(),
        Ok(DaoFunction::Exec) => "DAO: Exec".into(),
        Ok(DaoFunction::AuthMoneyTransfer) => "DAO: Auth transfer".into(),
        Err(_) => format!("DAO: call 0x{code:02x}"),
    }
}

pub async fn net_value_atomic(drk: &Drk, tx: &Transaction, tx_hash: &str) -> Result<i64, String> {
    let received = sum_own_drk_outputs(drk, tx).await?;
    let spent = sum_coins_spent_in_tx(drk, tx_hash).await?;
    let net = received as i64 - spent as i64;
    Ok(net)
}

/// DRK this wallet receives in `tx`: payment, change and fee change outputs.
/// Other tokens are left out so history stays in DRK.
async fn sum_own_drk_outputs(drk: &Drk, tx: &Transaction) -> Result<u64, String> {
    use darkfi_money_contract::model::DARK_TOKEN_ID;

    let secrets = crate::sync::all_money_note_secrets(drk).await?;
    let mut total = 0u64;
    for output in crate::lightwallet_client::visible_money_outputs(tx).await? {
        for secret in &secrets {
            if let Ok(note) = output.note.decrypt::<MoneyNote>(secret) {
                if note.token_id == *DARK_TOKEN_ID {
                    total = total.saturating_add(note.value);
                }
                break;
            }
        }
    }
    Ok(total)
}

async fn sum_coins_spent_in_tx(drk: &Drk, tx_hash: &str) -> Result<u64, String> {
    let mut total = 0u64;
    for (owncoin, _, is_spent, _, spent_tx_hash) in
        drk.get_coins(true).await.map_err(|e| e.to_string())?
    {
        if !is_spent
            || spent_tx_hash != tx_hash
            || owncoin.note.token_id != *darkfi_money_contract::model::DARK_TOKEN_ID
        {
            continue;
        }
        total = total.saturating_add(owncoin.note.value);
    }
    Ok(total)
}

pub fn outgoing_recipient(drk: &Drk, tx_hash: &str) -> Option<String> {
    crate::transactions::sent_recipient_address(tx_hash)
        .or_else(|| crate::sync::load_sent_recipient(drk, tx_hash))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn outgoing_recipient_falls_back_to_disk_when_memory_cache_misses() {
        let dir = std::env::temp_dir().join(format!("nh-tx-inspect-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).unwrap();
        let ex = crate::shared_executor();
        smol::block_on(async {
            let drk = Drk::new(
                darkfi_sdk::crypto::keypair::Network::Testnet,
                dir.join("cache").to_string_lossy().into_owned(),
                dir.join("wallet.db").to_string_lossy().into_owned(),
                "test-pass".to_string(),
                None,
                &ex,
                false,
            )
            .await
            .unwrap();
            drk.initialize_wallet().await.unwrap();
            let tx_hash = "aa11bb22cc33dd44";
            crate::transactions::clear_sent_session_cache();
            crate::sync::persist_sent_payment_meta(&drk, tx_hash, None, Some("drk_recipient_1"));
            assert!(crate::transactions::sent_recipient_address(tx_hash).is_none());
            assert_eq!(
                outgoing_recipient(&drk, tx_hash).as_deref(),
                Some("drk_recipient_1")
            );
        });
        let _ = std::fs::remove_dir_all(&dir);
    }
}
