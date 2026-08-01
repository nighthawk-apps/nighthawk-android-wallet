# Bundled DarkFi full node (`darkfid_exec`)

Place one or more **`darkfid_exec`** binaries here so Gradle packs them into the app (merged into `darkfi-android-sdk` assets as `darkfid/<abi>/darkfid_exec`).

```
artifacts/darkfid/arm64-v8a/darkfid_exec
artifacts/darkfid/x86_64/darkfid_exec     # optional, emulators
artifacts/darkfid/armeabi-v7a/darkfid_exec
```

## How to populate

Cross-compile from vendored DarkFi (see **`scripts/build-darkfid-android.sh`**) — it installs into **`artifacts/darkfid/`**.

CI should run that script (or unzip a secured build artifact here) **before** `./gradlew :app:assemble*`.

When packaged, Nighthawk starts **darkfid** as a foreground service and the wallet **`drk`** layer connects to loopback JSON-RPC — same as upstream **`bin/app/src/plugin/drk.rs`**.

Shipping these files in APKs invokes **DarkFi AGPL-3.0** obligations; see **`docs/darkfid-embedded-android.md`**.

Gradle task **`syncDarkfidArtifacts`** copies these into APK assets as `darkfid/<abi>/darkfid_exec`.
