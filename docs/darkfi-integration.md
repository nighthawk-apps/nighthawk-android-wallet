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
│  - Chat: UniFFI `start_darkirc` + callback; optional legacy **darkirc_exec**; SOCKS tor-android; wallet HTTP shares prefs │
│  - Mesh: JNA C ABI (`nh_mesh_*`) — encrypted EventGraph hop only
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

`DarkfiChatController` starts the **in-process** daemon via UniFFI (`start_darkirc`) and receives messages on `DarkircEventCallback.onMessage`. That is the Chat tab path (same as iOS).

Optional `DarkfiChatPreferences.runEmbeddedDarkirc` can still start packaged **`darkirc_exec`** in a foreground service for a **legacy IRC** listener. Do not treat Kotlin `DarkircIrcClient` as the default EventGraph transport. Nearby BLE is [Nighthawk Mesh](nighthawk-mesh.md).

| Topic | DarkFi source | This repo (today) |
|-------|----------------|-------------------|
| Clearnet vs Tor seeds | `darkirc` + `darkfi` net settings (`bin/darkirc`) | Native daemon + `DarkfiChatDefaults` / Tor SOCKS |
| Channel presets | **`darkirc_config.toml`** **`autojoin`** | `DEFAULT_PUBLIC_CHANNELS` |
| Tor toggle | `use_tor.txt` semantics | `DarkfiChatPreferences.useTorForChat` / `routeOutboundThroughTor` |
| EventGraph | `EventGraph` in `bin/darkirc` | In-process `darkirc_daemon.rs` |
| Nearby hop | (desktop p2p only) | BLE mesh C ABI — EventGraph only |
| Legacy IRC wire | `IrcServer` / `Client` | Optional `darkirc_exec` + `DarkircIrcClient` |

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

- UniFFI **0.32** `cdylib` named **`darkfi_mobile_ffi`** (`uniffi.toml` sets the Kotlin package to `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi`).
- Generated bindings live beside other SDK sources (`darkfi-android-sdk/src/main/java/.../darkfi_mobile_ffi/darkfi_mobile_ffi.kt`); **prefer** calling through **`DarkfiMobileFfiApi`** so the FFI surface stays swappable.
- Linked against vendored `third_party/darkfi` (`Drk`, in-process darkirc, UnifOMR). Mesh neighbor APIs are **C ABI** (`nh_mesh_*`), not UniFFI — rebuild with `SKIP_UNIFFI_BINDGEN=1` after mesh-only changes.

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
| **`drk`‑level wallet operations** | **Aligned** | `DarkfiWalletHandle` + `NativeDarkfiSynchronizer` (stub only if `.so` missing) |
| Embedded upstream **`darkirc`** (P2P / DAG) | **Aligned** | In-process UniFFI; optional `darkirc_exec` is legacy IRC |
| **Nighthawk Mesh** | **EventGraph hop** | Encrypted BLE; LWD/SoftAP **off** — [nighthawk-mesh.md](nighthawk-mesh.md) |

**Summary:** Chain‑facing **constants and presets track the pinned upstream tree**. **`drk`** wallet ops and in-process **darkirc** run behind UniFFI. Optional `darkirc_exec` is not the Chat tab path.

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

- Native **`drk`** sync and broadcast require a rebuilt `libdarkfi_mobile_ffi.so` per ABI. Without it, `StubDarkfiSynchronizer` disables transfer.
- Chat default is in-process UniFFI EventGraph. Legacy IRC (`darkirc_exec` / desktop reverse-port) is documented in **[DarkIRC chat on Android](android-darkirc-chat.md)**.
- Mesh does not share internet and does not invent chat when DarkIRC is stopped.
- DRK fiat conversion may show “unavailable” until pricing endpoints support DRK.
- Product copy in default `values/strings.xml` has been pointed at DarkFi (single balance, confidential/public receive wording); translated locales may still carry older phrases until refreshed on Crowdin.

## References

- DarkFi tree: [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi) — pinned SHA in **`docs/upstream/darkfi-revision.txt`**
- **[Upstream DarkFi reference](upstream/README.md)** — bump checklist for RPC / config files
- **[Wallet roadmap](wallet-roadmap.md)** — embedded wallet vs hosted coordinator (UniFFI shape)
- **[Packaging darkfid](darkfid-embedded-android.md)** — upstream vs Nighthawk subprocess model, build/CI
- Wallet CLI surface (orientation): `bin/drk/` in that repo (`walletdb`, transfers/RPC helpers)—mirror naming here when JNI lands.
- UniFFI: [mozilla/uniffi-rs](https://github.com/mozilla/uniffi-rs)
