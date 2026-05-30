# darkfi-mobile-ffi

Rust `cdylib` that exposes a small UniFFI surface for the Android SDK (`namespace com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`).

Product direction for expanding this crate—embedded **`drk`**-equivalent wallet logic vs thinner APIs—is **`docs/wallet-roadmap.md`**.

## Build the native library

From the **`rust/`** workspace root:

```bash
cargo build --package darkfi-mobile-ffi --release
```

Cross-compile for Android ABIs with [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) (installed separately), for example:

```bash
cargo ndk -t arm64-v8a -t armeabi-v7a build --package darkfi-mobile-ffi --release
```

Copy the resulting `.so` files into [`darkfi-android-sdk/src/main/jniLibs`](../../darkfi-android-sdk/src/main/jniLibs) per ABI (see that folder’s README). Those artifacts are **Git-ignored** at the repository root—each clone must build or fetch them; only Kotlin/Rust **sources** are versioned.

## Regenerate Kotlin bindings

Install the UniFFI CLI (matching this crate’s `uniffi` dependency, **0.31.x**):

```bash
cargo install uniffi --version 0.31.1 --features cli --locked
```

From the **`rust/`** workspace root (where the `uniffi-bindgen` binary is configured in Cargo):

```bash
cargo run --bin uniffi-bindgen generate target/release/libdarkfi_mobile_ffi.dylib \
  --language kotlin \
  --crate darkfi_mobile_ffi \
  --metadata-no-deps \
  --out-dir ../darkfi-android-sdk/src/main/java \
  --no-format
```

*(Use `.so` instead of `.dylib` on Linux, and `.dll` on Windows)*

Note: We use `cargo run --bin uniffi-bindgen` with `--metadata-no-deps` to avoid Cargo metadata conflicts with upstream dependencies. `--no-format` avoids invoking `ktlint` when it is not on `PATH`.

After regeneration, sanity-check hand-edits: UniFFI 0.31.1 can occasionally fuse a brace with the following top-level declarations; the last block of `darkfi_mobile_ffi.kt` should end the `FfiConverterTypeDarkfiWalletNativeError` object with `}` **before** the generated `bridgePing` / `bridgeVersion` functions.

## Kotlin façade

Prefer calling through [`DarkfiMobileFfiApi`](../../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/uniffi/DarkfiMobileFfiApi.kt) from app code instead of importing generated symbols directly.
