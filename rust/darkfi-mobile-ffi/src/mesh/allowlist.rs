//! BLE lightwalletd method gate. Bulk UnifOMR never rides Bluetooth.

use super::types::{BLE_ALLOWED_LWD, BLE_FORBIDDEN_LWD, LWD_CTRL_MAX};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum LwdCtrlError {
    Empty,
    TooLarge,
    Forbidden(&'static str),
    UnknownMethod,
}

impl std::fmt::Display for LwdCtrlError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Empty => write!(f, "empty lwd ctrl"),
            Self::TooLarge => write!(f, "lwd ctrl exceeds 32 KiB"),
            Self::Forbidden(m) => write!(f, "forbidden on BLE: {m}"),
            Self::UnknownMethod => write!(f, "unknown lwd method"),
        }
    }
}

/// `method\n` + proto bytes. First line is the gRPC method leaf name.
pub fn validate_lwd_ctrl(payload: &[u8]) -> Result<&str, LwdCtrlError> {
    if payload.is_empty() {
        return Err(LwdCtrlError::Empty);
    }
    if payload.len() > LWD_CTRL_MAX {
        return Err(LwdCtrlError::TooLarge);
    }
    let nl = payload.iter().position(|&b| b == b'\n').unwrap_or(payload.len());
    let method = std::str::from_utf8(&payload[..nl]).unwrap_or("");
    if let Some(bad) = BLE_FORBIDDEN_LWD.iter().copied().find(|m| method.eq_ignore_ascii_case(m))
    {
        return Err(LwdCtrlError::Forbidden(bad));
    }
    if BLE_ALLOWED_LWD
        .iter()
        .any(|m| method.eq_ignore_ascii_case(m))
    {
        return Ok(BLE_ALLOWED_LWD
            .iter()
            .find(|m| method.eq_ignore_ascii_case(m))
            .copied()
            .unwrap());
    }
    Err(LwdCtrlError::UnknownMethod)
}

pub fn gateway_eligible(mesh_on: bool, gateway_opt_in: bool, charging: bool, unmetered: bool) -> bool {
    mesh_on && gateway_opt_in && charging && unmetered
}
