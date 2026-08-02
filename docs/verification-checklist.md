# Verification checklist (UnifOMR / Nighthawk)

Use against **standalone** `darkfi-lightwalletd` with `fhe-omr`. Tick when proven.

## Protocol / sync

- [x] UnifOMR any-match: multi-clue height matches when own clue is not first (unit test)
- [x] Register clue PK for all wallet payment addresses (moonshine + mobile)
- [x] UnifOMR detection_keys cap raised to 16 (aligned with PerfOMR)
- [x] Matrix: both registered / only sender / only receiver / neither — GetClue→UnifOMR vs decoy (`scripts/e2e_unifomr_matrix.sh`)
- [x] GetUnifOmrDigest decrypt round-trip on live LWD (64MB gRPC / 48MB key limits)
- [x] Clue directory: no registration-bit leak (always `found=true` + fixed-length decoy)
- [x] SealPIR-style striped `FetchPirBatch` (windows > BFV degree; dummy stripe queries)
- [x] RLWE `n=1024` (paper Param2) with negacyclic mul
- [x] Android n=1024 parity (FFI / client crypto aligned with LWD)
- [x] PIR assemble length (length-prefixed SealPIR limbs / CompactBlock reassembly)
- [x] R_PRIME / noise FN (`CLUE_ERROR_BOUND=2`, range-check vs clue noise)
- [x] Clue hint 24h TTL (orphan `SendTransaction` clues)
- [x] GetClue 250ms pad (timing padding on `GetCluePublicKey`)
- [x] Live e2e registration matrix on testnet LWD cache (both / recv_only / send_only / neither + GetUnifOmrDigest)
- [x] iOS/Android UnifOMR any-match unit parity (`test_unifomr_any_match_second_clue`)
- [ ] Receiver with clue PK registered receives via UnifOMR (`GetUnifOmrDigest` + `FetchPirBatch`) funded e2e
- [ ] Sender with registered recipient builds UnifOMR clue and sees tx in history (funded)
- [ ] Sender without optimized path still sees sent tx (PerfOMR / LWD fallback)
- [ ] Receiver without optimized path discovers via trial decrypt
- [ ] Matrix: Android == iOS == Moonshine funded send/receive parity
- [ ] No duplicate notes after fallback
- [ ] No misses after restart, rescan, background/foreground
- [ ] No false-success UI when sync/broadcast failed
- [ ] Reorg fires UI callback / surfaces message; history consistent
- [ ] Tip regression on Moonshine rewinds sync height

## Privacy / security

- [x] Alternate LWD paths work without MoneyNote memo signaling (RPC + `omr_clue` / envelope)
- [x] Clue PK register/get rate-limited on LWD
- [x] Payment memo not logged in clear at INFO (boolean only)
- [x] Instrumented tests do not log seed phrases
- [x] `darkfid_rpc_url` default unset (no hardcoded `:18345`)
- [x] iOS bootstrap network follows `STEALTH_MAINNET` / `STEALTH_TESTNET`
- [x] Android `useTor` follows `routeOutboundThroughTor`
- [x] Android `generateDmKeypair` uses UniFFI
- [x] Reorg callback wired (iOS `SDKSynchronizerLive`, Android `NativeDarkfiSynchronizer`)
- [x] Docs state UnifOMR `0x05`, no pool; UniFFI wallet = tip turso/aegis256 (Moonshine SQLCipher is separate)
- [ ] No PII/keys/addresses/txids in **release** builds (manual log review)
- [ ] Secrets only in Keychain / encrypted DataStore (manual review)
- [x] Documented MVP limits accepted for ship (`doc/unifomr_mvp_limits.md`)

## Build

- [x] UniFFI Kotlin/Swift regenerated after UDL (`darkfid_rpc_url`, `DmKeypair`)
- [x] Rebuild Android `.so` / iOS xcframework after FFI changes before release
- [x] Live registration matrix on testnet LWD (localnet darkfid optional; funded send/receive still open)
