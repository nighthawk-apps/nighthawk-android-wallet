# DAO Hub — implementation plan

DarkFi governance via `drk dao` (upstream: `third_party/darkfi/bin/drk/src/dao.rs`). Nighthawk ships a read-first hub, then signing once vote/exec builders are validated on testnet.

## Milestones

### M1 — Read-only (current)

- **FFI:** `list_daos`, `list_proposals(dao_name?)`, `get_proposal(proposal_bulla_b58)` on `DarkfiWalletHandle` (`rust/darkfi-mobile-ffi/src/dao.rs`).
- **SDK:** `DarkfiSynchronizer` + `NativeDarkfiSynchronizer` / `StubDarkfiSynchronizer`.
- **UI:** Transfer tab and Settings → **DAO Hub**; hub → DAO detail → proposal detail. No propose/vote/exec.
- **Native:** Rebuild `libdarkfi_mobile_ffi.so` after FFI changes: `./scripts/build-darkfi-mobile-ffi-android.sh`.

### M2 — Propose (testnet)

- UniFFI: build unsigned propose tx; Android review screen; broadcast via existing transfer path.
- Mirror `drk dao propose` auth-call encoding.

### M3 — Vote / execute (testnet)

- Vote builder + tally display; execute when quorum/time satisfied.
- Hold until M2 flows succeed on testnet.

### M4 — Polish

- Auth-call human labels, block-window countdown, explorer links, error copy aligned with desktop `drk`.

## Navigation routes

| Route | Purpose |
|-------|---------|
| `dao_hub` | List wallet DAOs |
| `dao_detail/{daoName}` | DAO params + proposals |
| `dao_proposal/{proposalBulla}` | Read-only proposal detail |

## UI entry points

1. **Transfer** tab — DAO Hub row (primary).
2. **Settings** — DAO Hub row (secondary).

## Out of scope for M1

- Signing, fee selection for governance txs, mainnet governance without testnet sign-off.

## Related docs

- `docs/implementation-plan.md` — P2-1 DAO read APIs
- `docs/app-features.md` — product feature list
