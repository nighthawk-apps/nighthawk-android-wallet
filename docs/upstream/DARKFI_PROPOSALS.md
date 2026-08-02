# Upstream DarkFi proposals (crypto / wallet)

Nighthawk cannot land these directly in `darkrenaissance/darkfi`. This list is
for upstream discussion / PRs.

**Client pin policy (pre-release):** Android / iOS / desktop UniFFI track tip
`bin/drk` (turso + experimental aegis256). There is no SQLCipher overlay.
Moonshine keeps its own SQLCipher pruned DB by design (not `drk`).

## P1 — Wallet storage encryption (resolved for Nighthawk UniFFI)

**Status.** Adopted upstream tip: `WalletDb` uses turso with experimental
`aegis256` and `hexkey = blake3(wallet_pass)`. Mobile/desktop pass the same
`wallet_pass` through `Drk::new`; OS sandbox + encrypted prefs / Keychain /
desktop PIN vault still protect the passphrase.

**Residual upstream asks.**
1. Stabilize turso encryption API (today marked experimental).
2. Document KDF / cipher parameters as a compatibility contract for light clients.
3. Optional: feature-gated SQLCipher backend only if a migration story is needed
   for third-party wallets that already shipped SQLCipher (Nighthawk has not).

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

Track upstream net acceptor/connector and seed-address updates when bumping the
client pin; no Nighthawk fork required.
