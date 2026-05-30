# Packaging `darkirc` in the Android app

DarkIRC (`bin/darkirc` in the DarkFi repo) is **AGPL-3.0**. Shipping its binary in the APK obligates you to comply with the license (source/corresponding offer, etc.). Coordinate with your legal review before distributing.

## What was implemented

- **Foreground service** ([`DarkircDaemonService`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/chat/darkirc/DarkircDaemonService.kt)) starts the packaged executable and shows a low-priority notification (Android foreground rules).
- **[`DarkircEmbeddedRunner`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/chat/darkirc/DarkircEmbeddedRunner.kt)** copies `assets/darkirc/<abi>/darkirc_exec` into app-private storage, marks it executable, and spawns it with `--config` pointing at a generated `darkirc_config.toml` under `filesDir/darkirc/`.
- **Generated config** uses clearnet **`tcp+tls`** lilith seeds ([`DarkfiChatDefaults`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/chat/DarkfiChatDefaults.kt)), loopback IRC `tcp://127.0.0.1:6667`, and sandboxed datastore paths — aligned with upstream defaults but without relying on `~/.config` on device.
- **Preferences**: `DarkfiChatPreferences.runEmbeddedDarkirc` (default **true**). User toggle: **Chat → settings → Run embedded DarkIRC node**.
- **Bootstrap**: [`DarkircDaemonBootstrap.maybeStart`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/chat/darkirc/DarkircDaemonBootstrap.kt) runs from [`NighthawkWalletApplication`](../app/src/main/java/com/nighthawkwallet/android/NighthawkWalletApplication.kt) when the pref is on **and** a binary exists for the device ABI.

### Where binaries live (artifact layout)

Production builds expect **`artifacts/darkirc/<abi>/darkirc_exec`** at the repository root.

The **`darkfi-android-sdk`** module runs **`syncDarkircArtifacts`**, wired through the Variant API (`addGeneratedSourceDirectory`) into **`generated-darkirc-bundle-assets/darkirc/`**, then merges them into the library AAR/APK alongside `darkirc/README.txt` from **`src/main/assets`**.

Git tracks **`artifacts/darkirc/README.md`** and **`.gitkeep`** ABI dirs; **`darkirc_exec`** files are **`gitignored`** — CI should build or download them **before** `assemble`.

Binaries are **not** committed by default; omitting them keeps CI green but chat’s embedded daemon will not start until you supply artifacts.

## Building the binary

Prerequisites: Rust, [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk), Android NDK, and a clone of **darkfi** (same tree you use for `make darkirc` on desktop).

From the wallet repo root:

```bash
export DARKFI_SRC="$HOME/src/darkfi"   # your path
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/$(ls "$HOME/Library/Android/sdk/ndk" | head -1)"
./scripts/build-darkirc-android.sh
```

The script should place `darkirc_exec` under each ABI folder you build for. Then rebuild the app.

### Verification

1. Install APK on device/emulator whose ABI you built.
2. Open the app — you should see the foreground notification when embedded DarkIRC is enabled.
3. Chat settings: IRC `127.0.0.1`, port `6667`, **Retry** — connection should succeed after P2P/DAG sync (first launch may take minutes).

## Limitations / follow-ups

- **Battery & data**: a full darkirc node is heavy; users can disable the toggle.
- **DAG sync / seeds**: if lilith endpoints are unreachable, the daemon logs errors; see [DarkFi network troubleshooting](https://dark.fi/book/misc/network-troubleshooting.html).
- **Tor profile inside darkirc**: the generated config uses **clearnet `tcp+tls`** seeds for simpler bootstrap. To mirror upstream `active_profiles = ["tor"]`, extend the generator to set `[net] tor_socks5_proxy` to the app’s loopback SOCKS and switch profiles (ensure embedded Tor is up before `darkirc` connects — ordering is a follow-up).
