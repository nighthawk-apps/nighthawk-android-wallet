//! Headless localnet wallet for UnifOMR send tests.
//!
//! Desktop uses this same Android FFI send path.
//!
//!   localnet_tx init
//!   localnet_tx sync
//!   localnet_tx send --to <addr> --count <n> --amount <drk>

use std::path::PathBuf;
use std::time::{Duration, Instant};

use darkfi_mobile_ffi::bootstrap;
use darkfi_mobile_ffi::mnemonic::DarkfiMnemonic;
use darkfi_mobile_ffi::sync;
use darkfi_mobile_ffi::transactions;
use darkfi_mobile_ffi::DrkBootstrapConfig;
use darkfi_mobile_ffi::DrkWalletPtr;
use drk::Drk;

fn env_dir() -> PathBuf {
    PathBuf::from(std::env::var("E2E_WALLET_DIR").expect("E2E_WALLET_DIR"))
}

fn lwd() -> String {
    std::env::var("E2E_LWD").unwrap_or_else(|_| "http://127.0.0.1:9067".into())
}

fn words(phrase: &str) -> Vec<String> {
    phrase.split_whitespace().map(str::to_string).collect()
}

async fn open_wallet() -> Result<DrkWalletPtr, String> {
    let dir = env_dir();
    let phrase = std::fs::read_to_string(dir.join("mnemonic.txt")).map_err(|e| e.to_string())?;
    let ex = darkfi_mobile_ffi::shared_executor();
    let config = DrkBootstrapConfig {
        network: "localnet".into(),
        mnemonic: words(phrase.trim()),
        wallet_db_path: dir.join("wallet.db").to_string_lossy().into(),
        cache_path: dir.join("cache").to_string_lossy().into(),
        wallet_pass: "e2e-localnet".into(),
        lightwallet_server_url: lwd(),
        birthday_height: -1,
        lightwallet_tls_pin_sha256: None,
        use_tor: false,
        tor_socks_port: 0,
        use_nym: false,
        nym_socks_port: 0,
        darkfid_rpc_url: None,
        // Sends stay UnifOMR. Receiving must trial-decrypt: the sender's
        // change note is not covered by the recipient clue.
        strict_omr_only: false,
    };
    bootstrap::bootstrap_drk(&config, &ex).await
}

async fn balance_atomic(drk: &Drk) -> Result<u64, String> {
    let balances = drk.money_balance().await.map_err(|e| e.to_string())?;
    Ok(balances.values().copied().sum())
}

async fn default_address(drk: &Drk) -> Result<String, String> {
    use darkfi_sdk::crypto::keypair::{Address, Network, StandardAddress};
    let pubkey = drk.default_address().await.map_err(|e| e.to_string())?;
    let address: Address = StandardAddress::from_public(Network::Testnet, pubkey).into();
    Ok(address.to_string())
}

async fn sync_wallet(wallet: &DrkWalletPtr) -> Result<u64, String> {
    sync::sync_once_via_lightwallet(wallet.clone(), &lwd()).await?;
    let drk = wallet.read().await;
    balance_atomic(&drk).await
}

fn arg(flag: &str) -> Option<String> {
    let mut args = std::env::args().skip(1);
    while let Some(a) = args.next() {
        if a == flag {
            return args.next();
        }
    }
    None
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("warn")),
        )
        .init();

    let cmd = std::env::args().nth(1).unwrap_or_else(|| "sync".into());
    let dir = env_dir();
    std::fs::create_dir_all(&dir)?;

    if let Err(e) = smol::block_on(async {
        if cmd == "init" {
            let phrase_path = dir.join("mnemonic.txt");
            if !phrase_path.exists() {
                let phrase = DarkfiMnemonic::default()
                    .make_seed(None, None)
                    .map_err(|e| e.to_string())?;
                std::fs::write(&phrase_path, format!("{phrase}\n")).map_err(|e| e.to_string())?;
            }
            let wallet = open_wallet().await?;
            let addr = {
                let drk = wallet.read().await;
                default_address(&drk).await?
            };
            println!("ADDRESS={addr}");
            return Ok::<(), String>(());
        }

        let wallet = open_wallet().await?;
        if cmd == "sync" {
            let bal = sync_wallet(&wallet).await?;
            let addr = {
                let drk = wallet.read().await;
                default_address(&drk).await?
            };
            println!("ADDRESS={addr}");
            println!("BALANCE={bal}");
            return Ok(());
        }

        if cmd == "send" {
            let to = arg("--to").ok_or("missing --to")?;
            let count: u32 = arg("--count").unwrap_or_else(|| "1".into()).parse().map_err(|e: std::num::ParseIntError| e.to_string())?;
            let amount = arg("--amount").unwrap_or_else(|| "0.01".into());
            let mut ok = 0u32;
            let mut fail = 0u32;
            for i in 0..count {
                let mut sent = false;
                let start = Instant::now();
                while start.elapsed() < Duration::from_secs(90) {
                    let _ = sync_wallet(&wallet).await;
                    let result = {
                        let drk = wallet.read().await;
                        let built = transactions::build_transfer(
                            &drk,
                            &to,
                            &amount,
                            Some("DRK"),
                            Some(&format!("localnet-{i}")),
                            Some(&lwd()),
                            None,
                        )
                        .await;
                        match built {
                            Ok(bytes) => {
                                transactions::broadcast_transfer(
                                    &drk,
                                    &bytes,
                                    Some(&format!("localnet-{i}")),
                                    Some(&to),
                                    Some(&lwd()),
                                    None,
                                )
                                .await
                            }
                            Err(e) => Err(e),
                        }
                    };
                    match result {
                        Ok(hash) => {
                            println!("TX={hash}");
                            ok += 1;
                            sent = true;
                            break;
                        }
                        Err(e) => {
                            eprintln!("send {i} retry: {e}");
                            smol::Timer::after(Duration::from_secs(3)).await;
                        }
                    }
                }
                if !sent {
                    fail += 1;
                    eprintln!("FAIL send {i}");
                    if fail >= 5 {
                        break;
                    }
                }
            }
            println!("SEND_DONE ok={ok} fail={fail}");
            if fail > 0 {
                return Err(format!("{fail} sends failed"));
            }
            return Ok(());
        }

        Err(format!("unknown command {cmd}"))
    }) {
        eprintln!("{e}");
        std::process::exit(1);
    }
    Ok(())
}
