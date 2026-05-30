# Embedded Tor on Android (tor-android)

## Goal

Run **Guardian Project [`tor-android`](https://gitlab.com/guardianproject/tor-android)** + **`jtorctl`** inside the app so SOCKS is available on loopback **without a separate Tor app**. When built-in Tor is disabled, users point SOCKS at their own listener (same defaults: `127.0.0.1:9050` / `9150` fallback probe).

## Implementation checklist

- [x] Gradle: `info.guardianproject:tor-android`, `info.guardianproject:jtorctl`, `com.jaredrummler:android-shell`
- [x] Ship `torrc`, `geoip`, `geoip6`, `bridges.txt` under `darkfi-android-sdk/src/main/assets/common/` (Tor Project–style geoip + bundled torrc/bridges)
- [x] Kotlin runtime: `com.nighthawkapps.lib.android.sdk.tor.embedded.*` (operator, control, resources; RxJava removed from control layer)
- [x] `DarkfiChatPreferences.useEmbeddedTor` (default **true**)
- [x] `DarkfiChatController`: `EmbeddedTorController.awaitEmbeddedSocksIfEnabled()` before TCP SOCKS probe when embedded Tor is enabled
- [x] UI: Settings → Tor network — **Built-in Tor (tor-android)** toggle + copy updates

## Test matrix (manual / automated)

| ID | Case | Expect |
|----|------|--------|
| T1 | Default prefs on fresh install | `useEmbeddedTor == true` |
| T2 | Tor route OFF | No embedded start on chat connect |
| T3 | Tor route ON, embedded ON, non-loopback IRC | App starts embedded Tor; SOCKS probe succeeds on 9050/alt |
| T4 | Embedded ON, SOCKS already up | Connect proceeds without redundant failure |
| T5 | Embedded ON but device blocks exec / Tor fails | After timeout, TCP probe may still succeed if another SOCKS proxy listens on probed ports |
| T6 | User turns **Built-in Tor** OFF | Only external SOCKS at configured host/port |

**Automated:** `DarkfiChatPreferencesEmbeddedTorTest` — default and round-trip for `useEmbeddedTor`.

**Not covered in CI:** actually running `libtor.so` (needs device/emulator with working `sh` and binary execution).

## Attribution

Embedded Tor uses **Guardian Project** [`tor-android`](https://gitlab.com/guardianproject/tor-android) and **jtorctl**. GeoIP databases are standard Tor Project–style data files.

## Follow-ups

- JNI / Rust JSON-RPC: honor the same SOCKS prefs when the native stack opens sockets.
- Lifecycle: explicitly `stopEmbeddedTor()` when the user turns Tor off app-wide (optional coordinator).
- Reduce APK size: optional download of geoip vs bundling.
