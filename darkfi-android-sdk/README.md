# DarkFi Android SDK

Kotlin bindings for the DarkFi mobile wallet, generated from `rust/darkfi-mobile-ffi` via UniFFI.

## UnifOMR Sync Types

The SDK exposes the following sync-related types:

### `SyncMethod`
```kotlin
enum class SyncMethod {
    UNIF_OMR,       // UnifOMR BFV FHE-based detection (default)
    TRIAL_DECRYPT,  // Trial decryption fallback
    UNKNOWN         // Legacy/unknown
}
```

### `SyncFallbackReason`
```kotlin
enum class SyncFallbackReason {
    NONE,                     // UnifOMR working normally
    SERVER_OMR_UNSUPPORTED,   // Server lacks OMR capabilities
    OMR_DETECTION_FAILED,     // Detection errors, auto-retry scheduled
    MISSING_OMR_CLUES,        // Non-OMR wallet transactions detected
    KEY_POOL_EXPIRED,         // Pool refresh in progress
    KEY_POOL_NOT_REGISTERED,  // Registration pending
    UNKNOWN                   // Transient/unknown issue
}
```

### `DrkLightSyncState`

Now includes:
- `fallbackReason: SyncFallbackReason` — why the wallet is in fallback mode
- `fallbackUserMessage: String` — user-facing explanation (display in status bar or notification banner)
- `omrDowngradeWarning: Boolean` — true when OMR was unexpectedly disabled (security audit S2); UI should show a warning banner
- `omrDowngradeCount: UInt` — per-session count of OMR→non-OMR transitions; >3 indicates possible adversarial toggling
- `reorgDetected: Boolean` — true when a chain reorg was detected and the wallet needs re-scanning (security audit R1)

### `DrkBootstrapConfig`

New fields:
- `useTor: Boolean` — route lightwalletd traffic through Tor
- `torSocksPort: UShort` — SOCKS5 port (default 9150)

## Cross-Wallet Compatibility

When `SyncFallbackReason.MISSING_OMR_CLUES` is active, the UI should display:

> "Some transactions were sent from a wallet that doesn't support UnifOMR. Using trial decryption to find those transactions. For the most private and fastest sync, prefer Nighthawk or Moonshine for all DarkFi transactions."

This message is available in `DrkLightSyncState.fallbackUserMessage`.

## Chain Integrity (Security Audit July 2026)

New sync engine capabilities:
- **Reorg recovery** — `SyncEngine::rewind_to_height()` rolls back scan cursor; deletes post-reorg coins, un-spends rolled-back spends, purges block cache
- **Tip regression detection** — `new_tip < prev_tip` triggers reorg recovery
- **Server switch reset** — `reset_for_server_switch()` clears stale server state (tip hash, OMR counters)
- **Inter-match OMR gap scanning** — Trial-decrypt gaps >100 blocks between consecutive OMR matches
- **OMR downgrade tracking** — `omr_downgrade_warning` surfaced via `DrkLightSyncState` for UI banners
- **Windowed failure decay** — Failure count halved on success instead of zeroed, preventing adversarial gaming

## Key Delivery

The BFV detection key (~37 KB) is **never** embedded in transaction encrypted notes:

| Data | Size | Transport |
|------|------|-----------|
| OMR clue (blake3 tag) | 8 bytes | `RegisterOmrClue` RPC at broadcast |
| BFV detection key | ~37 KB | `GetOmrDigest` RPC at sync time |
| Pool keys | ~37 KB/epoch | `RegisterKeyPool` RPC via Tor |

