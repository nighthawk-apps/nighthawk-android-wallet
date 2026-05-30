# DarkIRC / P2P parity (upstream scan)

This summarizes what **darkfi `bin/darkirc`** does today and how the Kotlin client lines up. It is not a substitute for reading [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi).

## Transport & discovery

- **P2P / Event Graph / DAG**: Implemented inside upstream **`darkirc`** (Rust). Nighthawk can run the same stack **in-process** via bundled **`darkirc_exec`** — a packaged copy of `bin/darkirc` with generated TOML under `filesDir/darkirc/`. That daemon owns **`P2p`**, **`EventGraph`**, and DAG sync; Kotlin only speaks IRC to `irc_listen`.
- **Kotlin IRC client**: Connects to **`tcp://127.0.0.1:<port>`** (embedded daemon or external node). Does **not** reimplement the Event Graph in Kotlin.
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

**Not covered in CI (needs device / daemon):** live P2P sync, real ChaCha decrypt, RLN verify, long-running network drop during read loop.

## Room & kotlinx.serialization

- **Now:** pending sends use **`kotlinx.serialization`** JSON on disk (`DarkfiChatOutgoingQueueStore`).
- **Later:** migrate the same fields to **Room** `@Entity` when **KSP/Room** is added to the Gradle build; no Room plugin is present in this repository yet.
