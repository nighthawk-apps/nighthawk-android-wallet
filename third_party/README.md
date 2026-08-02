# Third-party sources

## `darkfi/`

Vendored [darkrenaissance/darkfi](https://github.com/darkrenaissance/darkfi) at the
commit in `docs/upstream/darkfi-revision.txt` (full tip SHA, no `bin/drk` overlay).

Fetch or refresh:

```bash
./scripts/vendor-darkfi.sh
export DARKFI_SRC="$PWD/third_party/darkfi"
```

The vendor script **hard-resets** the tree to the pin (drops local overlays).

Used by:

- `rust/darkfi-mobile-ffi` — UniFFI wallet (`drk` path dependency, turso/aegis256)
- `scripts/build-darkirc-android.sh` — embedded DarkIRC binary

The full tree is **not** committed; run the vendor script after clone.
