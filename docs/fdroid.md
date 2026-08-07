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
./scripts/vendor-darkfi.sh
./scripts/build-darkfi-mobile-ffi-android.sh
bundle exec fastlane fdroid
# → assembleDarkfitestnetRelease (unsigned)
```

## AutoUpdate after merge

1. Bump `WALLET_VERSION_NAME` / `WALLET_VERSION_CODE` in `gradle.properties`.
2. Push a git tag (e.g. `v3.00.00`) on the commit F-Droid should build (`nighthawk-dark` or `main` once DarkFi is default).
3. F-Droid `UpdateCheckMode: Tags` + `UpdateCheckData` on `WALLET_VERSION_*` picks up the tag.

Tagging alone does **not** run this repo’s GitHub `Deploy` workflow (that is branch/`workflow_dispatch` + Play secrets). F-Droid’s scanner is separate.

## Upstream MR

Recipe lives in [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) as
`metadata/com.nighthawkwallet.android.testnet.yml` (fork: `nighthawk24/fdroiddata`).
