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

fn proposal_status_word(proposal: &ProposalRecord) -> &'static str {
    if proposal.exec_tx_hash.is_some() {
        "Paid"
    } else if proposal.mint_height.is_some() {
        "Open"
    } else {
        "Waiting"
    }
}

fn proposal_summary_line(
    proposal: &ProposalRecord,
    dao_name: &str,
    network: darkfi_sdk::crypto::keypair::Network,
) -> String {
    let status = proposal_status_word(proposal);
    if let Some(data) = proposal.data.as_ref() {
        if let Ok(attrs) =
            darkfi_serial::deserialize::<darkfi_money_contract::model::CoinAttributes>(data)
        {
            use darkfi_sdk::crypto::keypair::{Address, StandardAddress};
            let address: Address = StandardAddress::from_public(network, attrs.public_key).into();
            let shown = address.to_string();
            let short: String = shown.chars().take(6).collect();
            return format!(
                "{status} · Pay {} {} to {short}…",
                encode_base10(attrs.value, BALANCE_BASE10_DECIMALS),
                attrs.token_id,
            );
        }
    }
    format!("{status} · 1 payment · {dao_name}")
}

fn map_proposal(
    proposal: &ProposalRecord,
    dao_name: &str,
    network: darkfi_sdk::crypto::keypair::Network,
) -> DrkDaoProposalSummary {
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
        summary_line: proposal_summary_line(proposal, dao_name, network),
    }
}

fn map_proposal_detail(
    proposal: &ProposalRecord,
    dao_name: &str,
    network: darkfi_sdk::crypto::keypair::Network,
) -> DrkDaoProposalDetail {
    let summary = map_proposal(proposal, dao_name, network);
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
    let mut rows: Vec<DrkDaoSummary> = daos.iter().map(map_dao).collect();
    let known: std::collections::HashSet<String> =
        rows.iter().map(|dao| dao.bulla_b58.clone()).collect();
    for found in crate::dao_compact::discovered_daos(drk) {
        let bulla_b58 = bs58::encode(found.bulla).into_string();
        if known.contains(&bulla_b58) {
            continue;
        }
        let short: String = bulla_b58.chars().take(8).collect();
        rows.push(DrkDaoSummary {
            name: format!("unknown-{short}"),
            bulla_b58,
            gov_token_id: "unknown".into(),
            quorum_display: "unknown".into(),
            proposer_limit_display: "unknown".into(),
            approval_ratio_percent: 0.0,
            mint_height: i64::from(found.height),
            can_propose: false,
            can_vote: false,
            can_exec: false,
        });
    }
    Ok(rows)
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
            map_proposal(p, &name, drk.network)
        })
        .collect())
}

fn network_byte(drk: &Drk) -> u8 {
    match drk.network {
        darkfi_sdk::crypto::keypair::Network::Mainnet => 0x00,
        darkfi_sdk::crypto::keypair::Network::Testnet => 0x01,
    }
}

/// Broadcast a built transaction through lightwalletd when a URL is configured.
/// Falls back to darkfid only when no lightwalletd URL was given.
///
/// Every output this wallet can decrypt gets the wallet's own UnifOMR clue, so
/// strict-OMR sync sees change, fee notes and self-mints. `recipient` names the
/// output paid to someone else; it gets that wallet's clue when one is
/// registered. Without a clue a strict-OMR recipient only finds it by trial
/// decryption.
async fn broadcast_wallet_tx(
    drk: &Drk,
    tx: &darkfi::tx::Transaction,
    recipient: Option<(darkfi_sdk::crypto::PublicKey, u32)>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    use darkfi_money_contract::client::MoneyNote;

    if let Some(url) = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
    {
        let bytes = darkfi_serial::serialize_async(tx).await;
        let url = crate::lightwallet_client::normalize_lightwallet_url(url);
        let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
            &url,
            lightwallet_tls_pin,
        );
        let secrets = drk.get_money_secrets().await.map_err(|e| e.to_string())?;
        let own_indexes: Vec<u32> = crate::lightwallet_client::visible_money_outputs(tx)
            .await?
            .iter()
            .filter(|output| {
                secrets
                    .iter()
                    .any(|s| output.note.decrypt::<MoneyNote>(s).is_ok())
            })
            .map(|output| output.index)
            .collect();
        let network = network_byte(drk);
        let mut recipient_clue = None;
        if crate::lightwallet_sync::experimental_unifomr() {
        if let Some((public_key, index)) = recipient {
            if !own_indexes.contains(&index) {
                match crate::transactions::resolve_outgoing_omr_clue(
                    &public_key.to_bytes(),
                    network,
                    Some(url.as_str()),
                    lightwallet_tls_pin,
                )
                .await
                {
                    Ok((_scheme, clue)) => recipient_clue = Some((index, clue)),
                    Err(e) => tracing::warn!(
                        target: "wallet-tx",
                        "recipient has no UnifOMR clue key; strict-OMR wallets will not see output {index}: {e}"
                    ),
                }
            }
        }
        }
        let wallet_secret = drk.default_secret().await.map_err(|e| e.to_string())?;
        let wallet_secret = zeroize::Zeroizing::new(wallet_secret.inner().to_repr());
        client
            .send_transaction_with_output_clues(
                bytes,
                &own_indexes,
                recipient_clue,
                wallet_secret.as_ref(),
                network,
            )
            .await
            .map_err(|e| format!("broadcast via lightwalletd: {e}"))?;
    } else {
        let mut output = Vec::new();
        drk.broadcast_tx(tx, &mut output)
            .await
            .map_err(|e| format!("broadcast via darkfid: {e}"))?;
    }
    if let Err(e) = crate::sync::remember_own_outputs_from_tx(drk, tx).await {
        tracing::warn!(target: "wallet-tx", "own fee notes not recorded yet: {e}");
    }
    Ok(())
}

async fn install_lightwallet_chain_context(
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) {
    let Some(url) = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        drk::rpc::set_chain_context_fallback(None, None);
        return;
    };
    let url = crate::lightwallet_client::normalize_lightwallet_url(url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &url,
        lightwallet_tls_pin,
    );
    match client.get_light_info().await {
        Ok(info) if info.chain_tip_height > 0 && info.block_target > 0 => {
            drk::rpc::set_chain_context_fallback(
                Some(info.chain_tip_height.saturating_add(1)),
                Some(info.block_target),
            );
        }
        _ => drk::rpc::set_chain_context_fallback(None, None),
    }
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
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    install_lightwallet_chain_context(lightwallet_server_url, lightwallet_tls_pin).await;
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
    crate::sync::ensure_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin)
        .await
        .map_err(|e| format!("merkle rebuild for DAO propose: {e}"))?;

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

    broadcast_wallet_tx(drk, &tx, None, lightwallet_server_url, lightwallet_tls_pin).await?;

    Ok(base_to_b58(proposal_bulla.inner()))
}

/// Vote on a DAO proposal (yes/no with full governance token weight).
/// Returns the transaction hash on success.
pub async fn vote_on_proposal(
    drk: &Drk,
    proposal_bulla_b58: &str,
    vote_yes: bool,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    install_lightwallet_chain_context(lightwallet_server_url, lightwallet_tls_pin).await;
    crate::sync::ensure_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin)
        .await
        .map_err(|e| format!("merkle rebuild for DAO vote: {e}"))?;

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

    broadcast_wallet_tx(drk, &tx, None, lightwallet_server_url, lightwallet_tls_pin).await?;

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
    Ok(map_proposal_detail(&proposal, &dao_name, drk.network))
}

fn parse_proposal_bulla(proposal_bulla_b58: &str) -> Result<DaoProposalBulla, String> {
    let bytes = bs58::decode(proposal_bulla_b58.trim())
        .into_vec()
        .map_err(|e| format!("invalid proposal bulla: {e}"))?;
    let repr: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "proposal bulla must be 32 bytes".to_string())?;
    DaoProposalBulla::from_bytes(repr).map_err(|e| format!("{e}"))
}

fn dao_exec_spend_hook() -> darkfi_sdk::crypto::FuncId {
    use darkfi_dao_contract::DaoFunction;
    use darkfi_sdk::crypto::{FuncRef, DAO_CONTRACT_ID};
    FuncRef {
        contract_id: *DAO_CONTRACT_ID,
        func_code: DaoFunction::Exec as u8,
    }
    .to_func_id()
}

async fn prepare_dao_spend(
    drk: &Drk,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<(), String> {
    crate::sync::ensure_spendable_money_tree(drk, lightwallet_server_url, lightwallet_tls_pin)
        .await?;
    use darkfi_sdk::crypto::contract_id::{DAO_CONTRACT_ID, MONEY_CONTRACT_ID};
    let money = MONEY_CONTRACT_ID.to_string();
    let dao = DAO_CONTRACT_ID.to_string();
    crate::transactions::prefetch_contract_zkas(
        lightwallet_server_url,
        lightwallet_tls_pin,
        &[&money, &dao],
    )
    .await
}

/// True while the proposal's voting window is still open on the chain tip
/// reported by lightwalletd. Unknown tip or block target counts as closed.
async fn voting_window_open(
    proposal: &ProposalRecord,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> bool {
    let Some(url) = lightwallet_server_url
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        return false;
    };
    let url = crate::lightwallet_client::normalize_lightwallet_url(url);
    let client = crate::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &url,
        lightwallet_tls_pin,
    );
    let Ok(info) = client.get_light_info().await else {
        return false;
    };
    if info.block_target == 0 {
        return false;
    }
    let now = darkfi_dao_contract::blockwindow(
        info.chain_tip_height.saturating_add(1),
        info.block_target,
    );
    let p = &proposal.proposal;
    now < p
        .creation_blockwindow
        .saturating_add(p.duration_blockwindows)
}

/// Pay an approved proposal from the treasury.
///
/// While the voting window is open, payment needs the early-exec key and the
/// early quorum (`early-exec.zk` rejects a closed window). After it closes,
/// the normal path is the only one that verifies.
pub async fn exec_proposal(
    drk: &Drk,
    proposal_bulla_b58: &str,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    install_lightwallet_chain_context(lightwallet_server_url, lightwallet_tls_pin).await;
    let bulla = parse_proposal_bulla(proposal_bulla_b58)?;
    let proposal = drk
        .get_dao_proposal_by_bulla(&bulla)
        .await
        .map_err(|e| e.to_string())?;
    let dao = drk
        .get_dao_by_bulla(&proposal.proposal.dao_bulla)
        .await
        .map_err(|e| e.to_string())?;
    let early = voting_window_open(&proposal, lightwallet_server_url, lightwallet_tls_pin).await;
    if early && dao.params.early_exec_secret_key.is_none() {
        return Err(
            "Voting is still open. Paying early needs the early-exec key; wait for the window to close."
                .into(),
        );
    }
    prepare_dao_spend(drk, lightwallet_server_url, lightwallet_tls_pin).await?;
    let tx = drk
        .dao_exec_transfer(&proposal, early)
        .await
        .map_err(|e| format!("dao_exec_transfer: {e}"))?;
    let recipient = payout_output(&proposal, &tx).await;
    broadcast_wallet_tx(
        drk,
        &tx,
        recipient,
        lightwallet_server_url,
        lightwallet_tls_pin,
    )
    .await?;
    Ok(tx.hash().to_string())
}

/// The payout output of an exec transaction, found by the coin the proposal commits to.
async fn payout_output(
    proposal: &ProposalRecord,
    tx: &darkfi::tx::Transaction,
) -> Option<(darkfi_sdk::crypto::PublicKey, u32)> {
    let data = proposal.data.as_ref()?;
    let attrs =
        darkfi_serial::deserialize::<darkfi_money_contract::model::CoinAttributes>(data).ok()?;
    let coin = attrs.to_coin();
    let outputs = crate::lightwallet_client::visible_money_outputs(tx)
        .await
        .ok()?;
    let index = outputs.iter().find(|o| o.coin == coin)?.index;
    Some((attrs.public_key, index))
}

fn token_label(
    token_id: &darkfi_money_contract::model::TokenId,
    aliases: &std::collections::HashMap<String, String>,
) -> String {
    use darkfi_money_contract::model::DARK_TOKEN_ID;
    if *token_id == *DARK_TOKEN_ID {
        return "DRK".to_string();
    }
    let id = token_id.to_string();
    match aliases.get(&id) {
        Some(alias) => alias.clone(),
        None => {
            let short: String = id.chars().take(8).collect();
            format!("{short}…")
        }
    }
}

/// Balances held by this DAO's treasury, one line per token.
pub async fn treasury_lines(drk: &Drk, dao_name: &str) -> Result<String, String> {
    use std::collections::BTreeMap;

    let dao = drk
        .get_dao_by_name(dao_name)
        .await
        .map_err(|e| e.to_string())?;
    let hook = dao_exec_spend_hook();
    let user_data = dao.bulla().inner();
    let coins = drk.get_coins(false).await.map_err(|e| e.to_string())?;
    let mut totals: BTreeMap<String, (darkfi_money_contract::model::TokenId, u64)> =
        BTreeMap::new();
    for (coin, _, _, _, _) in &coins {
        if coin.note.spend_hook != hook || coin.note.user_data != user_data {
            continue;
        }
        let entry = totals
            .entry(coin.note.token_id.to_string())
            .or_insert((coin.note.token_id, 0));
        entry.1 = entry.1.saturating_add(coin.note.value);
    }
    if totals.is_empty() {
        return Ok(if dao.mint_height.is_none() {
            "Waiting for the group to be confirmed on chain".to_string()
        } else {
            "0 DRK".to_string()
        });
    }
    let aliases = drk.get_aliases_mapped_by_token().await.unwrap_or_default();
    Ok(totals
        .values()
        .map(|(token_id, total)| {
            format!(
                "{} {}",
                encode_base10(*total, BALANCE_BASE10_DECIMALS),
                token_label(token_id, &aliases)
            )
        })
        .collect::<Vec<_>>()
        .join("\n"))
}

fn parse_positive_amount(label: &str, raw: &str) -> Result<u64, String> {
    use darkfi::util::parse::decode_base10;
    let value = decode_base10(raw.trim(), BALANCE_BASE10_DECIMALS, true)
        .map_err(|e| format!("{label}: {e}"))?;
    if value == 0 {
        return Err(format!("{label} must be more than 0"));
    }
    Ok(value)
}

/// Create the DAO parameters and mint the DAO on chain.
///
/// All six role keys are kept: `mint.zk` proves every one of them, and a key
/// dropped before it is exported is lost. Hand roles to other members with
/// [`export_role`] after the DAO is minted.
pub async fn create_dao(
    drk: &Drk,
    name: &str,
    proposer_limit: &str,
    quorum: &str,
    early_exec_quorum: &str,
    approval_ratio: f64,
    gov_token_id: &str,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    use darkfi_sdk::crypto::{keypair::Keypair, BaseBlind};
    use drk::dao::DaoParams;
    use rand::rngs::OsRng;

    let name = name.trim();
    if name.is_empty() {
        return Err("group name is empty".into());
    }
    if !approval_ratio.is_finite() || approval_ratio <= 0.0 || approval_ratio > 1.0 {
        return Err("yes share must be more than 0 and at most 1".into());
    }
    let proposer_limit = parse_positive_amount("proposer minimum", proposer_limit)?;
    let quorum = parse_positive_amount("quorum", quorum)?;
    let early_exec_quorum = if early_exec_quorum.trim().is_empty() {
        quorum
    } else {
        parse_positive_amount("early quorum", early_exec_quorum)?
    };
    if early_exec_quorum < quorum {
        return Err("early quorum must be at least the quorum".into());
    }
    if early_exec_quorum == u64::MAX {
        return Err("early quorum is too large".into());
    }
    let gov_token_id = drk
        .get_token(gov_token_id.trim().to_string())
        .await
        .map_err(|e| format!("governance token: {e}"))?;
    let approval_ratio_base = 100_u64;
    let approval_ratio_quot = (approval_ratio * approval_ratio_base as f64).round() as u64;
    if approval_ratio_quot == 0 {
        return Err("yes share must be at least 0.01".into());
    }

    let notes = Keypair::random(&mut OsRng);
    let proposer = Keypair::random(&mut OsRng);
    let proposals = Keypair::random(&mut OsRng);
    let votes = Keypair::random(&mut OsRng);
    let exec = Keypair::random(&mut OsRng);
    let early_exec = Keypair::random(&mut OsRng);
    let bulla_blind = BaseBlind::random(&mut OsRng);
    let params = DaoParams::new(
        proposer_limit,
        quorum,
        early_exec_quorum,
        approval_ratio_base,
        approval_ratio_quot,
        gov_token_id,
        Some(notes.secret),
        notes.public,
        Some(proposer.secret),
        proposer.public,
        Some(proposals.secret),
        proposals.public,
        Some(votes.secret),
        votes.public,
        Some(exec.secret),
        exec.public,
        Some(early_exec.secret),
        early_exec.public,
        bulla_blind,
    );
    // Circuits and the spend tree first: a failure here leaves nothing behind.
    prepare_dao_spend(drk, lightwallet_server_url, lightwallet_tls_pin).await?;
    let mut output = Vec::new();
    drk.import_dao(name, &params, &mut output)
        .await
        .map_err(|e| format!("import dao: {e}"))?;
    let minted = async {
        let tx = drk
            .dao_mint(name)
            .await
            .map_err(|e| format!("dao_mint: {e}"))?;
        broadcast_wallet_tx(drk, &tx, None, lightwallet_server_url, lightwallet_tls_pin).await
    }
    .await;
    if let Err(e) = minted {
        // Not on chain: drop the record so the name can be used again.
        let mut output = Vec::new();
        if let Err(remove) = drk.remove_dao(name, &mut output).await {
            tracing::warn!(target: "wallet-dao", "remove unminted DAO {name}: {remove}");
        }
        return Err(e);
    }
    Ok(name.to_string())
}

/// Send coins into the treasury. The spend hook and DAO id are set here.
pub async fn fund_dao(
    drk: &Drk,
    dao_name: &str,
    amount: &str,
    token_id_str: Option<&str>,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    let dao = drk
        .get_dao_by_name(dao_name)
        .await
        .map_err(|e| e.to_string())?;
    let token_input = token_id_str
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("DRK")
        .to_string();
    let token = drk
        .get_token(token_input)
        .await
        .map_err(|e| format!("token: {e}"))?;
    prepare_dao_spend(drk, lightwallet_server_url, lightwallet_tls_pin).await?;
    let tx = drk
        .transfer(
            amount,
            token,
            dao.params.dao.notes_public_key,
            Some(dao_exec_spend_hook()),
            Some(dao.bulla().inner()),
            false,
        )
        .await
        .map_err(|e| format!("fund dao: {e}"))?;
    broadcast_wallet_tx(drk, &tx, None, lightwallet_server_url, lightwallet_tls_pin).await?;
    Ok(tx.hash().to_string())
}

/// Secret keys a role needs, as `drk` names them.
fn role_secrets(role: &str) -> Result<&'static [&'static str], String> {
    Ok(match role {
        "proposer" => &["proposer", "proposals"],
        "voter" => &["proposals", "votes"],
        "executor" => &["notes", "proposals", "votes", "exec", "early_exec"],
        "all" => &[
            "notes",
            "proposer",
            "proposals",
            "votes",
            "exec",
            "early_exec",
        ],
        _ => return Err("role must be proposer, voter, executor, or all".into()),
    })
}

/// DAO parameters with only `role`'s secret keys, as `drk dao import` TOML.
///
/// The exact approval ratio follows as integers: `drk` truncates the float
/// (0.29 reads as 28/100), which changes the DAO bulla.
pub async fn export_role(drk: &Drk, dao_name: &str, role: &str) -> Result<String, String> {
    let dao = drk
        .get_dao_by_name(dao_name)
        .await
        .map_err(|e| e.to_string())?;
    let keep = role_secrets(role)?;
    let mut params = dao.params.clone();
    if !keep.contains(&"notes") {
        params.notes_secret_key = None;
    }
    if !keep.contains(&"proposer") {
        params.proposer_secret_key = None;
    }
    if !keep.contains(&"proposals") {
        params.proposals_secret_key = None;
    }
    if !keep.contains(&"votes") {
        params.votes_secret_key = None;
    }
    if !keep.contains(&"exec") {
        params.exec_secret_key = None;
    }
    if !keep.contains(&"early_exec") {
        params.early_exec_secret_key = None;
    }
    let missing = [
        ("notes", dao.params.notes_secret_key.is_none()),
        ("proposer", dao.params.proposer_secret_key.is_none()),
        ("proposals", dao.params.proposals_secret_key.is_none()),
        ("votes", dao.params.votes_secret_key.is_none()),
        ("exec", dao.params.exec_secret_key.is_none()),
        ("early_exec", dao.params.early_exec_secret_key.is_none()),
    ];
    if let Some((key, _)) = missing
        .iter()
        .find(|(key, absent)| *absent && keep.contains(key))
    {
        return Err(format!(
            "this phone does not hold the {key} key for the {role} role"
        ));
    }
    let full = params.toml_str();
    let mut toml = full
        .lines()
        .filter(|line| !line.trim_start().starts_with('#') && !line.trim().is_empty())
        .collect::<Vec<_>>()
        .join("\n");
    toml.push_str(&format!(
        "\napproval_ratio_quot = {}\napproval_ratio_base = {}\n",
        params.dao.approval_ratio_quot, params.dao.approval_ratio_base
    ));
    Ok(toml)
}

fn exact_ratio_field(toml: &str, key: &str) -> Option<u64> {
    toml.lines().find_map(|line| {
        let (k, v) = line.split_once('=')?;
        if k.trim() == key {
            v.trim().parse::<u64>().ok()
        } else {
            None
        }
    })
}

/// Import a DAO from [`export_role`] output or a `drk dao create` file.
pub async fn import_dao_toml(drk: &Drk, name: &str, toml: &str) -> Result<String, String> {
    use drk::dao::DaoParams;

    let name = name.trim();
    if name.is_empty() {
        return Err("group name is empty".into());
    }
    let mut params = DaoParams::from_toml_str(toml).map_err(|e| format!("group file: {e}"))?;
    if let (Some(quot), Some(base)) = (
        exact_ratio_field(toml, "approval_ratio_quot"),
        exact_ratio_field(toml, "approval_ratio_base"),
    ) {
        if base == 0 || quot > base {
            return Err("group file: approval ratio is invalid".into());
        }
        params.dao.approval_ratio_quot = quot;
        params.dao.approval_ratio_base = base;
    }
    let mut output = Vec::new();
    drk.import_dao(name, &params, &mut output)
        .await
        .map_err(|e| format!("import dao: {e}"))?;
    Ok(name.to_string())
}

pub async fn generate_token(drk: &Drk, alias: Option<&str>) -> Result<String, String> {
    use darkfi_sdk::crypto::{keypair::SecretKey, BaseBlind};
    use rand::rngs::OsRng;

    let mint_authority = SecretKey::random(&mut OsRng);
    let token_blind = BaseBlind::random(&mut OsRng);
    let token_id = drk
        .import_mint_authority(mint_authority, token_blind)
        .await
        .map_err(|e| format!("generate token: {e}"))?;
    if let Some(alias) = alias.map(str::trim).filter(|s| !s.is_empty()) {
        let mut output = Vec::new();
        drk.add_alias(alias.to_string(), token_id, &mut output)
            .await
            .map_err(|e| format!("alias: {e}"))?;
    }
    Ok(token_id.to_string())
}

pub async fn mint_token(
    drk: &Drk,
    token_id_str: &str,
    amount: &str,
    recipient_address: &str,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    use darkfi_money_contract::MoneyFunction;
    use darkfi_sdk::crypto::keypair::Address;
    use std::str::FromStr;

    let token = drk
        .get_token(token_id_str.trim().to_string())
        .await
        .map_err(|e| format!("token: {e}"))?;
    if recipient_address.trim().is_empty() {
        return Err("recipient is empty".into());
    }
    parse_positive_amount("amount", amount)?;
    let recipient =
        Address::from_str(recipient_address.trim()).map_err(|e| format!("recipient: {e}"))?;
    prepare_dao_spend(drk, lightwallet_server_url, lightwallet_tls_pin).await?;
    let tx = drk
        .mint_token(amount, *recipient.public_key(), token, None, None)
        .await
        .map_err(|e| format!("mint token: {e}"))?;
    let minted = crate::lightwallet_client::visible_money_outputs(&tx)
        .await?
        .into_iter()
        .find(|o| o.func_code == MoneyFunction::TokenMintV1 as u8)
        .map(|o| (*recipient.public_key(), o.index));
    broadcast_wallet_tx(
        drk,
        &tx,
        minted,
        lightwallet_server_url,
        lightwallet_tls_pin,
    )
    .await?;
    Ok(tx.hash().to_string())
}

pub async fn freeze_token(
    drk: &Drk,
    token_id_str: &str,
    lightwallet_server_url: Option<&str>,
    lightwallet_tls_pin: Option<[u8; 32]>,
) -> Result<String, String> {
    let token = drk
        .get_token(token_id_str.trim().to_string())
        .await
        .map_err(|e| format!("token: {e}"))?;
    prepare_dao_spend(drk, lightwallet_server_url, lightwallet_tls_pin).await?;
    let tx = drk
        .freeze_token(token)
        .await
        .map_err(|e| format!("freeze token: {e}"))?;
    broadcast_wallet_tx(drk, &tx, None, lightwallet_server_url, lightwallet_tls_pin).await?;
    Ok(tx.hash().to_string())
}

/// Mint keys this phone holds.
pub async fn list_mint_authorities(drk: &Drk) -> Result<Vec<crate::DrkMintAuthority>, String> {
    let authorities = drk
        .get_mint_authorities()
        .await
        .map_err(|e| e.to_string())?;
    let aliases = drk.get_aliases_mapped_by_token().await.unwrap_or_default();
    Ok(authorities
        .into_iter()
        .map(|(token_id, _, _, frozen, _)| crate::DrkMintAuthority {
            token_id: token_id.to_string(),
            alias: aliases.get(&token_id.to_string()).cloned(),
            frozen,
        })
        .collect())
}
