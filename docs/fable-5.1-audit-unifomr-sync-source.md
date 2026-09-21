## UnifOMR audit — findings

### 1. MUST-FIX before 3.00.014

| # | Where | Problem | Fix |
|---|---|---|---|
| M1 | FFI `sync.rs:1173` (`apply_omr_sparse_window`) + `sync.rs:1891` (`process_compact_block`) + `sync.rs:904-923` | **Money Merkle tree double-append.** In non-strict mode (the platform default, see N1) `try_omr_sync` first appends every commitment in `[scan_start, tip]` via `GetNoteCommitments` and persists the tree (`:1315`), then `trial_decrypt_range(td_start=max(padded_start,birthday), padded_end)` re-appends every output of every block again via `process_compact_block` (`:1891`, persisted `:2026`) — including `padded_start..scan_start` already appended in earlier cycles. Tree diverges from chain → wrong leaf positions → spend proofs fail. Moonshine avoids this with a `seen` set (`moonshine/src/sync.rs:764-766`). | In the supplemental path call `process_compact_block(drk, block, /*append=*/false)`-style variant that only trial-decrypts and `mark()`s existing positions (or dedup via a `seen` set of already-appended commitments); never append in both passes. |
| M2 | FFI `sync.rs:1315` vs `sync.rs:926` | **Tree persist and cursor persist are non-atomic.** Tree is written in `apply_omr_sparse_window`, but `persist_scanned_height` only runs after supplemental trial decrypt. Any error in between (M3 makes this the common case) leaves the tree advanced and the cursor not → next cycle appends the same window again. | Persist `scanned_height` immediately after `insert_merkle_trees` in `apply_omr_sparse_window`, or persist tree+cursor in one kvdb batch; make supplemental trial decrypt failures non-fatal for cursor advance. |
| M3 | FFI `lightwallet_client.rs:1000,1906-1917` + server `server.rs:129-133` (`spawn_compact_range`, since `d62bd33`) | **`get_compact_block_range` pads past tip → server aborts.** FFI `pad_block_range` has no tip clamp; server now sends `Status::aborted("chain discontinuity")` for any missing height. Every call whose 1024-bucket end exceeds tip fails (client discards already-received blocks on stream error, `:1028-1032`). Hits: supplemental trial decrypt (`sync.rs:967`, re-pads each 500-block batch), PIR-fallback full-window fetch (`sync.rs:1100`, re-pads already-clamped range back past tip), and `try_trial_decryption_sync` (`:1385`). Result: non-strict OMR cycles error out (→ M2), full trial decrypt never reaches tip. No client handling of `Aborted` exists. | Add `tip` param to `pad_block_range` and clamp like Moonshine (`moonshine/src/sync.rs:1467`), or make `get_compact_block_range` accept a pre-padded flag and skip re-padding at `sync.rs:1100`. |
| M4 | FFI `sync.rs:2067-2095` (`fetch_blocks_via_pir`) vs server `server.rs:1401` + `config.rs:147` | **PIR limb loop exceeds server OMR rate limit.** One `FetchPirBatch` per limb, `1+ceil(len/8)` limbs; a ~300 B compact block needs ~39 RPCs; default `omr_rate_limit_per_min = 30`, counted per IP (shared Tor exits). PIR fails → falls to M3 path. Client `omr_limiter` (6/min) is not applied to `fetch_pir_batch` (`lightwallet_client.rs:1535`) so client never self-throttles. | Server: add `limb_count`/multi-limb per request (or exclude PIR from the digest limiter / separate higher bucket); client: request multiple limb columns per RPC and add backoff on `ResourceExhausted`. |
| M5 | FFI `sync.rs:830-846` | **`complete=false` clamp can rewind cursor below `scan_start`.** Server truncates the flattened prefix (`unifomr.rs:1046-1070`) starting from `padded_start < scan_start`; if the first 262,144 messages lie below `scan_start`, `covered_end < scan_start` → `window_tip < scan_start` → `apply_omr_sparse_window(scan_start, window_tip)` with start>end and `persist_scanned_height(covered_end)` rewinds. | `if covered_end < scan_start { return Err(..) }` else `window_tip = covered_end`; alternatively make the server cap from `scan_start` upward. |

### 2. SHOULD-FIX soon

| # | Where | Problem | Fix |
|---|---|---|---|
| S1 | FFI `unifomr.rs:590-592` (`det_pk.try_encrypt`) | **Detection key is 2× larger than necessary.** fhe-rs public-key encryption stores both polys (`public_key.rs:98` `seed: None`); symmetric `SecretKey::try_encrypt` stores a 32-byte seed for `c1` (`secret_key.rs:110-138`). Current size: 1024 × 2 × 3 × 4096 × 5 B ≈ **120 MiB** upload per sync cycle; seed form ≈ 60 MiB. Server `Ciphertext::from_bytes` already handles seeds — no server change. | Encrypt sk coefficients with `det_sk.try_encrypt` (client holds sk anyway). Consider also mod-switching det-key CTs to 2 moduli if noise budget allows (cryptographer review). |
| S2 | FFI `sync.rs:259-265` | Key rebuilt with fresh randomness every cycle (intentional, unlinkability) but **re-uploaded ~120 MiB every 5 s poll cycle** when behind tip (`LIGHTWALLET_POLL_BASE_SECS = 5`, `sync.rs:61`). Over Tor (~1-2 MB/s) that is 1-2 min per window of ≤4096 blocks. | Widen `MAX_OMR_WINDOW` toward server `max_range` (10 000) when padding permits; increase poll base once caught up; document the cost. |
| S3 | server `server.rs:1250-1256` | **FHE permit (2 total) held during the ~120 MiB body read.** Two slow Tor uploaders starve digest + PIR for everyone. Comment explains the trade-off (avoid parking 160 MiB per waiter). | Use a separate upload byte-budget semaphore (e.g. 3×160 MiB) for the read phase, acquire FHE permit only after `key_done`. |
| S4 | server `server.rs:1475-1476` (`fetch_pir_batch`) | PIR response CT not mod-switched (full 3 moduli, ~120 KiB); digest path does `switch_to_level` (`unifomr.rs:903`). | `ct.switch_to_level(ct.max_switchable_level())` before `to_bytes` → ~40 KiB. Client decrypts fine at any level. |
| S5 | FFI `sync.rs:2069` | `queries.clone()` re-uploads identical ~120 KiB stripe queries per limb RPC (39-257× per window). | Tied to M4: multi-limb requests. |
| S6 | FFI `lightwallet_client.rs:1159-1215` | `get_note_commitments`/`get_nullifiers` are unpadded → server sees exact `scan_start`/`tip` every cycle, negating digest padding. | Request `[padded_start, padded_end]` and filter locally (data is small). |
| S7 | FFI `lib.rs:390-402` + docs `security-threat-model.md:36`, `UPGRADE_OMR.md:96-103`, iOS FFI `README.md:41` | `MissingOmrClues` is never set on the supplemental path (sync_type stays `Omr`, status `Degraded`); docs still describe "zero-match threshold / gap >100" scanning that no longer exists (code trial-decrypts the whole padded window). | Set a distinct fallback reason when supplemental trial decrypt runs; update docs. |
| S8 | FFI `lightwallet_sync.rs:256,279` vs `lib.rs:712` | Engine default `strict_omr_only = true` differs from both app defaults (false). Harmless for apps (config overrides), but desktop/other FFI callers that skip `set_strict_omr_only` silently get strict mode. | Pick one default and document it. |
| S9 | FFI `sync.rs:1173,1891` | `Coin::from(base)` skipped on `from_repr` failure without appending → any non-canonical 32-byte coin from a malicious/buggy server silently shifts all later leaf positions. | Treat as hard error (fail window, don't advance). |

### 3. NON-ISSUES / verified intentional

- **N1 strict default:** Android `DarkfiChatPreferences.kt:33-34` and iOS `UserPreferencesStorage.swift:220-221` default `false` → fallback ON, matching READMEs (`nighthawk-ios-wallet/README.md:350`). Runtime toggles wired on both (`SettingsViewModel.kt:112-120`, `SDKSynchronizerLive.swift:20-26`, `AdvancedView.swift:57`).
- **N2 crypto/wire lockstep:** constants identical (diffed): `SCHEME 0x05`, `CLUE_VERSION 0x01`, both domains, `CLUE_N/Q/H/ERROR_BOUND/SIGMA/PLAINTEXT_BITS/R_PRIME`, BFV `D=4096, t=CLUE_Q, [40,40,40]`, `DIGEST_FORMAT_VERSION 0x01`, `MAX_OMR_MESSAGES 262 144`, det-key header `[0x02|net|0x05|n u16 LE]`, clue PK `[ver|scheme|n|16n]`, ownership/attest domains `CluePK-v2`/`DirAttest-v1`, `OWNERSHIP_PROOF_WIRE_LEN 128`, PIR domain `PirKey-v1`, `MAX_PIR_STRIPES 8`, `MAX_PIR_LIMBS 4096`. Server-only `UNIFOMR_CLUE_DIR_DECOY` is correct (client never builds decoys). `lightwallet.proto` byte-identical across 3 repos.
- **N3 multi-key digest framing:** server frames `[u32 len][digest]` only when `keys.len()>1` (`server.rs:1359-1372`); client mirrors on `clients.len()` (`sync.rs:2139-2168`). Budget math (`SERVER_DETECTION_KEYS_TOTAL_BUDGET = 160 MiB`, one ~120 MiB key per RPC) matches server `MAX_DETECTION_KEY(S_TOTAL)_BYTES`.
- **N4 OMR-digest window** *is* tip-clamped (`sync.rs:686`) — the M3 bug is confined to `GetBlockRange` callers.
- **N5 GetCluePublicKey**: always `found=true`, decoy is deterministic and directory-attested, fixed 250 ms deadline (`server.rs:1609-1614`); client verifies attestation against `GetLightInfo.directory_attest_pubkey` (`transactions.rs:284-310`). Sender to unregistered recipient gets a decoy clue → recipient relies on supplemental trial decrypt — by design.
- **N6 untrusted-input parsing:** FFI `decrypt_digest_slots`, `unpack_slot_heights`, `parse_detection_key` (server) all length-check before slicing; `try_into().unwrap()` uses are guarded. `reject_oversized_window` uses checked `height_span`. Tonic client limits 160 MiB both directions (`lightwallet_client.rs:85-94`) — fine for ≤5 MiB digests.
- **N7 TLS/transport:** remote `https://` requires a pin (`lightwallet_client.rs:653-684`); socks5 URL maps port 443 → `https` (`:1966`); pin plumbing exists on both platforms with current+previous rotation (`LightwalletTlsPin.kt`, `LightwalletTlsPin.swift`). `redact_sync_error` strips IPv4 and URLs (not IPv6 — minor).
- **N8 reorg wiring:** both platforms register `setReorgCallback` and surface `summaryMessage` (`NativeDarkfiSynchronizer.kt:62-73`, `SDKSynchronizerLive.swift:253-258`); FFI `handle_reorg_recovery` does `rewind_to_height` + `prune_above` + `invalidate_transactions_above` (`lib.rs:1050-1101`).
- **N9 windowed decay/backoff:** `record_omr_failure` halts strict at 5, exponential backoff `2^min(n,6)`, success halves (`lightwallet_sync.rs:568-612`) — as documented.

### 4. Effective constants

| Constant | Server | FFI (Android/iOS) | Moonshine |
|---|---|---|---|
| Scheme / clue ver / digest ver | 0x05 / 0x01 / 0x01 | same | same |
| CLUE_N / Q / H / bound / σ / ℓ / R′ | 1024 / 1 032 193 / 80 / 84 / 0.5 / 2 / 149 | same | same |
| BFV D / t / moduli | 4096 / q / [40,40,40] | same | same |
| MAX_OMR_MESSAGES | 262 144 (64 chunks) | 262 144 | — |
| Det-key size (computed) | cap 160 MiB/key, 160 MiB total | ≈120 MiB/key (1024×122 880 B) | same |
| Digest size (computed) | ≈80 KiB / 4096 msgs, ≤5 MiB | decode cap 160 MiB | — |
| Max keys per RPC | 16 | 16 (practically 1) | 16 |
| Block range cap | 10 000 | `MAX_OMR_WINDOW` 4096 (pre-pad), `MIN_BUCKET` 1024 | `MIN_BUCKET` 1024, tip-clamped |
| Trial batch | — | 500 | — |
| PIR stripes / limbs / query CT / window bytes | 8 / 4096 / 4 MiB / 64 MiB | 8 / 4096 | — |
| OMR rate limit | 30/min/IP (digest **and** PIR) | 6/min (digest only) | — |
| FHE permits | 2 | — | — |
| Clue lookup timing pad | 250 ms | — | — |
| Poll / max cycle | — | 5 s base ±30 %, 600 s | 1 s |
| OMR failure threshold / backoff cap | — | 5 / 2⁶ | — |
| strict_omr_only default | — | engine `true`, apps `false` | — |

The Android and iOS defaults for TLS pinning fallback line up consistently with the engine's bootstrap config, though the FFI default of true for `with_tls_pins` diverges for desktop callers who skip passing config — worth flagging as a minor inconsistency.

The reorg callback and fallback reason binding on both platforms look fine, and I'm also noting a minor UI quirk where degraded status from supplemental trial decrypt can show with no fallback reason, though the status text clarifies it. I'm now working through the size math for the report: detection key at ~120 MiB, digest chunks around 80 KiB per 4096 messages capping near 5 MiB, and PIR query/response sizes at ~120 KiB each, plus rate limits (30/min per IP default, 6/min client-side for digests). Still need to address the FHE permit-during-upload issue and the potential window padding from 4096 to 8192 if scan_start isn't aligned.

Checking the padding logic, I see the range extension handles unaligned starts correctly, staying under the 10k block ceiling, and confirming fhe_permits are acquired only after the rate limit check passes. I'm flagging a real concern though: the server's rate limiting for OMR digests uses peer IP, which means shared Tor exit nodes would all draw from the same 30/min budget — worth calling out as a should-fix in the writeup.

