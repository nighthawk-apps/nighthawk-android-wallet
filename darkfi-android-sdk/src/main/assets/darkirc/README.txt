# Embedded DarkIRC binary (optional)

Prebuilt **`darkirc_exec`** files are bundled from the **repo-root** tree (not here):

```
artifacts/darkirc/<abi>/darkirc_exec
```

Gradle **`syncDarkircArtifacts`** (runs before `preBuild`) copies them into
`generated-darkirc-bundle-assets/darkirc/...`, which is merged into the APK.

Put ABI folders under **`artifacts/darkirc/`** (same names: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
Filenames **must be** `darkirc_exec` (`noCompress` in `darkfi-android-sdk/build.gradle.kts`).

Build with **`scripts/build-darkirc-android.sh`** — see **`docs/darkirc-embedded-android.md`**.

Static **`README`** here rides along in assets; binaries come from **`artifacts/`** at build time.
