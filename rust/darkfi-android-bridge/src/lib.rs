//! Thin Rust entrypoint for DarkFi Android JNI/UniFFI.
//!
//! DarkIRC + Arti/Tor live in the upstream `darkrenaissance/darkfi` workspace (`bin/darkirc`, `darkfi` crate `p2p-tor`).
//! Link those crates here via `cargo-ndk`, export a C ABI (`start_darkirc`, …), then call from Kotlin — until then the
//! chat UI uses the Kotlin IRC client documented in `docs/android-darkirc-chat.md`.

/// Sanity check callable from native tests or future JNI bindings.
#[must_use]
pub fn darkfi_bridge_ping() -> &'static str {
    "pong"
}
