# Instant Sync Strategy — Nighthawk Android (Source of Truth)

> **Last updated**: 2026-09-07
>
> This document describes the instant sync changes planned across all Nighthawk
> platforms (Android, iOS, Desktop, Moonshine). The Android repo hosts the
> **source-of-truth** `darkfi-mobile-ffi` Rust crate that iOS mirrors and
> Desktop consumes via Cargo path dependency.

## Philosophy

Port DarkFi contracts + wire formats. Keep UniFFI + LWD. Do **not** port the
official GUI or its `darkfid` JSON-RPC scanner. Official `scan_blocks` into
Nighthawk would be a regression — it reveals wallet interest to the full node.

## Platform Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                    darkfi-mobile-ffi (Rust)                        │
│  proto/lightwallet.proto · sync.rs · lightwallet_client.rs         │
│  omr.rs · unifomr.rs · bootstrap.rs · birthday.rs                 │
│  NEW: checkpoint.rs · sync_pipeline.rs · zkas_cache.rs             │
├────────────┬────────────┬──────────────┬──────────────────────────┤
│  Android   │    iOS     │   Desktop    │      Moonshine           │
│  UniFFI    │  UniFFI    │  Cargo dep   │  Own sync.rs/client.rs   │
│  (Kotlin)  │  (Swift)   │  (Tauri)     │  (standalone Rust CLI)   │
└────────────┴────────────┴──────────────┴──────────────────────────┘
```

## Changes (Android — this repo)

### Shared Rust Core (`rust/darkfi-mobile-ffi/`)

| # | Change | Files |
|---|--------|-------|
| 1a | Historical `GetTreeState` with `state_root` authentication | `proto/lightwallet.proto`, `src/lightwallet_client.rs` |
| 1b | Concurrent gRPC — split write lock into read/apply phases | `src/sync.rs`, `src/lightwallet_client.rs` |
| 1c | Checkpoint / snapshot blobs for instant restore | `proto/lightwallet.proto`, **NEW** `src/checkpoint.rs`, `src/bootstrap.rs` |
| 1d | OMR/PIR metering separate from GetChainTip | `src/lightwallet_client.rs`, `src/sync.rs` |
| 2a | Birthday enforcement — never trial-decrypt below birthday | `src/sync.rs`, `src/lightwallet_sync.rs` |
| 2b | Pipeline: prefetch OMR window N+1 while applying N | **NEW** `src/sync_pipeline.rs`, `src/sync.rs` |
| 2c | OMR-first audit / documentation hardening | `src/sync.rs` (logging only) |
| 2d | Cache LookupZkas / proving keys | **NEW** `src/zkas_cache.rs`, `src/sync.rs`, `src/transactions.rs` |
| 2e | Proto version lockstep guard | `proto/lightwallet.proto`, `src/lightwallet_client.rs`, `src/darkfi_mobile_ffi.udl` |

### Kotlin SDK (`darkfi-android-sdk/`)

| File | Change |
|------|--------|
| `DarkfiSyncStatus.kt` | Add `PROTO_MISMATCH` enum value |

## Execution Order

1. **2a** Birthday enforcement (safety fix)
2. **1b** Concurrent gRPC (unblocks pipelining)
3. **2b** Pipeline OMR prefetch
4. **1a** Historical GetTreeState
5. **1c** Checkpoint snapshots
6. **2d** ZkAS / proving key cache
7. **1d** OMR/PIR metering
8. **2e** Proto version lockstep
9. **2c** OMR-first audit (docs)

## Proto Sync Policy

After any proto change in this repo:
1. Copy `rust/darkfi-mobile-ffi/proto/lightwallet.proto` → iOS repo
2. Desktop auto-picks up via Cargo path dep
3. Moonshine: manually port (divergent proto)

## `scan_blocks` Rejection

The `drk::rpc::scan_blocks` function talks directly to `darkfid` JSON-RPC.
It is NOT used anywhere in the mobile FFI sync paths. `sync.rs` [L23–L33]
documents that direct `darkfid` sync is disabled in production behind
`#[cfg(feature = "direct-darkfid")]`.

## See Also

- [docs/UPGRADE_OMR.md](UPGRADE_OMR.md) — UnifOMR Param2 upgrade details
- [docs/security-threat-model.md](security-threat-model.md) — privacy model
- [docs/Architecture.md](Architecture.md) — overall app architecture
