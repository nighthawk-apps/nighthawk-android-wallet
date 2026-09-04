# Third-party sources

## `darkfi/`

Git submodule of [nighthawk24/darkfi](https://github.com/nighthawk24/darkfi)
at the commit in `docs/upstream/darkfi-revision.txt` (F-Droid pin:
`fdroid-arti-0.45` / tag `fdroid-55a81e5c6` — Arti 0.45 + keccak overlay).

## `RandomX/`

Git submodule of [darkrenaissance/RandomX](https://github.com/darkrenaissance/RandomX).
F-Droid cargo-patches DarkFi's Codeberg RandomX git dep onto this path
(Codeberg 429s on their builders) and copies `docs/upstream/RandomX.Cargo.lock`
next to `Cargo.toml` so the scanner has a lockfile.

Init or refresh:

```bash
git submodule update --init --recursive
./scripts/vendor-darkfi.sh
export DARKFI_SRC="$PWD/third_party/darkfi"
```

The vendor script verifies the DarkFi gitlink matches the revision file, then
compiles event_graph zkas proofs.

Used by:

- `rust/darkfi-mobile-ffi` — UniFFI wallet (`drk` path dependency, turso/aegis256)
- `scripts/build-darkirc-android.sh` — embedded DarkIRC binary
