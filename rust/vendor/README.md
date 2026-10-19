# Vendored crates

`fjall` is [fjall 3.1.12](https://github.com/fjall-rs/fjall) (MIT OR Apache-2.0) plus one change in `fjall/src/locked_file.rs`.

Rust 1.91, which the F-Droid recipe installs, returns `ErrorKind::Unsupported` from `std::fs::File::try_lock` on Android. Wallet open then failed with `Fjall error: try_lock() not supported`. The patch falls back to `libc::flock`, which Android has supported for years. Newer Rust (1.97+) calls `flock` itself; the fallback is unused there. Upstream dev-dependencies (`nanoid`, `test-log`, `rand`) are omitted so this copy stays a library patch.
