# Nighthawk Wallet (DarkFi Edition)

Privacy-preserving wallet (work-in-progress) by [Nighthawk Apps](https://nighthawkapps.com). This branch ships as a **new Android application id** running on the DarkFi network with DRK currency. The app aims to integrates a fully working native DarkFi wallet API via **UniFFI** (`rust/darkfi-mobile-ffi` → generated Kotlin + `DarkfiMobileFfiApi`), allowing for complete chain synchronization, transaction broadcasting, and robust native interactions.

## Download (placeholders)

Store listings are **not finalized** for `com.nighthawkwallet.android`. Badge targets below are explicit placeholders until publishing completes:

- **F-Droid:** `https://PLACEHOLDER_FDROID_PACKAGE_URL`
- **Google Play:** `https://PLACEHOLDER_PLAY_STORE_LISTING_URL`

Replace these URLs in documentation when production listings exist—do not invent live links prematurely.

## Build the project

End-to-end build steps for CLI users. Android Studio installation, emulator setup, signing keys, **build variant** UI, and troubleshooting are covered in **[Setup Documentation](docs/Setup.md)**—use that guide for JDK/SDK/`ANDROID_HOME`, devices, and IDE troubleshooting.

### Prerequisites

- **JDK 17 or newer** (Temurin is a common distribution; Gradle may use toolchains — see Setup).
- **Android SDK**: install via Android Studio’s SDK Manager, set **`ANDROID_HOME`**, and add **`platform-tools`** to `PATH` for `adb`.
- **Android NDK**: install an NDK revision side-by-side with the SDK (`$ANDROID_HOME/ndk/<version>`). **`ANDROID_NDK_HOME`** must point at that folder when using **`cargo-ndk`** (Rust cross-build).
- **Rust** ([rustup](https://rustup.rs/)) and **Cargo**.
- **`cargo-ndk`**: `cargo install cargo-ndk`
- **Rust Android std targets** (run once after installing Rust):

  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
  ```

### 1. Build the UniFFI native libraries

This produces **`libdarkfi_mobile_ffi.so`** for each ABI and copies it into **`artifacts/mobile-ffi/`** and **`darkfi-android-sdk/src/main/jniLibs/`** (Gradle **`syncMobileFfiArtifacts`** merges artifacts into the APK).

```bash
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build-darkfi-mobile-ffi-android.sh
```

If **`artifacts/mobile-ffi`** or **`jniLibs`** already contain matching **`.so`** files, skip rebuilding until Rust or the UDL changes.

<details>
<summary>Manual cargo-ndk copy (equivalent to the script)</summary>

```bash
export ANDROID_NDK_HOME
cd rust
cargo ndk \
  -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 \
  build --release -p darkfi-mobile-ffi

JNILIBS=../darkfi-android-sdk/src/main/jniLibs
T=target
mkdir -p "$JNILIBS/arm64-v8a" "$JNILIBS/armeabi-v7a" "$JNILIBS/x86" "$JNILIBS/x86_64"
cp "$T/aarch64-linux-android/release/libdarkfi_mobile_ffi.so"   "$JNILIBS/arm64-v8a/"
cp "$T/armv7-linux-androideabi/release/libdarkfi_mobile_ffi.so" "$JNILIBS/armeabi-v7a/"
cp "$T/i686-linux-android/release/libdarkfi_mobile_ffi.so"       "$JNILIBS/x86/"
cp "$T/x86_64-linux-android/release/libdarkfi_mobile_ffi.so"   "$JNILIBS/x86_64/"
cd ..
```

</details>

Details and **UniFFI Kotlin regeneration** when editing **`darkfi_mobile_ffi.udl`**: [`rust/darkfi-mobile-ffi/README.md`](rust/darkfi-mobile-ffi/README.md).

### 2. Build the Android app

From the repository root:

```bash
# Debug APK — mainnet flavor (production applicationId)
./gradlew :app:assembleDarkfimainnetDebug

# Debug APK — testnet flavor (distinct applicationId `.testnet`, can install alongside mainnet)
./gradlew :app:assembleDarkfitestnetDebug

# Build all conventional outputs (Gradle default; may assemble multiple variants)
./gradlew assembleDebug

# Release APK (requires a configured release keystore; see docs/Setup.md)
# ./gradlew :app:assembleDarkfimainnetRelease
```

Output APKs appear under **`app/build/outputs/apk/`** (path includes flavor and build type). Install with `adb install -r path/to.apk`.

### 3. Optional checks before you ship a change

Quick validation (see **[Verification](#verification-local--ci-parity)** for the full parity table):

```bash
./gradlew :darkfi-android-sdk:testDebugUnitTest
./gradlew :app:assembleDarkfimainnetDebug
```

## Chat (DarkIRC-style)

In-app **Chat** uses a Kotlin IRC client against DarkIRC’s listener (`CAP` / `NICK` / `USER` / `JOIN` / `PRIVMSG`). When enabled, the app can run a **packaged `darkirc_exec`** in a foreground service (loopback `127.0.0.1:6667`) — see [DarkIRC embedded Android](docs/darkirc-embedded-android.md). That is **not** upstream’s in-process **`DarkIrc` plugin** (Event Graph + P2P inside the same Rust binary); full P2P/DAG/ChaCha parity remains follow-up — [DarkFi integration](docs/darkfi-integration.md), [DarkIRC upstream](docs/darkfi-chat-upstream.md).

| Capability | Behavior |
|------------|----------|
| Default endpoint | `127.0.0.1:6667` (pair with workstation `darkirc` + `adb reverse tcp:6667 tcp:6667`) |
| Tor toggle | Persists “prefer Tor” for seed hints and routes **non-loopback** IRC through **SOCKS5** at `127.0.0.1:9050` (default bundled tor-android listener) |
| SOCKS readiness | Embedded Tor starts in-process when enabled; the client waits up to **90s** for the SOCKS port to accept TCP, probing **9050** / **9150** as needed |
| Loopback IRC | SOCKS is **skipped** when the IRC host is loopback so USB reverse + desktop `darkirc` works |
| Auto-reconnect | On `ON_RESUME`, reconnects only when **not** already connected (direct or via Tor) |
| Chat settings | **Settings → Chat** exposes identity phrase/public ID, IRC endpoint, Tor/SOCKS, reconnect |
| Identity & IRC PASS | Stored with **EncryptedSharedPreferences** (legacy plaintext prefs migrated on upgrade); KeyStore failures fall back to app-private prefs and log `DarkfiChat` |
| Cleartext traffic | Allowed app-wide so plain TCP IRC / loopback SOCKS work—HTTPS endpoints still use TLS |

### Build and test (chat-related)

From the repo root:

```bash
# Compile debug APK (example flavor)
./gradlew :app:assembleDarkfitestnetDebug

# Unit tests for IRC parser, Tor routing, SOCKS probe, and minimal DarkIRC handshake (JVM; handshake uses Robolectric for `android.util.Log`)
./gradlew :darkfi-android-sdk:testDebugUnitTest
```

Manual smoke tests are described in [DarkIRC chat on Android](docs/android-darkirc-chat.md) (desktop `darkirc`, `adb reverse`, and Tor + non-loopback IRC).

## Verification (local / CI parity)

Commands mirror [.github/workflows/pull-request.yml](.github/workflows/pull-request.yml) where tooling resolves:

| Step | Command |
|------|---------|
| JVM libraries | `./gradlew :configuration-api-lib:check :preference-api-lib:check :spackle-lib:check` |
| Wallet SDK unit tests | `./gradlew :darkfi-android-sdk:testDebugUnitTest` |
| Repo JVM test aggregation | `./gradlew test` (modules without tests report `NO-SOURCE`) |
| DarkFi mainnet debug APK | `./gradlew :app:assembleDarkfimainnetDebug` |
| Android Lint (release model) | `ORG_GRADLE_PROJECT_IS_MINIFY_ENABLED=false ./gradlew :app:lintDarkfimainnetRelease` |

Notes:

- `./gradlew checkProperties` validates signing-related Gradle props—expects CI/release defaults when that task is enabled.
- `./gradlew ktlint` resolves **`com.pinterest.ktlint:ktlint-cli:<version>:all`** (see **`KTLINT_VERSION`** in `gradle.properties`; fat jar for the CLI). Maven Central is used, with `repo.maven.apache.org` duplicated as a narrow mirror for the `com.pinterest.ktlint` group.
- `./gradlew detektAll` runs in CI; a repo-wide clean pass requires clearing the current Detekt weighted-issue backlog.

## Architecture (current)

- **UI:** `ui-lib` Compose flows for onboarding, backup, restore, send (mock), receive, settings, chat diagnostics.
- **Themes:** Advanced settings expose three variants—**Stealth (default)**, **Light**, and **Midnight** (`AppThemeVariant`, persisted as `APP_THEME_VARIANT`; legacy dark toggle migrates on read).
- **Settings hub:** Main settings surface includes optional **identity**—IRC-style nickname, chat **public ID** (copy/share), and gated **secret phrase** reveal for chat (`SettingsHubIdentitySection`); distinct from the wallet recovery seed.
- **Wallet SDK:** `darkfi-android-sdk` provides `PersistableDarkfiWallet`, BIP-39 loading/validation (`DarkfiMnemonic`, 24-word default), `DarkfiWalletCoordinator`, and `DarkfiSynchronizer` (real-time balance and transaction synchronization).
- **Balance model:** A single **confirmed / spendable** DRK tally flows through `DarkfiSynchronizer.confirmedBalanceAtomic` → `WalletSnapshot`—no transparent vs shielded split (aligned with DarkFi’s private ledger model).
- **RPC presets:** “Change server” maps to **`DarkfiEndpoint`** (`tcp://` URL style; ports match upstream `bin/drk/drk_config.toml` darkfid defaults—mainnet **8345**, testnet **18345**; legacy **26660/26670** in stored JSON auto-migrate), persisted via `EncryptedPreferenceKeys.DARKFI_ENDPOINT_PRESET` alongside wallet JSON.
- **Native DarkFi:** **UniFFI + JNA** bindings live under `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`; runtime calls load **`libdarkfi_mobile_ffi.so`** from **`darkfi-android-sdk/src/main/jniLibs/<abi>/`**. Rebuild and recopy whenever **`rust/darkfi-mobile-ffi`** changes—see **[Build the project](#build-the-project)**. See also **UniFFI & native bridge** below and [DarkFi integration](docs/darkfi-integration.md).

## UniFFI & native bridge

| Piece | Role today |
|-------|------------|
| **`rust/darkfi-mobile-ffi`** | Real UniFFI `cdylib` (`libdarkfi_mobile_ffi`; UDL `src/darkfi_mobile_ffi.udl`). Builds with `cargo` from **`rust/`** workspace ([crate README](rust/darkfi-mobile-ffi/README.md)). Fully linked to the upstream DarkFi workspace, providing complete wallet API bindings via FFI. |
| **Generated Kotlin** | `darkfi-android-sdk/.../uniffi/darkfi_mobile_ffi/darkfi_mobile_ffi.kt`, package `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`, regenerated with **`uniffi-bindgen`** (commands in crate README). |
| **`DarkfiMobileFfiApi`** | Stable Kotlin façade (`com.nighthawkapps.lib.android.sdk.uniffi`) so app/UI code avoids importing generated symbols directly. |
| **`rust/darkfi-android-bridge`** | Older minimal `cdylib`/`darkfi_android_bridge`; kept for incremental experiments—**prefer `darkfi-mobile-ffi`** for UniFFI ([crate README](rust/darkfi-android-bridge/README.md)). |
| **`darkfi-android-sdk` JNA dependency** | UniFFI 0.31 Kotlin bindings use JNA (`net.java.dev.jna:jna`). |
| **`darkfi-android-sdk/src/main/jniLibs/`** | **`libdarkfi_mobile_ffi.so`** per ABI (see [jniLibs README](darkfi-android-sdk/src/main/jniLibs/README.md)); Gradle does **not** invoke Cargo automatically—use **`cargo-ndk`** ([Build the project](#build-the-project)). |

**Production mapping:** Mirrors DarkFi wallet RPC / `bin/drk` semantics—single balance field, scan/progress, broadcast. The **`DarkfiSynchronizer`** is fully implemented with native Rust-backed clients. `./gradlew :darkfi-android-sdk:testDebugUnitTest` covers Kotlin helpers and chain correctness via the native path.

## Additional setup (Android Studio)

JDK/SDK install order, device debugging, IDE **build variants**, and Gradle troubleshooting: [Setup Documentation](docs/Setup.md).

## Reporting an issue

Security disclosures: [GitHub Security Advisories](https://github.com/nighthawk-apps/nighthawk-android-wallet/security) or maintainer support channels.

Technical issues / features: file a GitHub issue on this repository.

General support: [DM @NighthawkWallet on X](https://x.com/nighthawkwallet).

## Contributing

Read [Contributing Guidelines](docs/CONTRIBUTING.md).

Translations: [Crowdin](https://crowdin.com/project/nighthawk-wallet/) [![Crowdin](https://badges.crowdin.net/nighthawk-wallet/localized.svg)](https://crowdin.com/project/nighthawk-wallet)

## Known issues

1. Intel-based machines may need `WALLET_IS_DEPENDENCY_LOCKING_ENABLED=false` in `~/.gradle/gradle.properties` if Gradle locking flakes appear during IDE sync.
2. Gradle may warn about mixed AGP detection for composite builds—safe to ignore when versions match under `build-conventions-*`.
3. Enabling `IS_ANDROID_INSTRUMENTATION_TEST_COVERAGE_ENABLED` prevents interactive debugging of the debug APK (CI-only flag).
4. Compose + Jacoco coverage interaction remains limited upstream.

## Install notes

This tree does **not** migrate data from older forks or package IDs—every install is treated as a new DarkFi-oriented wallet; restore from your backed-up seed phrase if needed.

## Shortcuts

`ui-lib/src/main/res/ui/common/xml/shortcuts.xml` hard-codes `android:targetPackage="com.nighthawkwallet.android"` because shortcuts XML does not honor `${applicationId}` substitution. Flavor-specific variants require codegen or Gradle template expansion—until then, avoid shipping mismatched testnet shortcuts silently.

## Disclosure policy

Do not disclose vulnerabilities publicly before coordinated disclosure with maintainers. Encrypt mail where possible.

## Disclaimers

- Funding/on-ramp and third-party exchange shortcuts were removed; acquire DRK through channels you trust.
- Chat connects to **DarkIRC-style IRC over TCP** (Kotlin client); **Arti/Tor inside the daemon** is not in this APK until JNI links DarkFi—loopback **SOCKS** (bundled tor-android or your configured proxy) covers the Android IRC hop when Tor is on and the IRC host is non-loopback.
- Fiat hints depend on public APIs (`COIN_GECKO_ASSET_ID`) and may be unavailable.

## Contact Nighthawk Apps

Email: `nighthawkwallet@protonmail.com` (see disclosure section for PGP expectations).
