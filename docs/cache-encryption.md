# Compact-block cache encryption (P4 follow-up)

Wallet DB uses upstream **turso + experimental aegis256** (keyed from
`wallet_pass` via `blake3`). Compact-block / Merkle side caches under
`cache_path` remain recoverable by full resync and are sandboxed by the OS.

**Status:** tracked. Full at-rest encryption of `compact_blocks.db` should land
after upstream settles an encrypted-cache primitive (see
`docs/upstream/DARKFI_PROPOSALS.md` P4). Until then:

- Keep cache directories inside the app sandbox only
- Wipe cache on wallet wipe / network switch
- Do not store mnemonics or payment secrets in the cache layer
