//! Checkpoint / snapshot download + apply for instant wallet restore.
//!
//! Instead of replaying `[0, tip]` via `GetNoteCommitments`, the client asks
//! the LWD server for a pre-built checkpoint at (or near) the wallet birthday.
//! The checkpoint contains a serialized Money Merkle tree, nullifier set, and
//! scan cursor. The client verifies integrity via `blake3(height LE || tree_data
//! || nullifier_index) == snapshot_hash` and authenticates against
//! `GetTreeState(height).state_root`.
//!
//! Falls back to `backfill_money_tree_to_birthday` if the server does not
//! support `GetCheckpointSnapshot`.

use crate::lightwallet_client::{CheckpointSnapshotDto, LightwalletClient};

/// Downloaded checkpoint data from the server.
#[derive(Debug, Clone)]
pub struct CheckpointData {
    pub height: u32,
    pub block_hash: Vec<u8>,
    pub state_root: Vec<u8>,
    pub tree_data: Vec<u8>,
    pub nullifier_index: Vec<u8>,
    pub scan_cursor: u32,
    pub snapshot_hash: Vec<u8>,
}

fn hex_encode(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

/// Canonical snapshot integrity digest: `blake3(height LE || tree_data || nullifier_index)`.
/// Must stay lockstep with `darkfi-lightwalletd` and Moonshine.
pub fn snapshot_integrity_hash(height: u32, tree_data: &[u8], nullifier_index: &[u8]) -> [u8; 32] {
    let mut hasher = blake3::Hasher::new();
    hasher.update(&height.to_le_bytes());
    hasher.update(tree_data);
    hasher.update(nullifier_index);
    *hasher.finalize().as_bytes()
}

/// Reassemble a streamed `GetCheckpointSnapshot` response.
///
/// The server splits `tree_data` into 1 MiB chunks; metadata lives on the first
/// frame. Subsequent frames contribute only additional `tree_data` bytes.
pub fn assemble_checkpoint_chunks(
    chunks: impl IntoIterator<Item = CheckpointSnapshotDto>,
) -> Result<CheckpointSnapshotDto, String> {
    let mut iter = chunks.into_iter();
    let mut first = iter
        .next()
        .ok_or_else(|| "Empty checkpoint snapshot stream from server".to_string())?;
    for chunk in iter {
        first.tree_data.extend_from_slice(&chunk.tree_data);
    }
    Ok(first)
}

impl CheckpointData {
    /// Verify the integrity of the checkpoint using blake3.
    pub fn verify_integrity(&self) -> Result<(), String> {
        if self.snapshot_hash.len() != 32 {
            return Err(format!(
                "Checkpoint snapshot_hash must be 32 bytes, got {}",
                self.snapshot_hash.len()
            ));
        }
        let computed = snapshot_integrity_hash(self.height, &self.tree_data, &self.nullifier_index);
        if computed.as_slice() != self.snapshot_hash.as_slice() {
            return Err(format!(
                "Checkpoint integrity check failed at height {}: expected {}, got {}",
                self.height,
                hex_encode(&self.snapshot_hash),
                hex_encode(&computed),
            ));
        }
        Ok(())
    }
}

/// Height passed to `GetCheckpointSnapshot`.
///
/// Birthday `0` requests the latest snapshot (`preferred_height = 0`).
/// Any other birthday requests the greatest snapshot at or below that height.
pub fn checkpoint_preferred_height(birthday: u32) -> u32 {
    if birthday > 0 {
        birthday
    } else {
        0
    }
}

/// Accept a snapshot when the request was "latest" (`requested == 0`), or
/// when the returned height is at or below the birthday that was requested.
///
/// A non-zero birthday must not install a tree above that birthday: the
/// server is not supposed to return one, and appending the scan window on
/// top of it would double-count leaves.
pub fn checkpoint_height_acceptable(requested: u32, returned: u32) -> bool {
    requested == 0 || returned <= requested
}

/// Download and apply a checkpoint snapshot for instant restore.
///
/// 1. Calls `GetCheckpointSnapshot(birthday)` when `birthday > 0`, else
///    `GetCheckpointSnapshot(0)` (latest).
/// 2. Accepts the returned height when [`checkpoint_height_acceptable`].
/// 3. Verifies `blake3(height || tree_data || nullifier_index) == snapshot_hash`.
/// 4. Installs `tree_data` and records that height as the tree position.
///    The scan cursor is that tree position only. Note discovery still
///    starts at `max(snapshot + 1, birthday)` via `clamp_scan_start`.
/// 5. Sets `merkle_from_genesis`. A snapshot below the birthday does not
///    replay `0..=birthday`; `ensure_merkle_caught_up` appends only the
///    open suffix `(snapshot, birthday)`.
///
/// `NOT_FOUND` and other RPC errors propagate so the caller keeps the full
/// `GetNoteCommitments` replay.
pub async fn download_and_apply_checkpoint(
    drk: &drk::Drk,
    client: &LightwalletClient,
    birthday: u32,
) -> Result<u32, String> {
    let (last, _) = drk.get_last_scanned_block().map_err(|e| e.to_string())?;
    if last > 0 {
        return Err(format!(
            "refusing checkpoint; wallet already scanned to {last}"
        ));
    }

    let requested = checkpoint_preferred_height(birthday);
    let snapshot = client.get_checkpoint_snapshot(requested).await?;

    let checkpoint = CheckpointData {
        height: snapshot.height,
        block_hash: snapshot.block_hash,
        state_root: snapshot.state_root,
        tree_data: snapshot.tree_data,
        nullifier_index: snapshot.nullifier_index,
        scan_cursor: snapshot.scan_cursor,
        snapshot_hash: snapshot.snapshot_hash,
    };

    if !checkpoint_height_acceptable(requested, checkpoint.height) {
        return Err(format!(
            "Checkpoint height {} is above requested height {requested}",
            checkpoint.height
        ));
    }

    // Step 1: Verify snapshot integrity.
    checkpoint.verify_integrity()?;

    // Step 2: Verify state_root against GetTreeState at the checkpoint height.
    match client.get_authenticated_tree_state(checkpoint.height).await {
        Ok(tree_state) => {
            if !tree_state.block_hash.is_empty()
                && !checkpoint.block_hash.is_empty()
                && tree_state.block_hash != checkpoint.block_hash
            {
                return Err(format!(
                    "Checkpoint block_hash mismatch at height {}",
                    checkpoint.height
                ));
            }
            if !tree_state.state_root.is_empty()
                && !checkpoint.state_root.is_empty()
                && tree_state.state_root != checkpoint.state_root
            {
                return Err(format!(
                    "Checkpoint state_root mismatch at height {}: tree_state={}, checkpoint={}",
                    checkpoint.height,
                    hex_encode(&tree_state.state_root),
                    hex_encode(&checkpoint.state_root),
                ));
            }
        }
        Err(e) => {
            tracing::warn!(
                target: "wallet-checkpoint",
                "GetTreeState verification skipped (server error: {e}); proceeding with snapshot_hash only"
            );
        }
    }

    // Step 3: Deserialize and install Money Merkle tree.
    let tree: darkfi_sdk::crypto::MerkleTree =
        darkfi_serial::deserialize_async(&checkpoint.tree_data)
            .await
            .map_err(|e| format!("Failed to deserialize checkpoint tree: {e}"))?;

    drk.cache
        .insert_merkle_trees(&[(drk::money::KVDB_MERKLE_TREES_MONEY, &tree)])
        .map_err(|e| format!("Failed to persist checkpoint tree: {e}"))?;

    // Step 4: The scan cursor is the tree position only. Note discovery
    // uses clamp_scan_start(cursor, birthday), which is the first height
    // after this tree (or the birthday, when that is later).
    let cursor = checkpoint.height;
    crate::sync::persist_scanned_height(drk, cursor)?;
    crate::sync::set_merkle_from_genesis(drk, true)?;
    crate::sync::set_merkle_tree_height(drk, checkpoint.height)?;

    tracing::info!(
        target: "wallet-checkpoint",
        "Applied checkpoint at height {} (scan_cursor={}), {} bytes tree, {} bytes nullifiers",
        checkpoint.height,
        cursor,
        checkpoint.tree_data.len(),
        checkpoint.nullifier_index.len(),
    );

    Ok(checkpoint.height)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::lightwallet_client::CheckpointSnapshotDto;

    fn signed_checkpoint(height: u32, tree_data: &[u8], nullifiers: &[u8]) -> CheckpointData {
        CheckpointData {
            height,
            block_hash: vec![0u8; 32],
            state_root: vec![0u8; 32],
            tree_data: tree_data.to_vec(),
            nullifier_index: nullifiers.to_vec(),
            scan_cursor: height.saturating_sub(1),
            snapshot_hash: snapshot_integrity_hash(height, tree_data, nullifiers).to_vec(),
        }
    }

    #[test]
    fn checkpoint_integrity_valid() {
        let cp = signed_checkpoint(50000, b"fake_tree_data", b"fake_nullifiers");
        assert!(cp.verify_integrity().is_ok());
    }

    #[test]
    fn checkpoint_integrity_tampered_tree() {
        let mut cp = signed_checkpoint(50000, b"fake_tree_data", b"fake_nullifiers");
        cp.tree_data = b"tampered".to_vec();
        assert!(cp.verify_integrity().is_err());
    }

    #[test]
    fn checkpoint_integrity_tampered_nullifiers() {
        let mut cp = signed_checkpoint(50000, b"fake_tree_data", b"fake_nullifiers");
        cp.nullifier_index = b"other".to_vec();
        assert!(cp.verify_integrity().is_err());
    }

    #[test]
    fn checkpoint_integrity_wrong_height() {
        let mut cp = signed_checkpoint(50000, b"fake_tree_data", b"fake_nullifiers");
        cp.height = 49999;
        assert!(cp.verify_integrity().is_err());
    }

    #[test]
    fn checkpoint_integrity_empty_hash_rejected() {
        let cp = CheckpointData {
            height: 1,
            block_hash: vec![],
            state_root: vec![],
            tree_data: b"tree".to_vec(),
            nullifier_index: vec![],
            scan_cursor: 0,
            snapshot_hash: vec![],
        };
        assert!(cp.verify_integrity().is_err());
    }

    #[test]
    fn assemble_checkpoint_chunks_concatenates_tree_data() {
        let height = 100u32;
        let tree = b"AAAABBBBCCCC".to_vec();
        let nfs = b"nf".to_vec();
        let hash = snapshot_integrity_hash(height, &tree, &nfs).to_vec();
        let assembled = assemble_checkpoint_chunks([
            CheckpointSnapshotDto {
                height,
                block_hash: vec![1; 32],
                state_root: vec![2; 32],
                tree_data: b"AAAA".to_vec(),
                nullifier_index: nfs.clone(),
                scan_cursor: 99,
                snapshot_hash: hash.clone(),
            },
            CheckpointSnapshotDto {
                height,
                block_hash: vec![1; 32],
                state_root: vec![2; 32],
                tree_data: b"BBBBCCCC".to_vec(),
                nullifier_index: vec![],
                scan_cursor: 99,
                snapshot_hash: hash,
            },
        ])
        .unwrap();
        assert_eq!(assembled.tree_data, tree);
        assert_eq!(assembled.nullifier_index, nfs);
        assert_eq!(assembled.height, 100);
    }

    #[test]
    fn assemble_checkpoint_chunks_empty_fails() {
        assert!(assemble_checkpoint_chunks(Vec::<CheckpointSnapshotDto>::new()).is_err());
    }

    #[test]
    fn checkpoint_request_is_birthday_or_latest() {
        assert_eq!(checkpoint_preferred_height(0), 0);
        assert_eq!(checkpoint_preferred_height(1), 1);
        assert_eq!(checkpoint_preferred_height(5000), 5000);
    }

    #[test]
    fn checkpoint_accepts_snapshot_at_or_below_birthday() {
        assert!(checkpoint_height_acceptable(5000, 4096));
        assert!(checkpoint_height_acceptable(5000, 5000));
        assert!(!checkpoint_height_acceptable(5000, 5001));
        assert!(!checkpoint_height_acceptable(1, 2));
    }

    #[test]
    fn checkpoint_latest_request_accepts_any_height() {
        assert!(checkpoint_height_acceptable(0, 0));
        assert!(checkpoint_height_acceptable(0, 1));
        assert!(checkpoint_height_acceptable(0, 50_000));
    }
}
