# Security threat model

Nighthawk Android holds wallet seed material, chat crypto keys, and optional PIN/biometric gates. Primary assets:

| Asset | Storage | Threat |
|-------|---------|--------|
| Wallet seed | Encrypted prefs (`PERSISTABLE_DARKFI_WALLET`) | Backup extraction, malware with app UID |
| Wallet PIN | PBKDF2 hash in encrypted prefs | Brute force, plaintext prefs |
| Chat identity / IRC password | `DarkfiChatSecureStore` (AES prefs) | Legacy plaintext migration |
| DM channel secrets | Encrypted prefs + generated `darkirc_config.toml` | On-disk TOML readable to app UID |
| Offline chat queue | AES-GCM file (`darkfi_chat_outgoing_queue.enc`) | Cleartext JSON legacy |
| Deep links (`drk:`) | Intent → Send prefill | Malicious URIs, log leakage |

## Trust boundaries

- **User device:** OS, other apps without root cannot read app-private storage; rooted devices are out of scope for strong guarantees.
- **Network:** Wallet RPC to user-selected endpoints; CoinGecko price API pinned in release builds; optional Tor for HTTP/P2P.
- **Public chat:** DM **public** keys may be posted to `#channels`; users are warned before posting or pasting keys.

## Controls (2025-06)

- PIN: 6-digit numeric, PBKDF2 (100k iter), lockout after 5 failures (30s).
- `SecureScreen()` on PIN, seed/backup, chat settings, E2E, new DM sheet.
- Sensitive clipboard copies auto-clear on background / 60s timeout.
- Debug-only `IntentConfigurationReceiver` (guarded by `FLAG_DEBUGGABLE`).
- Embedded daemon stdout logged only on debuggable builds.
- Deep link parsing: `drk` scheme only, bounded address/memo, no sensitive debug logs.

## Controls (2026-07 — chain integrity & sync privacy)

- **Reorg recovery (R1):** `SyncEngine::rewind_to_height()` rolls back scan cursor; `sync.rs` deletes coins with `creation_height > rollback`, un-spends with `spent_height > rollback`.
- **Tip regression detection (R4):** `update_chain_tip_hash()` triggers reorg on `new_tip < prev_tip`.
- **Block cache invalidation (R3):** `MobileBlockCache::prune_above()` purges cached blocks from orphaned forks.
- **OMR inter-match gap scanning (S1):** Trial-decrypt gaps >100 blocks between consecutive OMR matches, plus leading and trailing gaps.
- **OMR zero-match threshold (S1):** Lowered from 50 to 10 blocks for quicker cross-wallet detection.
- **OMR downgrade tracking (S2):** `omr_downgrade_warning` and `omr_downgrade_count` in `LightSyncState`; per-session tracking; critical log at >3 toggles.
- **Server switch reset (S3/S4):** `reset_for_server_switch()` clears tip hash, OMR counters, catch-up atomics.
- **Windowed OMR failure decay (S5):** `record_omr_success()` halves failure count instead of zeroing, preventing adversarial intermittent-success gaming.

## Residual risks

- `darkirc_config.toml` must remain plaintext for the native `darkirc` binary; file mode restricted to app UID.
- Certificate pinning for CoinGecko may require pin updates when the CDN rotates certs.
- Biometric unlock skips PIN re-entry for the session (by design).
- No `prev_hash` chain validation between consecutive compact blocks (R2 — planned).
- Block cache (`compact_blocks.db`) and sled Merkle cache are unencrypted (defense-in-depth, not yet addressed).
- `chain_name` verification on server switch not yet implemented (S3 partial).
