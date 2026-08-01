//! Read-only DAO wallet queries (upstream `bin/drk/src/dao.rs`).

use darkfi::util::parse::encode_base10;
use darkfi_dao_contract::model::DaoProposalBulla;
use darkfi_sdk::crypto::pasta_prelude::PrimeField;
use darkfi_sdk::pasta::pallas;
use drk::dao::{DaoRecord, ProposalRecord};
use drk::money::BALANCE_BASE10_DECIMALS;
use drk::Drk;

use crate::{DrkDaoProposalDetail, DrkDaoProposalSummary, DrkDaoSummary};

fn base_to_b58(base: pallas::Base) -> String {
    bs58::encode(base.to_repr()).into_string()
}

fn opt_height(h: Option<u32>) -> i64 {
    h.map(i64::from).unwrap_or(-1)
}

fn approval_ratio_percent(base: u64, quot: u64) -> f64 {
    if base == 0 {
        0.0
    } else {
        (quot as f64 / base as f64) * 100.0
    }
}

fn map_dao(record: &DaoRecord) -> DrkDaoSummary {
    let dao = &record.params.dao;
    DrkDaoSummary {
        name: record.name.clone(),
        bulla_b58: base_to_b58(record.bulla().inner()),
        gov_token_id: dao.gov_token_id.to_string(),
        quorum_display: encode_base10(dao.quorum, BALANCE_BASE10_DECIMALS),
        proposer_limit_display: encode_base10(dao.proposer_limit, BALANCE_BASE10_DECIMALS),
        approval_ratio_percent: approval_ratio_percent(
            dao.approval_ratio_base,
            dao.approval_ratio_quot,
        ),
        mint_height: opt_height(record.mint_height),
        can_propose: record.params.proposer_secret_key.is_some(),
        can_vote: record.params.votes_secret_key.is_some(),
        can_exec: record.params.exec_secret_key.is_some(),
    }
}

fn proposal_summary_line(proposal: &ProposalRecord, dao_name: &str) -> String {
    let p = &proposal.proposal;
    let status = if proposal.exec_tx_hash.is_some() {
        "Executed"
    } else if proposal.mint_height.is_some() {
        "Active"
    } else {
        "Pending"
    };
    format!(
        "{status} · {} auth call(s) · {} block window(s) · DAO {dao_name}",
        p.auth_calls.len(),
        p.duration_blockwindows,
    )
}

fn map_proposal(proposal: &ProposalRecord, dao_name: &str) -> DrkDaoProposalSummary {
    let p = &proposal.proposal;
    DrkDaoProposalSummary {
        proposal_bulla_b58: base_to_b58(proposal.bulla().inner()),
        dao_name: dao_name.to_string(),
        dao_bulla_b58: base_to_b58(p.dao_bulla.inner()),
        auth_call_count: u32::try_from(p.auth_calls.len()).unwrap_or(u32::MAX),
        duration_blockwindows: p.duration_blockwindows,
        creation_blockwindow: p.creation_blockwindow,
        mint_height: opt_height(proposal.mint_height),
        exec_height: opt_height(proposal.exec_height),
        is_executed: proposal.exec_tx_hash.is_some(),
        summary_line: proposal_summary_line(proposal, dao_name),
    }
}

fn map_proposal_detail(proposal: &ProposalRecord, dao_name: &str) -> DrkDaoProposalDetail {
    let summary = map_proposal(proposal, dao_name);
    DrkDaoProposalDetail {
        proposal_bulla_b58: summary.proposal_bulla_b58,
        dao_name: summary.dao_name,
        dao_bulla_b58: summary.dao_bulla_b58,
        auth_call_count: summary.auth_call_count,
        duration_blockwindows: summary.duration_blockwindows,
        creation_blockwindow: summary.creation_blockwindow,
        mint_height: summary.mint_height,
        exec_height: summary.exec_height,
        is_executed: summary.is_executed,
        summary_line: summary.summary_line,
        propose_tx_hash: proposal.tx_hash.as_ref().map(|t| t.to_string()),
        exec_tx_hash: proposal.exec_tx_hash.as_ref().map(|t| t.to_string()),
        has_plaintext_data: proposal.data.is_some(),
    }
}

async fn dao_name_for_bulla(drk: &Drk, dao_bulla_b58: &str) -> Result<String, String> {
    let daos = drk.get_daos().await.map_err(|e| e.to_string())?;
    for d in &daos {
        if base_to_b58(d.bulla().inner()) == dao_bulla_b58 {
            return Ok(d.name.clone());
        }
    }
    Ok(String::from("(unknown DAO)"))
}

pub async fn list_daos(drk: &Drk) -> Result<Vec<DrkDaoSummary>, String> {
    let daos = drk.get_daos().await.map_err(|e| e.to_string())?;
    Ok(daos.iter().map(map_dao).collect())
}

pub async fn list_proposals(
    drk: &Drk,
    dao_name: Option<String>,
) -> Result<Vec<DrkDaoProposalSummary>, String> {
    let proposals: Vec<ProposalRecord> = match dao_name.as_deref() {
        Some(name) => drk
            .get_dao_proposals(name)
            .await
            .map_err(|e| e.to_string())?,
        None => drk.get_proposals().await.map_err(|e| e.to_string())?,
    };

    let daos = drk.get_daos().await.map_err(|e| e.to_string())?;
    let mut bulla_to_name = std::collections::HashMap::new();
    for d in &daos {
        bulla_to_name.insert(base_to_b58(d.bulla().inner()), d.name.clone());
    }

    Ok(proposals
        .iter()
        .map(|p| {
            let dao_bulla = base_to_b58(p.proposal.dao_bulla.inner());
            let name = bulla_to_name
                .get(&dao_bulla)
                .cloned()
                .unwrap_or_else(|| "(unknown DAO)".to_string());
            map_proposal(p, &name)
        })
        .collect())
}

/// Create a DAO transfer proposal, build the on-chain propose transaction,
/// broadcast it, and return the proposal bulla as b58.
pub async fn propose_transfer(
    drk: &Drk,
    dao_name: &str,
    duration_blockwindows: u64,
    amount: &str,
    token_id_str: Option<&str>,
    recipient_address: &str,
) -> Result<String, String> {
    use darkfi_sdk::crypto::keypair::Address;
    use std::str::FromStr;

    // Resolve token
    let token_input = token_id_str
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("DRK")
        .to_string();
    let token = drk
        .get_token(token_input)
        .await
        .map_err(|e| format!("token: {e}"))?;

    // Parse recipient
    let recipient_addr = Address::from_str(recipient_address.trim())
        .map_err(|e| format!("recipient address: {e}"))?;
    let recipient = *recipient_addr.public_key();

    // Step 1: Create the proposal record (stored locally)
    let proposal_record = drk
        .dao_propose_transfer(
            dao_name,
            duration_blockwindows,
            amount,
            token,
            recipient,
            None, // spend_hook
            None, // user_data
        )
        .await
        .map_err(|e| format!("dao_propose_transfer: {e}"))?;

    let proposal_bulla = proposal_record.bulla();

    // Step 2: Build the on-chain propose transaction (ZK proofs + fee)
    let tx = drk
        .dao_transfer_proposal_tx(&proposal_record)
        .await
        .map_err(|e| format!("dao_transfer_proposal_tx: {e}"))?;

    // Step 3: Broadcast
    let mut output = Vec::new();
    drk.broadcast_tx(&tx, &mut output)
        .await
        .map_err(|e| format!("broadcast propose tx: {e}"))?;

    Ok(base_to_b58(proposal_bulla.inner()))
}

/// Vote on a DAO proposal (yes/no with full governance token weight).
/// Returns the transaction hash on success.
pub async fn vote_on_proposal(
    drk: &Drk,
    proposal_bulla_b58: &str,
    vote_yes: bool,
) -> Result<String, String> {
    let bytes = bs58::decode(proposal_bulla_b58)
        .into_vec()
        .map_err(|e| format!("invalid proposal bulla: {e}"))?;
    let repr: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "proposal bulla must be 32 bytes".to_string())?;
    let bulla = DaoProposalBulla::from_bytes(repr).map_err(|e| format!("{e}"))?;

    // Build vote tx (full weight, no fractional voting)
    let tx = drk
        .dao_vote(&bulla, vote_yes, None)
        .await
        .map_err(|e| format!("dao_vote: {e}"))?;

    // Broadcast
    let mut output = Vec::new();
    drk.broadcast_tx(&tx, &mut output)
        .await
        .map_err(|e| format!("broadcast vote tx: {e}"))?;

    Ok(tx.hash().to_string())
}

pub async fn get_proposal(
    drk: &Drk,
    proposal_bulla_b58: &str,
) -> Result<DrkDaoProposalDetail, String> {
    let bytes = bs58::decode(proposal_bulla_b58)
        .into_vec()
        .map_err(|e| format!("invalid proposal bulla: {e}"))?;
    let repr: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "proposal bulla must be 32 bytes".to_string())?;
    let bulla = DaoProposalBulla::from_bytes(repr).map_err(|e| format!("{e}"))?;

    let proposal = drk
        .get_dao_proposal_by_bulla(&bulla)
        .await
        .map_err(|e| e.to_string())?;

    let dao_bulla = base_to_b58(proposal.proposal.dao_bulla.inner());
    let dao_name = dao_name_for_bulla(drk, &dao_bulla).await?;
    Ok(map_proposal_detail(&proposal, &dao_name))
}
