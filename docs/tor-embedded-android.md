# Embedded Tor on Android (Arti)

## Goal

Run **in-process Arti** (`arti-client` 0.45 via UniFFI `start_arti_proxy`) so SOCKS is available on loopback **without a separate Tor app**. When built-in Tor is disabled, users point SOCKS at their own listener (same defaults: `127.0.0.1:9050` / `9150` fallback probe).

Guardian `tor-android` is no longer shipped. Chat P2P, wallet LWD, and fiat HTTP share the same Arti SOCKS listener.

## Implementation checklist

- [x] UniFFI `startArtiProxy` / `isArtiRunning` / `stopArtiProxy`
- [x] `AppTorCoordinator` starts Arti when `useEmbeddedTor` is true
- [x] `DarkfiChatPreferences.useEmbeddedTor` (default **true**)
- [x] UI: Settings → Tor network — **Built-in Tor (Arti)** toggle
- [x] Chat daemon `use_tor` dials onion seeds through the Arti SOCKS port

## Test matrix

| ID | Case | Expect |
|----|------|--------|
| T1 | Default prefs on fresh install | `useEmbeddedTor == true` |
| T2 | Tor route OFF | Splash does not wait; chat uses clearnet seeds |
| T3 | Tor route ON, embedded ON | Arti SOCKS on configured port; `isArtiRunning` before darkirc / LWD |
| T6 | User turns **Built-in Tor** OFF | Only external SOCKS at configured host/port |

**Automated:** `DarkfiChatPreferencesEmbeddedTorTest` — default and round-trip for `useEmbeddedTor`.
