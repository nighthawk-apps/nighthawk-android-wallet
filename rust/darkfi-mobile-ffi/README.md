# darkfi-mobile-ffi

Rust `cdylib` that exposes a UniFFI surface for the Android SDK (`namespace com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`) and iOS (`DarkfiMobileFfi` module).

Product direction for expanding this crate—embedded **`drk`**-equivalent wallet logic vs thinner APIs—is **`docs/wallet-roadmap.md`**.

## Sync Architecture — UnifOMR

The crate implements **UnifOMR** (Oblivious Message Retrieval with near-optimal concrete efficiency) for private block scanning. The sync engine uses a tiered approach:

### Detection Flow

```
┌─────────────────────────────────────────────────────────────┐
│  1. UnifOMR (primary) — BFV FHE query via GetOmrDigest RPC │
│     Server evaluates homomorphic tag comparison, returns    │
│     encrypted digest. Client decrypts → matching heights.   │
│                                                             │
│  2. Pool Digest — Pre-registered keys via RegisterKeyPool   │
│     No detection key sent at query time.                    │
│     Tor-preferred for registration.                         │
│                                                             │
│  3. Trial Decrypt (fallback) — Download all compact blocks  │
│     and trial-decrypt each note with wallet secret keys.    │
│     Slower, less private. Used when OMR unavailable.        │
└─────────────────────────────────────────────────────────────┘
```

### Key Delivery — Detection Key vs OMR Clue

**IMPORTANT**: The BFV detection key (~37 KB) is NEVER sent via the encrypted notes field.

| Data | Where | Size | When |
|------|-------|------|------|
| **OMR clue** (blake3 tag) | `OmrClueRegistration.omr_clue` | 8 bytes | Transaction broadcast |
| **BFV detection key** | `OmrDigestRequest.detection_key` | ~37 KB | Sync-time query |
| **Pool keys** | `RegisterKeyPoolRequest.keys` | ~37 KB/epoch | Pre-registered via Tor |

### Cross-Wallet Compatibility

When the same seed is used on a non-OMR wallet (e.g. `drk` CLI), transactions from that wallet won't have UnifOMR clues. The sync engine detects this via empty OMR digests and falls back to trial decryption, surfacing `SyncFallbackReason::MissingOmrClues` to the UI.

**Recommendation**: Use Nighthawk or Moonshine for all DarkFi transactions to maintain the most private and fastest sync.

### SyncFallbackReason Enum

Exposed to Kotlin/Swift via UniFFI:

- `None` — UnifOMR working normally
- `ServerOmrUnsupported` — Server lacks OMR capabilities
- `OmrDetectionFailed` — Detection errors, auto-retry scheduled
- `MissingOmrClues` — Non-OMR wallet transactions detected
- `KeyPoolExpired` — Pool refresh in progress
- `KeyPoolNotRegistered` — Registration pending
- `Unknown` — Transient/unknown issue

### Chain Integrity (Security Audit July 2026)

- **Reorg recovery** — `SyncEngine::rewind_to_height()` rolls back scan cursor; `sync.rs` deletes post-reorg coins, un-spends rolled-back spends, purges block cache via `MobileBlockCache::prune_above()`
- **Tip regression** — `update_chain_tip_hash()` detects `new_tip < prev_tip` as a reorg signal
- **Server switch reset** — `reset_for_server_switch()` clears tip hash, OMR counters, and catch-up detection atomics
- **Inter-match gap scanning** — Trial-decrypt gaps >100 blocks between consecutive OMR matches (leading, inter-match, trailing)
- **Zero-match threshold** — Lowered from 50 to 10 blocks for faster cross-wallet detection
- **OMR downgrade tracking** — `omr_downgrade_warning` + `omr_downgrade_count` in `LightSyncState` for UI surfacing
- **Windowed failure decay** — `record_omr_success()` halves failure count instead of zeroing to prevent adversarial gaming

## Environment

- **Rust** stable via [rustup](https://rustup.rs/)
- **Vendored DarkFi** at `third_party/darkfi` (run `./scripts/vendor-darkfi.sh` from the Android repo root before building)
- **Android cross-build**: [cargo-ndk](https://github.com/bbqsrc/cargo-ndk) + `ANDROID_NDK_HOME` — see the root [README](../../README.md#build-the-project)
- **UniFFI 0.31.x** — provided by the crate; bindgen runs via `cargo run --bin uniffi-bindgen` from the `rust/` workspace

## Build the native library (host)

From the **`rust/`** workspace root:

```bash
cargo build --package darkfi-mobile-ffi --release
```

Cross-compile for Android ABIs with **cargo-ndk** (or use the repo script):

```bash
export ANDROID_NDK_HOME=/path/to/ndk
../scripts/build-darkfi-mobile-ffi-android.sh
```

The script cross-compiles `libdarkfi_mobile_ffi.so` into `darkfi-android-sdk/src/main/jniLibs/<abi>/` and regenerates Kotlin bindings. Those `.so` files are **git-ignored** — each clone must build locally.

## Regenerate Kotlin bindings

**Preferred:** re-run `./scripts/build-darkfi-mobile-ffi-android.sh` (builds native libs + bindgen).

**Manual** (from the `rust/` workspace, after a host `cargo build -p darkfi-mobile-ffi`):

```bash
cargo run --bin uniffi-bindgen generate \
  darkfi-mobile-ffi/src/darkfi_mobile_ffi.udl \
  --language kotlin \
  --crate darkfi_mobile_ffi \
  --metadata-no-deps \
  --out-dir ../darkfi-android-sdk/src/main/java \
  --no-format
```

Generating from the **UDL** (not an old `.dylib`) ensures new types like `SyncMethod` and `SyncFallbackReason` appear in Kotlin even when the host library is stale.

`--no-format` avoids invoking `ktlint` when it is not on `PATH`.

After regeneration, sanity-check hand-edits: UniFFI 0.31.1 can occasionally fuse a brace with the following top-level declarations; the last block of `darkfi_mobile_ffi.kt` should end the `FfiConverterTypeDarkfiWalletNativeError` object with `}` **before** the generated `bridgePing` / `bridgeVersion` functions.

## Kotlin facade

Prefer calling through [`DarkfiMobileFfiApi`](../../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/uniffi/DarkfiMobileFfiApi.kt) from app code instead of importing generated symbols directly.

## Dependencies

```toml
fhe = "0.1.1"        # BFV lattice-based FHE (UnifOMR detection keys)
fhe-traits = "0.1.1"  # FHE trait interface
blake3 = "1.x"        # Detection tag derivation
rand09 = "0.9"        # Fresh randomness for BFV ciphertexts
```
