# Third-party sources

## `darkfi/`

Upstream DarkFi is [git.dark.fi/darkrenaissance/darkfi](https://git.dark.fi/darkrenaissance/darkfi),
not the GitHub mirror. This submodule is [nighthawk24/darkfi](https://github.com/nighthawk24/darkfi)
at the commit in `docs/upstream/darkfi-revision.txt` (`nighthawk-testnet`:
latest dark.fi master merged in, Arti kept at 0.45),
because that Nighthawk pin is not published on git.dark.fi.

## `RandomX/`

Git submodule of [darkrenaissance/RandomX](https://codeberg.org/darkrenaissance/RandomX)
at `0e31d5a23983164a171276dea7bea94dc58ca8ed`. DarkFi's `Cargo.toml` fetches that
commit from Codeberg. F-Droid path-patches that Codeberg URL onto this checkout
(Codeberg 429s on their builders) and copies `docs/upstream/RandomX.Cargo.lock`
next to `Cargo.toml` so the scanner has a lockfile.

`git.dark.fi` returns HTTP 504. The other two git deps are mirrored as well:
`kvdb-overlay` `accd2689` from `https://gitlab.com/nighthawk24/kvdb-overlay`, and
`rust-url` branch `v258` from `https://github.com/darkrenaissance/rust-url`.

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
