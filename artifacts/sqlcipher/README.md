# SQLCipher for Android NDK (legacy)

**Not required** for tip UniFFI / `drk` / `darkirc` builds. Upstream wallet storage is
turso + experimental aegis256; tip `darkirc` uses sled-overlay only.

This tree and `scripts/build-sqlcipher-android.sh` remain only for experimental or
out-of-tree work that still links `rusqlite` with the `sqlcipher` feature (e.g.
historical darkirc Android notes in upstream README).

Moonshine’s CLI wallet keeps its own SQLCipher dependency in the `moonshine`
repo and does not use these artifacts.
