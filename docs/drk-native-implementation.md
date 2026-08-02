# Native `drk` implementation plan (Android)

This document maps upstream **`bin/app/src/plugin/drk.rs`** and **`bin/drk`** to Nighthawk’s UniFFI + Kotlin stack. It is the execution checklist after the bootstrap scaffold lands in `rust/darkfi-mobile-ffi`.

## Upstream reference

| Upstream | Role |
|----------|------|
| [`bin/app/src/plugin/drk.rs`](https://github.com/darkrenaissance/darkfi/blob/master/bin/app/src/plugin/drk.rs) | In-process plugin: paths, `Drk::new`, scan loop, balance UI hooks |
| [`bin/drk/src/lib.rs`](https://github.com/darkrenaissance/darkfi/blob/master/bin/drk/src/lib.rs) | Wallet core: turso + aegis256 `wallet.db`, money contracts, RPC to `darkfid` |
| [`bin/drk/drk_config.toml`](https://github.com/darkrenaissance/darkfi/blob/master/bin/drk/drk_config.toml) | Default endpoints / network |
| [`bin/darkfid/src/rpc/`](https://github.com/darkrenaissance/darkfi/tree/master/bin/darkfid/src/rpc) | Chain JSON-RPC consumed by `drk` |

Pinned revision: **`docs/upstream/darkfi-revision.txt`** (full tip SHA; no `bin/drk` overlay).

## Current Nighthawk state (this PR)

| Layer | Status |
|-------|--------|
| Kotlin **`PersistableDarkfiWallet`** | Seed, network, `DarkfiEndpoint`, optional birthday |
| **`DrkWalletPaths`** | `filesDir/drk/wallet.db`, `filesDir/drk/cache` (mirrors Android plugin paths) |
| **`DrkWalletPassStore`** | Random `wallet_pass` in encrypted prefs (turso/aegis key material) |
| UniFFI **`DrkBootstrapConfig`** + **`DarkfiWalletHandle::new`** | `Drk::new`, wallet/money init, mnemonic import |
| **`NativeDarkfiSynchronizer`** | Probe-gated; balance, address, scan via FFI |
| **`DarkfiSynchronizerFactory`** | Native when probe OK; stub fallback on open failure |
| **`DarkfidLineJsonRpcCaller`** | Kotlin JSON-RPC to remote `darkfid` (Tor-aware) |

Phase 1 — Link upstream `drk` in `darkfi-mobile-ffi` — **done**

1. Vendor DarkFi: `./scripts/vendor-darkfi.sh` (writes `third_party/darkfi/` at pinned SHA).
2. Path dependencies in `rust/darkfi-mobile-ffi/Cargo.toml` (`drk`, `darkfi-sdk`).
3. Workspace `[patch.crates-io]` in `rust/Cargo.toml` (halo2 + url forks required by upstream).
4. **`DarkfiWalletHandle::new`** calls `Drk::new`, `initialize_wallet`, `initialize_money`, mnemonic key import.
5. Android: `./scripts/build-darkfi-mobile-ffi-android.sh` (uses repo-local `CARGO_HOME=.cargo-home`).

**Wallet crypto:** tip `drk` encrypts with turso experimental `aegis256`. Wipe any
pre-tip local `wallet.db` files after the pin bump (format is not SQLCipher-compatible).

## Phase 2 — Scan + balance (plugin parity)

Implemented in `darkfi-mobile-ffi`:

| Step | Status |
|------|--------|
| `Drk::new` + wallet/money init | Done |
| Mnemonic → `import_money_secrets` (blake3 derive) | Done |
| `refresh_now()` → `scan_blocks` + `DrkSyncSnapshot` | Done |
| Background `subscribe_blocks` + 20s retry | Done (`sync.rs`, upstream `drk.rs` parity) |
| Embedded **darkfid** (loopback RPC) | Done when `darkfid_exec` packaged — `DarkfidDaemonBootstrap` / FGS |
| `Drk::new` receives `darkfid_endpoint_url` | Done (`bootstrap.rs`) |
| darkfid **`ping`** probe + DISCONNECTED status | Done (`DarkfidConnectionProbe`, `NativeDarkfiSynchronizer`) |
| `sync_snapshot()` → last scanned / chain tip | Done |
| `confirmed_balance_atomic()` | Done |
| `primary_deposit_address()` | Done |
| Kotlin `NativeDarkfiSynchronizer` wiring | Done |

## Phase 3 — Transactions

| Feature | Upstream API | Mobile export | Status |
|---------|--------------|---------------|--------|
| Build transfer | `drk.transfer(...)` | `build_transfer(recipient, amount, token_id?)` → signed tx bytes | Done |
| Broadcast | `drk.broadcast_tx` (+ simulate / mark spend) | `broadcast_transfer(tx_bytes)` → txid | Done |
| History | `get_txs_history()` | `list_transactions()` → `DrkTransactionRecord` / `DarkfiTransactionOverview` | Done |
| Birthday scan | `get_block_by_height` + cache seed | `DrkBootstrapConfig.birthday_height` in bootstrap | Done |
| Fee estimate | `get_tx_fee` / `tx.calculate_fee` | `estimate_transfer_fee(...)` | Done |
| Instrumented JNI | — | `DarkfiMobileFfiInstrumentedTest` | Done |

Keep signing **inside Rust**; Kotlin never sees spend keys.

## Phase 4 — Tor / endpoint policy

- Today: Kotlin **`DarkfidLineJsonRpcCaller`** uses tor-android SOCKS.
- Target: Rust owns sockets with the same SOCKS/VPN policy, or JNI callback into Kotlin for connect (document threat model either way).
- **`DrkBootstrapConfig.darkfid_endpoint_url`** stays the single source of truth for node URL display + Rust connect.

## Phase 5 — Testing matrix

| Test | Type |
|------|------|
| Bootstrap validation | Rust unit tests in `darkfi-mobile-ffi` |
| Mnemonic derivation | Rust unit tests in `mnemonic.rs` |
| Bootstrap config mapping | Kotlin `DarkfiMobileFfiBootstrapConfigTest` |
| Sync snapshot → UI progress | Kotlin `DarkfiSyncSnapshotMappingTest` |
| Coordinator / factory / paths | Kotlin Robolectric tests |
| UniFFI round-trip | Instrumented test with built `.so` |
| Scan against testnet `darkfid` | Manual / CI job with secrets — see [`alpha-testnet-connection.md`](alpha-testnet-connection.md) |
| Balance after faucet | Integration |

## Non-goals (short term)

- Reimplementing **`drk`** logic in Kotlin
- Hosted / server-side signing wallet
- Full CLI **`drk`** binary in APK (only UniFFI library)

## Related docs

- [`alpha-testnet-connection.md`](alpha-testnet-connection.md) — 0.3 alpha testnet: run `darkfid`, wallet RPC presets, P2P seeds
- [`wallet-roadmap.md`](wallet-roadmap.md) — product direction (embedded drk vs thin client)
- [`darkfi-integration.md`](darkfi-integration.md) — build + UniFFI regeneration
- [`darkirc-embedded-android.md`](darkirc-embedded-android.md) — Event Graph via bundled `darkirc_exec`
