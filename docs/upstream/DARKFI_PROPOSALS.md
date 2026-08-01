# Upstream DarkFi proposals (crypto / wallet)

Nighthawk cannot land these directly in `darkrenaissance/darkfi`. This list is
for upstream discussion / PRs. Local mobile clients keep a `bin/drk` SQLCipher
overlay at vendor pin `ae0339804` + `bin/drk @ c4d1776` until resolved.

## P1 — Wallet storage encryption for mobile

**Problem.** Upstream replaced `drk` rusqlite/SQLCipher with turso + aegis256.
Mobile wallets require SQLCipher (`PRAGMA key`) for existing threat models and
App Store / Play storage expectations. Turso encryption is not SQLCipher-compatible;
existing wallet DBs would not open.

**Recommended fix.** Either:
1. Restore optional `rusqlite` + `sqlcipher` feature on `bin/drk` alongside turso
   (feature-gated backends), or
2. Document a migration path from SQLCipher → turso with explicit re-encrypt
   tooling and a stable KDF, then give clients time to migrate.

**Why.** Without a SQLCipher-compatible path, mobile cannot track upstream `drk`
without breaking encrypted-at-rest wallets.

## P2 — Secret zeroization in wallet key paths

**Problem.** Mnemonic → `SecretKey` derivation and note decryption paths often
leave seed material in `String` / heap buffers without `zeroize`.

**Recommended fix.** Adopt `zeroize` / `ZeroizeOnDrop` on seed buffers in
`bin/drk` key import and money secret loaders; prefer borrowing over cloning
mnemonics across plugin boundaries.

**Why.** Reduces secret lifetime in process memory for all frontends (app, drk CLI).

## P3 — Exclusive wallet mutation locking

**Problem.** Wallet DB mutations (spend marking, coin insert, keygen) can race
under shared locks when sync and send run concurrently.

**Recommended fix.** Document and enforce a single-writer wallet mutex API in
`Drk` (or make `WalletDb` ops explicitly serialized with clear async semantics)
so FFI/GUI layers do not invent divergent locking.

**Why.** Prevents TOCTOU between sync coin discovery and spend marking.

## P4 — Optional compact-block / scan cache encryption

**Problem.** Compact-block caches and Merkle/scan side stores are often plaintext
on disk even when the wallet DB is encrypted.

**Recommended fix.** Provide an optional encrypted sled/sqlite cache keyed from
the wallet passphrase (or a derived subkey), with clear docs that caches are
recoverable by resync if lost.

**Why.** Reduces forensic recovery of sync interest / note ciphertext blobs.

## P5 — Address / payment-key ownership proofs as a reusable primitive

**Problem.** Nighthawk LWD now requires Schnorr ownership proofs for UnifOMR
clue-PK directory registration (`DarkFi-UnifOMR-CluePK-v2`). Similar patterns
will recur for any off-chain directory keyed by payment pubkey.

**Recommended fix.** Export a small SDK helper:
`sign_payment_binding(sk, domain, payload) / verify_payment_binding(pk, …)`
with domain separation constants, rather than each app inventing transcripts.

**Why.** Avoids divergent Schnorr transcripts across ecosystem services.

## P6 — Transaction “hint” commitments for off-chain metadata

**Problem.** UnifOMR clues sit outside the consensus-signed tx bytes, enabling
first-writer races on light servers.

**Recommended fix (long-term).** Consensus-native or tx-committed clue/hint
commitment (e.g. hash of clue bound into a signed ephemeral field or output
metadata), so light servers can reject unbound clues.

**Why.** Removes class of unsigned-hint poisoning without trusting LWD peer bind.

## P7 — Net / RPC hardening already upstream

Recent upstream work (bounded broadcasts, subscriber teardown, host registry
retention, inbound slot leak fix) is valuable. Mobile FFI vendors should track
these once the wallet-DB strategy (P1) is settled — no additional proposal
beyond “keep merging net/rpc hardening”.

---

### Out of scope for upstream (stay Nighthawk-layer)

- UniFFI surface (`darkfi-mobile-ffi`)
- UnifOMR Param2 / LWD gRPC / decoy clue directory
- TLS pin policy for lightwalletd clients
