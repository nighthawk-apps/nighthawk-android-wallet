//! Apply compact DAO calls from lightwalletd.
//!
//! The server drops proofs. Payload layouts match `compact_dao_call` in
//! `darkfi-lightwalletd`:
//! - mint (0): `PublicKey`; bulla is the DAO bulla
//! - propose (1): `(pallas::Base, AeadEncryptedNote)`; bulla is the proposal bulla
//! - vote (2): `(ElGamalEncryptedNote<4>, Vec<Nullifier>)`; bulla is the proposal bulla
//! - exec (3): `u8` early-exec flag; bulla is the proposal bulla
//! - auth transfer (4): `(Vec<ElGamalEncryptedNote<5>>, ElGamalEncryptedNote<3>)`

use std::collections::HashMap;

use darkfi_dao_contract::model::{DaoBulla, DaoProposal, DaoProposalBulla};
use darkfi_money_contract::model::Nullifier;
use darkfi_sdk::crypto::note::{AeadEncryptedNote, ElGamalEncryptedNote};
use darkfi_sdk::crypto::util::{fp_mod_fv, fp_to_u64};
use darkfi_sdk::crypto::{MerkleNode, MerkleTree, PublicKey, SecretKey};
use darkfi_sdk::pasta::pallas;
use darkfi_sdk::tx::TransactionHash;
use darkfi_serial::{deserialize, serialize};
use drk::cache::{CacheOverlay, CacheSmtStorage, KVDB_MONEY_SMT_TREE};
use drk::dao::{
    ProposalRecord, VoteRecord, KVDB_MERKLE_TREES_DAO_DAOS, KVDB_MERKLE_TREES_DAO_PROPOSALS,
};
use drk::Drk;
use num_bigint::BigUint;

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

#[allow(dead_code)]
pub async fn apply_dao_calls(drk: &Drk, calls: &[CompactDaoCall]) -> Result<(), String> {
    if calls.is_empty() {
        return Ok(());
    }
    let money_tree = drk
        .get_money_tree()
        .await
        .map_err(|e| format!("load money tree for DAO calls: {e}"))?;
    let overlay = CacheOverlay::new(&drk.cache)
        .map_err(|e| format!("open cache overlay for DAO SMT: {e}"))?;
    let smt_store = CacheSmtStorage::new(overlay, KVDB_MONEY_SMT_TREE);
    let smt_snapshot = smt_store
        .snapshot()
        .map_err(|e| format!("snapshot money nullifiers SMT: {e}"))?;
    apply_dao_calls_with_state(drk, calls, &money_tree, &smt_snapshot, true).await
}

pub(crate) async fn apply_dao_calls_with_state(
    drk: &Drk,
    calls: &[CompactDaoCall],
    money_tree: &MerkleTree,
    nullifiers_smt_snapshot: &HashMap<BigUint, pallas::Base>,
    append_trees: bool,
) -> Result<(), String> {
    if calls.is_empty() {
        return Ok(());
    }
    let daos = drk.get_daos().await.map_err(|e| e.to_string())?;
    let (mut daos_tree, mut proposals_tree) = drk
        .get_dao_trees()
        .await
        .map_err(|e| format!("load DAO merkle trees: {e}"))?;
    let mut daos_tree_dirty = false;
    let mut proposals_tree_dirty = false;
    for call in calls {
        if let Err(e) = apply_one(
            drk,
            &daos,
            call,
            &mut daos_tree,
            &mut daos_tree_dirty,
            &mut proposals_tree,
            &mut proposals_tree_dirty,
            money_tree,
            nullifiers_smt_snapshot,
            append_trees,
        )
        .await
        {
            tracing::warn!(
                target: "wallet-sync",
                "compact DAO call kind {} at height {} skipped: {e}",
                call.kind,
                call.height
            );
        }
    }
    if daos_tree_dirty || proposals_tree_dirty {
        drk.cache
            .insert_merkle_trees(&[
                (KVDB_MERKLE_TREES_DAO_DAOS, &daos_tree),
                (KVDB_MERKLE_TREES_DAO_PROPOSALS, &proposals_tree),
            ])
            .map_err(|e| format!("persist DAO merkle trees: {e}"))?;
        let _ = drk.cache.kvdb.flush_default_mode();
    }
    Ok(())
}

#[allow(clippy::too_many_arguments)]
async fn apply_one(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
    daos_tree: &mut MerkleTree,
    daos_tree_dirty: &mut bool,
    proposals_tree: &mut MerkleTree,
    proposals_tree_dirty: &mut bool,
    money_tree: &MerkleTree,
    nullifiers_smt_snapshot: &HashMap<BigUint, pallas::Base>,
    append_trees: bool,
) -> Result<(), String> {
    match call.kind {
        DAO_KIND_MINT => apply_mint(drk, call, daos_tree, daos_tree_dirty, append_trees).await,
        DAO_KIND_PROPOSE => {
            apply_propose(
                drk,
                daos,
                call,
                proposals_tree,
                proposals_tree_dirty,
                money_tree,
                nullifiers_smt_snapshot,
                append_trees,
            )
            .await
        }
        DAO_KIND_VOTE => apply_vote(drk, daos, call).await,
        DAO_KIND_EXEC => apply_exec(drk, call).await,
        DAO_KIND_AUTH_MONEY_TRANSFER => apply_auth_money_transfer(drk, daos, call).await,
        _ => Ok(()),
    }
}

async fn apply_mint(
    drk: &Drk,
    call: &CompactDaoCall,
    daos_tree: &mut MerkleTree,
    daos_tree_dirty: &mut bool,
    append_trees: bool,
) -> Result<(), String> {
    let bulla = dao_bulla(&call.bulla)?;
    if append_trees {
        daos_tree.append(MerkleNode::from(bulla.inner()));
        *daos_tree_dirty = true;
    }
    let notes_public: PublicKey =
        deserialize(&call.payload).map_err(|e| format!("mint notes key: {e}"))?;
    let tx_hash = tx_hash(&call.tx_hash)?;
    if drk.get_dao_by_bulla(&bulla).await.is_ok() {
        let leaf = if append_trees {
            daos_tree
                .mark()
                .ok_or_else(|| "DAO Merkle tree mark failed".to_string())?
        } else {
            crate::sync::placeholder_leaf_position()?
        };
        drk.confirm_dao(&bulla, &leaf, &tx_hash, &0, &call.height)
            .await
            .map_err(|e| e.to_string())?;
        return Ok(());
    }
    remember_discovered_dao(drk, &bulla, &notes_public, call.height, &tx_hash)
}

#[allow(clippy::too_many_arguments)]
async fn apply_propose(
    drk: &Drk,
    daos: &[drk::dao::DaoRecord],
    call: &CompactDaoCall,
    proposals_tree: &mut MerkleTree,
    proposals_tree_dirty: &mut bool,
    money_tree: &MerkleTree,
    nullifiers_smt_snapshot: &HashMap<BigUint, pallas::Base>,
    append_trees: bool,
) -> Result<(), String> {
    let expected = proposal_bulla(&call.bulla)?;
    if append_trees {
        proposals_tree.append(MerkleNode::from(expected.inner()));
        *proposals_tree_dirty = true;
    }
    let (token_commit, note): (pallas::Base, AeadEncryptedNote) =
        deserialize(&call.payload).map_err(|e| format!("propose payload: {e}"))?;
    let _ = token_commit;
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
        let leaf_pos = if append_trees {
            Some(
                proposals_tree
                    .mark()
                    .ok_or_else(|| "DAO proposals Merkle tree mark failed".to_string())?,
            )
        } else {
            None
        };
        let tx = tx_hash(&call.tx_hash)?;
        if let Ok(mut existing) = drk.get_dao_proposal_by_bulla(&expected).await {
            if let Some(pos) = leaf_pos {
                existing.leaf_position = Some(pos);
            }
            existing.money_snapshot_tree = Some(money_tree.clone());
            existing.nullifiers_smt_snapshot = Some(nullifiers_smt_snapshot.clone());
            existing.mint_height = Some(call.height);
            existing.tx_hash = Some(tx);
            existing.call_index = Some(0);
            drk.put_dao_proposal(&existing)
                .await
                .map_err(|e| format!("confirm proposal: {e}"))?;
            return Ok(());
        }
        let record = ProposalRecord {
            proposal,
            data: None,
            leaf_position: leaf_pos,
            money_snapshot_tree: Some(money_tree.clone()),
            nullifiers_smt_snapshot: Some(nullifiers_smt_snapshot.clone()),
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
    store_vote(drk, call, &proposal_bulla, &note, &nullifiers, &secret).await
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
    let (_enc_attrs, change): (Vec<ElGamalEncryptedNote<5>>, ElGamalEncryptedNote<3>) =
        deserialize(&call.payload).map_err(|e| format!("auth transfer payload: {e}"))?;
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
    #[allow(dead_code)]
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

#[cfg(test)]
mod tests {
    use super::*;
    use darkfi::zk::halo2::Field;
    use darkfi_money_contract::model::DARK_TOKEN_ID;
    use darkfi_sdk::crypto::{
        keypair::Keypair,
        smt::{PoseidonFp, EMPTY_NODES_FP},
        BaseBlind, Blind,
    };
    use drk::cache::{CacheOverlay, CacheSmt, CacheSmtStorage, KVDB_MONEY_SMT_TREE};
    use drk::dao::DaoParams;
    use rand::rngs::OsRng;

    #[test]
    fn apply_dao_calls_populates_merkle_trees_and_proposal_snapshots_on_insert_and_update() {
        let dir = std::env::temp_dir().join(format!("nh-dao-compact-{}", std::process::id()));
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
            let mut out = Vec::new();
            drk.initialize_money(&mut out).await.unwrap();
            drk.initialize_dao().await.unwrap();

            // Seed one nullifier into KVDB_MONEY_SMT_TREE so snapshots are non-empty.
            {
                let overlay = CacheOverlay::new(&drk.cache).unwrap();
                let store = CacheSmtStorage::new(overlay, KVDB_MONEY_SMT_TREE);
                let mut smt = CacheSmt::new(store, PoseidonFp::new(), &EMPTY_NODES_FP);
                let nf = Nullifier::from(pallas::Base::from(42u64));
                drk.smt_insert(&mut smt, &[nf]).unwrap();
                let diff = smt.store.overlay.0.diff(&[]).unwrap();
                smt.store.overlay.0.apply_diff(&diff).unwrap();
            }

            let notes = Keypair::random(&mut OsRng);
            let proposer = Keypair::random(&mut OsRng);
            let proposals = Keypair::random(&mut OsRng);
            let votes = Keypair::random(&mut OsRng);
            let exec = Keypair::random(&mut OsRng);
            let early_exec = Keypair::random(&mut OsRng);
            let params = DaoParams::new(
                100,
                100,
                200,
                100,
                50,
                *DARK_TOKEN_ID,
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
                BaseBlind::random(&mut OsRng),
            );
            drk.import_dao("alpha", &params, &mut out).await.unwrap();
            let dao = drk.get_dao_by_name("alpha").await.unwrap();

            // Foreign DAO mint first (position 0) + our DAO mint (position 1).
            let foreign_dao_bulla = DaoBulla::from(pallas::Base::from(99u64));
            let foreign_pk = Keypair::random(&mut OsRng).public;
            let mint_calls = vec![
                CompactDaoCall {
                    height: 10,
                    tx_hash: vec![0x01; 32],
                    kind: DAO_KIND_MINT,
                    bulla: foreign_dao_bulla.to_bytes().to_vec(),
                    payload: serialize(&foreign_pk),
                },
                CompactDaoCall {
                    height: 10,
                    tx_hash: vec![0x02; 32],
                    kind: DAO_KIND_MINT,
                    bulla: dao.bulla().to_bytes().to_vec(),
                    payload: serialize(&dao.params.dao.notes_public_key),
                },
            ];
            apply_dao_calls(&drk, &mint_calls).await.unwrap();

            let confirmed_dao = drk.get_dao_by_name("alpha").await.unwrap();
            let dao_pos = confirmed_dao.leaf_position.expect("DAO leaf_position");
            assert_eq!(u64::from(dao_pos), 1);
            let (daos_tree, _) = drk.get_dao_trees().await.unwrap();
            assert!(daos_tree.witness(dao_pos, 0).is_ok());

            // Foreign proposal (pos 0), INSERT proposal (pos 1), UPDATE proposal (pos 2).
            let foreign_prop_bulla = DaoProposalBulla::from(pallas::Base::from(777u64));
            let foreign_note = AeadEncryptedNote::encrypt(
                &DaoProposal {
                    auth_calls: vec![],
                    creation_blockwindow: 1,
                    duration_blockwindows: 5,
                    user_data: pallas::Base::ZERO,
                    dao_bulla: foreign_dao_bulla,
                    blind: Blind::random(&mut OsRng),
                },
                &foreign_pk,
                &mut OsRng,
            )
            .unwrap();

            let prop_insert = DaoProposal {
                auth_calls: vec![],
                creation_blockwindow: 2,
                duration_blockwindows: 5,
                user_data: pallas::Base::ZERO,
                dao_bulla: dao.bulla(),
                blind: Blind::random(&mut OsRng),
            };
            let enc_insert = AeadEncryptedNote::encrypt(
                &prop_insert,
                &dao.params.dao.proposals_public_key,
                &mut OsRng,
            )
            .unwrap();

            let prop_update = DaoProposal {
                auth_calls: vec![],
                creation_blockwindow: 3,
                duration_blockwindows: 5,
                user_data: pallas::Base::ZERO,
                dao_bulla: dao.bulla(),
                blind: Blind::random(&mut OsRng),
            };
            let enc_update = AeadEncryptedNote::encrypt(
                &prop_update,
                &dao.params.dao.proposals_public_key,
                &mut OsRng,
            )
            .unwrap();
            // Pre-insert local unconfirmed proposal with plaintext `data`.
            drk.put_dao_proposal(&ProposalRecord {
                proposal: prop_update.clone(),
                data: Some(b"plaintext-proposal".to_vec()),
                leaf_position: None,
                money_snapshot_tree: None,
                nullifiers_smt_snapshot: None,
                mint_height: None,
                tx_hash: None,
                call_index: None,
                exec_height: None,
                exec_tx_hash: None,
            })
            .await
            .unwrap();

            let propose_calls = vec![
                CompactDaoCall {
                    height: 11,
                    tx_hash: vec![0x03; 32],
                    kind: DAO_KIND_PROPOSE,
                    bulla: foreign_prop_bulla.to_bytes().to_vec(),
                    payload: serialize(&(pallas::Base::ZERO, foreign_note)),
                },
                CompactDaoCall {
                    height: 11,
                    tx_hash: vec![0x04; 32],
                    kind: DAO_KIND_PROPOSE,
                    bulla: prop_insert.to_bulla().to_bytes().to_vec(),
                    payload: serialize(&(pallas::Base::ZERO, enc_insert)),
                },
                CompactDaoCall {
                    height: 12,
                    tx_hash: vec![0x05; 32],
                    kind: DAO_KIND_PROPOSE,
                    bulla: prop_update.to_bulla().to_bytes().to_vec(),
                    payload: serialize(&(pallas::Base::ZERO, enc_update)),
                },
            ];
            apply_dao_calls(&drk, &propose_calls).await.unwrap();

            let inserted = drk
                .get_dao_proposal_by_bulla(&prop_insert.to_bulla())
                .await
                .unwrap();
            assert_eq!(inserted.leaf_position.map(u64::from), Some(1));
            assert!(inserted.money_snapshot_tree.is_some());
            assert!(inserted
                .nullifiers_smt_snapshot
                .as_ref()
                .is_some_and(|s| !s.is_empty()));

            let updated = drk
                .get_dao_proposal_by_bulla(&prop_update.to_bulla())
                .await
                .unwrap();
            assert_eq!(
                updated.data.as_deref(),
                Some(b"plaintext-proposal".as_slice())
            );
            assert_eq!(updated.leaf_position.map(u64::from), Some(2));
            assert!(updated.money_snapshot_tree.is_some());
            assert!(updated
                .nullifiers_smt_snapshot
                .as_ref()
                .is_some_and(|s| !s.is_empty()));
            assert_eq!(updated.mint_height, Some(12));

            let (_, proposals_tree) = drk.get_dao_trees().await.unwrap();
            assert!(proposals_tree
                .witness(inserted.leaf_position.unwrap(), 0)
                .is_ok());
            assert!(proposals_tree
                .witness(updated.leaf_position.unwrap(), 0)
                .is_ok());
        });
        let _ = std::fs::remove_dir_all(&dir);
    }
}
