//! UniFFI panic fence: convert Rust panics at chat / mesh / Arti boundaries
//! into fallible errors so a daemon thread cannot abort the wallet process.
//!
//! The default panic hook still logs location (never payload). `catch_unwind`
//! only helps when the crate is compiled with `panic = unwind` (the default).

use crate::DarkfiWalletNativeError;

pub fn catch_wallet<T, F>(ctx: &'static str, f: F) -> Result<T, DarkfiWalletNativeError>
where
    F: FnOnce() -> Result<T, DarkfiWalletNativeError>,
{
    match std::panic::catch_unwind(std::panic::AssertUnwindSafe(f)) {
        Ok(result) => result,
        Err(_) => {
            tracing::error!(target: "darkfi-mobile-ffi", "isolated panic in {ctx}");
            Err(DarkfiWalletNativeError::NativeDrkUnavailable(format!(
                "{ctx} failed"
            )))
        }
    }
}

pub fn catch_string<T, F>(ctx: &'static str, f: F) -> Result<T, String>
where
    F: FnOnce() -> Result<T, String>,
{
    match std::panic::catch_unwind(std::panic::AssertUnwindSafe(f)) {
        Ok(result) => result,
        Err(_) => {
            tracing::error!(target: "darkfi-mobile-ffi", "isolated panic in {ctx}");
            Err(format!("{ctx} failed"))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn catch_wallet_maps_panic_to_error() {
        let err = catch_wallet::<(), _>("test_op", || panic!("unit-test panic")).unwrap_err();
        match err {
            DarkfiWalletNativeError::NativeDrkUnavailable(msg) => {
                assert_eq!(msg, "test_op failed");
                assert!(!msg.contains("unit-test"));
            }
            other => panic!("unexpected {other:?}"),
        }
    }

    #[test]
    fn catch_wallet_forwards_ok() {
        let ok = catch_wallet("test_ok", || Ok(7_u32)).expect("ok");
        assert_eq!(ok, 7);
    }

    #[test]
    fn catch_string_maps_panic_to_error() {
        let err = catch_string::<(), _>("mesh_op", || panic!("boom")).unwrap_err();
        assert_eq!(err, "mesh_op failed");
    }
}
