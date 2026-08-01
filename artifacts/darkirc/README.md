# Bundled DarkIRC daemon (`darkirc_exec`)

Place one or more **`darkirc_exec`** binaries here so Gradle packs them into the app (merged into `darkfi-android-sdk` assets as `darkirc/<abi>/darkirc_exec`).

```
artifacts/darkirc/arm64-v8a/darkirc_exec
artifacts/darkirc/x86_64/darkirc_exec     # optional, emulators
artifacts/darkirc/armeabi-v7a/darkirc_exec
```

## How to populate

Cross-compile from DarkFi locally (see **`scripts/build-darkirc-android.sh`**) — it installs into **`artifacts/darkirc/`**.

The script vendors pinned DarkFi (`scripts/vendor-darkfi.sh`), stages **`artifacts/sqlcipher/<abi>/`** static libs into upstream `bin/darkirc/sqlcipher/` (upstream Android linker expects `libsqlite3.a`), and builds **arm64-v8a** + **x86_64** (emulator). Run **`scripts/build-sqlcipher-android.sh`** first if SQLCipher artifacts are missing.

CI should run that script (or unzip a secured build artifact here) **before** `./gradlew :app:assemble*`.

Shipping these files in APKs invokes **DarkFi AGPL-3.0** obligations; see **`docs/darkirc-embedded-android.md`**.
