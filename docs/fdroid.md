# F-Droid (DarkFi testnet edition)

## Package identity

| | Legacy Zcash listing | DarkFi **testnet** edition (this MR) |
|--|----------------------|--------------------------------------|
| Application id | `com.nighthawkapps.wallet.android` | **`com.nighthawkwallet.android.testnet`** |
| Gradle flavor | `zcashmainnet` | **`darkfitestnet`** |
| Version props | `ZCASH_VERSION_*` | **`WALLET_VERSION_*`** |
| F-Droid metadata | existing `fdroiddata` recipe | **`metadata/com.nighthawkwallet.android.testnet.yml`** |
| Default LWD | (Zcash) | Studio testnet ngrok TLS (`DarkfiEndpoint.defaultForNetwork`) |

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

## 3.00.007 (this release)

| Field | Value |
|-------|--------|
| `WALLET_VERSION_NAME` | `3.00.007` |
| `WALLET_VERSION_CODE` | `30001807` |
| Suggested git tag | `v3.00.007` |
| Fastlane changelog | `fastlane/metadata/android/en-US/changelogs/30001807.txt` |
| Local F-Droid APK | `bundle exec fastlane fdroid` → `assembleDarkfitestnetRelease` (unsigned) |

What's new for testers:

- UnifOMR send/receive plus trial-decrypt fallback on Nighthawk (Moonshine stay UnifOMR-strict unless `--force-trial` / `--allow-trial`).
- Android NDK pin **26.1.10909125**; `cargo-ndk` searched in repo `.cargo-home` and `~/.cargo`.
- Payment memo parsing in release FFI; coordinator stub test waits on real `Dispatchers.IO`.
- Instant restore from lightwalletd `GetCheckpointSnapshot` (blake3 integrity; birthday-safe).
- Scan ranges never trial-decrypt below wallet birthday.
- Proto lockstep: client and lightwalletd speak `proto_version` **1.x.x**.

After tagging, update the fdroiddata recipe only if the checkout branch / submodule SHAs changed. `UpdateCheckData` on `gradle.properties` should pick up `30001807` automatically.

## Upstream MR

Recipe lives in [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) as
`metadata/com.nighthawkwallet.android.testnet.yml` (fork: `nighthawk24/fdroiddata`).
