# Alpha testnet endpoints (vendored snapshot)

Extracted from `third_party/darkfi/` at the revision in [`darkfi-revision.txt`](darkfi-revision.txt).  
Regenerate after `./scripts/vendor-darkfi.sh` if upstream changes seeds or ports.

## darkfid testnet — JSON-RPC (wallet connects here)

| Setting | Value |
|---------|--------|
| General RPC | `tcp://127.0.0.1:18345` |
| Management RPC | `tcp://127.0.0.1:18346` |
| Stratum (mining) | `tcp://127.0.0.1:18347` (optional, uncomment in config) |

Source: `bin/darkfid/darkfid_config.toml` → `[network_config."testnet".rpc]`

## darkfid testnet — P2P seeds (node sync only)

**`tcp+tls` (port 18340):**

```
tcp+tls://lilith0.dark.fi:18340
tcp+tls://lilith1.dark.fi:18340
```

**`tor` (port 18341):**

```
tor://wgxxaifz5gv4iggcflyl67lgmsihffs6bbwobqah4np52t3y3olrnpid.onion:18341
tor://eu7b6sqsxvyfgmufquwr622fbbaqut7qwvpedzlste3b66bv7jvxlpyd.onion:18341
```

**`tor+tls` (port 18340):**

```
tor+tls://lilith0.dark.fi:18340
tor+tls://lilith1.dark.fi:18340
```

Source: `bin/darkfid/darkfid_config.toml` → `[network_config."testnet".net.profiles.*]`

## drk testnet default endpoint

```
endpoint = "tcp://127.0.0.1:18345"
```

Source: `bin/drk/drk_config.toml` → `[network_config."testnet"]`

## darkirc testnet P2P seeds (chat)

**`tcp+tls` (port 25551):**

```
tcp+tls://lilith0.dark.fi:25551
tcp+tls://lilith1.dark.fi:25551
```

Source: `bin/darkirc/darkirc_config.toml`

## Official guides

- [Running a Node (alpha testnet)](https://dark.fi/book/testnet/node.html)
- [Node port reference](https://dark.fi/book/misc/nodes/node-configurations.html)
- [App 0.3-alpha release](https://github.com/darkrenaissance/darkfi/releases/tag/app-0.3-alpha)
