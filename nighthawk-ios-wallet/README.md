# Nighthawk iOS Wallet — Sync Parity Notes

## UnifOMR Support

The iOS wallet shares the same Rust FFI crate (`darkfi-mobile-ffi`) as the Android wallet, providing identical sync behavior:

- **UnifOMR** (default): BFV FHE-based private block scanning via `GetOmrDigest` RPC
- **Trial Decrypt** (fallback): Full compact block download when OMR is unavailable
- **Pool-Based Delivery** (planned): Pre-register detection keys via Tor for enhanced privacy

## Cross-Wallet Fallback

When the same seed is used on both iOS and a non-OMR wallet (e.g. `drk` CLI), transactions from the non-OMR wallet won't have UnifOMR clues. The sync engine:

1. Detects empty OMR digests for non-trivial block ranges
2. Falls back to trial decryption for those blocks
3. Surfaces `SyncFallbackReason::MissingOmrClues` to the UI
4. Displays a recommendation to use Nighthawk or Moonshine

## Key Delivery Architecture

The BFV detection key (~37 KB) is **never** sent via the encrypted notes field:

- **8-byte blake3 tag**: Embedded in transactions via `RegisterOmrClue` RPC
- **BFV detection key**: Sent only via `GetOmrDigest` RPC at sync time
- **Pool keys**: Pre-registered via `RegisterKeyPool` RPC (Tor-preferred)

## FFI Types

The following UniFFI types are available in Swift:

- `SyncMethod` — `UnifOmr`, `TrialDecrypt`, `Unknown`
- `SyncFallbackReason` — `None`, `ServerOmrUnsupported`, `OmrDetectionFailed`, `MissingOmrClues`, `KeyPoolExpired`, `KeyPoolNotRegistered`, `Unknown`
- `DrkLightSyncState` — includes `fallbackReason`, `fallbackUserMessage`, `omrDowngradeWarning`, `omrDowngradeCount`, and `reorgDetected` fields
- `DrkBootstrapConfig` — includes `useTor` and `torSocksPort` fields

## Chain Integrity (Security Audit July 2026)

The shared Rust FFI crate now includes:
- **Reorg recovery** — `SyncEngine::rewind_to_height()` rolls back scan cursor; deletes post-reorg coins, un-spends rolled-back spends, purges block cache via `MobileBlockCache::prune_above()`
- **Tip regression detection** — `new_tip < prev_tip` triggers reorg recovery
- **Server switch reset** — `reset_for_server_switch()` clears stale server state (tip hash, OMR counters)
- **Inter-match OMR gap scanning** — Trial-decrypt gaps >100 blocks between consecutive OMR matches (leading, inter-match, trailing)
- **OMR downgrade tracking** — `omr_downgrade_warning` surfaced via `DrkLightSyncState` for UI banners; per-session toggle count
- **Windowed failure decay** — Failure count halved on success instead of zeroed, preventing adversarial gaming
