//! Apply compact DAO calls from lightwalletd.
//!
//! The server drops proofs. Payload layouts match `compact_dao_call` in
//! `darkfi-lightwalletd`:
//! - mint (0): `PublicKey`; bulla is the DAO bulla
//! - propose (1): `(pallas::Base, AeadEncryptedNote)`; bulla is the proposal bulla
//! - vote (2): `(ElGamalEncryptedNote<4>, Vec<Nullifier>)`; bulla is the proposal bulla
//! - exec (3): `u8` early-exec flag; bulla is the proposal bulla
//! - auth transfer (4): `(Vec<ElGamalEncryptedNote<5>>, ElGamalEncryptedNote<3>)`

use darkfi_dao_contract::model::{DaoBulla, DaoProposal, DaoProposalBulla};
use darkfi_money_contract::model::Nullifier;
use darkfi_sdk::crypto::note::{AeadEncryptedNote, ElGamalEncryptedNote};
use darkfi_sdk::crypto::util::{fp_mod_fv, fp_to_u64};
use darkfi_sdk::crypto::{PublicKey, SecretKey};
use darkfi_sdk::pasta::pallas;
use darkfi_sdk::tx::TransactionHash;
use darkfi_serial::{deserialize, serialize};
use drk::dao::{ProposalRecord, VoteRecord};
use drk::Drk;

use crate::note_batch::{MONEY_FUNC_AUTH_TOKEN_FREEZE, MONEY_FUNC_AUTH_TOKEN_MINT};

const DAO_KIND_MINT: u32 = 0;
const DAO_KIND_PROPOSE: u32 = 1;
const DAO_KIND_VOTE: u32 = 2;
const DAO_KIND_EXEC: u32 = 3;
const DAO_KIND_AUTH_MONEY_TRANSFER: u32 = 4;
const DISCOVERED_DAOS_TREE: &str = "discovered_daos";
const MONEY_FUNC_HISTORY_TREE: &str = "money_func_history";

/// One `CompactDaoCall` from the lightwalletd stream.
#[derive(Debug, Clone)]
pub struct CompactDaoCall {
    pub height: u32,
    pub tx_hash: Vec<u8>,
    pub kind: u32,
    pub bulla: Vec<u8>,
    pub payload: Vec<u8>,
}

/// Auth-token mint or freeze. `coin` is 32 zero bytes and the note is empty.
#[derive(Debug, Clone)]
pub struct CompactFuncEvent {
    pub height: u32,
    pub tx_hash: Vec<u8>,
    pub func_code: u32,
}

pub async fn rewind_compact_records(drk: &Drk, rollback_height: u32) -> Result<(), String> {
    let height = drk::walletdb::Value::from(i64::from(rollback_height));
    let sql = [
        format!(
            "UPDATE {} SET {} = NULL, {} = NULL WHERE {} > ?1;",
            *drk::dao::DAO_PROPOSALS_TABLE,
            drk::dao::DAO_PROPOSALS_COL_EXEC_HEIGHT,
            drk::dao::DAO_PROPOSALS_COL_EXEC_TX_HASH,
            drk::dao::DAO_PROPOSALS_COL_EXEC_HEIGHT,
        ),
        format!(
            "DELETE FROM {} WHERE {} > ?1 AND {} IS NULL;",
            *drk::dao::DAO_PROPOSALS_TABLE,
            drk::dao::DAO_PROPOSALS_COL_MINT_HEIGHT,
            drk::dao::DAO_PROPOSALS_COL_DATA,
        ),
        format!(
            "DELETE FROM {} WHERE {} > ?1;",
            *drk::dao::DAO_VOTES_TABLE,
            drk::dao::DAO_VOTES_COL_BLOCK_HEIGHT,
        ),
    ];
    for query in sql {
        drk.wallet
            .exec_sql(&query, vec![height.clone()])
            .await
            .map_err(|e| format!("dao reorg: {e}"))?;
    }
    prune_height_prefixed(drk, MONEY_FUNC_HISTORY_TREE, rollback_height)?;
    prune_height_prefixed(drk, "dao_auth_transfers", rollback_height)?;
    prune_discovered_daos(drk, rollback_height)?;
    Ok(())
}

fn prune_height_prefixed(drk: &Drk, tree_name: &str, rollback_height: u32) -> Result<(), String> {
    let Ok(tree) = drk.cache.kvdb.open_tree_default(tree_name) else {
        return Ok(());
    };
    let stale: Vec<Vec<u8>> = tree
        .iter()
        .flatten()
        .filter_map(|(key, _)| {
            if key.len() < 4 {
                return None;
            }
            let mut bytes = [0u8; 4];
            bytes.copy_from_slice(&key[..4]);
            (u32::from_le_bytes(bytes) > rollback_height).then(|| key.to_vec())
        })
        .collect();
    for key in stale {
        tree.remove(key.as_slice())
            .map_err(|e| format!("prune {tree_name}: {e}"))?;
    }
    Ok(())
}

fn prune_discovered_daos(drk: &Drk, rollback_height: u32) -> Result<(), String> {
    let Ok(tree) = drk.cache.kvdb.open_tree_default(DISCOVERED_DAOS_TREE) else {
        return Ok(());
    };
    let stale: Vec<Vec<u8>> = tree
        .iter()
        .flatten()
        .filter_map(|(key, value)| {
            if value.len() < 36 {
                return None;
            }
            let mut bytes = [0u8; 4];
            bytes.copy_from_slice(&value[32..36]);
            (u32::from_le_bytes(bytes) > rollback_height).then(|| key.to_vec())
        })
        .collect();
    for key in stale {
        tree.remove(key.as_slice())
            .map_err(|e| format!("prune discovered daos: {e}"))?;
    }
    Ok(())
}

pub async fn apply_dao_calls(drk: &Drk, calls: &[CompactDaoCall]) -> Result<(), String> {
    if calls.is_empty() {
        return Ok(());
    }
    let daos = drk.get_daos().await.map_err(|e| e.to_string())?;
    for call in calls {
        if let Err(e) = apply_one(drk, &daos, call).await {
            tracing::warn!(
                target: "wallet-sync",
                "compact DAO call kind {} at height {} skipped: {e}",
                call.kind,
                call.height
            );
        }
    }
    Ok(())
}

async fn apply_one(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
) -> Result<(), String> {
    match call.kind {
        DAO_KIND_MINT => apply_mint(drk, call).await,
        DAO_KIND_PROPOSE => apply_propose(drk, daos, call).await,
        DAO_KIND_VOTE => apply_vote(drk, daos, call).await,
        DAO_KIND_EXEC => apply_exec(drk, call).await,
        DAO_KIND_AUTH_MONEY_TRANSFER => apply_auth_money_transfer(drk, daos, call).await,
        _ => Ok(()),
    }
}

async fn apply_mint(drk: &Drk, call: &CompactDaoCall) -> Result<(), String> {
    let bulla = dao_bulla(&call.bulla)?;
    let notes_public: PublicKey =
        deserialize(&call.payload).map_err(|e| format!("mint notes key: {e}"))?;
    let tx_hash = tx_hash(&call.tx_hash)?;
    if drk.get_dao_by_bulla(&bulla).await.is_ok() {
        let leaf = crate::sync::placeholder_leaf_position()?;
        drk.confirm_dao(&bulla, &leaf, &tx_hash, &0, &call.height)
            .await
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    remember_discovered_dao(drk, &bulla, &notes_public, call.height, &tx_hash)
}

async fn apply_propose(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
) -> Result<(), String> {
    let (token_commit, note): (pallas::Base, AeadEncryptedNote) =
        deserialize(&call.payload).map_err(|e| format!("propose payload: {e}"))?;
    let _ = token_commit;
    let expected = proposal_bulla(&call.bulla)?;
    for dao in daos {
        let Some(secret) = dao.params.proposals_secret_key else {
            continue;
        };
        let Ok(proposal) = note.decrypt::<DaoProposal>(&secret) else {
            continue;
        };
        if proposal.to_bulla() != expected {
            continue;
        }
        let tx = tx_hash(&call.tx_hash)?;
        if drk.get_dao_proposal_by_bulla(&expected).await.is_ok() {
            let key = serialize(&expected);
            let tx_bytes = serialize(&tx);
            let query = format!(
                "UPDATE {} SET {} = ?1, {} = ?2, {} = ?3 WHERE {} = ?4;",
                *drk::dao::DAO_PROPOSALS_TABLE,
                drk::dao::DAO_PROPOSALS_COL_MINT_HEIGHT,
                drk::dao::DAO_PROPOSALS_COL_TX_HASH,
                drk::dao::DAO_PROPOSALS_COL_CALL_INDEX,
                drk::dao::DAO_PROPOSALS_COL_BULLA,
            );
            drk.wallet
                .exec_sql(
                    &query,
                    vec![
                        drk::walletdb::Value::from(i64::from(call.height)),
                        drk::walletdb::Value::from(tx_bytes),
                        drk::walletdb::Value::from(0i64),
                        drk::walletdb::Value::from(key),
                    ],
                )
                .await
                .map_err(|e| format!("confirm proposal: {e}"))?;
            return Ok(());
        }
        let record = ProposalRecord {
            proposal,
            data: None,
            leaf_position: None,
            money_snapshot_tree: None,
            nullifiers_smt_snapshot: None,
            mint_height: Some(call.height),
            tx_hash: Some(tx),
            call_index: Some(0),
            exec_height: None,
            exec_tx_hash: None,
        };
        drk.put_dao_proposal(&record)
            .await
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    Ok(())
}

async fn apply_vote(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
) -> Result<(), String> {
    let proposal_bulla = proposal_bulla(&call.bulla)?;
    let proposal_row = match drk.get_dao_proposal_by_bulla(&proposal_bulla).await {
        Ok(row) => row,
        Err(_) => return Ok(()),
    };
    let Some(dao) = daos
        .iter()
        .find(|dao| dao.bulla() == proposal_row.proposal.dao_bulla)
    else {
        return Ok(());
    };
    let Some(secret) = dao.params.votes_secret_key else {
        return Ok(());
    };
    let (note, nullifiers): (ElGamalEncryptedNote<4>, Vec<Nullifier>) =
        deserialize(&call.payload).map_err(|e| format!("vote payload: {e}"))?;
    store_vote(
        drk,
        call,
        &proposal_bulla,
        &note,
        &nullifiers,
        &secret,
    )
    .await
}

async fn store_vote(
    drk: &Drk,
    call: &CompactDaoCall,
    proposal: &DaoProposalBulla,
    note: &ElGamalEncryptedNote<4>,
    nullifiers: &[Nullifier],
    secret: &SecretKey,
) -> Result<(), String> {
    let opened = note
        .decrypt_unsafe(secret)
        .map_err(|e| format!("vote decrypt: {e}"))?;
    let option = fp_to_u64(opened[0]).ok_or("vote option is not a u64")?;
    if option > 1 {
        return Err(format!("malformed vote option {option}"));
    }
    let vote = VoteRecord {
        id: 0,
        proposal: *proposal,
        vote_option: option != 0,
        yes_vote_blind: darkfi_sdk::crypto::Blind(fp_mod_fv(opened[1])),
        all_vote_value: fp_to_u64(opened[2]).ok_or("all-vote value is not a u64")?,
        all_vote_blind: darkfi_sdk::crypto::Blind(fp_mod_fv(opened[3])),
        block_height: call.height,
        tx_hash: tx_hash(&call.tx_hash)?,
        call_index: 0,
        nullifiers: nullifiers.to_vec(),
    };
    drk.put_dao_vote(&vote).await.map_err(|e| e.to_string())
}

async fn apply_auth_money_transfer(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
) -> Result<(), String> {
    let (_enc_attrs, change): (
        Vec<ElGamalEncryptedNote<5>>,
        ElGamalEncryptedNote<3>,
    ) = deserialize(&call.payload).map_err(|e| format!("auth transfer payload: {e}"))?;
    for dao in daos {
        let Some(secret) = dao.params.notes_secret_key else {
            continue;
        };
        if change.decrypt_unsafe(&secret).is_err() {
            continue;
        }
        let tree = drk
            .cache
            .kvdb
            .open_tree_default("dao_auth_transfers")
            .map_err(|e| format!("open auth transfers: {e}"))?;
        let mut key = call.height.to_le_bytes().to_vec();
        key.extend_from_slice(&call.tx_hash);
        let value = serialize(&dao.bulla());
        tree.insert(key.as_slice(), value.as_slice())
            .map_err(|e| format!("store auth transfer: {e}"))?;
        return Ok(());
    }
    Ok(())
}

async fn apply_exec(drk: &Drk, call: &CompactDaoCall) -> Result<(), String> {
    let bulla = proposal_bulla(&call.bulla)?;
    if drk.get_dao_proposal_by_bulla(&bulla).await.is_err() {
        return Ok(());
    }
    let early: u8 = deserialize(&call.payload).map_err(|e| format!("exec payload: {e}"))?;
    let _ = early;
    let key = serialize(&bulla);
    let tx = serialize(&tx_hash(&call.tx_hash)?);
    let query = format!(
        "UPDATE {} SET {} = ?1, {} = ?2 WHERE {} = ?3;",
        *drk::dao::DAO_PROPOSALS_TABLE,
        drk::dao::DAO_PROPOSALS_COL_EXEC_HEIGHT,
        drk::dao::DAO_PROPOSALS_COL_EXEC_TX_HASH,
        drk::dao::DAO_PROPOSALS_COL_BULLA,
    );
    drk.wallet
        .exec_sql(
            &query,
            vec![
                drk::walletdb::Value::from(i64::from(call.height)),
                drk::walletdb::Value::from(tx),
                drk::walletdb::Value::from(key),
            ],
        )
        .await
        .map_err(|e| format!("exec update: {e}"))?;
    Ok(())
}

pub fn record_func_events(drk: &Drk, events: &[CompactFuncEvent]) -> Result<(), String> {
    if events.is_empty() {
        return Ok(());
    }
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(MONEY_FUNC_HISTORY_TREE)
        .map_err(|e| format!("open func history: {e}"))?;
    for event in events {
        if event.func_code != MONEY_FUNC_AUTH_TOKEN_MINT
            && event.func_code != MONEY_FUNC_AUTH_TOKEN_FREEZE
        {
            continue;
        }
        let mut key = event.height.to_le_bytes().to_vec();
        key.extend_from_slice(&event.tx_hash);
        key.push(event.func_code as u8);
        tree.insert(key.as_slice(), &[event.func_code as u8])
            .map_err(|e| format!("store func history: {e}"))?;
    }
    Ok(())
}

fn remember_discovered_dao(
    drk: &Drk,
    bulla: &DaoBulla,
    notes_public: &PublicKey,
    height: u32,
    tx_hash: &TransactionHash,
) -> Result<(), String> {
    let tree = drk
        .cache
        .kvdb
        .open_tree_default(DISCOVERED_DAOS_TREE)
        .map_err(|e| format!("open discovered daos: {e}"))?;
    let mut value = notes_public.to_bytes().to_vec();
    value.extend_from_slice(&height.to_le_bytes());
    value.extend_from_slice(tx_hash.inner());
    let key = bulla.to_bytes();
    tree.insert(key.as_slice(), value.as_slice())
        .map_err(|e| format!("store discovered dao: {e}"))?;
    Ok(())
}

/// DAOs seen in a mint whose quorum and role keys are not in this wallet.
pub fn discovered_daos(drk: &Drk) -> Vec<DiscoveredDao> {
    let Ok(tree) = drk.cache.kvdb.open_tree_default(DISCOVERED_DAOS_TREE) else {
        return Vec::new();
    };
    let mut out = Vec::new();
    for item in tree.iter().flatten() {
        let (key, value) = item;
        if key.len() != 32 || value.len() < 36 {
            continue;
        }
        let mut bulla = [0u8; 32];
        bulla.copy_from_slice(&key);
        let mut notes = [0u8; 32];
        notes.copy_from_slice(&value[..32]);
        let mut height_bytes = [0u8; 4];
        height_bytes.copy_from_slice(&value[32..36]);
        out.push(DiscoveredDao {
            bulla,
            notes_public: notes,
            height: u32::from_le_bytes(height_bytes),
        });
    }
    out
}

#[derive(Debug, Clone)]
pub struct DiscoveredDao {
    pub bulla: [u8; 32],
    pub notes_public: [u8; 32],
    pub height: u32,
}

fn dao_bulla(bytes: &[u8]) -> Result<DaoBulla, String> {
    let bytes: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "DAO bulla is not 32 bytes".to_string())?;
    DaoBulla::from_bytes(bytes).map_err(|e| format!("DAO bulla: {e}"))
}

fn proposal_bulla(bytes: &[u8]) -> Result<DaoProposalBulla, String> {
    let bytes: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "proposal bulla is not 32 bytes".to_string())?;
    DaoProposalBulla::from_bytes(bytes).map_err(|e| format!("proposal bulla: {e}"))
}

fn tx_hash(bytes: &[u8]) -> Result<TransactionHash, String> {
    let bytes: [u8; 32] = bytes
        .try_into()
        .map_err(|_| "tx hash is not 32 bytes".to_string())?;
    Ok(TransactionHash(bytes))
}

