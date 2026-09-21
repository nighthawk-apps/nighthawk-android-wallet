# Nighthawk Android (DarkFi) app-layer audit — 3.00.014 / 30001814

Read-only. Paths relative to `/Users/adi/GitHub/new-nighthawk-android-wallet`.

## 1. MUST-FIX before 3.00.014

**1.1 Chat HUD peers dialog leaks peer IPs to DNS and renders raw IPs (uncommitted)**
`darkfi-android-sdk/.../chat/hud/PeerHostDisplay.kt:45-64`, `ui-lib/.../chat/view/ChatNetworkHud.kt:104-107, 112-121, 139`
`dnsName()` calls `reverseLookup` → `InetAddress.getByName(ip).canonicalHostName`, a PTR query through the system resolver over clearnet even when the transport is Tor — exposes the set of P2P peers you're connected to. On miss it returns the literal IP (`?: host`, line 55); the initial `labels` (line 107) and the fallback `slot.displayUrl` (line 139) also render the raw `tcp://IP:port`. `PeerHostDisplayTest.dnsName_fallsBackToIpWhenReverseLookupMisses` enshrines the leak.
Fix: delete `reverseLookup`; for IP literals return an opaque label (`"peer ${slot+1}"` or a truncated hash), never `host`/`displayUrl`; flip the test to assert the IP is not shown.

**1.2 `refreshNow()` runs blocking FFI on the main thread (ANR) and can crash on a destroyed handle**
`DarkfiWalletCoordinator.kt:26,76-80` (`scope` = `Dispatchers.Main.immediate`; `rescanBlockchain` launches on it) → `NativeDarkfiSynchronizer.kt:310-332` (`refreshNow` has no `withContext`; `handle.refreshNow/listTransactions/confirmedBalanceAtomic` each `block_on(drk.read())`, which waits on the background scan's write lock). Callers: `SendViewModel.kt:196`, `WalletViewModel.kt:280-284`, `AppTorCoordinator.kt:216-217`. In the Tor-toggle path `reloadSynchronizer()` (IO) races `rescanBlockchain()` (Main): the old handle is `close()`d while `refreshNow` is mid-flight; `refreshBalanceBestEffort` (340-348) only catches `DarkfiWalletNativeException`, so UniFFI's `IllegalStateException("…already been destroyed")` escapes and kills the process.
Fix: wrap `refreshNow()` body in `withContext(Dispatchers.IO)`; catch `Throwable` in `refresh*BestEffort`; make `reloadSynchronizer` suspend and call `rescan` after it completes.

**1.3 Release native library is unoptimized and unstripped**
`rust/Cargo.toml:10-13` — `[profile.release] opt-level = 0, lto = false, strip = "none"`. Result: `jniLibs/arm64-v8a/libdarkfi_mobile_ffi.so` is 190 MB, `not stripped, with debug_info` (x86_64: 194 MB). ZK proof building for `build_transfer` at `-O0` is minutes-long; `useLegacyPackaging=true` extracts ~400 MB to disk; `DarkfiNativeProbe.run()` (dlopen) executes on the main thread from `AppWalletCoordinator.get()`.
Fix: `opt-level = 3, lto = "thin", strip = "symbols", codegen-units = 1` (keep `panic = "unwind"` for the FFI fence); rebuild all ABIs.

**1.4 Reorg notification never surfaces**
`NativeDarkfiSynchronizer.kt:62-73` sets `_fallbackReason/_fallbackUserMessage/_syncStatusMessage` then immediately calls `applySyncSnapshotBestEffort()` (line 70), which overwrites all three from `lightSyncSnapshot()` (388-395). `_status` is never set to `REORG_DETECTED`, so `DarkfiDaemonStatusMapping.kt:40` → `WalletView.kt:179` banner is unreachable for native reorgs.
Fix: add a dedicated `MutableStateFlow<ReorgEvent?>` (or set `_status = REORG_DETECTED` with a sticky TTL) and don't let the poller clobber it.

**1.5 Default testnet endpoint is a dev ngrok tunnel with a 60-day rotating pin**
`DarkfiEndpoint.kt:39-47` (`epidermis-sandbox-marshland.ngrok-free.dev:443`), `gradle.properties:181` (comment: "refresh when leaf cert rotates (~60d)"), `app/build.gradle.kts:27-36` (release fails closed without a pin; `PREVIOUS_META_DATA_KEY` is never populated by Gradle). Every shipped APK hard-fails sync when the leaf rotates, and F-Droid reproducible builds bake the stale pin.
Fix: point default at a Nighthawk-controlled LWD host with a stable cert (or pin the CA/intermediate), and wire `LIGHTWALLET_TLS_PIN_SHA256_PREVIOUS` into the manifest for rotation overlap.

**1.6 APK declares 32-bit ABIs without a matching FFI library**
`app/build.gradle.kts:50` — `abiFilters` includes `armeabi-v7a`, `x86`; `darkfi-android-sdk/src/main/jniLibs/{armeabi-v7a,x86}/` are empty. JNA's AAR contributes `libjnidispatch.so` for those ABIs, so 32-bit devices install fine, then `DarkfiNativeProbe` → `MissingLibrary` → stub wallet (no sync/send). Also triggers F-Droid `check-apk` "different libs in different ABI" — the exact thing the comment says it avoids. The two present `.so`s are from different dates (arm64 Sep 18, x86_64 Sep 11) → not built from the same source.
Fix: either build all four via `MOBILE_FFI_ABIS` and verify in CI, or restrict `abiFilters` to `arm64-v8a, x86_64`.

## 2. SHOULD-FIX soon

- **Token send uses DRK balance/units** — `AndroidSend.kt:238-239` `evaluateBalance(balanceAtomic /*DRK*/, amountText)` even when `selectedTokenId` ≠ DRK; `:251` labels the fee `"$fee $unit"` in the token's unit though fees are DRK. Fix: check against `tokenBalances[selectedTokenId].balanceAtomic` and always label fee "DRK".
- **Amount entry ignores locale / no numeric keyboard** — `EnterAmount.kt:142-161` has no `KeyboardOptions(keyboardType = Decimal)`; `DarkfiAmountParser.kt:20` `BigDecimal(trimmed)` rejects `1,5`. Fix: set Decimal keyboard, normalize `,`→`.` before parse. (Request screen at `AndroidRequest.kt:139` does set Decimal; `DeepLinkUtil.kt:110` emits un-trimmed raw `amountDisplay`.)
- **Background `SyncWorker` is a no-op** — `SyncWorker.kt:29-43` only `takeWhile(status != DISCONNECTED …)`; `NativeDarkfiSynchronizer.init` ends with `_status = DISCONNECTED` (line 80), so the worker exits on first emission and never calls `refreshNow()`.
- **Wallet addresses logged** — `ReceiveQrCodes.kt:111` `Twig.debug { "WalletAddresses $walletAddresses" }`; `MainActivity.kt:487` logs the endpoint. Testnet release has `LOGCAT_ENABLED=true`. Remove both.
- **Chat daemon auto-starts for every wallet user** — `NavigationMainContent.kt:34-36` calls `connectOrRetry()` on first main content; with the splash "continue without Tor" path this opens clearnet P2P to lilith seeds without the user ever opening Chat. Start lazily on Chat tab or behind an opt-in.
- **SOCKS port field applies per keystroke** — `TorNetworkSettingsScreen.kt:130-141` restarts the network profile 4× while typing `9050`; `prefs.socksPort` coerces `0`→`1`. Apply on focus loss/Done.
- **Recipient never validated client-side** — `DarkfiTransferSupport.kt:11-17` only checks non-blank; `DarkfiSynchronizer.kt:127-132` `validateAddressStub` accepts anything ≥32 chars. Network-byte mismatch (testnet addr on mainnet) is only caught by Rust `InvalidAddress` after fee estimate. Add a base58 + network-prefix check before enabling Continue.
- **`DarkfiEndpointNetworkGuard` validates ports, not networks** — `DarkfiEndpointNetworkGuard.kt:27-39` despite the name; mainnet default `DarkfiEndpoint.kt:49-55` is `tcp://127.0.0.1:9067` (unusable on a phone). Fine for testnet-only release; fix before mainnet flavor ships.
- **Tx detail shows the string "null"** — `TransactionDetails.kt:311` `"${overview.minedHeight}"` for pending txs. `DrkTransactionMapping.kt:6-17` drops `record.status`; Rust `transactions.rs:510` sets `is_sent = status == "Broadcasted"` so a sent tx's direction flips once its status changes — surface `status` to Kotlin and derive direction from it.
- **Restore validation on main thread without guard** — `RestoreViewModel.kt:88-99` calls `validateDarkfiMnemonic` (JNA) inside a flow collected in `viewModelScope`; `UnsatisfiedLinkError` here crashes rather than degrading like `DarkfiNativeProbe`. Wrap in `Dispatchers.IO` + `runCatching`.
- **Recoverable-open wipe is over-broad** — `DarkfiMobileFfiApi.kt:137-153` treats `"connectionfailed"` as grounds to `wipeLocalState()` (drops the scan cache on a transient network error, forcing a full rescan).
- **Address copy isn't time-cleared** — `ReceiveQrCodes.kt:169`, `AndroidRequest.kt:157`, `AndroidWallet.kt:151`, `AndroidTransactionHistory.kt:40` use plain `setPlainText` (no `EXTRA_IS_SENSITIVE`, no auto-clear) while seed/DM keys correctly use `setSensitivePlainText`.

## 3. Verified OK

- **Seed storage**: `PersistableDarkfiWallet` JSON in Tink AES-256-GCM DataStore keyed by Android Keystore (`AndroidPreferenceProvider.newEncrypted`, `SecureDataStoreSerializer.createAead`); `wallet_pass` is 32 random bytes from `SecureRandom`, separate Tink keyset (`DrkWalletPassStore.kt:39-54`), not derived from seed; `wipeLocalState` doesn't touch it.
- **Backup exclusion**: `allowBackup="false"`, `data_extraction_rules.xml`/`backup_rules.xml` exclude all domains for cloud + d2d.
- **FLAG_SECURE**: `SecureScreen()` on seed, backup (long/short), restore, PIN, chat, chat settings, DM sheet; gated by `IS_SECURE_SCREEN_PROTECTION_ACTIVE=true` → `BuildConfig.IS_SECURE_SCREEN_ENABLED`.
- **Seed/DM copy**: `setSensitivePlainText` sets `EXTRA_IS_SENSITIVE`, 60 s timed clear + clear on background.
- **strictOmrOnly**: `DarkfiChatPreferences.kt:33-37` default `false` → `DrkBootstrapConfig.strictOmrOnly` (`DarkfiMobileFfiApi.kt:88`); README "fallback ON" claim is accurate. Toggle persists and applies live (`SettingsViewModel.kt:112-122`).
- **darkfid_rpc_url**: hard-coded `null` (`DarkfiMobileFfiApi.kt:87`); no 18345 in the sync path (only in `DarkfidChainDefaults` for the optional embedded node).
- **Tor wrapping**: `TorDarkfidEndpoint` leaves TLS URLs intact (Rust process SOCKS + pin), wraps cleartext remotes as `socks5://`, skips loopback. Network security config: cleartext only for `127.0.0.1`/`localhost`/`10.0.2.2`.
- **TLS pin**: 64-hex parsing, `current+previous` concatenation matches Rust `pin.len() % 32 == 0 && ≤128` (`lib.rs:584-585`).
- **Memo bounds**: 255 UTF-8 bytes, UTF-8-safe truncation in UI (`EnterMemo.kt:73`) and `normalize()` before FFI.
- **Handle lifecycle**: `openLock` serializes `Drk::new`; `replaceLock` + `close()` joins poller before `handle.close()`; `walletAddresses`/`generateNewAddress` run on IO in `WalletViewModel`.
- **22-word DarkFi vs 24-word BIP39**: restore enforces `size == 22` + `validateDarkfiMnemonic`; chat identity uses `generateBip39ChatMnemonic` separately.
- **ProGuard**: `darkfi-android-sdk/proguard-consumer.txt` keeps `com.sun.jna.**`, `Structure` fields, `Callback` methods, `com.nighthawkapps.lib.uniffi.**`; mainnet release strips `android.util.Log`.
- **Build metadata**: `WALLET_VERSION_CODE=30001814`/`3.00.014` ↔ `docs/fdroid.md` ↔ `README.md` ↔ `changelogs/30001814.txt` consistent; `Cargo.toml` bumped to 0.2.1 matching changelog; minSdk 27/target 37/compile 37.2, NDK `26.1.10909125` wired via `stealth.android-build-conventions:82`; release unsigned unless keystore props set (F-Droid OK); `dependenciesInfo` off.
- **Payment URI**: `drk:<addr>?amount=<display>&memo=<b64>`; parser bounds amount (≤ 21M×1e8, >0), address ≤256 chars, no control chars. No desktop counterpart found in `third_party/darkfi` to compare against.

## 4. UDL ↔ generated Kotlin

`darkfi_mobile_ffi.kt` matches `darkfi_mobile_ffi.udl` exactly: all 19 namespace functions, `DarkfiWalletHandle` constructor + 23 methods, records (`DmKeypair`, `DrkBootstrapConfig` 12 fields incl. `strictOmrOnly`, `DrkLightSyncState` 10 fields, `DrkTransactionRecord` 9 fields, DAO records, `ReorgEvent`), enums `SyncMethod`/`SyncFallbackReason`, 13-variant `DarkfiWalletNativeException`, callbacks `DarkircEventCallback`/`ReorgEventCallback`. No stale symbols. Note the UDL itself is unmodified while `lib.rs`/`sync.rs` are — bindings only need regeneration if the UDL changes, but the two shipped `.so`s must be rebuilt from the same tree (see 1.6).

## Test gaps (G)

Covered: amount parser, memo, TLS pin, Tor endpoint rewrite, endpoint guard, sync-snapshot mapping, tx mapping, coordinator lifecycle (stub only), deep-link, HUD parsing. Missing: main-thread/dispatcher assertions for `NativeDarkfiSynchronizer.refreshNow`; reorg callback → status flow; `SyncWorker` behavior; `PeerHostDisplay` "never shows IP" (current test asserts the opposite); token-send balance check; locale amount parsing; ABI/jniLibs presence check in CI. No `@Ignore`d tests; instrumented FFI tests skip via `assumeTrue` when the `.so` is absent.