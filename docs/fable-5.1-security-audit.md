# Fable 5.1 security audit — Nighthawk Android (app + FFI)

**Audit date:** 2026-09-20  
**Model:** Claude Fable 5.1  
**Review date:** 2026-09-21  
**Shipped:** app `3.00.015` / `WALLET_VERSION_CODE=30001815`; FFI `darkfi-mobile-ffi` 0.2.2  

Full auditor transcripts:

- [`fable-5.1-audit-android-app-source.md`](fable-5.1-audit-android-app-source.md) — subagent `a595c041`
- [`fable-5.1-audit-ffi-wallet-darkirc-source.md`](fable-5.1-audit-ffi-wallet-darkirc-source.md) — subagent `ceca61f2`
- [`fable-5.1-audit-unifomr-sync-source.md`](fable-5.1-audit-unifomr-sync-source.md) — subagent `dbde00de`
- [`fable-5.1-audit-unifomr-explore-source.md`](fable-5.1-audit-unifomr-explore-source.md) — subagent `0659fd0c`

---

## Android app MUST-FIX

| ID | Finding | Status | Evidence |
|----|---------|--------|----------|
| A1.1 | Peer HUD reverse-DNS / raw IP | **FIXED** | `PeerHostDisplay` opaque `peer N`; tests assert no IP |
| A1.2 | `refreshNow` on Main / destroyed handle | **FIXED** | `NativeDarkfiSynchronizer` uses `Dispatchers.IO` |
| A1.3 | Release `opt-level=0` | **FIXED** | `rust/Cargo.toml` `opt-level=3`, `lto=thin`, strip |
| A1.4 | Reorg banner clobbered by snapshot | **FIXED** | Sticky `_lastReorg` until `refreshNow` |
| A1.5 | Default ngrok LWD + rotating pin | **DEFERRED** | Testnet Studio endpoint retained intentionally; pin rotation ops |
| A1.6 | abiFilters include empty v7a/x86 | **FIXED** | `arm64-v8a`, `x86_64` only |

## FFI UnifOMR sync MUST-FIX

| ID | Finding | Status | Evidence |
|----|---------|--------|----------|
| M1 | Money Merkle double-append (non-strict) | **FIXED** | `process_compact_block(..., append_commitments: bool)` |
| M2 | Tree vs cursor persist non-atomic | **PARTIAL** | Reorg/cursor paths improved; full atomicity still SHOULD |
| M3 | `pad_block_range` no tip clamp | **FIXED** | `aligned_end.min(tip)` in `lightwallet_client.rs` |
| M4 | PIR vs OMR rate limit | **FIXED** (server) | LWD separate PIR limiter; client limiter still SHOULD |
| M5 | `complete=false` can rewind cursor | **FIXED** | `clamp_omr_truncated_tip` + covered_end guards |

## FFI wallet / DarkIRC MUST-FIX

| ID | Finding | Status | Evidence |
|----|---------|--------|----------|
| 1.1 | Reorg callback deadlock | **FIXED** | Collect reorg → `drop(drk_guard)` → callback |
| 1.2 | Send needs darkfid | **FIXED** | `set_zkas_lookup_fallback` / LWD `LookupZkas` |
| 1.3 | `generate_new_address` = random key | **FIXED** | HD via `bootstrap::generate_hd_address` |
| 1.4 | Plaintext memos in unencrypted kvdb | **FIXED** | `wrap_payment_meta` / `unwrap_payment_meta` XChaCha20-Poly1305 encryption (`sync.rs`) |
| 1.5 | Broadcast timeout keeps SENT meta | **FIXED** | Pending marker removed on broadcast error (`transactions.rs:424-425`) |
| 1.6 | No `put_tx_history_record` | **FIXED** | `put_tx_history_record(..., "Broadcasted")`; `is_sent` derived from negative net atomic (`transactions.rs`) |
| 1.7 | Mesh wipe → all-zero key | **FIXED** | `identity::wipe` regenerates via RNG after zeroize |
| 1.8 | `recv_seq` before AEAD | **FIXED** | Decrypt / seq check ordering in `mesh/session.rs` |
| 1.9 | Tor onion disabled | **FIXED** | `allow_onion_addrs(true)` / `connect_to_onion_services` |
| 1.10 | ~1/2048 mnemonics 21 words / validation bypass | **FIXED** | `make_seed` enforces 22 words; `validate_darkfi_mnemonic` verifies count + `is_new_seed` prefix |
| 1.11 | DM channel/nick plaintext | **FIXED** | Saltbox encrypts channel + nick + msg; `is_outgoing` via FFI; AndroidChat uses `line.isOutgoing` |
| 1.12 | Release `opt-level=0` | **FIXED** | Same as A1.3 |

## SHOULD-FIX / process

App SHOULD list (token balance, locale amounts, SyncWorker no-op, chat auto-start clearnet, wipe-on-connection strings, …) deferred.  
Funded UnifOMR e2e matrix still open (process gate).
