# darkfi-android-bridge

Rust library historically used as a thin DarkFi façade for Android.

## Current state

- Static **`darkfi_bridge_ping()`** only — validates basic `cdylib` builds.
- **UniFFI** for product code lives in **[`darkfi-mobile-ffi`](../darkfi-mobile-ffi/README.md)** (`libdarkfi_mobile_ffi`, generated Kotlin package `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`, façade `DarkfiMobileFfiApi` in `darkfi-android-sdk`). Prefer that crate when extending the native API.

## Building for Android ABIs (manual)

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
# Set `$ANDROID_NDK_HOME` and use cargo-ndk or `cargo build --target aarch64-linux-android --release`
```

Copy **`target/<triple>/release/libdarkfi_android_bridge.so`** → **`darkfi-android-sdk/src/main/jniLibs/<abi>/`** only if you still ship this artifact; the UniFFI path expects **`libdarkfi_mobile_ffi.so`** instead (see [jniLibs README](../../darkfi-android-sdk/src/main/jniLibs/README.md)).

## Linking DarkFi

Clone [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi) and add path/workspace dependencies incrementally—ideally behind **`darkfi-mobile-ffi`** so Kotlin bindings stay on one UDL surface.
