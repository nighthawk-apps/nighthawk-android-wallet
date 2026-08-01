# Packaging `darkfid` in the Nighthawk Android app

DarkFi’s full node daemon **`darkfid`** is **AGPL-3.0**. Shipping its binary in the APK obligates you to comply with the license (source/corresponding offer, etc.). Coordinate with legal review before distributing.

## Upstream: what runs in-process vs external

Upstream **`bin/app`** (desktop and Android APK from DarkFi) bundles **wallet logic** and **chat P2P** differently:

| Subsystem | Upstream `bin/app` | Separate daemon? |
|-----------|-------------------|------------------|
| **Wallet (`drk`)** | In-process Rust plugin (`plugin/drk.rs`) | **Yes — external `darkfid`** on JSON-RPC loopback |
| **Chat (`darkirc`)** | In-process Rust plugin (`plugin/darkirc.rs`, P2P + EventGraph) | **No** — no standalone `darkirc` process |
| **`darkfid` binary** | Not in `Cargo.toml`, not spawned by app code | User runs `./darkfid` before wallet sync works |

The wallet plugin hardcodes testnet RPC at **`tcp://127.0.0.1:18345`** and retries every 20 seconds until `darkfid` answers (`subscribe_blocks`, `scan_blocks`). See vendored `third_party/darkfi/bin/app/src/plugin/drk.rs`.

Upstream Android only adds a **foreground service** (`ForegroundService.java`) with the text “Running p2p network…” — it keeps the **in-process chat/P2P** stack alive. It does **not** start or embed `darkfid`.

So when people say the upstream app “connects directly to the daemon,” they usually mean one of:

1. **Chat** — P2P runs inside the app (no `darkirc_exec`).
2. **Wallet** — `drk` runs inside the app but talks to **`darkfid` over loopback JSON-RPC**, same as desktop. On Android that still implies something listening on `127.0.0.1:18345` unless you point RPC elsewhere.

There is **no** upstream `android.Dockerfile` or Makefile target for `darkfid` (unlike `bin/darkirc`).

## Nighthawk approach: bundled `darkfid_exec` subprocess

Nighthawk keeps upstream’s **split architecture** for the wallet:

```
┌─────────────────────────────────────────────────────────────┐
│  Nighthawk APK                                               │
│  ┌─────────────────────┐    JSON-RPC (loopback or SOCKS)    │
│  │ drk (UniFFI in-proc)│ ───────────────────────────────►   │
│  └─────────────────────┘         tcp://127.0.0.1:18345      │
│  ┌─────────────────────┐                                    │
│  │ darkfid_exec (child)│ ◄── full node: P2P, chain DB, RPC  │
│  └─────────────────────┘                                    │
└─────────────────────────────────────────────────────────────┘
```

This matches upstream semantics (wallet client + full node RPC) while removing the manual “run darkfid on your PC” step on mobile.

### Implemented pieces

| Piece | Role |
|-------|------|
| [`DarkfidEmbeddedRunner`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/darkfid/DarkfidEmbeddedRunner.kt) | Copy `assets/darkfid/<abi>/darkfid_exec` → `filesDir`, spawn with generated TOML |
| [`DarkfidEmbeddedConfigGenerator`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/darkfid/DarkfidEmbeddedConfigGenerator.kt) | Testnet/mainnet seeds, sandboxed `chain_db` / `p2p`, clearnet or Tor P2P profile |
| [`DarkfidDaemonService`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/darkfid/DarkfidDaemonService.kt) | Foreground service (Android background limits) |
| [`DarkfidConnectionProbe`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/darkfid/DarkfidConnectionProbe.kt) | TCP + JSON-RPC `ping` before wallet sync |
| [`DarkfiDaemonLifecycleCoordinator`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/daemon/DarkfiDaemonLifecycleCoordinator.kt) | Start Tor → darkfid → darkirc → wallet probe |
| [`DarkfiDaemonPreferences`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/daemon/DarkfiDaemonPreferences.kt) | `runEmbeddedDarkfid` (default **true**) |
| Gradle **`syncDarkfidArtifacts`** | Merges `artifacts/darkfid/**/darkfid_exec` into APK assets |

Rust wallet sync (`rust/darkfi-mobile-ffi`) uses the same endpoint URL as Kotlin (`DarkfiEndpoint`, default testnet `18345`).

## Artifact layout

Production builds expect:

```
artifacts/darkfid/arm64-v8a/darkfid_exec
artifacts/darkfid/x86_64/darkfid_exec     # optional, emulators
artifacts/darkfid/armeabi-v7a/darkfid_exec
```

Git tracks `artifacts/darkfid/README.md` and `.gitkeep` per ABI; **`darkfid_exec` is gitignored**. CI or local release builds must produce binaries **before** `assemble`.

## Building `darkfid_exec`

Prerequisites:

- Vendored DarkFi tree (`./scripts/vendor-darkfi.sh` → `third_party/darkfi`)
- Rust toolchain, [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk)
- Android NDK (`ANDROID_NDK_HOME`)

From repo root:

```bash
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/$(ls "$HOME/Library/Android/sdk/ndk" | tail -1)"
./scripts/build-darkfid-android.sh
./gradlew :app:assembleDarkfitestnetDebug
```

The build script runs `cargo ndk -t arm64-v8a -o artifacts/darkfid/arm64-v8a build --release -p darkfid` and renames the output to **`darkfid_exec`**.

Expect a **long** compile and a **large** binary (validator, `sled` DB, P2P, Monero-related deps in `bin/darkfid/Cargo.toml`). First device sync can take minutes and significant storage under `filesDir/darkfid/`.

### Verification

1. Install APK on arm64 device/emulator (build the matching ABI).
2. Open app — foreground notification when embedded darkfid is enabled and binary present.
3. Wallet home should move from **Disconnected** → syncing once JSON-RPC responds on `127.0.0.1:18345`.
4. Logcat: `Embedded darkfid started`, then `darkfid:` stdout lines from the child process.

### Without bundled binary

If `darkfid_exec` is missing, embedded mode no-ops and the wallet still expects RPC at the configured endpoint. Options:

- Run `darkfid` on a host and **`adb reverse tcp:18345 tcp:18345`**
- Emulator: point endpoint at host via `10.0.2.2:18345` (see [`alpha-testnet-connection.md`](alpha-testnet-connection.md))
- Disable embedded pref and use a remote RPC URL (Tor SOCKS rewrite via `TorDarkfidEndpoint` when Tor is on)

## Alternative: in-process `darkfid` (not implemented)

`bin/darkfid` exposes a library crate (`Darkfid` in `lib.rs`) used by its `main.rs`. In theory Nighthawk could link that into `darkfi-mobile-ffi` and run the validator inside the JNI process — closer to how upstream embeds chat.

Trade-offs:

- **Pros:** One process, no subprocess lifecycle, no duplicate memory for Rust runtimes
- **Cons:** Very large FFI surface, long cold start, harder Android signal/ threading story, AGPL coupling across the whole `.so`, no upstream Android maintenance for this path

The subprocess model mirrors the **documented desktop workflow** (run `darkfid`, open app) with minimal divergence from upstream `DrkPlugin`.

## Operational notes

- **Battery / data:** A full node on mobile is heavy; expose `runEmbeddedDarkfid` in Settings when UI is wired.
- **Tor:** When app Tor is on, embedded config uses SOCKS P2P profile; wallet JSON-RPC to loopback darkfid stays local; remote RPC endpoints can use `socks5://127.0.0.1:9050/...`.
- **CI:** Run `./scripts/build-darkfid-android.sh` (or cache artifacts) before `assemble` in release workflows — same pattern as [`artifacts/darkirc/README.md`](../artifacts/darkirc/README.md).
- **Size:** Budget APK growth similar to or larger than embedded darkirc; consider ABI splits or Play feature delivery if size becomes blocking.

## Related docs

- [`alpha-testnet-connection.md`](alpha-testnet-connection.md) — endpoints, adb reverse, emulator host
- [`upstream/alpha-testnet-endpoints.md`](upstream/alpha-testnet-endpoints.md) — seeds and ports from vendored config
- [`drk-native-implementation.md`](drk-native-implementation.md) — UniFFI wallet phase status
- [`darkirc-embedded-android.md`](darkirc-embedded-android.md) — parallel packaging for chat daemon
