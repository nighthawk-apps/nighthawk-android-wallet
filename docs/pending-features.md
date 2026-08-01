# Pending features checklist

Short backlog. Full task IDs and iOS parity: **[`implementation-plan.md`](implementation-plan.md)** · **[`app-features.md`](app-features.md)**.

## P0 — Correctness / mainnet readiness

| Item | Status | Notes |
|------|--------|--------|
| Rebuild `libdarkfi_mobile_ffi.so` per ABI | **in progress** | `./scripts/build-darkfi-mobile-ffi-android.sh` |
| Regen UniFFI Kotlin (memo APIs) | todo | After `.so` build |
| `payment_memos` table on existing wallets | **done** | `ensure_payment_memos_table()` |
| Mainnet `threshold = 11` in embedded darkfid | **done** | `DarkfidChainDefaults.confirmationThreshold` |
| Mainnet Tor P2P seeds | **done** | Onion (testnet) + tor+tls lilith over SOCKS (mainnet) |
| Flavor ↔ endpoint guard | **done** | `DarkfiEndpointNetworkGuard` |
| `darkfi-mobile-ffi` Cargo deps | **done** | Host + NDK compile |

## P1 — Wallet UX (high user value)

| Item | Why | Notes |
|------|-----|--------|
| Multi-token balances / send picker | Money contract supports many `TokenId`s | FFI `list_tokens` + portfolio UI (today DRK-only) |
| Rich transaction details | Contract calls invisible | Decode `ContractCall` → labels (Transfer, DAO, Deploy) |
| Recipient resolution on tx list | `getRecipients` returns empty | Map spends/outputs from scan or tx bytes |
| Real sync progress | Stub / partial scan | Wire `scan_blocks` → `processorInfo` / `progress` flows |
| Fee + net value in history | `DrkTransactionRecord` zeros fee/net | Populate from `drk` after broadcast / scan |

## P2 — Smart contracts & advanced Money

| Item | Why | Notes |
|------|-----|--------|
| DAO flows in app | Built-in DAO contract | Propose / vote / exec screens or deep links to `drk` |
| Custom token mint display | `token_mint_v1` | Show minted assets with alias table |
| OTC / swap UI | `swap_v1` uses memo for secrets | Separate from payment memo; do not conflate |
| `output_user_data` / `spend_hook` transfers | `drk::transfer` supports hooks | Power-user / contract-integrated sends |

## P3 — Network & chat polish

| Item | Why | Notes |
|------|-----|--------|
| `replay_mode` chat pref UI | Pref exists, no toggle | `DarkircEmbeddedConfigGenerator` |
| Remote darkfid presets (optional) | Self-hosted nodes | Trusted-host warning + TLS |
| Package `darkfid_exec` like darkirc | Embedded node optional today | Same artifact pipeline as `scripts/build-darkirc-android.sh` |
| Chat E2E + DM queue hardening | Production chat | DM UI shipped (P3-5); queue/REHASH hardening still open |
| DarkIRC 1:1 DM UI | Book flow on device | **done** — Channels/Direct, +, pubkey share (`docs/dm-implementation-plan.md`) |

## P4 — Quality & docs

| Item | Why | Notes |
|------|-----|--------|
| Instrumented send-with-memo test | End-to-end memo | Needs testnet + native lib |
| Refresh `darkfi-integration.md` parity matrix | Doc drift | Embedded darkirc/darkfid status |
| `docs/darkfi-mainnet-android.md` | Operator checklist | Flavor, ports, Tor, seeds |
| Fix `darkfi-mobile-ffi` host `cargo build` deps | `bs58`, `hmac`, etc. missing on bare build | Workspace `Cargo.toml` deps for CI |

## Done recently

- Payment memo: `MoneyNote::memo` on transfer, `payment_memos` table, UniFFI + send UI + tx details decrypt
- Embedded darkirc binaries (arm64 + x86_64), chat Tor in Settings, reconnect guard
- Upstream darkirc runtime prefs (`dags_count`, `fast_mod`)
