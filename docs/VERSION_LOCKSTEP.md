# Nighthawk DarkFi Ecosystem Version Lockstep

Local working-tree alignment across Nighthawk DarkFi clients and services (2026-11-19).

## Component Versions & Pins

| Component | Repository | App / Crate Version | DarkFi Pin | Arti | UniFFI | Network Default | Lightwalletd Default (Testnet) |
|-----------|-----------|---------------------|------------|------|--------|-----------------|--------------------------------|
| **Android** | `nighthawk-android-wallet` | `3.01.14` (`30100814`) | `9d7ceaf77` | `0.48.0` | `0.32.2` | **Nym** (`nym-smolmix` `5f0dba82`) | `https://epidermis-sandbox-marshland.ngrok-free.dev:443` |
| **iOS** | `nighthawk-ios-wallet` | `3.01.14` (`30100815`) | `9d7ceaf77` | `0.48.0` | `0.32.2` | **Nym** (`nym-smolmix` `5f0dba82`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **Desktop** | `nighthawk-desktop` | `3.1.12` | `9d7ceaf77` (via shared FFI) | `0.48.0` | `0.32.2` | **Tor** (`use_tor = true`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **Moonshine CLI** | `moonshine` | `3.1.11` | `9d7ceaf77` | `0.48.0` | n/a | **Tor** (`use_tor = true`) | `https://epidermis-sandbox-marshland.ngrok-free.dev` |
| **darkfi-lightwalletd** | `darkfi-lightwalletd` | `0.3.8` (gRPC proto `2.1.0`) | `9d7ceaf77` | n/a | n/a | RPC to `darkfid` | Serves Mac Studio ngrok endpoint (`:9067` local) |
| **darkfi-mobile-ffi** | `rust/darkfi-mobile-ffi` (canonical on Android; iOS/desktop symlink or path-dep) | `0.2.3` | `9d7ceaf77` | `0.48.0` | `0.32.2` | Nym / Tor / Direct (`DarkircTransport`) | Mempool confirmation via `GetTransaction` before `Broadcasted` |

## Rotated Testnet TLS Pin (Mac Studio)

- **Endpoint**: `https://epidermis-sandbox-marshland.ngrok-free.dev` (`:443` on Android)
- **Leaf TLS SHA-256 Pin**: `c02faee24a8cf271aa91c28b942949ba10ec4413c6d427a598b15a5467978824`

## Ecosystem Alignment Decisions

1. **Network defaults**: Android and iOS default to **Nym** mixnet; Desktop and Moonshine default to **Tor**.
2. **Start screen & navigation**: All apps open on the **Chat** tab. Mobile bottom nav: **Chat → Wallet → DEX → DAO → Settings**.
3. **Android ABIs & NDK**: 64-bit only (`arm64-v8a`, `x86_64`), NDK **`30.0.16248370`**, `cargo-ndk@4.1.2`.
4. **Zero spyware / telemetry**: No Firebase, Crashlytics, or third-party analytics SDKs in Android, iOS, Desktop, or CI.
5. **Send confirmation**: Clients report **Broadcasted** only after verifying the transaction in lightwalletd's mempool via `GetTransaction`.
6. **Addresses**: Wallet/DAO use DarkFi addresses (`drk:` invoices). There is no separate Nighthawk address format.
7. **Trial decryption default**: Experimental UnifOMR is off unless explicitly enabled.
8. **CI gates**: Android `./gradlew checkProperties :darkfi-android-sdk:testDebugUnitTest :ui-lib:testDebugUnitTest`; iOS `stealthTests`; Desktop TypeScript + `cargo test`.
