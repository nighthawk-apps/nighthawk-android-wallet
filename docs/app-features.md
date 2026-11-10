# Nighthawk DarkFi — application feature catalog

Canonical list of **Android** capabilities for the DarkFi wallet APK (`com.nighthawkwallet.android` / `.testnet`). Use this document to track iOS parity.

**Legend**

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented and usable (may need native `.so` or testnet node) |
| 🟡 | Partial / stub / UI-only / requires external daemon |
| ❌ | Not implemented |

**Build flavors:** `darkfimainnet` · `darkfitestnet`  
**Light client path:** wallets → **`darkfi-lightwalletd` gRPC `:9067`** → `darkfid`. Default sync is padded trial decryption. Experimental UnifOMR is off unless Advanced settings turn it on.  
**Network privacy:** Nym mixnet by default. Tor is in-process Arti. Direct uses the device network.  
**Chat:** DarkIRC on **both** platforms is in-process UniFFI (`start_darkirc`). Optional Android `darkirc_exec` is a legacy IRC subprocess. Nearby **Nighthawk Mesh** is an encrypted EventGraph hop over BLE (share-internet off).

---

## 1. Onboarding & wallet core

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Create new wallet (BIP39-style mnemonic) | ✅ | DarkFi uses **22-word** upstream mnemonic via UniFFI `generateDarkfiMnemonic` |
| Restore from seed phrase | ✅ | Import path in onboarding |
| Wallet encrypted at rest | ✅ | Encrypted prefs + turso/aegis256 `wallet.db` (native `drk`) |
| PIN / app lock | ✅ | PIN setup and gate |
| Backup reminder / seed backup flow | ✅ | Required after create/restore; then educational carousel, then Home |
| Educational onboarding carousel | ✅ | After Tor connecting + create/restore + seed backup |
| Birthday height (faster restore) | 🟡 | `birthdayHeight` in persist model; FFI bootstrap supports it |
| Multiple accounts in one app | ❌ | Single `PersistableDarkfiWallet` today |
| View / copy receive address | ✅ | `drk` deposit addresses; QR receive flow |
| Single wallet address | ✅ | One receive address; Request money builds a `drk:` invoice |
| Address formats (`drk…`) | ✅ | Confidential / public receive encodings |

---

## 2. Balance & sync

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Confirmed balance (DRK) | ✅ | `confirmedBalanceAtomic` via native `drk` when `.so` loaded |
| Transparent vs shielded split | | **N/A** — DarkFi transfers are private; show single balance |
| Fiat conversion display | 🟡 | Fiat currency setting exists; rate source project-specific |
| Sync progress (% / blocks) | ✅ | `syncSnapshot()` from the sync engine. A stale served tip says lightwalletd has not reached darkfid recently |
| Pull-to-refresh / rescan | ✅ | Settings → rescan; `refreshNow()` |
| Embedded `darkfid` fullnode | 🟡 | Foreground service when `darkfid_exec` bundled; prefs toggle |
| Remote `darkfid` JSON-RPC | ✅ | Loopback, emulator `10.0.2.2`, custom host:port |
| Endpoint presets / change server | ✅ | Mainnet/testnet ports; **network/port guard** (P0-6) |
| Tor for wallet RPC (SOCKS) | ✅ | Settings → Tor network; in-process Arti or external SOCKS |
| Keep screen on while syncing | ✅ | Manual test doc / setting pattern from Nighthawk |

---

## 3. Send & receive

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Send DRK (native build + broadcast) | ✅ | `NativeDarkfiSynchronizer` + `drk.transfer` |
| Send without native lib | 🟡 | Stub synchronizer; transfer disabled with message |
| Fee estimate before send | ✅ | `estimateTransferFee` |
| Send confirmation dialog | ✅ | Amount, recipient, fee, memo |
| Payment memo | ✅ | Up to 255 UTF-8 bytes. Experimental UnifOMR puts it in `omr_metadata_enc`. The default send leaves `MoneyNote.memo` empty |
| Memo on transaction details | ✅ | Decrypt incoming / stored outgoing (P0) |
| QR scan recipient / amount | ✅ | SCAN route; deep link `drk:…?amount=&memo=` |
| Receive QR display | ✅ | Receive money + QR codes screens |
| Request specific amount (payment URI) | ✅ | Request money QR uses this wallet’s single `drk:` address |
| Multi-token / custom assets send | ✅ | Send screen token picker. DRK is the default token |

---

## 4. Transaction history & details

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Transaction list | 🟡 | Native `list_transactions` when `.so` present; else empty |
| Transaction details screen | ✅ | Fee, height, status, memo row |
| Recipient address on tx | 🟡 | `getRecipients` empty (P1-4) |
| Mined / pending status | 🟡 | Status string from `drk` history |
| Export tx / block explorer link | ✅ | `DarkfiExplorer` — `/tx/{id}` only (testnet or mainnet) |
| Rich contract call breakdown | ✅ | `contractSummary` on transaction details |

---

## 5. DarkIRC chat (P2P)

| Feature | Android | Notes for iOS |
|---------|---------|---------------|
| Public `#` channels | ✅ | UniFFI `start_darkirc` + `DarkircEventCallback` (same as iOS) |
| In-process EventGraph | ✅ | `libdarkfi_mobile_ffi.so` |
| Optional `darkirc_exec` | 🟡 | Legacy IRC subprocess; not the default message path |
| Tor for chat / P2P | ✅ | In-process Arti SOCKS for native daemon |
| Connection status | ✅ | `darkirc_status()` → direct, Tor, or Nym |
| Chat settings (DAG, fast mode) | ✅ | Settings → Chat |
| E2E encrypted DMs | ✅ | ChaCha UniFFI; daemon refuses plaintext DMs |
| DM key generation | ✅ | `generate_dm_keypair` UniFFI (not CLI) |
| Nighthawk Mesh (BLE EventGraph hop) | ✅ | Encrypted DAG only; share-internet **off** — [nighthawk-mesh.md](nighthawk-mesh.md) |
| `replay_mode` preference | 🟡 | Pref exists; no UI (P3-1) |

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
| DAO / contract admin UI | 🟡 | Read-only DAO hub tab. Signing stays off the hub |

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
| Memo field (512 bytes) | Payment memo, max **255 UTF-8 bytes**. Default send leaves the on-chain note memo empty |
| Lightwalletd | `darkfi-lightwalletd` gRPC **:9067**. Default sync is trial decryption. UnifOMR is an `fhe-omr` server build and an opt-in client setting |
| Embedded Tor | In-process Arti. The app default network is Nym, not Tor |
| `drk:` payment URI | `drk:address?amount=&memo=` with memo as standard base64 |
| Orchard / Sapling | Money contract `TransferV1` |
| — | DarkIRC chat, in-process on Android and iOS |
| — | DAO hub (read-only) and custom-token send |

---

## 10. Verification checklist (iOS port)

When implementing each iOS screen, tick against this list:

1. Same **network** semantics (mainnet vs testnet build/config).
2. Same **endpoint ports** and mismatch guard.
3. **Memo** send + tx detail parity.
4. **Tor** toggle behavior (wallet HTTP + IRC).
5. **Chat** in-process UniFFI on both platforms; optional Android `darkirc_exec` is legacy.
6. **Nighthawk Mesh** is EventGraph-only (no share-internet).
7. **Native library** load failure messaging (no silent wrong balances).
8. Document any intentional **omission** (e.g. no transparent pool).

---

## Maintenance

- Update this file when a feature moves from 🟡 → ✅ or new screens ship.
- Link PRs to task IDs in [`implementation-plan.md`](implementation-plan.md).
- Android-specific build steps: root [`README.md`](../README.md).
