# DarkIRC chat on Android

The Compose chat tab talks **IRC over TCP** to a DarkIRC-compatible listener — the same protocol handled by `darkfi/bin/darkirc/src/irc/server.rs` + `client.rs` on desktop (`CAP`, `NICK`, `USER`, `JOIN`, `PRIVMSG`).

With **Run embedded DarkIRC node** enabled (Chat → settings; default on), the app can start a packaged **`darkirc`** binary and keep it in a **foreground service** — see **`docs/darkirc-embedded-android.md`** (build the binary separately; AGPL compliance required to ship).

## Why channels can be empty while IRC shows Connected

1. **History cap (fixed in app):** The Kotlin client must **not** send `CAP REQ :no-history` to darkirc. That cap disables DAG replay on `JOIN` / welcome.
2. **P2P / DAG sync:** Public messages live on the event graph. Embedded `darkirc` must complete a P2P handshake with lilith seeds (`tcp+tls://lilith0.dark.fi:25551`, …) and run `sync_selected` before history `PRIVMSG`s exist. While IRC stays connected, the app re-JOINs channels on a schedule (bootstrap burst, then every 45s) so late P2P sync and new DAG traffic keep appearing in the read loop. If the first connect had zero messages, it runs extra re-JOIN bursts at 30s / 90s / 180s (IRC session stays up).
3. **Lilith outages:** If logcat / host `darkirc` shows `Version Exchange failed` or `Unable to connect to seed`, the seeds are reachable on TCP but the P2P handshake failed — history stays empty until lilith accepts peers again. Use `./scripts/dev-darkirc-adb-reverse.sh` on a workstation where host `darkirc` has working P2P, then open Chat (embedded auto-skips when port 6667 is forwarded).
4. **Dev fallback:** Run desktop `darkirc` + `adb reverse tcp:6667 tcp:6667` (see below) if emulator clearnet seeds fail.

## Quick path (desktop darkirc + USB debugging)

1. Build/run [`darkirc`](https://github.com/darkrenaissance/darkfi/tree/master/bin/darkirc) on your workstation with defaults (`tcp://127.0.0.1:6667`).
2. Enable USB debugging on the phone and connect it.
3. Run `adb reverse tcp:6667 tcp:6667` so the phone’s `127.0.0.1:6667` forwards to the workstation IRC listener.
4. Open **Chat** in Nighthawk — defaults (`127.0.0.1:6667`) connect immediately after handshake.

Password-protected DarkIRC servers send `PASS` first — store one via `DarkfiChatPreferences` once we expose UI for it (currently code-ready).

## Tor / Arti notes

- DarkFi’s Rust workspace embeds **Arti** for daemon-side Tor transports (`Cargo.toml` feature bundle `p2p-tor`).
- The Kotlin UI talks to **`darkirc`** over TCP (packaged as an optional `darkirc_exec` plus foreground service — see **`docs/darkirc-embedded-android.md`**), not `libdarkirc.so`. When Tor is on **and** the IRC host is **not** loopback, that client uses **SOCKS5** at the configured loopback address (defaults `127.0.0.1:9050`; the client also probes `9150`). Loopback IRC skips SOCKS because DarkIRC listens locally.

With **Built-in Tor** enabled (default), the app starts **tor-android** in-process so you do not need a separate SOCKS app. Turn it off in **Settings → Tor network** only if you run your own Tor SOCKS listener and point host/port at it.

When Tor is on and the IRC host is **not** loopback, the app starts embedded Tor when enabled, then TCP-probes **your configured port**, **9050**, and **9150** within a combined time budget—then opens the IRC socket through SOCKS5.

**Embedded darkirc + app Tor on:** The packaged daemon’s P2P profile stays **`tcp+tls` clearnet lilith seeds** (see generated TOML). The in-app “Tor” toggle does **not** switch that daemon to `tor://…onion` seeds—SOCKS/Tor apply to Kotlin IRC (non-loopback) and wallet HTTP. Diagnostics use the same clearnet seed URLs whenever embedded mode is active so DNS probes match what the daemon actually uses.

## Automated checks (JVM)

```bash
./gradlew :darkfi-android-sdk:testDebugUnitTest
```

Covers IRC wire parsing, Tor-vs-loopback routing policy, SOCKS TCP readiness probing, and a **minimal fake DarkIRC server** handshake against `DarkircIrcClient`.

## Manual verification checklist

**A — Desktop darkirc + USB**

1. Run `darkirc` on the workstation with default IRC listen (`tcp://127.0.0.1:6667`).
2. `adb reverse tcp:6667 tcp:6667`.
3. Open **Chat** with Tor **off**, host `127.0.0.1`, port `6667`.
4. Expect **Connected (direct)** and default channels listed; send a test message if another client is on the same IRC.

**B — Tor toggle + loopback**

1. Same as A but enable **Tor** in Chat.
2. Expect **Connected (direct)** (SOCKS skipped for loopback) and diagnostics mentioning loopback / adb reverse.

**C — Tor + SOCKS (non-loopback IRC)**

1. Ensure Tor SOCKS is available (built-in tor-android **or** your own listener matching host/port in Settings → Tor network).
2. Point IRC host at a **non-loopback** endpoint reachable via Tor (your own bridge/onion setup).
3. Enable **Tor** in Chat.
4. Expect **Connected via Tor** after SOCKS becomes reachable; if it times out, confirm bootstrap finished and that **9050** or **9150** matches your Tor SOCKS settings and chat preferences.

## Embedded daemon lifecycle (listener + resume)

- **Listener config**: On connect, embedded mode generates `darkirc_config.toml` with `irc_listen` aligned to chat settings (`tcp://127.0.0.1:<ircServerPort>`). If an older bundled process still runs with a stale bind, it is stopped first, then config is rewritten and the daemon restarted (darkirc does not hot-reload `irc_listen`).
- **Foreground service**: `DarkircDaemonService` is `START_STICKY`; the OS may kill the daemon child independently — before IRC the client waits until TCP accepts on the configured port, then opens the IRC session (`DarkircIrcClient`).

### Plan — resume after long background or process eviction

| Scenario | Intended behavior |
|----------|-------------------|
| App returns from background | `ProcessLifecycleOwner` **`onStart`** runs `warmEmbeddedOnForeground()` — requests the foreground service off the gate, then a background thread calls `syncEmbeddedIrcListenConfigAndEnsureRunning()` so the daemon and listener reconcile without blocking UI. |
| Cold start after full process kill | `Application.onCreate` runs `maybeStart()` plus lifecycle registration → first foreground **`onStart`** performs the same warm-up path. |
| Java process lives but **`darkirc` child exited** | The stdout drain clears `processRef`; the next warmup or Chat connect sees no live process and spawns again. **Zombie** (Java thinks alive but IRC port silent): TCP wait expires and the user taps connect again; optional future work is a probe-driven forced restart. |

**Suggested follow-ups:** (1) ~~When the outbound chat queue has work and IRC is down, reschedule warmup~~ — **`OutgoingChatReconnectWorker`** now calls **`prepareForLoopbackIrc`** before reconnect when embedded + loopback. (2) ~~Settings “Restart embedded node”~~ — **Chat settings → Restart embedded DarkIRC** calls **`DarkircDaemonBootstrap.restartEmbeddedLoopback`**.

## UniFFI native library (`libdarkfi_mobile_ffi.so`)

Build and bundle via **`./scripts/build-darkfi-mobile-ffi-android.sh`** (populates **`artifacts/mobile-ffi/`**; Gradle **`syncMobileFfiArtifacts`** merges into the APK). See **`artifacts/mobile-ffi/README.md`**.

## JNI / licensing reminder

Packaging `darkirc` inside the APK links **AGPL-3.0** DarkFi code — coordinate compliance (source offers, etc.) before distributing merged binaries.
