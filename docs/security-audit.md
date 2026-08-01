# Security audit remediation

Status of findings from the in-app security review (June 2025).

## Critical

| ID | Issue | Status |
|----|-------|--------|
| C1 | PIN stored plaintext in standard prefs | **Fixed** — `WalletPinSecureStore` (PBKDF2 + encrypted prefs); legacy migration |
| C2 | DM secrets in cleartext TOML | **Mitigated** — TOML still required by daemon; `AppPrivateFilePermissions` restricts to app UID |
| C3 | Plaintext chat outgoing queue | **Fixed** — `DarkfiChatOutgoingQueueCrypto` (AES-GCM) + legacy migration |

## High

| ID | Issue | Status |
|----|-------|--------|
| H1 | Deep link parsing / logging | **Fixed** — strict `drk` parsing, memo cap, no sensitive logs |
| H2 | RPC / HTTP pinning | **Partial** — CoinGecko API cert pin in `RetrofitHelper`; darkfid RPC is loopback |
| H3 | SecureScreen on sensitive UI | **Fixed** — PIN, chat settings, E2E, new DM |
| H4 | PIN strength | **Accepted** — 6-digit numeric per product; hash + lockout added |
| H5 | Deep-link UX | **Existing** — Send flow validates address via synchronizer |

## Medium

| ID | Issue | Status |
|----|-------|--------|
| M1 | Clipboard hygiene | **Fixed** — `setSensitivePlainText` + clear on background |
| M2 | Debug config receiver exported | **Fixed** — `FLAG_DEBUGGABLE` guard in receiver |
| M4 | Public keys in chat | **Accepted** — warning dialogs before paste/post |
| M6 | Daemon log leakage | **Fixed** — stdout only on debuggable builds |

## UI follow-ups

- **Share pubkey** moved to small button beside **Retry connection**.
- **AlertDialog** before pasting peer public key in new DM sheet.
- Existing dialog retained before posting key to a channel.

## Chain integrity (July 2026)

| ID | Issue | Status |
|----|-------|--------|
| R1 | Reorg detected but no rollback action | **Fixed** — `rewind_to_height()` in `SyncEngine`; `sync.rs` deletes post-reorg coins, un-spends rolled-back spends, resets scan cursor |
| R2 | No `prev_hash` chain validation | **Open** — compact blocks accepted without parent-hash chaining; planned for next release |
| R3 | Block cache not invalidated on reorg | **Fixed** — `MobileBlockCache::prune_above()` called during reorg recovery |
| R4 | Tip regression not detected as reorg | **Fixed** — `update_chain_tip_hash()` now detects `new_tip < prev_tip` |

## Sync privacy (July 2026)

| ID | Issue | Status |
|----|-------|--------|
| S1 | Cross-wallet OMR gaps not fully covered | **Fixed** — inter-match gap scanning + trailing gap; zero-match threshold lowered 50→10 |
| S2 | OMR downgrade warning not surfaced to UI | **Fixed** — `omr_downgrade_warning` + `omr_downgrade_count` in `LightSyncState`; session-level tracking |
| S3 | Server switch has no chain identity check | **Partial** — `reset_for_server_switch()` clears stale state; chain name verification planned |
| S4 | Server switch doesn't reset sync state | **Fixed** — `reset_for_server_switch()` clears tip hash, OMR counters, catch-up atomics |
| S5 | OMR failure counter easily gamed | **Fixed** — windowed decay (halve on success) instead of full reset |

See also [security-threat-model.md](./security-threat-model.md).
