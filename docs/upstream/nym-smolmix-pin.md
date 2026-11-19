# nym-smolmix pin (Android + iOS lockstep)

| Field | Value |
|-------|--------|
| Git | `https://github.com/nymtech/nym.git` |
| Rev | `5f0dba82d7a4c3d32fb21f78a0e3ddb6320b4ae8` |
| Crate | `nym-smolmix` 2.0.0 (develop) |

Both wallets patch this rev in `rust/Cargo.toml` `[patch.crates-io]`. Do not advance one platform without the other. crates.io only publishes up to 1.21.6; the 1.22.2 mixnet tag depends on yanked `tokio_with_wasm`.
