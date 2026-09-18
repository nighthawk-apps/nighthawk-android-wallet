# Compact-block cache encryption

Wallet DB uses upstream **turso + experimental aegis256** (keyed from
`wallet_pass` via `blake3`). Compact-block side caches under `cache_path`
are wrapped at rest with **XChaCha20-Poly1305**.

**Status:** implemented in `darkfi-mobile-ffi` `block_cache.rs`.

- Magic `NHC1` + 24-byte nonce + ciphertext
- Key: `blake3::derive_key("nighthawk compact-block-cache v1", wallet_pass)`
- Open via `MobileBlockCache::open_with_key`; reorg recovery uses the same key
- Wipe cache on wallet wipe / network switch / rewind
- Do not store mnemonics or payment secrets in the cache layer

Plaintext files from older builds are rejected when a key is set (full resync).
