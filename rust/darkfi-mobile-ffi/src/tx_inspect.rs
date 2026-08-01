//! Transaction inspection: contract labels, net value, and recipient hints.

use darkfi::tx::Transaction;
use darkfi_dao_contract::DaoFunction;
use darkfi_money_contract::{client::MoneyNote, model::MoneyTransferParamsV1, MoneyFunction};
use darkfi_sdk::crypto::contract_id::{DAO_CONTRACT_ID, MONEY_CONTRACT_ID};
use darkfi_serial::deserialize_async;
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

pub async fn net_value_atomic(drk: &Drk, tx: &Transaction) -> Result<i64, String> {
    let received = sum_decrypted_transfer_outputs(drk, tx).await?;
    let spent = sum_coins_spent_in_tx(drk, tx).await?;
    let net = received as i64 - spent as i64;
    Ok(net)
}

async fn sum_decrypted_transfer_outputs(drk: &Drk, tx: &Transaction) -> Result<u64, String> {
    let secret = drk.default_secret().await.map_err(|e| e.to_string())?;

    let mut total = 0u64;
    for call in &tx.calls {
        if call.data.contract_id != *MONEY_CONTRACT_ID {
            continue;
        }
        let data = &call.data.data;
        if data.is_empty() {
            continue;
        }
        let Ok(func) = MoneyFunction::try_from(data[0]) else {
            continue;
        };
        if !matches!(func, MoneyFunction::TransferV1) {
            continue;
        }

        let params: MoneyTransferParamsV1 = deserialize_async(&data[1..])
            .await
            .map_err(|e| e.to_string())?;

        for output in params.outputs {
            if output.tx_local {
                continue;
            }
            if let Ok(note) = output.note.decrypt::<MoneyNote>(&secret) {
                total = total.saturating_add(note.value);
            }
        }
    }

    Ok(total)
}

async fn sum_coins_spent_in_tx(drk: &Drk, tx: &Transaction) -> Result<u64, String> {
    let tx_hash = tx.hash().to_string();
    let mut total = 0u64;
    for (owncoin, _, is_spent, _, spent_tx_hash) in
        drk.get_coins(true).await.map_err(|e| e.to_string())?
    {
        if !is_spent || spent_tx_hash != tx_hash {
            continue;
        }
        total = total.saturating_add(owncoin.note.value);
    }
    Ok(total)
}

pub fn outgoing_recipient(_drk: &Drk, tx_hash: &str) -> Option<String> {
    crate::transactions::sent_recipient_address(tx_hash)
}
