# Pending features checklist

Short backlog. Full task IDs: **[`implementation-plan.md`](implementation-plan.md)** · **[`app-features.md`](app-features.md)**. Chat is in-process UniFFI on Android and iOS. Mesh: **[`nighthawk-mesh.md`](nighthawk-mesh.md)**.

## P0 — Correctness / mainnet readiness

| Item | Status | Notes |
|------|--------|--------|
| Rebuild `libdarkfi_mobile_ffi.so` after mesh C ABI | **done (arm64-v8a)** | `SKIP_UNIFFI_BINDGEN=1`; other ABIs still previous build until disk allows |
| `payment_memos` table on existing wallets | **done** | `ensure_payment_memos_table()` |
| Mainnet `threshold = 11` in embedded darkfid | **done** | `DarkfidChainDefaults.confirmationThreshold` |
| Mainnet Tor P2P seeds | **done** | Onion (testnet) + tor+tls lilith over SOCKS (mainnet) |
| Flavor ↔ endpoint guard | **done** | `DarkfiEndpointNetworkGuard` |
| `darkfi-mobile-ffi` Cargo deps | **done** | UniFFI **0.32**; host + NDK |

## P1 — Wallet UX

Shipped: multi-token balances/picker, contract labels, recipients, sync snapshot progress, fee + net value. Remaining polish is in P2/P4 (DAO signing, explorer links).

## P2 — Smart contracts & advanced Money

| Item | Why | Notes |
|------|-----|--------|
| DAO flows in app | Built-in DAO contract | Read-only hub shipped; signing after testnet |
| Custom token mint display | `token_mint_v1` | Alias table |
| OTC / swap UI | `swap_v1` | Separate from payment memo |
| `output_user_data` / `spend_hook` | `drk::transfer` | Power-user |

## P3 — Network & chat polish

| Item | Why | Notes |
|------|-----|--------|
| Encrypted EventGraph mesh hop | Nearby DAG when P2P is down | **done this pass** (BLE Noise; share-internet **off**) |
| Mesh LWD / SoftAP / bulk | Out of scope | Do not re-enable without a new product pass |
| `replay_mode` chat pref UI | Pref exists, no toggle | |
| Remote darkfid presets | Self-hosted nodes | Trusted-host warning + TLS |
| Chat E2E + DM queue hardening | Production chat | DMs shipped; queue/REHASH still open |
| DarkIRC 1:1 DM UI | Book flow on device | **done** |

## P4 — Quality & docs

| Item | Why | Notes |
|------|-----|--------|
| Instrumented send-with-memo test | End-to-end memo | Needs testnet + native lib |
| Rebuild iOS xcframework after mesh ABI | **done** | device + sim; `SKIP_UNIFFI_BINDGEN=1` |
| CI job: FFI script | Optional workflow | Documented in mainnet guide |

## Done recently

- In-process UniFFI DarkIRC (Android + iOS); DMs via `chacha_encrypt_dm` / `generate_dm_keypair`
- Nighthawk Mesh: neighbor Noise, EventPut/DagSync, no public DAG/announce/LWD on air
- Payment memo: `MoneyNote::memo` on transfer, `payment_memos` table, UniFFI + send UI
- Optional `darkirc_exec` remains packaged (legacy IRC), not the Chat tab path
