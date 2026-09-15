//! C ABI so Kotlin JNA / Swift `dlsym` can drive the engine without a UniFFI
//! UDL regen (checksums on the checked-in bindings stay valid).

use std::os::raw::{c_char, c_int, c_uchar};

use super::{
    clear_bulk_tcp_override, mesh_ingest_link_bytes, mesh_last_gateway_peer, mesh_neighbor_down,
    mesh_neighbor_up, mesh_peer_id, mesh_pop_events_json, mesh_pop_inbound_event, mesh_publish_dag,
    mesh_pump_gateway, mesh_record_bulk_bytes, mesh_request_dag_sync, mesh_set_gateway_eligible,
    mesh_set_os_power_state, mesh_status, mesh_submit_bulk_join, mesh_submit_bulk_offer,
    mesh_submit_lwd_ctrl, mesh_wipe, set_bulk_tcp_override, start_mesh, stop_mesh,
};

fn copy_bytes(dst: *mut c_uchar, cap: c_int, src: &[u8]) -> c_int {
    if dst.is_null() {
        return -2;
    }
    if cap < src.len() as c_int {
        return -3;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(src.as_ptr(), dst, src.len());
    }
    src.len() as c_int
}

#[no_mangle]
pub extern "C" fn nh_mesh_start() -> c_int {
    match start_mesh() {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_stop() -> c_int {
    match stop_mesh() {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_set_gateway_eligible(opt_in: c_int) -> c_int {
    match mesh_set_gateway_eligible(opt_in != 0) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_set_os_power_state(
    foreground: c_int,
    charging: c_int,
    unmetered: c_int,
) -> c_int {
    match mesh_set_os_power_state(foreground != 0, charging != 0, unmetered != 0) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_ingest_link_bytes(ptr: *const c_uchar, len: c_int) -> c_int {
    if ptr.is_null() || len < 0 {
        return -2;
    }
    let bytes = unsafe { std::slice::from_raw_parts(ptr, len as usize) };
    match mesh_ingest_link_bytes(bytes.to_vec()) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_pop_outbound(ptr: *mut c_uchar, cap: c_int) -> c_int {
    match super::mesh_pop_one_outbound() {
        Some(frame) => copy_bytes(ptr, cap, &frame),
        None => 0,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_status(ptr: *mut c_uchar, cap: c_int) -> c_int {
    copy_bytes(ptr, cap, mesh_status().as_bytes())
}

#[no_mangle]
pub extern "C" fn nh_mesh_peer_id(ptr: *mut c_uchar, cap: c_int) -> c_int {
    copy_bytes(ptr, cap, &mesh_peer_id())
}

#[no_mangle]
pub extern "C" fn nh_mesh_last_gateway_peer(ptr: *mut c_uchar, cap: c_int) -> c_int {
    match mesh_last_gateway_peer() {
        Some(id) => copy_bytes(ptr, cap, &id),
        None => 0,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_submit_lwd_ctrl(
    dest: *const c_uchar,
    method: *const c_char,
    body: *const c_uchar,
    body_len: c_int,
    corr_out: *mut c_uchar,
) -> c_int {
    if dest.is_null() || method.is_null() {
        return -2;
    }
    let mut dest_id = [0u8; 8];
    unsafe {
        dest_id.copy_from_slice(std::slice::from_raw_parts(dest, 8));
    }
    let method = unsafe { std::ffi::CStr::from_ptr(method) }
        .to_str()
        .unwrap_or("");
    let body = if body.is_null() || body_len <= 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(body, body_len as usize) }.to_vec()
    };
    match mesh_submit_lwd_ctrl(dest_id, method, &body) {
        Ok(corr) => {
            if !corr_out.is_null() {
                unsafe {
                    std::ptr::copy_nonoverlapping(corr.as_ptr(), corr_out, 16);
                }
            }
            0
        }
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_pump_gateway() -> c_int {
    mesh_pump_gateway();
    0
}

#[no_mangle]
pub extern "C" fn nh_mesh_pop_events_json(ptr: *mut c_uchar, cap: c_int) -> c_int {
    copy_bytes(ptr, cap, mesh_pop_events_json().as_bytes())
}

#[no_mangle]
pub extern "C" fn nh_mesh_wipe() -> c_int {
    match mesh_wipe() {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_submit_bulk_join(dest: *const c_uchar, corr_out: *mut c_uchar) -> c_int {
    if dest.is_null() {
        return -2;
    }
    let mut dest_id = [0u8; 8];
    unsafe {
        dest_id.copy_from_slice(std::slice::from_raw_parts(dest, 8));
    }
    match mesh_submit_bulk_join(dest_id) {
        Ok(corr) => {
            if !corr_out.is_null() {
                unsafe {
                    std::ptr::copy_nonoverlapping(corr.as_ptr(), corr_out, 16);
                }
            }
            0
        }
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_submit_bulk_offer(
    dest: *const c_uchar,
    kind: c_int,
    port: c_int,
    ssid: *const c_char,
    psk: *const c_uchar,
    psk_len: c_int,
    session_id: *const c_uchar,
) -> c_int {
    if dest.is_null() || ssid.is_null() {
        return -2;
    }
    let mut dest_id = [0u8; 8];
    unsafe {
        dest_id.copy_from_slice(std::slice::from_raw_parts(dest, 8));
    }
    let ssid = unsafe { std::ffi::CStr::from_ptr(ssid) }
        .to_str()
        .unwrap_or("");
    let psk = if psk.is_null() || psk_len <= 0 {
        Vec::new()
    } else {
        unsafe { std::slice::from_raw_parts(psk, psk_len as usize) }.to_vec()
    };
    let mut sid = [0u8; 16];
    if !session_id.is_null() {
        unsafe {
            sid.copy_from_slice(std::slice::from_raw_parts(session_id, 16));
        }
    }
    match mesh_submit_bulk_offer(dest_id, kind as u8, port as u16, ssid, &psk, sid) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_set_bulk_tcp(host: *const c_char, port: c_int) -> c_int {
    if host.is_null() || port <= 0 || port > 65535 {
        clear_bulk_tcp_override();
        return 0;
    }
    let host = unsafe { std::ffi::CStr::from_ptr(host) }
        .to_str()
        .unwrap_or("");
    if host.is_empty() {
        clear_bulk_tcp_override();
        return 0;
    }
    match set_bulk_tcp_override(Some(host.to_string()), port as u16) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_record_bulk_bytes(n: u64) -> c_int {
    if mesh_record_bulk_bytes(n) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_publish_dag(ptr: *const c_uchar, len: c_int) -> c_int {
    if ptr.is_null() || len < 0 {
        return -2;
    }
    let bytes = unsafe { std::slice::from_raw_parts(ptr, len as usize) };
    match mesh_publish_dag(bytes.to_vec()) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_request_dag_sync() -> c_int {
    match mesh_request_dag_sync() {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_neighbor_up(dest: *const c_uchar) -> c_int {
    if dest.is_null() {
        return -2;
    }
    let mut dest_id = [0u8; 8];
    unsafe {
        dest_id.copy_from_slice(std::slice::from_raw_parts(dest, 8));
    }
    match mesh_neighbor_up(dest_id) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_neighbor_down(dest: *const c_uchar) -> c_int {
    if dest.is_null() {
        return -2;
    }
    let mut dest_id = [0u8; 8];
    unsafe {
        dest_id.copy_from_slice(std::slice::from_raw_parts(dest, 8));
    }
    match mesh_neighbor_down(dest_id) {
        Ok(()) => 0,
        Err(_) => -1,
    }
}

#[no_mangle]
pub extern "C" fn nh_mesh_pop_inbound_event(id_out: *mut c_uchar, ptr: *mut c_uchar, cap: c_int) -> c_int {
    match mesh_pop_inbound_event() {
        Some((id, body)) => {
            if !id_out.is_null() {
                unsafe {
                    std::ptr::copy_nonoverlapping(id.as_ptr(), id_out, 16);
                }
            }
            copy_bytes(ptr, cap, &body)
        }
        None => 0,
    }
}
