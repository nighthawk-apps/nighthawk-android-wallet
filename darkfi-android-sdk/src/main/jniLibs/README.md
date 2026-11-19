Place compiled **`libdarkfi_mobile_ffi.so`** (UniFFI `cdylib` from [`rust/darkfi-mobile-ffi`](../../../../rust/darkfi-mobile-ffi/README.md)) per supported 64-bit ABI:

- `arm64-v8a/`
- `x86_64/`

Build with `./scripts/build-darkfi-mobile-ffi-android.sh` (which targets `arm64-v8a` and `x86_64` with NDK `30.0.16248370`), or directly from **`rust/`**:

```bash
cargo ndk -t arm64-v8a -t x86_64 build --release -p darkfi-mobile-ffi
```

These **`.so` files are not checked into Git** (see root `.gitignore`). After building, copy them here (commands in [`rust/darkfi-mobile-ffi/README.md`](../../../../rust/darkfi-mobile-ffi/README.md) and the repo **`README`** “Build the project”), or obtain them from CI artifacts.

> Nighthawk Android targets 64-bit ABIs only (`arm64-v8a`, `x86_64`) for Google Play 64-bit / 16 KB page alignment and F-Droid APK size compliance; 32-bit `armeabi-v7a` and `x86` are no longer built.

Until **`libdarkfi_mobile_ffi.so`** is present locally for each emulator/device ABI you use, UniFFI calls fail at runtime (`UnsatisfiedLinkError` / `DarkfiNativeProbe.MissingLibrary`); the app continues to use Kotlin stubs for chain/sync UX.

Optional legacy artifact: **`libdarkfi_android_bridge.so`** from `rust/darkfi-android-bridge` is not used by the UniFFI stack.
