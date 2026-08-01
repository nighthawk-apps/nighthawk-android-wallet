# UnifOMR limits (darkfi-lightwalletd)

**Active crypto profile:** paper Table-1 **Param2** (ePrint 2026/910), fully wired:
discrete Gaussian σ=0.5 errors, digest modulus-switch, all ℓ=2 plaintext bits
evaluated, `r′ = 149`.

**Archived MVP profile:** see [`unifomr_mvp_archive.md`](./unifomr_mvp_archive.md) (fork reference; constants are not kept in source).

## Measured detection rates (this stack, 2026-07-22)

Measured by `unifomr::tests::measure_fp_fn_rates`
(`cargo test --release measure_fp_fn -- --ignored --nocapture`):

| Metric | Analytic | Measured |
|--------|----------|----------|
| Per-coefficient FP rate `(2r′+1)/q` | 2.897e-4 | 2.441e-4 (16 / 65 536 coeffs) |
| `ε_p` (ℓ=2 AND) `((2r′+1)/q)²` | 8.39e-8 ≈ **2⁻²³·⁵** | 0 / 32 768 heights |
| `ε_n` (pertinent missed) | ≈ erfc(19.2) ≈ 2⁻⁵³⁰ per bit | 0 / 4 096 (all detected) |

Pertinent digest noise per coefficient is `e·u + e₁ − e₂·s` with all error terms
discrete Gaussian σ=0.5, `‖u‖₀ = h/2 = 40`, `‖s‖₀ = h = 80` ⇒ σ_total ≈ 5.5, so
`r′ = 149 ≈ 27σ`.

## Soundness / hardening changes (2026-07)

- **Gaussian σ=0.5 errors** — RLWE clue errors (`from_secret`, `encrypt_zeros`)
  are sampled from a CDT discrete Gaussian (σ=0.5, tail cut ±4), replacing the
  earlier uniform `[-84, 84]` sampling that inflated digest noise and forced the
  interim `R_PRIME = 32768` (per-coeff FP ≈ 6.3%).
- **Digest modulus-switch** — every digest layer CT is switched to the last BFV
  level before serialization (Q ≈ 2¹²⁰ → single 40-bit modulus). Plaintext slots
  are invariant; mod-switch rounding noise (≈ 2¹¹) is far below the last-level
  budget (≈ 2¹⁹). Digest CTs shrink ~3× (verified exact in
  `test_digest_mod_switch_preserves_plaintext`).
- **All ℓ=2 bits evaluated** — the detector emits `ℓ` CTs per clue layer
  (negacyclic coefficients 0 and 1 of the partial decrypt); the client requires
  **all ℓ** coefficients in range (AND) per layer, OR across layers. Digest wire:
  `[u32 chunk_count] ( [u32 layer_count] ( [u32 len][ct] × ℓ ) × layer_count )…`
- **Ownership-proof replay fix (v2)** — `RegisterCluePublicKey` proofs sign
  `b"DarkFi-UnifOMR-CluePK-v2" || network_byte || key_version (u32 LE) ||
  payment_pubkey || clue_public_key`. The network byte kills cross-network
  replay; `key_version` (unix seconds) is monotonic — the server only replaces
  a registration when the new version is strictly greater (signed rotation),
  and rejects lower/equal-version conflicts.
- **Layer-overflow fail-open** — heights with more than `MAX_CLUE_LAYERS = 64`
  valid clues are no longer silently truncated (censorship vector). The whole
  height is forced to match for **every** detection key (client fetches it and
  trial-decrypts), so an attacker stuffing a height cannot suppress detection.

## Ops hardening (retained across Param2 — do not remove)

These are independent of Table-1 parameters:

- UnifOMR-only / no silent PerfOMR fallback
- Detection-key count cap (**16**)
- Per-peer OMR / clue rate limits
- Hard gRPC / detection-key **size ceilings** (raised for Param2 keys; still enforced)
- TLS pin / remote HTTPS fail-closed on clients
- Clue PK directory decoys + ≥250 ms timing pad
- Clue hint TTL (**24h**) + SendTransaction peer bind
- Malformed-clue rejection (LWEmongrass)
- Multi `detection_keys` + framed multi-digest
- Supplemental trial decrypt on empty OMR (clients)
- MoneyNote memo on wire + sparse sync / tip notify

## Protocol deviations still in place (not Param2 table params)

1. **Any-match multi-clue** — per-clue BFV layers + client OR (not homomorphic product). Semantically equivalent; avoids CT×CT noise blow-up.
2. **SealPIR-style striped PIR** — BFV stripes, length-prefixed limbs; windows up to `8 × D` (`D=4096` under Param2). Not a full SealPIR Galois expander.
3. **Clue PK directory** — always `found=true` + decoy PK + timing pad; unregistered receivers use supplemental trial decrypt.
4. **Digest mod-switch is BFV-level** — the paper sketches switching the clue modulus `Q→Q′=q`; here the digest is a BFV ciphertext whose *plaintext* already lives mod `q`, so the equivalent size/noise reduction is realized by switching the BFV ciphertext to its last RNS level.

## Active Param2 structural parameters

| Param | Value |
|-------|--------|
| `n` (`CLUE_N`) | 1024 |
| `q` (`CLUE_Q` = BFV `t`) | 1032193 |
| `h` (`CLUE_H`) | 80 |
| `r` (`CLUE_ERROR_BOUND`, whp tail bound) | 84 |
| error distribution (`CLUE_ERROR_SIGMA`) | discrete Gaussian σ=0.5 |
| `ℓ` (`CLUE_PLAINTEXT_BITS`, all evaluated) | 2 |
| BFV `D` | 4096 |
| BFV moduli sizes | `[40, 40, 40]` (digest served at last level) |
| `R_PRIME` (`r′`) | **149** (paper value, active) |

Detection keys are larger than MVP (~2× BFV degree); gRPC decode/encode limits are **64 MB** (matching `MAX_DETECTION_KEYS_TOTAL_BYTES`; per-key cap 48 MB). Detection-key count remains capped at **16**, but a single Param2 det-key is ~38 MB, so clients chunk `GetUnifOmrDigest` requests to stay under the 64 MB total budget.

## Cross-client parity (required)

| Component | LWD | Moonshine | iOS FFI | Android FFI | Desktop |
|-----------|-----|-----------|---------|-------------|---------|
| Param2 `unifomr` constants (σ=0.5, r′=149, ℓ=2, mod-switch) | ✓ | via LWD | ✓ (synced copy) | ✓ (synced copy) | via Android FFI |
| Ownership proof v2 (network + key_version) | ✓ | ✓ | ✓ | ✓ | via FFI |
| Length-prefixed PIR limbs | ✓ | via LWD | ✓ | ✓ | via FFI |
| Ops hardening above | ✓ | ✓ | ✓ | ✓ | ✓ (TLS pin via prefs) |
| Supplemental trial on empty OMR | — | ✓ | ✓ | ✓ | via FFI |
| PIR-failure fallback = full padded window (never sparse match-set fetch) | n/a | ✓ | ✓ | ✓ | via FFI |
| Power-of-2 padded digest windows, tip-clamped (min bucket 1024) | n/a | ✓ | ✓ | ✓ | via FFI |
| Tor (arti) routing for remote LWD traffic, default ON | n/a | ✓ (embedded arti) | socks5 route via bootstrap `use_tor` | socks5 route via bootstrap `use_tor` | ✓ (`use_tor` pref, default true) |

Malformed UnifOMR clues are rejected at validation; clients fall back to trial decrypt over the window when OMR returns no matches (including decoy-directory / unregistered receivers).

**Round-2 privacy invariant:** the server may learn the padded digest window,
but never which heights matched. Matched blocks are fetched via batch PIR; on
any PIR failure clients stream the **entire padded window** (`GetBlockRange`)
instead of issuing a per-height fetch of the match set. The sparse
`GetCompactBlocksAtHeights` path is reserved for supplemental/gap trial
decrypt ranges that are not the OMR match set.

**Wire compatibility note:** the ℓ-bit digest layout, v2 ownership proofs, and
the `key_version` proto field are lockstep changes — server and clients must be
deployed from the same revision (pre-adversarial-deployment; no live wire
compatibility is claimed with earlier dev builds).

## TLS for funded / remote e2e

See [`TLS_PINNING.md`](./TLS_PINNING.md) and:

```bash
./scripts/generate_tls_cert.sh self-signed --domain studio.local
# or: ./scripts/generate_tls_cert.sh letsencrypt --domain lw.example.com --email ops@example.com
```

Distribute `scripts/certs/LIGHTWALLET_TLS_PIN_SHA256.txt` to every client before HTTPS e2e.
