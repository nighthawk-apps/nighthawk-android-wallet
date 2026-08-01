# UnifOMR Security & Integration Guide

This guide documents the UnifOMR implementation, security mitigations, key delivery architecture, and cross-wallet compatibility for the Nighthawk/Moonshine DarkFi mobile wallets.

---

## 1. Architecture Overview

### UnifOMR (Oblivious Message Retrieval)

The wallet uses **UnifOMR** — a BFV lattice-based FHE scheme (degree=2048, 2 moduli) — for private block scanning. This replaces the previous PerfOMR and FMD implementations.

```
Sender                    Lightwalletd               Receiver (Nighthawk)
──────                    ────────────               ────────────────────
1. Build tx + 8-byte      2. Store clue              3. Derive BFV detection key
   blake3 OMR clue            alongside                 (~37 KB query ciphertext)
   from recipient pk          compact block
                                                     4. Send detection key via
                           5. Homomorphic                GetOmrDigest RPC
                              tag comparison
                              (server-side)
                                                     6. Decrypt digest →
                           7. Return encrypted           matching block heights
                              digest (per-slot flags)
                                                     8. Fetch only matching
                                                        compact blocks
```

### Key Delivery: Detection Key is NOT in Encrypted Notes

**CRITICAL SECURITY PROPERTY**: The BFV detection key (~37 KB) is never embedded in the transaction's encrypted notes field.

| Data | Transport | Size | Purpose |
|------|-----------|------|---------|
| OMR clue (blake3 tag) | `RegisterOmrClue` RPC | 8 bytes | On-chain detection tag |
| BFV detection key | `GetOmrDigest` RPC | ~37 KB | Sync-time FHE query |
| Pool keys | `RegisterKeyPool` RPC | ~37 KB/epoch | Pre-registered via Tor |
| Encrypted note | On-chain (AEAD) | ~128-256 bytes | Standard note payload |

### Pool-Based Key Delivery (Option D+A)

For enhanced privacy, detection keys can be pre-registered via a separate Tor circuit:

1. Client generates BFV keys for future epochs
2. Registers via `RegisterKeyPool` RPC (over Tor)
3. Queries digest via `GetOmrDigestFromPool` — no detection key sent at query time
4. Server looks up key by `pool_id` + epoch

This decouples key registration from digest queries, preventing timing correlation.

---

## 2. Security Mitigations

### 2.1 UnifOMR Key Derivation (replaces FMD/PerfOMR)
- **Previous**: PerfOMR with degree=4096, 3 moduli (~56 KB ciphertexts)
- **Current**: UnifOMR with degree=2048, 2 moduli (~37 KB ciphertexts)
- Key material derived locally from wallet secret via domain-separated blake3
- Raw `omr_secret` never leaves the device

### 2.2 TLS Certificate Pinning
- Custom `PinnedVerifier` validates SHA-256 fingerprint of server leaf certificate
- Prevents MITM observation of block range requests and detection key transmission

### 2.3 Epoch-Based Key Rotation
- UnifOMR epoch size: 5760 blocks (~24h on DarkFi mainnet)
- Each epoch produces a fresh BFV query ciphertext with independent randomness
- Pool-based delivery pre-registers keys for multiple epochs

### 2.4 Randomized FHE Evaluation
- Server-side homomorphic evaluation uses per-slot random scalars
- Non-matching slots are multiplied by random values in plaintext modulus field
- Prevents distance-leaking attacks

### 2.5 Block Range Padding
- OMR requests padded to power-of-two bucket sizes via `pad_block_range()`
- Hides exact wallet birthday and scan position from the server

### 2.6 Trial Decrypt Fallback (Anti-Downgrade)
- Strict retry controls: fallback only after `max_omr_failures = 5` consecutive failures
- `SyncFallbackReason` enum surfaces exact reason to UI
- Exponential backoff before retrying OMR after failures

---

## 3. Cross-Wallet Compatibility

### Shared Seed with Non-OMR Wallets

When the same mnemonic is imported into both Nighthawk/Moonshine (UnifOMR) and the `drk` CLI wallet (no OMR):

1. **Transactions sent from `drk`** will NOT have UnifOMR clues
2. **Lightwalletd cannot detect** those transactions via OMR
3. **Sync engine detects** empty OMR digests for non-trivial ranges (>50 blocks)
4. **Falls back to trial decrypt** for the affected range
5. **Surfaces `MissingOmrClues`** to the UI with recommendation message

### User-Facing Messages

| Reason | UI Message |
|--------|-----------|
| `MissingOmrClues` | "Some transactions were sent from a wallet that doesn't support UnifOMR. Using trial decryption. For the most private and fastest sync, prefer Nighthawk or Moonshine." |
| `ServerOmrUnsupported` | "This server does not support UnifOMR detection. Connect to a UnifOMR-enabled lightwalletd." |
| `OmrDetectionFailed` | "UnifOMR detection failed. Will be retried automatically." |
| `KeyPoolExpired` | "Detection key pool expired. Refreshing automatically." |

---

## 4. Developer Integration

### Build Dependencies

```toml
fhe = "0.1.1"
fhe-traits = "0.1.1"
blake3 = "1"
rand09 = { package = "rand", version = "0.9" }
tokio-rustls = "0.26"
```

### Verification

```bash
# Full test suite (195 tests)
cargo test --package darkfi-mobile-ffi

# Check compilation only
cargo check --package darkfi-mobile-ffi
```

### Key Delivery Audit

The test `test_clue_is_tag_not_fhe_key` in `unifomr.rs` asserts:
- OMR clue is exactly 8 bytes (blake3 tag)
- Detection key is ~37 KB (BFV ciphertext)
- The two are completely different data
