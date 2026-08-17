# Nighthawk DarkFi — application feature catalog

Canonical list of **Android** capabilities for the DarkFi wallet APK (`com.nighthawkwallet.android` / `.testnet`). Use this document when porting the **iOS** app from Zcash to DarkFi so both clients reach parity deliberately.

**Legend**

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented and usable (may need native `.so` or testnet node) |
| 🟡 | Partial / stub / UI-only / requires external daemon |
| ❌ | Not implemented |
| 🔒 | Zcash had it; DarkFi equivalent differs (see notes) |

**Build flavors:** `darkfimainnet` · `darkfitestnet`  
**Light client path:** wallets → **`darkfi-lightwalletd` gRPC `:9067`** (UnifOMR) → `darkfid` (not direct darkfid for sync/scan).  
**Chat:** DarkIRC available on both Android (embedded `darkirc_exec`) and iOS (in-process UniFFI).

---

## 1. Onboarding & wallet core

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Create new wallet (BIP39-style mnemonic) | ✅ | DarkFi uses **22-word** upstream mnemonic via UniFFI `generateDarkfiMnemonic` |
| Restore from seed phrase | ✅ | Import path in onboarding |
| Wallet encrypted at rest | ✅ | Encrypted prefs + turso/aegis256 `wallet.db` (native `drk`) |
| PIN / app lock | ✅ | PIN setup and gate |
| Backup reminder / seed backup flow | ✅ | Settings → backup wallet |
| Birthday height (faster restore) | 🟡 | `birthdayHeight` in persist model; FFI bootstrap supports it |
| Multiple accounts in one app | ❌ | Single `PersistableDarkfiWallet` today |
| View / copy receive address | ✅ | `drk` deposit addresses; QR receive flow |
| Generate new address | ✅ | `generateNewAddress()` when native lib present |
| Address formats (`drk…`) | ✅ | Confidential / public receive encodings |

---

## 2. Balance & sync

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Confirmed balance (DRK) | ✅ | `confirmedBalanceAtomic` via native `drk` when `.so` loaded |
| Transparent vs shielded split | 🔒 | **N/A** — DarkFi transfers are private; show single balance |
| Fiat conversion display | 🟡 | Fiat currency setting exists; rate source project-specific |
| Sync progress (% / blocks) | 🟡 | UI hooks exist; stub shows placeholder until scan wired (P1-5) |
| Pull-to-refresh / rescan | ✅ | Settings → rescan; `refreshNow()` |
| Embedded `darkfid` fullnode | 🟡 | Foreground service when `darkfid_exec` bundled; prefs toggle |
| Remote `darkfid` JSON-RPC | ✅ | Loopback, emulator `10.0.2.2`, custom host:port |
| Endpoint presets / change server | ✅ | Mainnet/testnet ports; **network/port guard** (P0-6) |
| Tor for wallet RPC (SOCKS) | ✅ | Settings → Tor network; tor-android or external SOCKS |
| Keep screen on while syncing | ✅ | Manual test doc / setting pattern from Nighthawk |

---

## 3. Send & receive

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Send DRK (native build + broadcast) | ✅ | `NativeDarkfiSynchronizer` + `drk.transfer` |
| Send without native lib | 🟡 | Stub synchronizer; transfer disabled with message |
| Fee estimate before send | ✅ | `estimateTransferFee` |
| Send confirmation dialog | ✅ | Amount, recipient, fee, memo |
| Payment memo (private, UnifOMR metadata) | ✅ | Up to 255 UTF-8 bytes (`u8` length in OMR envelope; FFI `MAX_PAYMENT_MEMO_BYTES`) |
| Memo on transaction details | ✅ | Decrypt incoming / stored outgoing (P0) |
| QR scan recipient / amount | ✅ | SCAN route; deep link `drk:…?amount=&memo=` |
| Receive QR display | ✅ | Receive money + QR codes screens |
| Request specific amount (payment URI) | 🟡 | Deep link support; full “request” UX varies |
| Multi-token / custom assets send | ❌ | DRK default only (P1-1, P1-2) |
| ZIP-321 / unified address | 🔒 | Use DarkFi `drk` addresses instead |
| Shielding / deshielding | 🔒 | **N/A** on DarkFi |

---

## 4. Transaction history & details

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Transaction list | 🟡 | Native `list_transactions` when `.so` present; else empty |
| Transaction details screen | ✅ | Fee, height, status, memo row |
| Recipient address on tx | 🟡 | `getRecipients` empty (P1-4) |
| Mined / pending status | 🟡 | Status string from `drk` history |
| Export tx / block explorer link | 🟡 | Explorer integration project-specific |
| Rich contract call breakdown | ❌ | DAO / Deploy labels (P1-3) |

---

## 5. DarkIRC chat (P2P)

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Public IRC channels | ✅ | Kotlin IRC client + channel presets |
| Embedded `darkirc_exec` | ✅ | arm64-v8a + x86_64 in APK assets |
| Tor for chat / P2P | ✅ | Shared Tor settings; SOCKS for non-loopback IRC |
| Connection status (node + IRC) | ✅ | Green/yellow/red indicators on chat |
| Chat settings (embedded toggle, DAG, fast mode) | ✅ | Restart embedded node |
| E2E encrypted DMs | 🟡 | ChaCha via UniFFI + chat crypto settings |
| `replay_mode` preference | 🟡 | Pref exists; no UI (P3-1) |
| Full upstream P2P in Kotlin | ❌ | By design — Rust daemon owns Event Graph |

---

## 6. Settings & security

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Settings hub | ✅ | Bottom nav |
| Tor network (embedded vs external SOCKS) | ✅ | Dedicated screen with sections |
| Change server (darkfid endpoint) | ✅ | With network/port validation |
| Security (PIN) | ✅ | |
| Fiat currency | ✅ | |
| Backup wallet | ✅ | |
| About / version | ✅ | |
| Crash reporting | 🟡 | See `docs/testing/manual_testing/Crash Reporting.md` |
| Remote config / feature flags | 🟡 | Architecture doc; keys project-specific |
| Wipe / reset wallet data | ✅ | Rescan wipe path in settings |

---

## 7. Network & daemons (operator)

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Mainnet product flavor | ✅ | `darkfimainnet` |
| Testnet side-by-side install | ✅ | `.testnet` applicationId |
| Upstream-aligned RPC ports | ✅ | 8345 / 18345 |
| Embedded darkfid P2P seeds (lilith) | ✅ | Clearnet + Tor SOCKS seeds per network |
| Mainnet confirmation threshold 11 | ✅ | Embedded TOML (P0-4) |
| Stratum / mining UI | ❌ | Desktop `darkfid` + xmrig |
| DAO / contract admin UI | ❌ | P2 |

---

## 8. Native / FFI stack

| Component | Android | iOS equivalent |
|-----------|---------|----------------|
| `libdarkfi_mobile_ffi.so` | ✅ (build per ABI) | `darkfi_mobile_ffi` XCFramework or static lib |
| UniFFI `DarkfiWalletHandle` | ✅ | Same UDL → Swift |
| `StubDarkfiSynchronizer` fallback | ✅ | Graceful degrade without native lib |
| Tip `drk` (turso + aegis256) | ✅ | Same Rust crate / pin |
| Payment memo FFI | ✅ | `payment_memo` on transfer + `transaction_payment_memo` |

---

## 9. Zcash → DarkFi mapping (quick reference)

| Zcash (Nighthawk legacy) | DarkFi (this app) |
|--------------------------|-------------------|
| ZEC transparent + shielded pools | Single private DRK balance |
| Unified address / uview | `drk` deposit address |
| Memo field (512 bytes) | UnifOMR user memo, max **255 UTF-8 bytes** (off-chain `omr_metadata_enc`; on-chain `MoneyNote.memo` is unbounded `Vec<u8>` and left empty by `drk.transfer`) |
| Lightwalletd | `darkfi-lightwalletd` gRPC **:9067** (UnifOMR) → `darkfid` |
| Tor via librustzcash | tor-android SOCKS + optional daemon Tor |
| ZIP-321 | Payment URI `drk:address?amount=&memo=` |
| Orchard / Sapling | Money contract `TransferV1` |
| — | DarkIRC chat (new) |
| — | DAO / custom tokens (chain; app UI pending) |

---

## 10. Verification checklist (iOS port)

When implementing each iOS screen, tick against this list:

1. Same **network** semantics (mainnet vs testnet build/config).
2. Same **endpoint ports** and mismatch guard.
3. **Memo** send + tx detail parity.
4. **Tor** toggle behavior (wallet HTTP + IRC).
5. **Chat** optional embedded daemon vs external IRC.
6. **Native library** load failure messaging (no silent wrong balances).
7. Document any intentional **omission** (e.g. no transparent pool).

---

## Maintenance

- Update this file when a feature moves from 🟡 → ✅ or new screens ship.
- Link PRs to task IDs in [`implementation-plan.md`](implementation-plan.md).
- Android-specific build steps: root [`README.md`](../README.md).
