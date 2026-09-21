## Verdict

**Android ↔ iOS FFI UnifOMR sources are in lockstep.** All requested key files are byte-identical; `proto/lightwallet.proto` SHA matches LWD. Param2 crypto constants match across LWD + both FFIs. Moonshine stays lockstep on crypto via `darkfi-lightwalletd` path dep. Remaining issues are mostly **stale docs**, **default/privacy policy drift**, and **open funded-e2e gates** — not Android/iOS crypto divergence.

---

### Lockstep status (constants)

| Param | LWD / Android / iOS |
|-------|---------------------|
| Scheme | `0x05` |
| Clue | `n=1024`, `q=1032193`, `h=80`, σ=0.5, `ℓ=2`, max clue `32768` |
| `R_PRIME` | **149** |
| BFV | `D=4096`, moduli `[40,40,40]` |
| Digest | version `0x01`, `MAX_OMR_MESSAGES=262144` |
| PIR | domain `DarkFi-UnifOMR-PirKey-v1`, `MAX_PIR_STRIPES=8`, `MAX_PIR_LIMBS=4096` |
| Det-key budget | **160 MiB** total / per-key (streaming **1 MiB** chunks) |

Proto: identical SHA `7f7df3ab…` across LWD / Android / iOS.

Android↔iOS drift: only `Cargo.toml` (`staticlib` on iOS), `uniffi.toml`, README, and iOS-only `src/bin/*` tooling. **No UnifOMR logic drift.**

---

## Must-fix before 3.00.014

1. **Verification checklist still claims interim `R_PRIME=32768` (false ship status)**  
   - `/Users/adi/GitHub/darkfi-lightwalletd/docs/verification-checklist.md` **L17** — checked item says paper `r′=149` “not claimed” / interim 32768.  
   - Live code: `R_PRIME = 149` in LWD `unifomr.rs` **L118**, FFI `unifomr.rs` **L90**.  
   - Limits doc already correct (`unifomr_mvp_limits.md` **L108**). Checklist contradicts ship reality.

2. **Moonshine limits doc still lists pre-Param2 size budgets (ops risk)**  
   - `/Users/adi/GitHub/moonshine/docs/unifomr_mvp_limits.md` **L107**: “64 MB … per-key cap 48 MB … ~38 MB”.  
   - Live: LWD `server.rs` **L56–60** / clients `MAX_GRPC_MESSAGE_BYTES = 160 MiB`, keys **~120 MiB**.  
   - LWD’s copy of the same doc is updated; moonshine’s is not. Wrong nginx/`client_max_body_size` / gRPC limits will reject real keys.

3. **`key_version` width documented as `u32`, implemented as `u64`**  
   - Doc: `darkfi-lightwalletd/docs/unifomr_mvp_limits.md` **L45** (`u32 LE`).  
   - Code: `clue_ownership.rs` **L63–75**, FFI `unifomr.rs` **L1025–1037**, cache `v3` storage `cache.rs` **L381–453** — all **u64 LE**.  
   - Clients are consistent with each other; docs can mislead a third implementer.

4. **Bootstrap / product defaults vs privacy claims for Tor + strict OMR**  
   - FFI `SyncEngine` defaults `strict_omr_only = true` (`lightwallet_sync.rs` **L254–256**).  
   - Bootstrap overwrites with `config.strict_omr_only` (`lib.rs` **L712**); sample/default config is **`false`** (`lib.rs` **L1264**).  
   - With `strict_omr_only=false`, every UnifOMR cycle also **trial-decrypts the full padded window** (`sync.rs` **L904–922**) — bandwidth + LWD sees full-window fetch (by design for traffic uniformity / `drk` recovery).  
   - Docs claim Tor default ON (`unifomr_mvp_limits.md` parity table); FFI sample `use_tor: false` (`lib.rs` **L1261**); moonshine `default_use_tor() → true` (`config.rs` **L46–47**) but `Config::default()` hardcodes `use_tor: false` (**L57**).  
   - **Release gate:** confirm UI prefs actually pass the intended defaults; do not ship claiming “Tor on / UnifOMR-only” if bootstrap leaves both false.

5. **Funded e2e checklist still open (process gate)**  
   - LWD `verification-checklist.md` **L23–32** (receiver via digest+PIR, sender clue, fail-closed, Android==iOS==Moonshine parity, reorg UI, etc.).  
   - Crypto lockstep ≠ proven end-to-end receive path.

---

## Should-fix soon

6. **FFI README / `omr.rs` / `lib.rs` still describe MVP-era crypto**  
   - README: `GetOmrDigest`, “~37 KB”, “Pool keys” — Android & iOS README **L15–37**.  
   - `omr.rs` **L125–135**: `degree=2048`, `~37 KB`.  
   - `lib.rs` **L253–255**: `degree=2048`.  
   - Live: Param2 `D=4096`, det-key **~120 MiB**, RPC is `GetUnifOmrDigest` stream.

7. **Stale test comment claiming mod-switch / `R_PRIME=32768` not wired**  
   - FFI `transactions.rs` **L718–720** (both platforms). Assertion uses live `R_PRIME` (149); comment is wrong and conflicts with LWD limits SHIP state.

8. **LWD `server.rs` contradictory size comments**  
   - **L56** “~38MB” vs **L57** “~120 MiB” — same block. Prefer one.

9. **Moonshine dead gap-scan path**  
   - `compute_supplemental_heights` (`sync.rs` **L1765–1791**, gap **>10**) marked `dead_code`; live path is full-window TD when `!strict_omr` (**L281–306**). Remove or re-wire to avoid future accidental reintroduction of match-set-shaped sparse fetches.

10. **Nighthawk `!strict` does PIR *and* full-window TD** (`sync.rs` **L889–922**)  
    - Moonshine `!strict` skips PIR and only TD’s the window (**L283–306**). Same privacy goal, worse mobile cost on Nighthawk. Align or document.

11. **Client self-throttle vs server**  
    - Client `OMR_RATE_LIMIT_PER_MIN = 6` (`lightwallet_client.rs` **L525**).  
    - Server default `omr_rate_limit_per_min = 30` (`config.rs` **L147–148**).  
    - Under Tor + 1800s FHE, clients may self-limit harder than the server — fine for privacy, check catch-up UX.

12. **`range_check_matches` API shape drift (non-functional)**  
    - LWD: `-> Vec<u32>` (`unifomr.rs` **L753**).  
    - FFI: `-> Result<Vec<u32>, OmrError>` (**L685**).  
    - Moonshine correctly uses LWD’s `Vec` return. Sync algorithm matches; keep copies from diverging further.

13. **Envelope helpers: client has `wrap_envelope`, LWD parse-only**  
    - Intentional (server strips). Ensure LWD `strip_envelope` fail-closed path stays aligned with client `O2` (`omr_envelope.rs`).

---

## Intentional / non-issues

- **Android ≡ iOS** for `unifomr.rs`, `omr.rs`, `lightwallet_client.rs`, `lightwallet_sync.rs`, `sync.rs`, `sync_pipeline.rs`, `omr_envelope.rs`, `batch_pir.rs`, `memo.rs`.  
- **Proto lockstep** with `lightwalletd/proto/lightwallet.proto`.  
- **Param2 numeric lockstep** LWD ↔ both FFIs; Moonshine via `darkfi-lightwalletd` crate.  
- **Server-only decoys** (`decoy_clue_public_key`, `UNIFOMR_CLUE_DIR_DECOY`) live in LWD; clients verify ownership/attestation — correct split.  
- **GetClue timing pad 250 ms** on server (`server.rs` **L1609**); client `MIN_RESPONSE_TIME=100` is for unused local helpers — not a wire mismatch.  
- **`MAX_OMR_WINDOW=4096`** / moonshine `MAX_BLOCKS_PER_REQUEST=4096` with tip-clamped power-of-2 padding (min bucket 1024) — aligned.  
- **`complete=false` clamp** implemented on both FFI (`sync.rs` **L826–847**) and moonshine (`sync.rs` **L1359–1384**).  
- **TLS pin required for remote HTTPS** (`lib.rs` **L589–605**); secrets not logged at INFO in reviewed sync paths.  
- **Reorg rewind** exists (`lib.rs` **L1050+**, `sync.rs` **L404–456**); checklist still needs funded proof.  
- **Mesh allowlist rejects `GetUnifOmrDigest`** — intentional offline-ctrl boundary.

---

## Flows (brief)

| Flow | Status |
|------|--------|
| `RegisterCluePublicKey` (v2 ownership, all payment addrs) | Aligned FFI ↔ LWD |
| `GetCluePublicKey` + directory attest / decoy | Server; clients verify |
| `GetUnifOmrDigest` stream 1 MiB chunks | Aligned; budget 160 MiB |
| `FetchPirBatch` SealPIR stripes | Constants lockstep |
| Fallback | `strict_omr_only` / moonshine `strict_omr`: no TD; else full padded window (not empty-only) |
| `MissingOmrClues` | UI mapping when trial-decrypt while OMR “available” (`lib.rs` **L396–401**) |

---

**Bottom line for 3.00.014:** Crypto/wire between Android and iOS is synced; do not block on FFI drift. Block on (1) correcting ship docs that still say `R_PRIME=32768` / 64 MiB budgets / 37 KB keys, (2) confirming product defaults for Tor + `strict_omr_only`, and (3) closing the open funded UnifOMR send/receive checklist items.