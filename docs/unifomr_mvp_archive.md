# UnifOMR honest-MVP archive (fork reference)

**Status:** The running stack uses **paper Table-1 Param2** parameters (see [`unifomr_mvp_limits.md`](./unifomr_mvp_limits.md)). This document is the sole archive of the previous **honest MVP** profile so it can be restored or forked if mobile key size / latency requires it.

Active constants live only in:

- [`src/unifomr.rs`](../src/unifomr.rs) (LWD source of truth)
- Android / iOS `rust/darkfi-mobile-ffi/src/unifomr.rs` (must stay aligned with LWD)

## Why keep an archive doc

Param2 increases BFV degree (`2048 → 4096`) and RLWE modulus, which grows detection keys and gRPC payloads. If a deployment needs the lighter MVP profile again, restore from **this table** rather than inventing a third set.

## Archived MVP constants

| Param | Honest MVP (archived) | Active Param2 |
|-------|----------------------|---------------|
| `CLUE_N` | 1024 | 1024 |
| `CLUE_Q` (= BFV `t`) | 65537 | 1032193 |
| `CLUE_H` | 64 | 80 |
| `CLUE_ERROR_BOUND` (`r`) | 2 | 84 |
| `R_PRIME` (`r′`) | 256 | interim 32768 (paper lists 149 after mod-switch) |
| `ℓ` | 1 | 2 |
| BFV `D` | 2048 | 4096 |
| BFV moduli sizes | `[36, 37]` | `[40, 40, 40]` (noise headroom at D=4096) |

## How to fork / restore MVP

1. In each active `unifomr.rs`, replace Param2 constants with the **Archived MVP** column above and restore MVP `bfv_params()` (`D=2048`, plaintext `65537`, moduli `[36, 37]`).
2. Keep Android + iOS FFI copies aligned with LWD (rebuild moonshine / desktop against LWD + FFI).
3. Lower gRPC / `MAX_DETECTION_KEY_BYTES` if desired (MVP keys were smaller).
4. Keep **all ops hardening** (rate limits, det-key cap 16, TLS pin, decoy directory, clue TTL, fail-closed UnifOMR-only) — those are independent of the parameter profile.
5. Update this doc and `unifomr_mvp_limits.md` to state which profile is active.

## Hardening that must survive any fork

UnifOMR-only fail-closed, detection-key count cap (16), per-peer rate limits, gRPC size ceilings, TLS pin, clue PK decoys + timing pad, clue hint TTL / peer bind, malformed-clue rejection, multi `detection_keys`, supplemental trial on empty OMR.
