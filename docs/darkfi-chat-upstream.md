# DarkIRC / P2P parity (upstream scan)

This summarizes what **darkfi `bin/darkirc`** and **`bin/app`** do today and how Nighthawk aligns. It is not a substitute for reading [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi).

## Upstream `bin/app` vs Nighthawk Android

| Layer | Upstream desktop **`bin/app`** | Nighthawk Android |
|-------|-------------------------------|-------------------|
| Chat transport | **In-process** `plugin/darkirc.rs`: `P2p` + `EventGraph`, no IRC wire | **Embedded `darkirc_exec`** subprocess + Kotlin IRC to `127.0.0.1:6667` |
| Scene graph API | `/plugin/darkirc` methods `send`, signals `recv`, `connect` | `DarkfiChatController` → `DarkircIrcClient` |
| Tor toggle | `use_tor.txt` in app data → Arti **`tor`** profile + onion seeds `:25552` | Settings → **Tor network** → `AppTorCoordinator` + Guardian **tor-android** SOCKS; embedded darkirc uses **`socks5`** profile via loopback proxy |
| Wallet | In-process `DrkPlugin` → loopback `darkfid` | UniFFI `drk` + optional remote `darkfid`; Tor rewrites endpoint to `socks5://…/host:port` |

Upstream does **not** spawn external `darkirc`; Android uses the standalone daemon binary because embedding Event Graph in the APK UI process is not shipped yet. Functionally: **same network identity** (`magic_bytes`, lilith/onion seeds), different IPC (scene graph vs IRC loopback).

## Transport & discovery

- **P2P / Event Graph / DAG**: Implemented inside upstream **`darkirc`** (Rust). Nighthawk can run the same stack **in-process** via bundled **`darkirc_exec`** — a packaged copy of `bin/darkirc` with generated TOML under `filesDir/darkirc/`. That daemon owns **`P2p`**, **`EventGraph`**, and DAG sync; Kotlin only speaks IRC to `irc_listen`.
- **Kotlin IRC client**: Connects to **`tcp://127.0.0.1:<port>`** (embedded daemon or external node). Does **not** reimplement the Event Graph in Kotlin.
- **App-wide Tor** (`AppTorCoordinator`): When Tor routing is enabled, embedded Tor starts at **Application** launch (not only on chat connect). Wallet HTTP (Retrofit), Kotlin `darkfid` JSON-RPC, native `drk` connect URL, and embedded darkirc P2P all honor the same SOCKS prefs.
- **Reconnect / offline queue**: Outgoing lines typed while disconnected are stored as JSON (`kotlinx.serialization`) and **drained** after the next successful `connectAndJoin`. **WorkManager** (`OutgoingChatReconnectWorker`) triggers `connectOrRetry` when the network is available, if the UI has registered `DarkfiChatConnectionBridge`.

## Channel & contact configuration (`settings.rs`)

[`bin/darkirc/src/settings.rs`](https://github.com/darkrenaissance/darkfi/blob/master/bin/darkirc/src/settings.rs) defines TOML-backed structures the Android UI does **not** parse yet:

| Upstream | Meaning |
|----------|---------|
| `autojoin = ["#dev", …]` | Must be `#…` strings; no duplicates. |
| `[channel."#name"]` `topic`, `secret` (base58 **32-byte** secret) | Builds `ChaChaBox` for **channel** encryption. |
| `[contact."name"]` `dm_chacha_public`, `my_dm_chacha_secret` (base58 **32-byte**) | **DM** pairwise `ChaChaBox`. |
| `[rln]` | RLN identity (`nullifier`, `trapdoor`, `user_message_limit`). |

**Android (embedded darkirc):** `DarkircCryptoStore` + `DarkircEmbeddedConfigGenerator` emit upstream-compatible **`[channel.*]`** / **`[contact.*]`** blocks into `darkirc_config.toml`. Keys are generated via **`DarkircCliKeygen`** (`--gen-chacha-keypair`, `--gen-channel-secret`) when `darkirc_exec` is packaged. Wire encryption still runs **inside Rust**; Kotlin does not implement ChaChaBox.

## Runtime knobs (`darkirc_config.toml` top-level)

| Upstream key | Default (upstream) | Nighthawk |
|--------------|-------------------|-----------|
| `dags_count` | `8` | `DarkfiChatPreferences.darkircDagsCount` (1–24); Chat settings when embedded node is on |
| `fast_mod` | `false` | `darkircFastMode` + UI switch |
| `replay_mode` | `false` | `darkircReplayMode` (pref only; no UI yet) |
| `password` | optional IRC server auth | Emitted when IRC password is set in chat connection prefs |

`DarkircEmbeddedRunner` regenerates TOML from prefs on start/restart. Changing DAG count or fast mode restarts the embedded node so the daemon picks up new values.

## CLI hooks (`main.rs`)

Relevant flags include `gen_chacha_keypair`, `gen_channel_secret`, `chacha_secret`, `gen_rln_identity`, `encrypt_password` (bcrypt for IRC `PASS`), `--list-contacts`, `skip_dag_sync`, `fast_mode`, etc.

## Default `#channels`

`DarkfiChatDefaults.DEFAULT_PUBLIC_CHANNELS` should stay aligned with **`autojoin`** in upstream **`bin/darkirc/darkirc_config.toml`** when you bump **`docs/upstream/darkfi-revision.txt`** (use **`scripts/fetch-darkfi-upstream-reference.sh`** to pull that file at the pinned SHA).

## Testing matrix (this repo)

| Area | Coverage |
|------|----------|
| IRC wire parsing / malformed lines | `DarkircWireParserTest` |
| Tor SOCKS routing | `DarkfiChatTorRoutingTest` |
| Outgoing queue JSON | `DarkfiChatOutgoingQueueStoreTest` |
| Public id hash | `DarkfiChatIdentityCryptoTest` |
| Embedded TOML runtime + crypto | `DarkircEmbeddedConfigCryptoTest` |
| Chat defaults vs upstream pin | `DarkfiChatDefaultsUpstreamParityTest` |
| DM pubkey prefix parser | `DarkircDmPubkeyParserTest` |

**Not covered in CI (needs device / daemon):** live P2P sync, real ChaCha decrypt, RLN verify, long-running network drop during read loop, two-device DM round-trip.

## Room & kotlinx.serialization

- **Now:** pending sends use **`kotlinx.serialization`** JSON on disk (`DarkfiChatOutgoingQueueStore`).
- **Later:** migrate the same fields to **Room** `@Entity` when **KSP/Room** is added to the Gradle build; no Room plugin is present in this repository yet.
