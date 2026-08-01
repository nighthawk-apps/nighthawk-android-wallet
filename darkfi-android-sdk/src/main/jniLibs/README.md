Place compiled **`libdarkfi_mobile_ffi.so`** (UniFFI `cdylib` from [`rust/darkfi-mobile-ffi`](../../../../rust/darkfi-mobile-ffi/README.md)) per ABI:

- `armeabi-v7a/`
- `arm64-v8a/`
- `x86/`
- `x86_64/`

Build with **`cargo-ndk`** from the **`rust/`** workspace, for example:

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a build -p darkfi-mobile-ffi --release
```

These **`.so` files are not checked into Git** (see root `.gitignore`). After a **`cargo-ndk`** build, copy them here (commands in [`rust/darkfi-mobile-ffi/README.md`](../../../../rust/darkfi-mobile-ffi/README.md) and the repo **`README`** “Build the project”), or obtain them from CI artifacts.

Until **`libdarkfi_mobile_ffi.so`** is present locally for each emulator/device ABI you use, UniFFI calls fail at runtime (`UnsatisfiedLinkError` / `DarkfiNativeProbe.MissingLibrary`); the app continues to use Kotlin stubs for chain/sync UX.

Optional legacy artifact: **`libdarkfi_android_bridge.so`** from `rust/darkfi-android-bridge` is not used by the UniFFI stack.
