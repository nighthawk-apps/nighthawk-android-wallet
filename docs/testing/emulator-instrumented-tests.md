# Emulator instrumented tests

## Device selection

Gradle uses `ANDROID_SERIAL` when set. Example:

```bash
export ANDROID_SERIAL=emulator-5554
```

If Gradle targets a disconnected physical device, set `ANDROID_SERIAL` to the running emulator.

## 16KB page-size emulators (`sdk_gphone16k_*`)

`connectedDebugAndroidTest` may report **No compatible devices** when native `.so` files are not yet verified for 16KB alignment. Use the adb scripts instead:

```bash
export ANDROID_SERIAL=emulator-5554
./scripts/run-sdk-instrumented-tests.sh
./scripts/run-app-instrumented-tests.sh darkfitestnet
```

## SDK tests (JNI, memo, DM parser)

- `DarkfiMobileFfiInstrumentedTest` — UniFFI / mnemonic (skipped if native lib missing)
- `DarkfiPaymentMemoInstrumentedTest` — memo normalization
- `DarkfiTransferInstrumentedTest` — fee FFI linkage
- `DarkircDmPubkeyParserInstrumentedTest` — DM pubkey prefix parser

## App tests

- `AndroidApiTest` — min/target SDK guards

## Manual chat / DM smoke

1. Install: `./gradlew :app:installDarkfitestnetDebug`
2. Launch: `adb shell am start -n com.nighthawkwallet.android.testnet/com.nighthawkapps.lib.android.ui.screen.advancesetting.model.OneLauncherAlias`
3. Open **Chat** tab → confirm **Channels** / **Direct**, **+** (New conversation), **Post to channel** on a `#` channel.

Embedded DarkIRC may show **IRC: error** until P2P seeds connect; DM crypto still requires **embedded DarkIRC** enabled in chat settings.

## Low storage

If `INSTALL_FAILED_INSUFFICIENT_STORAGE`:

```bash
adb shell pm trim-caches 500M
adb uninstall com.nighthawkapps.lib.android.sdk.test   # optional
```
