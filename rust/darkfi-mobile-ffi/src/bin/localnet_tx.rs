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

/// Print own notes in the recent compact-block window. `recover` rewinds the
/// scan cursor to just before the first note that is not in the wallet and
/// trial-decrypts forward so own change is credited.
async fn probe_or_recover(wallet: &DrkWalletPtr, recover: bool) -> Result<(), String> {
    use std::collections::HashSet;
    use std::io::Cursor;

    use darkfi_sdk::crypto::note::AeadEncryptedNote;
    use darkfi_serial::Decodable;
    use darkfi_money_contract::client::MoneyNote;

    let (secrets, known, scanned_keys, balance) = {
        let drk = wallet.read().await;
        let secrets = drk.get_money_secrets().await.map_err(|e| e.to_string())?;
        let coins = drk.get_coins(true).await.map_err(|e| e.to_string())?;
        let mut known = HashSet::new();
        let mut spent_null_height = 0u32;
        let mut unspent_values = Vec::new();
        for (own, _created, is_spent, spent_height, _) in &coins {
            known.insert(own.coin.to_bytes().to_vec());
            if *is_spent && spent_height.is_none() {
                spent_null_height += 1;
            }
            if !is_spent {
                unspent_values.push(own.note.value);
            }
        }
        unspent_values.sort_unstable();
        println!(
            "COINS total={} unspent={} optimistic_spent={}",
            coins.len(),
            unspent_values.len(),
            spent_null_height
        );
        for v in &unspent_values {
            println!("UNSPENT value={v}");
        }
        let mut keys = Vec::new();
        let range = drk
            .cache
            .scanned_blocks
            .range(0u32.to_be_bytes()..)
            .map(|r| r.map(|(k, _)| k))
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| format!("scanned keys: {e}"))?;
        for k in range {
            if k.len() == 4 {
                let mut b = [0u8; 4];
                b.copy_from_slice(&k);
                keys.push(u32::from_be_bytes(b));
            }
        }
        keys.sort_unstable();
        let show = keys.len().saturating_sub(8);
        println!(
            "SCAN_KEYS n={} tail={:?}",
            keys.len(),
            &keys[show..]
        );
        let bal = balance_atomic(&drk).await?;
        (secrets, known, keys, bal)
    };
    println!("BALANCE={balance}");

    let client = darkfi_mobile_ffi::lightwallet_client::LightwalletClient::from_endpoint_and_pin(
        &lwd(),
        None,
    );
    let info = client.get_light_info().await?;
    let tip = info.chain_tip_height;
    println!("TIP={tip}");
    let start = tip.saturating_sub(3600);
    let blocks = client.get_compact_block_range(start, tip, tip).await?;
    println!("BLOCKS fetched={} range={start}..={tip}", blocks.len());

    let mut missed = 0u32;
    for block in &blocks {
        for tx in &block.txs {
            for output in &tx.outputs {
                if output.coin.len() != 32
                    || output.encrypted_note.len() < 48
                    || known.contains(&output.coin)
                {
                    continue;
                }
                let mut cursor = Cursor::new(output.encrypted_note.as_slice());
                let Ok(enc) = AeadEncryptedNote::decode(&mut cursor) else {
                    continue;
                };
                for secret in &secrets {
                    let Ok(note) = enc.decrypt::<MoneyNote>(secret) else {
                        continue;
                    };
                    missed += 1;
                    println!("MISSED height={} value={}", block.height, note.value);
                    if recover {
                        use darkfi_sdk::pasta::group::ff::PrimeField;
                        let mut repr = [0u8; 32];
                        repr.copy_from_slice(&output.coin);
                        let Some(base) = Option::<darkfi_sdk::pasta::pallas::Base>::from(
                            darkfi_sdk::pasta::pallas::Base::from_repr(repr),
                        ) else {
                            println!("MISSED coin is not canonical");
                            break;
                        };
                        let coin = darkfi_money_contract::model::Coin::from(base);
                        let drk = wallet.read().await;
                        sync::remember_own_output(&drk, block.height, &coin, &note, secret).await?;
                        println!("REMEMBERED value={}", note.value);
                    }
                    break;
                }
            }
        }
    }
    println!("MISSED={missed}");
    if !recover || missed == 0 {
        return Ok(());
    }
    let drk = wallet.read().await;
    println!("REBUILD tree to {tip}");
    sync::rebuild_money_tree_to_height(&drk, &client, tip).await?;
    let bal = balance_atomic(&drk).await?;
    println!("BALANCE_AFTER_REBUILD={bal}");
    Ok(())
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

        if cmd == "probe" || cmd == "recover" {
            return probe_or_recover(&wallet, cmd == "recover").await;
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
