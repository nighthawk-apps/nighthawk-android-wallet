# Bundled UniFFI native library (`libdarkfi_mobile_ffi.so`)

Place one `.so` per ABI so Gradle can merge them into the APK (via **`syncMobileFfiArtifacts`**):

```
artifacts/mobile-ffi/arm64-v8a/libdarkfi_mobile_ffi.so
artifacts/mobile-ffi/x86_64/libdarkfi_mobile_ffi.so   # emulators
```

## How to populate

```bash
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build-darkfi-mobile-ffi-android.sh
```

That script runs **`cargo-ndk`** from **`rust/`** and copies outputs here **and** into **`darkfi-android-sdk/src/main/jniLibs/`**.

Regenerate Kotlin after UDL edits: **`rust/darkfi-mobile-ffi/README.md`**.

These binaries are **Git-ignored** (see root `.gitignore`).
