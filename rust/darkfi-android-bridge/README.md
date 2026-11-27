# darkfi-android-bridge

Rust library historically used as a thin DarkFi façade for Android.

## Current state

- Static **`darkfi_bridge_ping()`** only — validates basic `cdylib` builds.
- **UniFFI** for product code lives in **[`blackhawk`](../../third_party/blackhawk/README.md)** (`libblackhawk`, generated Kotlin package `com.nighthawkapps.lib.uniffi.blackhawk`, façade `DarkfiMobileFfiApi` in `darkfi-android-sdk`). Prefer that crate when extending the native API. Groups, token mint, the stable clue-key version, and the Money-tree scan cursor are all on that crate. This bridge was not part of the 2026-11-03 native rebuild.

## Building for Android ABIs (manual)

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
# Set `$ANDROID_NDK_HOME` and use cargo-ndk or `cargo build --target aarch64-linux-android --release`
```

Copy **`target/<triple>/release/libdarkfi_android_bridge.so`** → **`darkfi-android-sdk/src/main/jniLibs/<abi>/`** only if you still ship this artifact; the UniFFI path expects **`libblackhawk.so`** instead (see [jniLibs README](../../darkfi-android-sdk/src/main/jniLibs/README.md)).

## Linking DarkFi

Clone [darkrenaissance/darkfi](https://git.dark.fi/darkrenaissance/darkfi) and add path/workspace dependencies incrementally—ideally behind **`blackhawk`** so Kotlin bindings stay on one UDL surface.
