//! Batched Sapling trial decrypt for a sync window.
//!
//! One secret against many notes is the common case. The Jubjub multiply stays
//! the windowed multiply in `sapling_ka_agree`, and the affine inversion is
//! shared across the batch. SQLite stays on the caller.

use std::io::Cursor;

use darkfi_money_contract::client::MoneyNote;
use darkfi_money_contract::model::CoinAttributes;
use darkfi_sdk::crypto::note::AeadEncryptedNote;
use darkfi_sdk::crypto::{PublicKey, SecretKey};
use darkfi_serial::Decodable;
use rayon::prelude::*;

/// Notes below this count stay on one thread. Larger windows split the multiply.
const BATCH_CHUNK: usize = 48;

/// `MoneyFunction::TokenMintV1`. Strict UnifOMR still trial-decrypts these.
pub const MONEY_FUNC_TOKEN_MINT: u32 = 0x06;
/// `MoneyFunction::AuthTokenMintV1`. History only; no coin.
pub const MONEY_FUNC_AUTH_TOKEN_MINT: u32 = 0x04;
/// `MoneyFunction::AuthTokenFreezeV1`. History only; no coin.
pub const MONEY_FUNC_AUTH_TOKEN_FREEZE: u32 = 0x05;

/// Trial-decrypt `(coin, encrypted_note)` pairs.
///
/// The result is aligned with `notes`. A hit is the first secret whose
/// plaintext is a `MoneyNote` and whose coin matches `coin`.
pub fn decrypt_money_notes(
    secrets: &[SecretKey],
    notes: &[(Vec<u8>, Vec<u8>)],
) -> Vec<Option<(MoneyNote, SecretKey)>> {
    let decoded: Vec<Option<AeadEncryptedNote>> = notes
        .iter()
        .map(|(_, encrypted)| {
            let mut cursor = Cursor::new(encrypted.as_slice());
            AeadEncryptedNote::decode(&mut cursor).ok()
        })
        .collect();

    let mut found = vec![None; notes.len()];
    for secret in secrets {
        let pending: Vec<usize> = (0..notes.len())
            .filter(|index| found[*index].is_none() && decoded[*index].is_some())
            .collect();
        if pending.is_empty() {
            break;
        }
        let epks: Vec<PublicKey> = pending
            .iter()
            .map(|index| decoded[*index].as_ref().unwrap().ephem_public)
            .collect();
        let shared = shared_secrets(secret, &epks);
        for (index, shared) in pending.into_iter().zip(shared) {
            let Ok(shared) = shared else {
                continue;
            };
            let Some(enc) = decoded[index].as_ref() else {
                continue;
            };
            let Ok(note) = enc.decrypt_with_shared::<MoneyNote>(&shared) else {
                continue;
            };
            if !note_matches_coin(&note, secret, &notes[index].0) {
                continue;
            }
            found[index] = Some((note, *secret));
        }
    }
    found
}

fn shared_secrets(
    secret: &SecretKey,
    epks: &[PublicKey],
) -> Vec<Result<[u8; 32], darkfi_sdk::error::ContractError>> {
    use darkfi_sdk::crypto::diffie_hellman::sapling_ka_agree_batch;
    if epks.len() <= BATCH_CHUNK {
        return sapling_ka_agree_batch(secret, epks);
    }
    epks.par_chunks(BATCH_CHUNK)
        .flat_map(|chunk| sapling_ka_agree_batch(secret, chunk))
        .collect()
}

fn note_matches_coin(note: &MoneyNote, secret: &SecretKey, coin: &[u8]) -> bool {
    if coin.len() != 32 {
        return false;
    }
    let attrs = CoinAttributes {
        public_key: PublicKey::from_secret(*secret),
        value: note.value,
        token_id: note.token_id,
        spend_hook: note.spend_hook,
        user_data: note.user_data,
        blind: note.coin_blind,
    };
    attrs.to_coin().to_bytes().as_slice() == coin
}

#[cfg(test)]
mod tests {
    use super::*;
    use darkfi_money_contract::model::TokenId;
    use darkfi_sdk::crypto::{Blind, FuncId, Keypair};
    use darkfi_sdk::pasta::pallas;
    use rand::rngs::OsRng;

    fn sample_note(value: u64) -> MoneyNote {
        MoneyNote {
            value,
            token_id: TokenId::from(pallas::Base::from(1u64)),
            spend_hook: FuncId::from(pallas::Base::from(0u64)),
            user_data: pallas::Base::from(2u64),
            coin_blind: Blind(pallas::Base::from(3u64)),
            value_blind: Blind(pallas::Scalar::from(4u64)),
            token_blind: Blind(pallas::Base::from(5u64)),
            memo: b"batch".to_vec(),
        }
    }

    #[test]
    fn batch_matches_sequential_and_checks_the_coin() {
        let key = Keypair::random(&mut OsRng);
        let other = Keypair::random(&mut OsRng);
        let own = sample_note(9);
        let foreign = sample_note(8);
        let own_enc = AeadEncryptedNote::encrypt(&own, &key.public, &mut OsRng).unwrap();
        let foreign_enc = AeadEncryptedNote::encrypt(&foreign, &other.public, &mut OsRng).unwrap();
        let own_coin = CoinAttributes {
            public_key: key.public,
            value: own.value,
            token_id: own.token_id,
            spend_hook: own.spend_hook,
            user_data: own.user_data,
            blind: own.coin_blind,
        }
        .to_coin()
        .to_bytes()
        .to_vec();
        let notes = vec![
            (own_coin.clone(), {
                let mut bytes = Vec::new();
                darkfi_serial::Encodable::encode(&own_enc, &mut bytes).unwrap();
                bytes
            }),
            (own_coin.clone(), {
                let mut bytes = Vec::new();
                darkfi_serial::Encodable::encode(&foreign_enc, &mut bytes).unwrap();
                bytes
            }),
            (vec![0u8; 32], {
                let mut bytes = Vec::new();
                darkfi_serial::Encodable::encode(&own_enc, &mut bytes).unwrap();
                bytes
            }),
        ];
        let found = decrypt_money_notes(&[key.secret, other.secret], &notes);
        let (note, secret) = found[0].clone().expect("own note");
        assert_eq!(note, own);
        assert_eq!(secret, key.secret);
        assert!(found[1].is_none(), "other recipient");
        assert!(found[2].is_none(), "coin mismatch");
        let sequential = own_enc.decrypt::<MoneyNote>(&key.secret).unwrap();
        assert_eq!(sequential, own);
    }
}
