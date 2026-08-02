# DarkFi 0.3 alpha testnet — connection guide

This documents how Nighthawk connects to the **DarkFi alpha testnet** (App **0.3-alpha**, upstream `network = "testnet"`), based on the pinned tree in `third_party/darkfi/` and [The DarkFi Book — Running a Node](https://dark.fi/book/testnet/node.html).

## Architecture (two layers)

| Layer | Daemon | What it does | Default testnet port |
|-------|--------|--------------|----------------------|
| **Chain / P2P** | `darkfid` | Syncs blocks from the network; serves JSON-RPC to wallets | P2P **18340** (clearnet `tcp+tls`), RPC **18345** |
| **Wallet** | `drk` (in APK via UniFFI) | Local turso/aegis256 wallet; scans blocks via `darkfid` JSON-RPC | Connects to **18345** |

Nighthawk embeds **`drk`** via UniFFI and can embed **`darkfid`** (packaged `darkfid_exec`) like upstream expects an external fullnode on loopback **18345**. Without the binary, run `darkfid` elsewhere or use **`adb reverse`** — see below.

Upstream desktop **App 0.3-alpha** runs both in-process: wallet plugin talks to loopback `tcp://127.0.0.1:18345` ([`bin/app/src/plugin/drk.rs`](https://github.com/darkrenaissance/darkfi/blob/master/bin/app/src/plugin/drk.rs)).

## Quick start (desktop reference)

From [dark.fi/book/testnet/node.html](https://dark.fi/book/testnet/node.html):

1. Build: `make darkfid drk` on upstream `master`.
2. First run creates configs under `~/.config/darkfi/` (`darkfid_config.toml`, `drk_config.toml`).
3. Set `network = "testnet"` in both configs (default in vendored templates).
4. Initialize wallet: `./drk wallet initialize`, `./drk wallet keygen`.
5. Run `./darkfid` — syncs via P2P seeds until `Blockchain synced!`.
6. In `drk interactive`: run `subscribe` to scan + listen for new blocks.

## Testnet ports (upstream defaults)

From `bin/darkfid/darkfid_config.toml` and `bin/drk/drk_config.toml` in the pinned revision:

| Service | URL / port |
|---------|------------|
| Wallet → **darkfid JSON-RPC** | `tcp://127.0.0.1:18345` |
| darkfid **management** RPC | `tcp://127.0.0.1:18346` |
| darkfid **Stratum** (mining / xmrig) | `tcp://127.0.0.1:18347` |
| darkfid **P2P** (clearnet) | `tcp+tls://…:18340` |
| darkirc P2P (chat — separate) | `tcp+tls://lilith0.dark.fi:25551`, `lilith1.dark.fi:25551` |

See also [`docs/upstream/alpha-testnet-endpoints.md`](upstream/alpha-testnet-endpoints.md) for seed lists copied from the vendored config.

## P2P seed nodes (darkfid sync)

`darkfid` discovers peers via **lilith** seeds (not wallet RPC):

**Clearnet (`tcp+tls`, port 18340):**

- `tcp+tls://lilith0.dark.fi:18340`
- `tcp+tls://lilith1.dark.fi:18340`

**Tor (port 18341):** onion addresses in `darkfid_config.toml` → `[network_config."testnet".net.profiles."tor"]`.

**Tor+TLS (port 18340):**

- `tor+tls://lilith0.dark.fi:18340`
- `tor+tls://lilith1.dark.fi:18340`

Chat (`darkirc`) uses different seeds on port **25551** — bundled `darkirc_exec` in the APK reads its own config.

## Nighthawk wallet endpoint (Settings → Change server)

The app stores a **`DarkfiEndpoint`**: host + port + optional TLS flag. This must point at a running **`darkfid` JSON-RPC** listener (**18345** on testnet), **not** at lilith P2P seeds.

Built-in presets (`DarkfiEndpointCatalog`):

| Preset | Use when |
|--------|----------|
| **Local darkfid (loopback)** | `darkfid` on the same device (unusual on phone) or **`adb reverse`** (below) |
| **Android emulator → host** | `darkfid` on dev machine; emulator uses `10.0.2.2:18345` |
| **Custom endpoint** | LAN IP of a machine running `darkfid`, or a TLS-wrapped remote RPC |

Default for `darkfitestnet` flavor: `tcp://127.0.0.1:18345` (matches upstream `drk_config.toml`).

### Physical device + darkfid on your laptop

1. On laptop: build/run `darkfid` with testnet config; wait for sync.
2. Expose RPC to the phone (pick one):
   - **adb reverse** (USB debugging):  
     `adb reverse tcp:18345 tcp:18345`  
     Then in the app choose **Local darkfid (loopback)**.
   - **LAN**: set `rpc_listen = "tcp://0.0.0.0:18345"` in `darkfid_config.toml` (test only; prefer TLS for non-local networks per upstream docs).
3. Open Nighthawk → **Settings → Change server** → select or enter host/port → restart connection (home status dot or restart dialog).

### Remote / non-LAN darkfid

Upstream requires **`tcp+tls`** for JSON-RPC when not on the same network ([testnet node guide](https://dark.fi/book/testnet/node.html#miner)) so wallet traffic is not plaintext. Nighthawk’s Rust `drk` client accepts standard `url` schemes; enable TLS in **Change server** when your node exposes TLS-wrapped RPC.

There is **no official public hosted darkfid RPC** in upstream docs — operators run their own node or use a trusted friend’s.

## Tor (optional)

- **Desktop App 0.3-alpha**: create `use_tor.txt` in app data ([release notes](https://github.com/darkrenaissance/darkfi/releases/tag/app-0.3-alpha)).
- **darkfid**: configure `[network_config."testnet".net]` profiles per [Tor guide](https://dark.fi/book/misc/nodes/tor-guide.html).
- **Nighthawk**: Tor for IRC/HTTP is separate from wallet RPC (Phase 4 in [`drk-native-implementation.md`](drk-native-implementation.md)).

## Wallet sync in the APK

After bootstrap, UniFFI starts a background loop mirroring upstream **`subscribe_blocks`** with **20s** retry (`rust/darkfi-mobile-ffi/src/sync.rs`). `DarkfiDaemonLifecycleCoordinator` also nudges `rescanBlockchain()` on the same interval when disconnected.

## Mining (optional)

Not required for sends/receives. Enable Stratum in `darkfid_config.toml`:

```toml
[network_config."testnet".stratum_rpc]
rpc_listen = "tcp://127.0.0.1:18347"
```

Then point `xmrig` at `127.0.0.1:18347` with your wallet address as recipient ([merge-mining guide](https://dark.fi/book/testnet/merge-mining.html)).

## Related docs

- [`darkfi-integration.md`](darkfi-integration.md) — build, RPC parity, Change server UI
- [`drk-native-implementation.md`](drk-native-implementation.md) — UniFFI phases
- [`docs/upstream/darkfi-revision.txt`](upstream/darkfi-revision.txt) — pinned SHA
