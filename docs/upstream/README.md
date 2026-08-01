# Upstream DarkFi reference

This folder pins the DarkFi Git revision used when auditing Kotlin RPC constants, endpoint defaults, and chat presets against [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi).

There is **no separate “darkfi wallet” repository** — wallet logic lives in the monorepo under **`bin/drk`** (library + CLI). The reference **native wallet UI** is **[`bin/app`](https://github.com/darkrenaissance/darkfi/tree/master/bin/app)** (Rust + miniquad, plugins for **`drk`**, **`DarkIrc`**, **`fud`**). Mobile UniFFI targets **`Drk::new`** in **`bin/drk/src/lib.rs`**, not a reimplementation of the miniquad scene graph.

The authoritative ✅ / ❌ summary lives in **`darkfi-integration.md`** → **Upstream parity status**.

## Files

| File | Purpose |
|------|---------|
| `darkfi-revision.txt` | First line: **40-character Git SHA**. Later lines: `# …` comments only. |

## Bump workflow

1. Pick the upstream commit you audited (`master` or a release tag SHA).
2. Put its full SHA on **line 1** of `darkfi-revision.txt`.
3. Run `./scripts/verify-darkfi-upstream-pin.sh` locally (same check as CI).
4. Run `./scripts/fetch-darkfi-upstream-reference.sh` and compare checked-out paths under `docs/upstream/_scratch/` with expectations:
   - `DarkfidJsonRpc` / `DarkfidJsonRpc.ManagementRoute` (see **`DarkfidJsonRpcUpstreamParityTest`**)
   - `DarkfiEndpoint` ports vs `bin/drk/drk_config.toml`
   - `DarkfiChatDefaults` (`DEFAULT_PUBLIC_CHANNELS`, seeds, `DEFAULT_CHANNEL_TOPICS`) vs `bin/darkirc/darkirc_config.toml` — see **`DarkfiChatDefaultsUpstreamParityTest`**
   - `bin/drk/src/lib.rs` for **`Drk`** / module layout when extending UniFFI

See also **[Wallet roadmap](../wallet-roadmap.md)** for how FFI relates to `drk`-style wallet logic.
