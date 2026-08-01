# DarkFi integration architecture

This describes how the Android app connects to DarkFi while keeping UI patterns from the Nighthawk codebase.

## Layers

```
┌─────────────────────────────────────────────────────────┐
│  app / ui-lib (Compose, navigation, ViewModels)        │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────┐
│  darkfi-android-sdk (Kotlin façade)                     │
│  - DarkfiWalletCoordinator, DarkfiSynchronizer          │
│  - PersistableDarkfiWallet, DarkfiEndpoint               │
│  - DRK formatting, BIP39 wordlist                       │
│  - Chat: Kotlin IRC; optional packaged **darkirc** (foreground svc); SOCKS tor-android; wallet HTTP shares prefs │
└───────────────────────────┬─────────────────────────────┘
                            │ UniFFI generated Kotlin (+ JNA) when `jniLibs` has `libdarkfi_mobile_ffi.so`
┌───────────────────────────▼─────────────────────────────┐
│  `rust/darkfi-mobile-ffi` — `cdylib` per ABI (`cargo-ndk`)   │
│  UDL-defined API (`darkfi_mobile_ffi.udl`)              │
└─────────────────────────────────────────────────────────┘
                            │ Optional / legacy experiments
┌───────────────────────────▼─────────────────────────────┐
│  `rust/darkfi-android-bridge` (`libdarkfi_android_bridge`)  │
└─────────────────────────────────────────────────────────┘
```

## Kotlin façade (`darkfi-android-sdk`)

### Responsibilities

- **Session lifecycle**: `DarkfiWalletCoordinator` watches encrypted prefs for `PersistableDarkfiWallet` and publishes `StateFlow<DarkfiSynchronizer?>`.
- **Sync abstraction**: `DarkfiSynchronizer` exposes `Flow` streams for status, progress, and transactions — implemented today by `StubDarkfiSynchronizer` until Rust fills in real sync.
- **Persistence**: JSON serialization of `PersistableDarkfiWallet` (seed words + network + endpoint). Older wallet blobs from other forks are **not** imported — users onboard or restore from a backed-up phrase.
- **Mnemonics**: English BIP39 wordlist shipped as `assets/bip39/english.txt`; validation mirrors standard checksum rules without the upstream vendor wordlist dependency.
- **Tor / SOCKS (Kotlin HTTP)**: `DarkfiChatPreferences.routeOutboundThroughTor` drives OkHttp SOCKS; with **`useEmbeddedTor`** (default true) the app runs **Guardian `tor-android`** in-process. **`useEmbeddedTor` false** uses only an external SOCKS at **`socksHost`/`socksPort`**. JNI/Rust JSON-RPC still uses the FFI socket stack until it honors SOCKS. See **`docs/tor-embedded-android.md`**.

### Chat / DarkIRC / Tor status

`DarkfiChatController` drives a **Kotlin IRC client** (`DarkircIrcClient`) that speaks the same CAP/NICK/USER flow implemented in `darkfi/bin/darkirc/src/irc/command.rs` (handshake against DarkIRC’s local listener, default `tcp://127.0.0.1:6667` on desktops).

| Topic | DarkFi source | This repo (today) |
|-------|----------------|-------------------|
| Clearnet vs Tor seeds | `darkirc` + `darkfi` net settings (`bin/darkirc`) | `DarkfiChatDefaults` literals + DNS diagnostics |
| Channel presets | **`darkirc_config.toml`** **`autojoin`** ([upstream](https://github.com/darkrenaissance/darkfi/blob/master/bin/darkirc/darkirc_config.toml)) | `DEFAULT_PUBLIC_CHANNELS` |
| Tor toggle | `use_tor.txt` semantics | `DarkfiChatPreferences.routeOutboundThroughTor` (wallet HTTP + IRC) |
| IRC wire | `IrcServer` / `Client` (`bin/darkirc/src/irc/`) | Kotlin TCP client (+ SOCKS5 when Tor flag **and** IRC host is **not** loopback) |
| P2P + DAG + Arti | `darkfi` workspace (`arti-client`, `p2p-tor`, …) | Still inside the Rust daemon — ship `darkirc` via JNI/`cargo-ndk`; until then run `darkirc` off-device and reverse-port IRC (see [DarkIRC chat on Android](android-darkirc-chat.md)). |

Kotlin states (`DarkfiChatConnectionState`): `Disconnected`, `Connecting`, `ConnectedDirect`, `ConnectedViaTor`, `Degraded`, `Error`.

- **Arti:** upstream DarkFi links Arti/Tor inside Rust. This APK can run **bundled tor-android** for SOCKS on loopback (default **9050**, **9150** fallback probe) for Kotlin HTTP and IRC clients.

- **Honesty guard:** DNS checks against `lilith*.dark.fi` only prove resolver reachability — real DAG participation requires your `darkirc` node (desktop or future JNI) to be synced.

Full **P2P / encryption / offline queue parity** versus upstream `darkirc` lives in **[DarkIRC / chat upstream](darkfi-chat-upstream.md)**.

Color semantics (UI suggestion):

| State | Indicator |
|-------|-----------|
| Disconnected | Neutral |
| Connecting | Amber |
| ConnectedDirect / ConnectedViaTor | Green (still show native-bridge disclaimer text) |
| Degraded | Orange |
| Error | Red |

## Rust UniFFI crate (`rust/darkfi-mobile-ffi`)

### Current state

- UniFFI **0.31** `cdylib` named **`darkfi_mobile_ffi`** (`uniffi.toml` sets the Kotlin package to `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`).
- Generated bindings live beside other SDK sources (`darkfi-android-sdk/src/main/java/.../darkfi_mobile_ffi/darkfi_mobile_ffi.kt`); **prefer** calling through **`DarkfiMobileFfiApi`** so the FFI surface stays swappable.
- **Not** yet linked against the full `darkfi` workspace—the UDL exposes `bridge_version`, `bridge_ping`, and `DarkfiWalletHandle` (`confirmed_balance_atomic`, **`primary_deposit_address`**) so Kotlin can stabilize while Rust grows incrementally behind the same ABI. **`DarkfiMobileFfiApi.drkBootstrapSummary`** maps `PersistableDarkfiWallet` to the **`Drk::new`** field shape (network, endpoint URL, word count — never logs seed words).

See **[`rust/darkfi-mobile-ffi/README.md`](../rust/darkfi-mobile-ffi/README.md)** for **`cargo`** / **`cargo-ndk`** builds and **`uniffi-bindgen`** regeneration (use `--no-format` if `ktlint` is not installed).

### Legacy bridge (`rust/darkfi-android-bridge`)

- Still a trivial `darkfi_android_bridge` crate (`darkfi_bridge_ping()` only)—kept optional; **`darkfi-mobile-ffi`** is the supported UniFFI path.

### Gradle

The Android library does **not** invoke Cargo. CI/local flows should run **`cargo-ndk`** (or equivalent) before packaging ABIs under `jniLibs/`.

## Endpoint configuration

`DarkfiEndpoint` is the **lightwalletd** gRPC sync URL (default port **9067**). Upstream `darkfid` JSON-RPC ports remain available as constants for embedded-node / management helpers only:

| Use | Port |
|-----|------|
| lightwalletd (wallet sync / UnifOMR) | 9067 |
| darkfid JSON-RPC mainnet / testnet / localnet | 8345 / 18345 / 28345 |
| darkfid management RPC | 8346 / 18346 / 28346 |

Display strings use `tcp://host:port` (and `tcps://…` if TLS is ever enabled). The field is still named `tls` in JSON for wire compatibility.

[`DarkfidJsonRpc`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidJsonRpc.kt) lists JSON-RPC **method names** from upstream [`bin/darkfid/src/rpc/mod.rs`](https://github.com/darkrenaissance/darkfi/blob/master/bin/darkfid/src/rpc/mod.rs) for when a socket transport or JNI caller is wired — **wallet balances and keys still live in `drk`’s local `wallet.db`**, not on these node methods.

Upstream **`darkfid`** exposes a **second** management listener (`ManagementRoute` in Kotlin — **`dnet.*`**, **`p2p.get_info`**); ports come from **`darkfid`** settings, not `drk_config.toml`. Mining-facing RPC (**`stratum`**, **`xmr`**) shares the darkfid daemon but uses additional **`RpcSettings`** binds upstream — intentionally **not** mirrored in Kotlin until product needs them.

### Upstream parity status

This table closes the audit loop against **`docs/upstream/darkfi-revision.txt`** (pinned SHA — not assumed floating **`master`**).

| Area | Status | Notes |
|------|--------|-------|
| **`darkfid` main JSON-RPC (`DefaultRpcHandler`)** | **Aligned** | [`DarkfidJsonRpc.Method`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidJsonRpc.kt) ↔ dispatcher match arms in upstream **`bin/darkfid/src/rpc/mod.rs`**. Wire format: CRLF‑framed JSON lines ([`DarkfidLineJsonRpcCaller`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidLineJsonRpcCaller.kt)). |
| **`darkfid` management JSON-RPC** | **Named + caller helper** | [`DarkfidJsonRpc.ManagementRoute`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidJsonRpc.kt) ↔ **`ManagementRpcHandler`**; [`DarkfidManagementRpc`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidManagementRpc.kt) builds **8346 / 18346** endpoints and reuses [`DarkfidLineJsonRpcCaller`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/rpc/DarkfidLineJsonRpcCaller.kt). |
| **`darkfid` mining RPC (`stratum` / `xmr`)** | **Out of scope** | Wallet APK does not expose merge‑mining / stratum callers; bump audit still fetches those **`rpc/*.rs`** files for drift spotting only. |
| **Wallet → lightwalletd** | **Aligned** | [`DarkfiEndpoint`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/wallet/DarkfiEndpoint.kt) defaults to **9067**. `darkfid` JSON-RPC ports are separate constants for embedded/management use. |
| **`darkirc_config.toml` `autojoin`** | **Aligned** | [`DEFAULT_PUBLIC_CHANNELS`](../darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/android/sdk/chat/DarkfiChatDefaults.kt) matches **`autojoin`** order including **`#lunardao`** — re‑diff **`darkirc_config.toml`** whenever the pin bumps ([`scripts/fetch-darkfi-upstream-reference.sh`](../scripts/fetch-darkfi-upstream-reference.sh)). |
| **`drk`‑level wallet operations** | **Explicitly unfinished** | Balance, scan, keys, signing live in **`drk`** + **`wallet.db`** upstream — mobile remains **`StubDarkfiSynchronizer`** + UniFFI placeholder until **`wallet-roadmap.md`** lands Rust behind **`DarkfiWalletHandle`**. |
| Embedded upstream **`darkirc`** (P2P / DAG / crypto) | **Explicitly unfinished** | Kotlin IRC client only — gaps and JNI path in **[DarkIRC / chat upstream](darkfi-chat-upstream.md)** and **[DarkIRC chat on Android](android-darkirc-chat.md)**. |

**Summary:** Chain‑facing **constants and presets track the pinned upstream tree** for **`darkfid`** main RPC, **`drk`** endpoint ports, separate **management** listener naming, and **`darkirc` `autojoin`** (**`#lunardao`** included). **`drk`** wallet semantics and **embedded upstream darkirc parity** remain **deliberately unfinished** until FFI / JNI work replaces stubs — not accidental drift.

### Upstream revision pin

Integration audits track a **single pinned SHA** for [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi):

- **`docs/upstream/darkfi-revision.txt`** — full commit SHA on line 1 (comment lines below allowed).
- **`scripts/verify-darkfi-upstream-pin.sh`** — asserts GitHub **`raw.githubusercontent.com`** returns HTTP 200 for `bin/drk/drk_config.toml`, `bin/darkirc/darkirc_config.toml`, and every **`bin/darkfid/src/rpc/*.rs`** used for RPC audits.
- **`scripts/fetch-darkfi-upstream-reference.sh`** — downloads those paths into **`docs/upstream/_scratch/`** (gitignored) for manual **`diff`** after bumps.

GitHub Actions workflow **[`.github/workflows/darkfi-upstream-pin.yml`](../.github/workflows/darkfi-upstream-pin.yml)** runs that verification weekly, on **`workflow_dispatch`**, and when bump PRs touch **`docs/upstream/**`** or the scripts.

### Wallet roadmap

See **[Wallet roadmap](wallet-roadmap.md)** for the adopted direction (**embedded `drk`-equivalent wallet logic via UniFFI** vs thin-client alternatives) and how it constrains the FFI boundary.

The in-app **Change server** flow edits presets (`DarkfiEndpointCatalog`) plus optional custom host/port and persists them independently from wallet seed JSON where configured. For alpha testnet, point at a running **`darkfid` JSON-RPC** on port **18345** — see **[Alpha testnet connection](alpha-testnet-connection.md)** and **[upstream endpoint snapshot](upstream/alpha-testnet-endpoints.md)**. To ship the node inside the APK (upstream still expects loopback RPC; chat P2P is in-process upstream but **`darkfid` is not**), see **[Packaging darkfid](darkfid-embedded-android.md)**.

### Error handling

Wallet-level errors use `DarkfiWalletError` sealed types (critical, processor, submission, setup). Chain height mismatches can be reintroduced when Rust exposes block metadata.

## Known limitations (explicit)

See **[Upstream parity status](#upstream-parity-status)** for the structured ✅ / ❌ matrix versus pinned upstream.

- No **`drk`‑parity** chain sync, **`wallet.db`** semantics, or real transaction broadcast yet — **`StubDarkfiSynchronizer`** and UniFFI **`WalletNotInitialized`** hold the place until **`wallet-roadmap.md`** Rust lands.
- **`darkirc`** inside this APK is **IRC wire only** — not Event Graph / P2P / ChaCha DM parity with **`bin/darkirc`** (upstream sample **`darkirc`** **`[rpc]`** listen **26660** is unrelated to **`DarkfiEndpoint`** **`darkfid`** ports **8345 / 18345**).
- DRK fiat conversion may show “unavailable” until pricing endpoints support DRK.
- Product copy in default `values/strings.xml` has been pointed at DarkFi (single balance, confidential/public receive wording); translated locales may still carry older phrases until refreshed on Crowdin.

## References

- DarkFi tree: [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi) — pinned SHA in **`docs/upstream/darkfi-revision.txt`**
- **[Upstream DarkFi reference](upstream/README.md)** — bump checklist for RPC / config files
- **[Wallet roadmap](wallet-roadmap.md)** — embedded wallet vs hosted coordinator (UniFFI shape)
- **[Packaging darkfid](darkfid-embedded-android.md)** — upstream vs Nighthawk subprocess model, build/CI
- Wallet CLI surface (orientation): `bin/drk/` in that repo (`walletdb`, transfers/RPC helpers)—mirror naming here when JNI lands.
- UniFFI: [mozilla/uniffi-rs](https://github.com/mozilla/uniffi-rs)
