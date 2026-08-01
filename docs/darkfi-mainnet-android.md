# DarkFi mainnet on Android — operator guide

Use this checklist when shipping or exercising **mainnet** builds of Nighthawk (DarkFi wallet).

## Build prerequisites

1. **NDK** — Set `ANDROID_NDK_HOME` to your NDK root (e.g. `~/Library/Android/sdk/ndk/29.0.14206865`).
2. **Native FFI** — Rebuild after any `rust/darkfi-mobile-ffi` or UDL change:

   ```bash
   export MOBILE_FFI_ABIS="arm64-v8a x86_64"
   ./scripts/build-darkfi-mobile-ffi-android.sh
   ```

3. **Embedded darkfid** (optional local fullnode):

   ```bash
   ./scripts/build-darkfid-android.sh
   ```

   Gradle task `syncDarkfidArtifacts` copies `artifacts/darkfid/**/darkfid_exec` into APK assets (see `darkfi-android-sdk/build.gradle.kts`).

4. **UniFFI Kotlin** — After UDL changes, regenerate or hand-update `darkfi_mobile_ffi.kt` and verify API checksums match the built `.so` (mismatch → `UniFFI API checksum mismatch` at runtime).

## Flavor and RPC

| Flavor | Typical darkfid RPC | Notes |
|--------|---------------------|--------|
| `darkfimainnet` | Tor SOCKS → loopback or remote mainnet JSON-RPC | `DarkfiEndpointNetworkGuard` blocks saving testnet URL on mainnet flavor |
| `darkfitestnet` | `127.0.0.1:18345` or testnet preset | Embedded TOML uses testnet seeds |

Embedded darkfid mainnet TOML uses **`threshold = 11`** (`DarkfidChainDefaults.confirmationThreshold`) and Tor P2P seed lists (`torOnionP2pSeeds`, `torTlsP2pSeeds`).

## On-device verification

1. Install mainnet debug APK: `./gradlew :app:assembleDarkfimainnetDebug`
2. Restore or create a **22-word** DarkFi wallet; confirm sync progress advances (`processorInfo` / `progress` from `sync_snapshot`).
3. Send a small transfer with an optional **payment memo**; open tx details — memo, fee, net amount, contract type, and recipient (outgoing) should populate when the native library is current.
4. If embedded darkfid is enabled in settings, confirm `DarkfidEmbeddedRunner` starts and RPC probe succeeds.

## CI recommendation

Add a workflow job that runs:

```bash
./scripts/build-darkfi-mobile-ffi-android.sh
./scripts/build-darkfid-android.sh   # optional
./gradlew :darkfi-android-sdk:testDebugUnitTest :ui-lib:compileDebugKotlin
```

Store `artifacts/mobile-ffi` and `artifacts/darkfid` as cached build outputs where possible.

## iOS parity

Mirror the same FFI surface (`list_token_balances`, enriched `DrkTransactionRecord`, `transaction_recipient`, `broadcast_transfer` with recipient storage) — see [`app-features.md`](app-features.md) and [`implementation-plan.md`](implementation-plan.md).
