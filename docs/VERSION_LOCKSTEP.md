# Nighthawk DarkFi Ecosystem Version Lockstep

Local working-tree alignment across Nighthawk DarkFi clients and services (2026-11-13).

## Component Versions & Pins

| Component | Repository | Base Commit | App / Crate Version | DarkFi Pin | Arti | UniFFI | Network Default | Lightwalletd Default (Testnet) |
|-----------|-----------|-------------|---------------------|------------|------|--------|-----------------|--------------------------------|
| **Android** | `new-nighthawk-android-wallet` (`nighthawk-android-wallet`) | `823eb46de` + local hardening | `3.01.12` (`30100812`) | `44ec78b6ef` | `0.48.0` | `0.32.2` | **Nym** (`nym-smolmix`) | `https://epidermis-sandbox-marshland.ngrok-free.dev:443` |
| **iOS** | `nighthawk-ios-wallet` | `effb77b5d` + local alignment | `3.01.13` (`build 17`) | `44ec78b6ef` | `0.48.0` | `0.32.2` | **Nym** (`nym-smolmix`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **Desktop** | `nighthawk-app-desktop` | `6337a38` + local hardening | `3.1.10` (`package.json` / `Cargo.toml` / `tauri.conf.json`) | `44ec78b6ef` (via shared FFI crate) | `0.48.0` | `0.32.2` | **Tor** (`use_tor = true`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **Moonshine CLI** | `moonshine` | `6cd04fd` + local alignment | `3.1.9` | `44ec78b6ef` (`../darkfi-nighthawk-testnet`) | `0.48.0` | n/a | **Tor** (`use_tor = true`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **darkfi-lightwalletd** | `darkfi-lightwalletd` | `d5ea573` + mempool confirm patch | `0.3.6` (gRPC proto `2.1.0`) | `44ec78b6ef` (`../darkfi-nighthawk-testnet`) | n/a | n/a | RPC to `darkfid` | Serves Mac Studio ngrok endpoint (`:9067` local) |
| **darkfi-mobile-ffi** | `rust/darkfi-mobile-ffi` (canonical on Android, synced to iOS) | Shared local crate | `0.2.3` | `44ec78b6ef` | `0.48.0` | `0.32.2` | Nym / Tor / Direct (`DarkircTransport`) | Mempool confirmation via `GetTransaction` before `Broadcasted` |
| **F-Droid Recipe** | `fdroiddata` (`com.nighthawkwallet.android.testnet.yml`) | `c0fa58e602` + local update | Next target `3.01.12` (published `3.00.013` / `30001813`) | `44ec78b6ef` | `0.48.0` | `0.32.2` | **Nym** | Matches upstream tag (`MOBILE_FFI_ABIS="arm64-v8a x86_64"`, NDK `30.0.16248370`) |

## Rotated Testnet TLS Pin (Mac Studio)

- **Endpoint**: `https://epidermis-sandbox-marshland.ngrok-free.dev` (`:443` on Android)
- **Leaf TLS SHA-256 Pin**: `c02faee24a8cf271aa91c28b942949ba10ec4413c6d427a598b15a5467978824`
- **Deployment Note**: Local `darkfi-lightwalletd` source includes `tx.pending` mempool confirmation (`GetTransaction` + `SendTransaction`); the currently running `darkfi-lightwalletd` binary from `d5ea573` should be rebuilt/restarted to serve mempool `GetTransaction` probes on Studio.

## Ecosystem Alignment Decisions

1. **Network defaults**: Android and iOS default to **Nym** mixnet; Desktop defaults to **Tor** (`use_tor = true`).
2. **Start screen & navigation**: All apps open on the **Chat** tab. Mobile bottom nav / tabs: **Chat -> Wallet -> DEX -> DAO -> Settings**.
3. **Android ABIs & NDK**: Google Play (64-bit + 16 KB page alignment) and F-Droid builds both target **64-bit only (`arm64-v8a`, `x86_64`)** using Android NDK **`30.0.16248370`** and `cargo-ndk@4.1.2`.
4. **Zero spyware / telemetry**: No Firebase, Crashlytics, Firebase Test Lab, or third-party analytics SDKs/scripts in Android, iOS, Desktop, or CI.
5. **Send confirmation contract**: Outgoing sends never show success offline. Clients report **Broadcasted** only after verifying the transaction lands in lightwalletd's mempool via `GetTransaction`.
6. **Desktop security boundary**: Desktop auto-unlock on launch (keychain-backed `wallet.dat` + keyring `addrbook` key failing closed without `.addrbook.key` disk fallback) is an accepted desktop UX boundary.
7. **Seed backup & authentication**: Android requires 3-word confirmation alongside checkbox acknowledgment before advancing; iOS supports `drk:` payment URIs (`.onOpenURL`), `Device Passcode` fallback when biometrics are absent, and `strictOmrOnly` documented default `false`.
8. **CI gates**: Android PR CI runs `./gradlew checkProperties :darkfi-android-sdk:testDebugUnitTest :ui-lib:testDebugUnitTest`; iOS pre-publish gate includes `stealthTests`; Desktop CI checks TypeScript, all 5 web tests (`amount.test.ts` + `chat-defaults.test.ts` included), `xmrig` sidecar verification, and `cargo test`.

