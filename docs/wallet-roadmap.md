# Wallet roadmap: embedded `drk` logic vs remote node

This records an architectural decision for how the Android wallet aligns with upstream **`bin/drk`** (local SQLite **`wallet.db`**) and **`darkfid`** (chain JSON-RPC).

## Decision (working direction)

**Ship wallet semantics on-device via UniFFI (`rust/darkfi-mobile-ffi`), evolving toward the same responsibilities as `drk`**—seed custody, scanning against chain state, balance derivation, transaction construction/signing, and submission—while **`darkfid`** remains the chain/node peer accessed over **`DarkfiEndpoint`** (Tor-aware TCP JSON-RPC today in Kotlin).

**Rationale**

- Matches privacy expectations for a wallet APK (keys and **`wallet.db`**-equivalent state stay on the phone).
- Matches existing repo layout: **`DarkfiSynchronizer`** façade + **`PersistableDarkfiWallet`** wait for Rust behind **`DarkfiWalletHandle`**, not for a mandatory trusted hosted coordinator.
- Keeps **`DarkfidJsonRpc`** focused on node RPC (`tx.broadcast`, subscriptions, etc.) where **`drk`** already talks today.

## Alternate path (explicit non-default)

**Thin client + hosted wallet coordinator** (server holds signing keys or orchestrates multisig / custody) could shrink on-device Rust but introduces jurisdiction, uptime, custody, and traffic-pattern risks inconsistent with how upstream **`drk`** is designed.

If product ever demanded this variant, UniFFI would narrow toward **authenticated transport + minimal UX APIs** instead of exporting **`Drk`**-parity primitives—requiring a deliberate ABI break and threat-model review.

## UniFFI surface implications

| Approach | Typical UniFFI exports |
|----------|-------------------------|
| **Embedded `drk`-style (chosen)** | Wallet init/unlock, sync progress, balances, address books, built txs or blinded intents, **`darkfid`** endpoint configuration, Tor/socket hooks where Rust owns sockets |
| Hosted thin client | Session tokens, limited portfolio reads, server-mediated broadcast—not aligned with current **`DarkfiWalletHandle`** stub |

Until Rust links the **`darkfi`** workspace, **`confirmed_balance_atomic`** and **`primary_deposit_address`** correctly stay **`WalletNotInitialized`**; landing **`Drk`** (or a curated subset of `bin/drk/src/lib.rs` modules: `walletdb`, `money`, `rpc`, `transfer`) behind that boundary is the tracked migration path.

## Operational split (stable)

| Concern | Upstream component | Mobile direction |
|---------|-------------------|------------------|
| Chain state / mempool | **`darkfid`** JSON-RPC | Kotlin **`DarkfidLineJsonRpcCaller`** today; JNI stack must honor SOCKS/VPN policy when moved native |
| Balances / keys / scan | **`drk`** + **`wallet.db`** | **UniFFI + on-device DB** mirroring **`drk`** semantics |

Review **`docs/upstream/darkfi-revision.txt`** whenever **`darkfid`** RPC or **`drk_config.toml`** endpoints change upstream.
