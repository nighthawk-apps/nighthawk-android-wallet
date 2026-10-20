# F-Droid (DarkFi testnet edition)

## Package identity

| | Legacy Zcash listing | DarkFi **testnet** edition (this MR) |
|--|----------------------|--------------------------------------|
| Application id | `com.nighthawkapps.wallet.android` | **`com.nighthawkwallet.android.testnet`** |
| Gradle flavor | `zcashmainnet` | **`darkfitestnet`** |
| Version props | `ZCASH_VERSION_*` | **`WALLET_VERSION_*`** |
| F-Droid metadata | existing `fdroiddata` recipe | **`metadata/com.nighthawkwallet.android.testnet.yml`** |
| Default LWD | (Zcash) | Local `127.0.0.1:9067`. Studio ngrok is an optional Change-server preset only. |

This is a **testnet** listing for alpha / Studio ngrok lightwalletd — not mainnet.
A future mainnet F-Droid app would use `com.nighthawkwallet.android` / `darkfimainnet`.

## Local unsigned APK (same task F-Droid runs)

```bash
git submodule update --init --recursive
./scripts/vendor-darkfi.sh
./scripts/build-darkfi-mobile-ffi-android.sh
bundle exec fastlane fdroid
# → assembleDarkfitestnetRelease (unsigned)
```

F-Droid clones this repo with `submodules: true` (DarkFi + RandomX). Bump a
submodule, commit the gitlink, and tag a new `WALLET_VERSION_*` — no fdroiddata
srclib SHA edit.

## AutoUpdate after merge

1. Bump `WALLET_VERSION_NAME` / `WALLET_VERSION_CODE` in `gradle.properties`.
2. Add Fastlane changelog `fastlane/metadata/android/en-US/changelogs/<WALLET_VERSION_CODE>.txt` (F-Droid What's New).
3. Push a git tag (e.g. `v3.00.04`) on the commit F-Droid should build (`nighthawk-dark` or `main` once DarkFi is default).
4. F-Droid `UpdateCheckMode: Tags` + `UpdateCheckData` on `WALLET_VERSION_*` picks up the tag.

Tagging alone does **not** run this repo’s GitHub `Deploy` workflow (that is branch/`workflow_dispatch` + Play secrets). F-Droid’s scanner is separate.

## 3.00.019 (this release)

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.019` |
| `WALLET_VERSION_CODE` | `30001819` |
| Suggested git tag | `v3.00.019` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001819.txt` |

What's new for testers:

- Paper is the theme on a light system setting. Stealth remains the theme on a dark system setting. An explicit choice in Advanced still wins.
- Paper colors and type now apply across the wallet, not only Settings and Chat.

## 3.00.018

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.018` |
| `WALLET_VERSION_CODE` | `30001818` |
| Suggested git tag | `v3.00.018` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001818.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |
| DarkFi pin | `e4e624179305952c95af1360100d379a81272586` (`nighthawk-testnet`, Arti 0.45) |

What's new for testers:

- Darkirc and the rest of the DarkFi pin now include current dark.fi master. Tor stays on Arti 0.45.
- The 3.00.017 F-Droid review fixes remain: Android wallet file lock, license-only copy, screenshot blocking, and a local lightwalletd default.

## 3.00.017

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.017` |
| `WALLET_VERSION_CODE` | `30001817` |
| Suggested git tag | `v3.00.017` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001817.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |
| DarkFi pin | `94b140902e4b67ba141b35b63b602b819f0b0c6e` (`nighthawk-testnet`) |

What's new for testers:

- Wallet open no longer fails on Android when Rust's `File::try_lock` is unsupported (F-Droid's Rust 1.91). The vendored fjall falls back to `flock`.
- Welcome and about copy follow the MIT license only.
- Seed, restore, and backup screens set `FLAG_SECURE`.
- A fresh install syncs to local lightwalletd. The Studio ngrok host is optional because that hostname changes.
- The store listing describes optional Bluetooth and nearby Wi-Fi mesh permissions.

## 3.00.016

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.016` |
| `WALLET_VERSION_CODE` | `30001816` |
| Suggested git tag | `v3.00.016` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001816.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |
| DarkFi pin | `94b140902e4b67ba141b35b63b602b819f0b0c6e` (`nighthawk-testnet`) |

What's new for testers:

- Strict UniFOMR is the default. Turn it off in settings to trial-decrypt payments that have no clue.
- PIR limb fetches are rounded up so the matched payload size is not visible from the request count.
- Gradle 9.8.1 and Android Gradle Plugin 9.4.1. kvdb-overlay is pinned because its latest git HEAD no longer builds with this DarkFi tree.

## 3.00.015

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.015` |
| `WALLET_VERSION_CODE` | `30001815` |
| Suggested git tag | `v3.00.015` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001815.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |
| DarkFi pin | `f0978c22242267b5684e6b8334cdf063652d2b37` (`nighthawk-testnet`) |

What's new for testers:

- Fable 5.1 follow-ups: DarkFi mnemonic seed-prefix validation, tx direction from net atomic, chat `isOutgoing` from FFI (Android UI no longer nick-spoofs).
- Payment memo/recipient meta encrypted at rest; broadcast failures clear pending sent markers.

## 3.00.014

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.014` |
| `WALLET_VERSION_CODE` | `30001814` |
| Suggested git tag | `v3.00.014` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001814.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |
| DarkFi pin | `f0978c22242267b5684e6b8334cdf063652d2b37` (`nighthawk-testnet`) |

What's new for testers:

- DarkFi pin includes public `Drk::scan_block`, `scan_blocks` without `progress_pub`, and darkfid sync hardening.
- Native FFI 0.2.1 (tower 0.5). Lightwalletd proto comments match tip-only checkpoints.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001814` automatically.

## 3.00.013

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.013` |
| `WALLET_VERSION_CODE` | `30001813` |
| Suggested git tag | `v3.00.013` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001813.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |

What's new for testers:

- Send, Receive, and Request live on the Wallet tab (Transfer hub removed).
- Fourth tab is DEX (coming soon). DAO Hub stays in Settings; Top-up moved there.
- Tab bar hides on send/receive/request. Send stays enabled unless sync failed.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001813` automatically.

## 3.00.012

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.012` |
| `WALLET_VERSION_CODE` | `30001812` |
| Suggested git tag | `v3.00.012` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001812.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |

What's new for testers:

- Transaction details open the real DarkFi explorer (`/tx/{id}` for testnet or mainnet).
- In-process Arti SOCKS for chat and wallet (Guardian tor-android removed).
- Gated `fud://` offers: opt-in, Tor or mesh only, never auto-download.
- Rust panic fence at chat/mesh/Arti FFI so a daemon crash cannot abort the wallet.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001812` automatically.

## 3.00.010

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.010` |
| `WALLET_VERSION_CODE` | `30001810` |
| Suggested git tag | `v3.00.010` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001810.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |

What's new for testers:

- Encrypted nearby DarkIRC EventGraph hop over BLE (Noise per neighbor; share-internet off).
- Chat remains in-process UniFFI; DMs must be saltbox.
- Native `arm64-v8a` FFI includes mesh neighbor C ABI.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001810` automatically.

## 3.00.009

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.009` |
| `WALLET_VERSION_CODE` | `30001809` |
| Suggested git tag | `v3.00.009` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001809.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |

What's new for testers:

- Native wallet open waits for `DarkfiWalletHandle` before wipe/retry (Fjall lock).
- Chat HUD/readability; swipe-to-reveal DRK on home.
- In-process UniFFI DarkIRC; optional encrypted BLE EventGraph hop (share-internet off).
- Package ABIs: arm64-v8a, armeabi-v7a, x86, x86_64 (drop JNA armeabi/mips).
- Explorer: https://explorer.testnet.dark.fi

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001809` automatically.

## 3.00.008

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.008` |
| `WALLET_VERSION_CODE` | `30001808` |
| Suggested git tag | `v3.00.008` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001808.txt` |

What's new for testers:

- Recover native Fjall `Locked` on wallet open: wait for the live `DarkfiWalletHandle` (and in-flight FFI) before wipe/retry.
- Chat HUD outbound peer slots + nick/link/encrypted readability; no fake Disconnected after turso wipe.
- Wallet home swipe-to-reveal DRK (extra tokens as later pager pages); no pinned zero DRK Tokens row.
- Instant restore from lightwalletd `GetCheckpointSnapshot` (blake3 integrity; birthday-safe).
- Scan ranges never trial-decrypt below wallet birthday.
- Proto lockstep: client and lightwalletd speak `proto_version` **1.x.x**.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001808` automatically.

## Upstream MR

Recipe lives in [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) as
`metadata/com.nighthawkwallet.android.testnet.yml` (fork: `nighthawk24/fdroiddata`).
