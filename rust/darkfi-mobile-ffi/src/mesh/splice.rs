//! Process-wide LWD splice used by a gateway. The wallet handle registers
//! a TLS-pinned `LightwalletClient`; the mesh engine never dials LWD itself
//! while holding the ingest lock.

use std::sync::{Arc, Mutex, OnceLock};

pub type LwdSpliceFn = Arc<dyn Fn(&str, &[u8]) -> Result<Vec<u8>, String> + Send + Sync>;

fn slot() -> &'static Mutex<Option<LwdSpliceFn>> {
    static SLOT: OnceLock<Mutex<Option<LwdSpliceFn>>> = OnceLock::new();
    SLOT.get_or_init(|| Mutex::new(None))
}

pub fn set_lwd_splice(f: Option<LwdSpliceFn>) {
    if let Ok(mut g) = slot().lock() {
        *g = f;
    }
}

pub fn call_lwd_splice(method: &str, body: &[u8]) -> Result<Vec<u8>, String> {
    let f = slot()
        .lock()
        .map_err(|e| e.to_string())?
        .clone()
        .ok_or_else(|| "no lwd splice registered".to_string())?;
    f(method, body)
}
