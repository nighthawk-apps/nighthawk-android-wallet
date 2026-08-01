# DarkFi Android — implementation plan (P0–P4)

Master task list for closing gaps after the Zcash → DarkFi port. Each phase is ordered; complete **P0** before shipping mainnet builds. Track status in [`pending-features.md`](pending-features.md) and iOS parity in [`app-features.md`](app-features.md).

## How to use this doc

| Column | Meaning |
|--------|---------|
| **ID** | Stable task reference (`P0-1`, `P1-2`, …) |
| **Status** | `done` / `in_progress` / `todo` |
| **Owner** | `android` / `rust` / `ios` / `docs` |

Update status when a task lands; iOS column notes what the Swift app must mirror.

---

## P0 — Correctness / mainnet readiness

| ID | Task | Status | Notes |
|----|------|--------|-------|
| P0-1 | Rebuild `libdarkfi_mobile_ffi.so` (all ABIs) + refresh jniLibs | done (arm64+x86_64) | `./scripts/build-darkfi-mobile-ffi-android.sh`; run full 4-ABI when disk allows |
| P0-2 | Regenerate UniFFI Kotlin after UDL changes (memo APIs) | in_progress | Hand-updated `darkfi_mobile_ffi.kt` + checksums; full `uniffi-bindgen` when bindgen completes |
| P0-3 | `payment_memos` table migration for existing wallets | done | `Drk::ensure_payment_memos_table()` on read/write |
| P0-4 | Mainnet embedded darkfid `threshold = 11` | done | `DarkfidChainDefaults.confirmationThreshold` |
| P0-5 | Network-aware Tor P2P seeds (mainnet tor+tls over SOCKS) | done | `torOnionP2pSeeds` + `torTlsP2pSeeds` in embedded TOML |
| P0-6 | Flavor ↔ RPC port guard | done | `DarkfiEndpointNetworkGuard` + save blocking in `WalletViewModel` |
| P0-7 | Fix `darkfi-mobile-ffi` Cargo deps for host + NDK builds | done | `bs58`, `bip39`, `crypto_box/chacha20`, `num-bigint/rand`, etc. |

**Exit criteria:** Mainnet APK + arm64 emulator can send with memo; embedded/testnet darkfid configs match upstream ports; no cross-network endpoint without warning.

---

## P1 — Wallet UX

| ID | Task | Status | iOS parity |
|----|------|--------|------------|
| P1-1 | FFI `list_tokens` + balances per `TokenId` | done | `list_token_balances` + `DarkfiTokenBalance` |
| P1-2 | Send screen token picker (not DRK-only) | done | Dropdown when multiple tokens |
| P1-3 | Decode `ContractCall` in tx details (Money/DAO labels) | done | `contract_summary` on history rows + tx UI |
| P1-4 | `getRecipients` from scan / tx bytes | done | `outgoing_recipients` table + `transaction_recipient` |
| P1-5 | Wire `scan_blocks` → `processorInfo` / `progress` | done | Poll `sync_snapshot` every 5s |
| P1-6 | Populate fee + net value in `DrkTransactionRecord` | done | `get_tx_fee` + decrypted outputs / spent coins |

**Exit criteria:** Home balance and history reflect real chain state; tx detail shows type, fee, memo, recipient when available.

---

## P2 — Smart contracts & advanced Money

| ID | Task | Status | iOS parity |
|----|------|--------|------------|
| P2-1 | DAO propose / vote / exec (read-only or full flows) | in progress | M1 read-only hub shipped (`docs/dao-implementation-plan.md`); signing after testnet |
| P2-2 | Display minted tokens + aliases | todo | Custom assets |
| P2-3 | OTC swap flows (separate from payment memo) | todo | Swap UI |
| P2-4 | Optional `spend_hook` / `user_data` on send (advanced) | todo | Power-user / contract sends |

---

## P3 — Network & chat

| ID | Task | Status | iOS parity |
|----|------|--------|------------|
| P3-1 | Chat `replay_mode` UI | todo | DarkIRC settings |
| P3-2 | Optional remote darkfid presets + trust warning | todo | Custom fullnode URL |
| P3-3 | Package `darkfid_exec` (like `darkirc_exec`) | done | `syncDarkfidArtifacts` in `darkfi-android-sdk/build.gradle.kts` |
| P3-4 | Chat E2E + offline queue hardening | todo | DM reliability |
| P3-5 | DarkIRC 1:1 DM UI (pubkey copy → contact → thread) | done | See [`dm-implementation-plan.md`](dm-implementation-plan.md) |

---

## P4 — Quality & documentation

| ID | Task | Status | iOS parity |
|----|------|--------|------------|
| P4-1 | Instrumented test: send with memo on testnet | in_progress | `DarkfiPaymentMemoInstrumentedTest` (normalization); E2E send still manual |
| P4-2 | Refresh `darkfi-integration.md` parity matrix | todo | Shared architecture doc |
| P4-3 | Add `docs/darkfi-mainnet-android.md` operator guide | done | Mainnet runbook |
| P4-4 | CI job: `build-darkfi-mobile-ffi-android.sh` | in_progress | Documented in mainnet guide; workflow optional |
| P4-5 | Maintain [`app-features.md`](app-features.md) | done | iOS feature checklist |

---

## Suggested execution order (one-by-one)

1. **P0-1 → P0-2** — Native build + UniFFI regen (unblocks memo on device).
2. **P4-3** — Mainnet operator doc (low code, high ops value).
3. **P1-5 → P1-6** — Real sync + history amounts (wallet credibility).
4. **P1-1 → P1-2** — Multi-token (DarkFi differentiator vs Zcash single-asset UX).
5. **P1-3 → P1-4** — Tx details + recipients.
6. **P3-3** — Embedded darkfid binary (optional node parity with desktop).
7. **P2-*** — Contract-specific features as product prioritizes.
8. **P4-1, P4-4** — CI and instrumented coverage.

---

## Native build commands (local)

```bash
export ANDROID_NDK_HOME=/path/to/ndk   # e.g. ~/Library/Android/sdk/ndk/29.0.14206865
./scripts/build-darkfi-mobile-ffi-android.sh
./gradlew :app:assembleDarkfimainnetDebug
```

Optional UniFFI regen after a host `cargo build --release -p darkfi-mobile-ffi`:

```bash
cd rust
cargo run --bin uniffi-bindgen generate target/release/libdarkfi_mobile_ffi.dylib \
  --language kotlin --crate darkfi_mobile_ffi --metadata-no-deps \
  --out-dir ../darkfi-android-sdk/src/main/java --no-format
```

---

## Related documents

- [`app-features.md`](app-features.md) — Full feature matrix for iOS port
- [`pending-features.md`](pending-features.md) — Short backlog
- [`drk-native-implementation.md`](drk-native-implementation.md) — UniFFI phases
- [`wallet-roadmap.md`](wallet-roadmap.md) — Embedded `drk` direction
