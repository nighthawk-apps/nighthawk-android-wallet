# Bundled DarkIRC daemon (`darkirc_exec`)

Place one or more **`darkirc_exec`** binaries here so Gradle packs them into the app (merged into `darkfi-android-sdk` assets as `darkirc/<abi>/darkirc_exec`).

```
artifacts/darkirc/arm64-v8a/darkirc_exec
artifacts/darkirc/x86_64/darkirc_exec     # optional, emulators
artifacts/darkirc/armeabi-v7a/darkirc_exec
```

## How to populate

```bash
./scripts/vendor-darkfi.sh
./scripts/build-darkirc-android.sh
```

Tip DarkFi `darkirc` uses sled-overlay (no SQLCipher staging). The script builds
**arm64-v8a** + **x86_64** into this directory.

CI should run that script (or unzip a secured build artifact here) **before** `./gradlew :app:assemble*`.

Shipping these files in APKs invokes **DarkFi AGPL-3.0** obligations; see **`docs/darkirc-embedded-android.md`**.
