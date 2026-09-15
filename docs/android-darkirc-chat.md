# DarkIRC chat on Android — current vs legacy

**Current Chat tab:** in-process UniFFI (`start_darkirc` + `DarkircEventCallback`). Same EventGraph path as iOS. Nearby BLE is [Nighthawk Mesh](nighthawk-mesh.md) (encrypted DAG hop only).

**This page** documents the **legacy IRC** path: Kotlin `DarkircIrcClient` talking TCP to a DarkIRC listener (`CAP` / `NICK` / `USER` / `JOIN` / `PRIVMSG`), including desktop `adb reverse` and optional packaged `darkirc_exec`. Do not treat it as the default product architecture.

With **Run embedded DarkIRC node** enabled (Chat → settings), the app can still start packaged **`darkirc`** in a **foreground service** — see **`docs/darkirc-embedded-android.md`** (AGPL). That process is independent of UniFFI EventGraph.

## Why channels can be empty while a legacy IRC session shows Connected

1. **History cap:** The Kotlin client must **not** send `CAP REQ :no-history` to darkirc. That cap disables DAG replay on `JOIN` / welcome.
2. **P2P / DAG sync:** Public messages live on the event graph. A listener-only `darkirc` must complete a P2P handshake with lilith seeds (`tcp+tls://lilith0.dark.fi:25551`, …) and run `sync_selected` before history `PRIVMSG`s exist.
3. **Lilith outages:** `Version Exchange failed` / `Unable to connect to seed` means history stays empty until seeds accept peers. Use `./scripts/dev-darkirc-adb-reverse.sh` when host `darkirc` has working P2P.
4. **Preferred path:** open **Chat** with UniFFI daemon (no IRC host). First DAG sync can take several minutes (`adb logcat -s darkfi-mobile-ffi darkfi-net`).

## Quick path (desktop darkirc + USB debugging) — legacy

1. Build/run [`darkirc`](https://github.com/darkrenaissance/darkfi/tree/master/bin/darkirc) on your workstation with defaults (`tcp://127.0.0.1:6667`).
2. Enable USB debugging on the phone and connect it.
3. Run `adb reverse tcp:6667 tcp:6667` so the phone’s `127.0.0.1:6667` forwards to the workstation IRC listener.
4. Point **legacy** IRC settings at `127.0.0.1:6667` — this does **not** replace in-process UniFFI chat.

Password-protected DarkIRC servers send `PASS` first — store one via `DarkfiChatPreferences`.

## Tor / Arti notes

- DarkFi’s Rust workspace embeds **Arti** (`p2p-tor`). In-process UniFFI chat uses SOCKS when Tor is on (Guardian **tor-android** by default).
- Legacy Kotlin IRC: when Tor is on **and** the IRC host is **not** loopback, the client uses **SOCKS5** (`127.0.0.1:9050`, probe `9150`). Loopback IRC skips SOCKS.

**Embedded `darkirc_exec` + app Tor:** generated TOML still uses **`tcp+tls` clearnet lilith seeds**. The in-app Tor toggle does **not** switch that subprocess to onion seeds.

## Automated checks (JVM)

```bash
./gradlew :darkfi-android-sdk:testDebugUnitTest
```

Covers IRC wire parsing (legacy client), Tor-vs-loopback routing, SOCKS probing, mesh inbox ingest, and UniFFI-related chat helpers.

## Manual verification (legacy IRC)

**A — Desktop darkirc + USB**

1. Run `darkirc` on the workstation (`tcp://127.0.0.1:6667`).
2. `adb reverse tcp:6667 tcp:6667`.
3. Use the optional IRC client path with Tor **off**.

**B / C — Tor** — loopback skips SOCKS; non-loopback IRC needs a reachable SOCKS port.

## Embedded daemon lifecycle (legacy subprocess)

- **Listener config**: generates `darkirc_config.toml` with `irc_listen` aligned to chat settings. Stale bind → stop, rewrite, restart.
- **Foreground service**: `DarkircDaemonService` is `START_STICKY`.

### Resume after background (legacy)

| Scenario | Intended behavior |
|----------|-------------------|
| App returns from background | `warmEmbeddedOnForeground()` + `syncEmbeddedIrcListenConfigAndEnsureRunning()` |
| Cold start | `Application.onCreate` `maybeStart()` plus first `onStart` warmup |
| Child exited | Next warmup or Chat connect respawns |

Chat settings → **Restart embedded DarkIRC** calls `DarkircDaemonBootstrap.restartEmbeddedLoopback`.

## UniFFI native library (`libdarkfi_mobile_ffi.so`)

Build via **`./scripts/build-darkfi-mobile-ffi-android.sh`**. Mesh C ABI only: `SKIP_UNIFFI_BINDGEN=1`. See [nighthawk-mesh.md](nighthawk-mesh.md) and **`artifacts/mobile-ffi/README.md`**.
