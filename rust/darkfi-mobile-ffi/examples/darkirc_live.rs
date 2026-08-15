//! Live-network smoke test for the embedded darkirc daemon (host build).
//!
//! Runs the exact same code path the Android/iOS apps use
//! (`start_darkirc` -> P2P connect -> static_sync -> sync_selected ->
//! history replay). Because the history replay callback only fires after the
//! DAG is fully synced, receiving events here proves mobile DAG sync works
//! against the current live network.
//!
//! Usage:  cargo run --example darkirc_live [--tor <socks_port>]

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

use darkfi_mobile_ffi::{
    darkirc_status, send_chat_message, start_darkirc, stop_darkirc, DarkircEventCallback,
};

struct CountingCallback {
    received: Arc<AtomicU64>,
}

impl DarkircEventCallback for CountingCallback {
    fn on_message(&self, _event_id: String, channel: String, nick: String, msg: String, ts: u64) {
        let n = self.received.fetch_add(1, Ordering::Relaxed) + 1;
        // Print a small sample so the operator can eyeball real traffic.
        if n <= 10 || n.is_multiple_of(500) {
            println!("[{n}] {ts} {channel} <{nick}> {msg}");
        }
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let use_tor = args.iter().any(|a| a == "--tor");
    let socks_port: u16 = args
        .iter()
        .position(|a| a == "--tor")
        .and_then(|i| args.get(i + 1))
        .and_then(|p| p.parse().ok())
        .unwrap_or(9150);

    let datastore = std::env::temp_dir().join(format!("darkirc-live-{}", std::process::id()));
    let received = Arc::new(AtomicU64::new(0));
    let cb = Box::new(CountingCallback {
        received: received.clone(),
    });

    println!(
        "Starting embedded darkirc ({}) datastore={}",
        if use_tor {
            "tor socks5"
        } else {
            "tcp+tls clearnet"
        },
        datastore.display()
    );
    start_darkirc(
        datastore.display().to_string(),
        use_tor,
        socks_port,
        Some(cb),
    )
    .expect("start_darkirc");

    // Wait up to 4 minutes for DAG sync + history replay (tor is slower).
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(240);
    let mut synced = false;
    while std::time::Instant::now() < deadline {
        std::thread::sleep(std::time::Duration::from_secs(5));
        let status = darkirc_status();
        let n = received.load(Ordering::Relaxed);
        println!("status={status} events_received={n}");
        if status == "failed" {
            eprintln!("FAIL: daemon failed");
            std::process::exit(1);
        }
        if n > 0 {
            synced = true;
            // Give the replay a little time to finish, then try a send.
            std::thread::sleep(std::time::Duration::from_secs(10));
            break;
        }
    }

    if !synced {
        eprintln!("FAIL: no events received within timeout — DAG sync did not complete");
        let _ = stop_darkirc();
        std::process::exit(1);
    }

    let n = received.load(Ordering::Relaxed);
    println!("PASS: DAG synced, {n} events replayed/relayed");

    // Optional live send (visible on the real network!) — only with --send.
    if args.iter().any(|a| a == "--send") {
        let msg = format!("nighthawk mobile ffi e2e {}", std::process::id());
        match send_chat_message("#random".into(), "nh-e2e".into(), msg.clone()) {
            Ok(()) => println!("sent to #random: {msg}"),
            Err(e) => eprintln!("send failed: {e:?}"),
        }
        std::thread::sleep(std::time::Duration::from_secs(10));
    }

    let _ = stop_darkirc();
    std::thread::sleep(std::time::Duration::from_secs(3));
    let _ = std::fs::remove_dir_all(&datastore);
}
