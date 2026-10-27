# Wallet sync speed plan

Catch-up and steady-state sync for the Nighthawk Android and iOS wallets, Moonshine, and `darkfi-lightwalletd`. This replaces the tip-only checkpoint story in `nighthawk-android-wallet/docs/instant-sync-strategy.md`.

The same text is in `nighthawk-android-wallet/docs/wallet-sync-speed-plan.md` and `darkfi-lightwalletd/docs/wallet-sync-speed-plan.md`. Keep the two copies identical.

Nothing on this wire has shipped. `LightInfo.proto_version` moves from `1.0.0` to `2.0.0`. Clients already treat a major mismatch as fatal (`proto_major_mismatch` in `lightwallet_client.rs`), so old binaries stop talking to a new server instead of mis-parsing. Do not keep a `1.0.0` compatibility path.

UnifOMR stays the detector. Trial decrypt stays the recovery path for payments that carry no clue. This plan speeds up the work those two modes still do: shipping the detection key, replaying every coin into a local Money tree, and applying one window at a time under the wallet write lock.

## Lockstep

`darkfi-lightwalletd/proto/lightwallet.proto` is the proto source of truth. After each proto edit, copy it to:

| Tree | What follows the proto |
|------|------------------------|
| `nighthawk-android-wallet/rust/darkfi-mobile-ffi` | Canonical client. iOS mirrors this crate. |
| `nighthawk-ios-wallet/rust/darkfi-mobile-ffi` | Same FFI. Its proto is behind lightwalletd today (no `extra_omr_clues`). |
| `new-nighthawk-android-wallet/rust/darkfi-mobile-ffi` | Same lag. Update it if that tree is still built. |
| `moonshine` | Own sync and client. Already sends `extra_omr_clues`. Port snapshot, padding, pool, and tip-subscribe changes by hand. |

Bump both `LIGHTWALLET_PROTO_VERSION` (`darkfi-lightwalletd/src/server.rs`) and `CLIENT_PROTO_VERSION` (`lightwallet_client.rs`, Moonshine’s copy) in the same change as the first breaking message edit. Land the phases below on a branch that stays at `2.0.0`; do not ship a mid-phase server to a `1.0.0` client.

Direct `darkfid` sync stays behind `direct-darkfid` and off in production.

## Where the time goes

On a successful UnifOMR cycle, `try_omr_sync` → `apply_omr_sparse_window` (`nighthawk-android-wallet/rust/darkfi-mobile-ffi/src/sync.rs`):

1. Rebuilds a Param2 detection key and uploads it (`cached_or_build_detection_key` does not cache, on purpose).
2. Downloads every note commitment and every nullifier in the window. Sparse PIR only skips compact blocks.
3. Appends every coin into the on-device Money tree, then flushes the tree and the scan cursor once, at the end of a window of at most 4096 blocks.
4. Holds `drk.write()` across that network I/O (`sync_once_via_lightwallet`).

`prefetch_omr_window` and `PipelineReorderBuffer` (`sync_pipeline.rs`) are unused by `sync.rs`. `GetCheckpointSnapshot` / `GetTreeState` on lightwalletd are tip-only (`cache.rs` keeps a single `META_TIP_MERKLE_TREE`; `nullifier_index` is always empty). The client refuses a snapshot above the birthday, so a new wallet still replays `0..=birthday-1` through `ensure_merkle_caught_up`.

`GetNoteCommitments` and `GetNullifiers` are requested on the exact scan range, which publishes the cursor the digest padding was meant to hide.

## Phase 1 — Historical Money-tree snapshots

Largest catch-up win. The phone installs a tree at a retained height and appends only the suffix.

### lightwalletd

In `src/cache.rs`, add a sled tree `merkle_snapshots`, keyed by big-endian height. Value is the same serialized `MerkleTree` already stored at the tip.

Write a snapshot from `append_coins_to_tip_tree` when `height % 1024 == 0` or `height` is the new tip. 1024 matches the client’s minimum padded bucket. Keep the tip keys (`META_TIP_MERKLE_TREE`, `META_TIP_MERKLE_HEIGHT`) as the latest snapshot so existing tip readers stay valid.

`rewind_to_height` deletes snapshots above the rewind height, then rebuilds the tip tree as it does now.

`prune_below_height` may drop compact blocks, coins, and nullifiers. It must not drop snapshots. A pruned server can still hand a wallet a tree. Document the disk cost: one serialized tree per 1024 blocks, plus the tip.

`GetTreeState` (`src/server.rs`):

- Height equal to the tip, or equal to a stored snapshot: return `TreeState` with `is_checkpoint = true` for snapshot rows.
- Any other height: `FAILED_PRECONDITION`, and include the greatest stored snapshot `<=` the request in the status text (the client uses `GetCheckpointSnapshot` for the real fetch).

`GetCheckpointSnapshot`:

- `preferred_height == 0`: latest snapshot (the tip snapshot once phase 1 is writing one every tip).
- Otherwise: greatest snapshot whose height is `<= preferred_height`.
- Missing history: `NOT_FOUND`.
- Keep 1 MiB chunked streaming. `snapshot_hash` stays `blake3(height LE || tree_data || nullifier_index)`.
- `nullifier_index` stays empty in this phase. Spends in the suffix still come from `GetNullifiers`. Putting a full nullifier set into the snapshot is a later measurement, not a blocker.
- `scan_cursor` is the snapshot height. The client must not treat that as “notes decrypted”.

`state_root` today is `blake3(tree_data)`. Keep that, and check it against the block header’s money-tree root if `darkfid` exposes one on the cached block. If the header root is not in the compact cache yet, record that gap in the snapshot test instead of inventing a second root.

### Clients

`checkpoint.rs` `download_and_apply_checkpoint`:

- Call `GetCheckpointSnapshot(birthday)` when `birthday > 0`, else `GetCheckpointSnapshot(0)`.
- Accept the returned height when it is `<=` the requested height. Delete the current refusal of any snapshot above the birthday; the server no longer returns those for a non-zero birthday.
- Install `tree_data`, set the scan cursor to the snapshot height only for the tree. Note discovery still starts at `max(snapshot_height, birthday)` via the existing birthday clamp.
- Set `merkle_from_genesis` after a snapshot installs. Lightwalletd’s tree already starts with the dummy `ZERO` leaf (`empty_tip_tree`).
- If the snapshot is below the birthday, `ensure_merkle_caught_up` appends commitments for `(snapshot, birthday)` with decryption skipped, then OMR starts at the birthday. That suffix is the only pre-scan replay left.
- If the server returns `NOT_FOUND`, keep today’s full `GetNoteCommitments` replay.

`apply_omr_sparse_window` already appends commitments from `scan_start`. After a snapshot, `scan_start` is the first height after the snapshot, so those leaves are appended once. Do not also append them from compact outputs.

### Tests

- Cache: snapshot at 1024 and at tip; rewind drops higher snapshots; prune drops the compact block and keeps the snapshot.
- RPC: `GetCheckpointSnapshot(birthday)` returns a height `<= birthday`; `GetTreeState` on a non-snapshot height fails.
- FFI: wallet with birthday 5000 installs snapshot `<= 5000`, marks the tree complete, and does not call `GetNoteCommitments` for `0..=snapshot`.

## Phase 2 — Padded commitment and nullifier ranges

Stops the exact cursor leak, and makes the suffix download the same shape as the digest window.

### Wire

`GetNoteCommitments` and `GetNullifiers` keep their messages. Lightwalletd rejects a range that is not a power-of-two bucket ending at or before the tip, using the same rule as `pad_block_range` in `lightwallet_client.rs` (minimum 1024, cap `max_range_per_request`). Return `INVALID_ARGUMENT` otherwise. Update the proto comments on both RPCs.

Moonshine’s commitment walk must pad before it calls. A testnet server and an unpadded client will fail closed, which is the point of the major bump.

### Clients

In `apply_omr_sparse_window` and `ensure_merkle_caught_up` / `rebuild_money_tree_to_height`, request `[padded_start, padded_end]` and drop heights outside `[scan_start, scan_end]` before `tree.append`. Same filter for nullifiers. A padded prefix that was already appended on a previous cycle must be ignored (height `< scan_start`), or the tree double-appends.

## Phase 3 — Apply the window without holding the wallet lock

Client-only. No proto change.

`sync_once_via_lightwallet` takes `drk.write()` before `try_omr_sync`. Split it:

1. Under a read lock, or with no wallet lock: capabilities, clue-PK registration, digest, commitment/nullifier streams, PIR.
2. Under `drk.write()`: Merkle append, coin inserts, spend marks, cursor flush.

Wire the next window through `prefetch_omr_window` while step 2 runs for the current one. `PipelineReorderBuffer` already returns windows in `scan_start` order. Digest evaluation stays ordered too: prefetch the next digest only after the current window’s covered end is known (`complete = false` can shrink `window_tip`). One in-flight prefetch is enough. Cap it at one so a phone does not hold two detection keys and two windows of coins.

Inside `apply_omr_sparse_window`:

- Load unspent coins once per window. Build `HashMap<nullifier_bytes, (coin, leaf)>`. Mark spends from the whole nullifier stream against that map. Delete the per-height `get_coins(false)` loop.
- Batch coin inserts in one SQLite transaction per few hundred notes.
- Flush the Money tree and the scan cursor every 256 heights, still in the same transaction as each other, so a dropped connection resumes inside the window instead of redoing 4096 blocks.

`UnifOmrClient::range_check_matches` (`unifomr.rs`) walks slots on one thread. Chunk the slot slice and run the range check with `rayon`. The check is pure; heights still merge and sort on the calling thread before the filter against `scan_start`.

PIR: `fetch_blocks_via_pir` falls straight through to `get_compact_block_range_exact` on the padded window. Retry `FetchPirBatch` three times with the existing client backoff before that download. A transport error must not look like a trial-decrypt cycle.

## Phase 4 — Change clues on mobile sends

Lightwalletd and Moonshine already do this. Mobile proto and the send path do not, so strict UnifOMR misses the sender’s own change and the wallet turns trial decrypt on for the whole window.

`darkfi-lightwalletd/proto/lightwallet.proto` has `OutputOmrClue` and `RawTransaction.extra_omr_clues`. `server.rs` stores each clue on its output index. Moonshine builds them in `self_change_clues` (`moonshine/src/main.rs`).

Copy those messages into the Android and iOS protos (phase 0 of the copy, if it has not landed). In `lightwallet_client.rs` `send_transaction`, fill `extra_omr_clues` for every output index other than the payment output, using the same wallet clue key Moonshine uses (`clue_keypair_from_wallet` + `build_omr_clue_from_pk`). Do not put the recipient clue on a change output. `omr_clue_output_index` stays the payment output.

Strict mode (`strict_omr_only`) then sees change as a normal UnifOMR match. No window-wide trial decrypt is required for the sender’s own coins.

Test: mobile send with change, strict sync, change coin appears, scan cursor advances, Money tree leaf count matches `GetNoteCommitments` for that height.

## Phase 5 — Detection-key pool

Removes the Param2 key upload from every poll. A pooled handle links those polls to each other. That is a real privacy change versus today’s fresh ciphertext. Bound it: one handle per process lifetime, rotate on wallet lock and on server switch, register the upload over the Tor route the wallet already uses, and expire the server copy after 24 hours (same horizon as clue hints).

### Wire

```protobuf
rpc RegisterDetectionKey(stream DetectionKeyChunk) returns (DetectionKeyHandle);
rpc GetUnifOmrDigestFromPool(PoolDigestRequest) returns (OmrDigestResponse);

message DetectionKeyHandle {
  bytes handle = 1;       // 32 bytes, server-chosen
  uint64 expires_unix = 2;
}

message PoolDigestRequest {
  bytes handle = 1;
  uint32 start_height = 2;
  uint32 end_height = 3;
}
```

Reuse `DetectionKeyChunk` for the upload. The first chunk still carries `num_keys`. Ignore `start_height` / `end_height` on registration; the range belongs on `PoolDigestRequest`.

`GetUnifOmrDigest` stays for the first cycle of a process and for a server that has lost the handle (`NOT_FOUND` → register again).

### lightwalletd

In-memory map is enough (process restart forces a re-upload). Keyed by the 32-byte handle. Store the parsed detection key, network byte, expiry, and byte length. Cap concurrent handles (start at 4096) and reject past `max_detection_key_bytes`. Expire lazily on lookup and on a one-minute sweep. Do not log the handle or the key. Rate-limit registration with the existing OMR limiter.

`GetUnifOmrDigestFromPool` runs the same evaluator as `GetUnifOmrDigest` after the lookup. Height padding rules are unchanged.

Cache the flattened clue list for a `(start, end)` window in memory so a second pooled client does not re-read every compact block from sled. Do not cache digest ciphertexts across ranges or across handles. An incremental FHE accumulator is out of scope until `unifomr::evaluate` has an append API that a reviewer can check against the paper. The sled skip is the server-side speedup that is safe to ship with the pool.

### Clients

`cached_or_build_detection_key` builds once per handle lifetime and uploads through `RegisterDetectionKey`. Later cycles send `PoolDigestRequest`. On `NOT_FOUND` or expiry, build a new key (fresh BFV randomness, same as today) and register again. `reset_for_server_switch` drops the handle.

## Phase 6 — Wake on the tip

`SubscribeBlocks` streams full `CompactBlock`s and drops the client if a send blocks for 30 seconds (`server.rs`). Nothing in the mobile FFI or Moonshine calls it. Replace the stream payload.

```protobuf
rpc SubscribeBlocks(Empty) returns (stream ChainTip);

// ChainTip already exists: height, hash, timestamp, block_target.
// Add prev_hash so the client can see a rewind without a second RPC.
```

Add `bytes prev_hash = 5` on `ChainTip`. The subscribe handler sends a `ChainTip` when the poller watch fires, not a compact block. If the client is slow, leave the notice unsent and keep the subscription; the next tip the client does read is compared to the last applied height, and a gap is filled by the normal padded sync. Delete the 30-second drop.

The client idle loop (`sync.rs`, jittered sleep on `LIGHTWALLET_IDLE_BASE_SECS`) waits on that stream. A new tip, or a tip lower than the last one, starts one sync cycle (one padded digest, one padded commitment/nullifier delta, PIR for matches). Jitter stays on the reconnect and on the fallback poll if the stream dies. A phone that is still applying a snapshot must not miss the tip forever: on reconnect, `GetChainTip` once, then resume the stream.

## Phase 7 — Smaller block bodies when PIR fails

After phase 3’s retries, a PIR failure still downloads every compact block in the padded window. Add a body that is only what trial decrypt and the tree need:

```protobuf
message CompactOutputBody {
  uint32 height = 1;
  bytes hash = 2;
  bytes prev_hash = 3;
  bytes coin = 4;
  bytes encrypted_note = 5;
  bytes omr_clue = 6;
  bytes omr_metadata_enc = 7;
  bytes tx_hash = 8;
}

rpc GetCompactOutputBodies(BlockRange) returns (stream CompactOutputBody);
```

Same padding rule as phase 2. The server already has these fields on `CompactOutput`. The client uses this RPC for the PIR-failure path and for supplemental trial decrypt. `GetBlockRange` stays for debugging and for any caller that still wants fees and value commitments.

This does not reveal the OMR match set: the range is the padded window the digest already used.

## Phase 8 — Show notes before the tree suffix finishes

The UI can list coins decrypted from the match set while `ensure_merkle_caught_up` is still appending the pre-birthday suffix. Spends stay disabled until `merkle_from_genesis` is set and the scan cursor has reached the tip the notes were decrypted against. A spend proof needs leaf positions in the caught-up tree; a balance display does not.

Surface two states through the existing sync status (extend `LightSyncStatus` or the status string, and the Kotlin/Swift status enum):

- `Scanning` — notes may update, send is disabled.
- `Synced` — tree includes genesis (via snapshot or replay) and the cursor is at the tip.

Do not mark `Synced` when only the match set has been decrypted.

## Deferred

- Witness-only storage. `MerkleTree::witness` can serve a spend without the phone holding every historical node, but a snapshot plus a short suffix is the same tree the prover already uses. Revisit only if a measured `tree_data` blob is too large for the devices we ship.
- A full nullifier set inside `CheckpointSnapshot.nullifier_index`. Suffix `GetNullifiers` is enough while snapshot spacing is 1024.
- Caching digest ciphertexts or an incremental BFV accumulator across block ranges.
- Turning trial decrypt off. It remains the path for a counterparty that does not attach a clue.

## Order

1. Phase 1 snapshots, together with the proto copy that pulls `extra_omr_clues` onto mobile and the `2.0.0` bump.
2. Phase 2 padded ranges (server reject + client filter).
3. Phase 3 apply-loop split, prefetch, nullifier map, flush, `rayon`, PIR retry.
4. Phase 4 change clues on the mobile send path.
5. Phase 5 detection-key pool.
6. Phase 6 tip subscription.
7. Phase 7 compact output bodies.
8. Phase 8 scan-versus-spend status.

Phases 3 and 4 do not wait on each other. Phases 6 and 7 do not wait on 5. Phase 5’s sled clue-list cache is the only server CPU work tied to the pool.

## Test bar

- `cargo test` in `darkfi-lightwalletd` for snapshot retention, padded-range rejection, pool expiry, and subscribe-without-drop.
- `cargo test -p darkfi-mobile-ffi` for checkpoint install, padded filtering, nullifier map, pipeline order, and change-clue send bytes.
- One localnet catch-up: wallet birthday in the middle of a chain that already has snapshots, strict UnifOMR, a payment plus change, balance stable across a second sync. The existing 100-send UnifOMR harness stays green.
- Moonshine padded `GetNoteCommitments` against the same server. An unpadded call must get `INVALID_ARGUMENT`.
