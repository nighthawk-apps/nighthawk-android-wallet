# SQLCipher for Android NDK

`drk` links **`rusqlite`** with the **`sqlcipher`** feature. Android cross-compiles fail with `unable to find library -lsqlcipher` until per-ABI static libraries are installed here:

```
artifacts/sqlcipher/arm64-v8a/libsqlcipher.a
artifacts/sqlcipher/arm64-v8a/libcrypto.a
artifacts/sqlcipher/armeabi-v7a/libsqlcipher.a
artifacts/sqlcipher/armeabi-v7a/libcrypto.a
…
```

Build both with:

```bash
./scripts/build-sqlcipher-android.sh
```

The script cross-compiles **OpenSSL `libcrypto.a`** per ABI (SQLCipher’s codec backend), then **`ndk-build`** for `libsqlcipher.a` with `-DSQLCIPHER_CRYPTO_OPENSSL`.

`rust/darkfi-mobile-ffi/build.rs` links `sqlcipher` and `crypto` when those files exist.

If a previous run failed with `openssl/crypto.h not found`, remove the stale tree and rebuild:

```bash
rm -rf .build/sqlcipher-android
./scripts/build-sqlcipher-android.sh
```
