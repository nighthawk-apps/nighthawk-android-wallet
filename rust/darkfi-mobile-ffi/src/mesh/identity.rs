//! Mesh identity is independent of the wallet spend key.
//!
//! This static key is used for an opportunistic X25519 handshake without peer
//! authentication. The peer static is mixed into the handshake and is not bound
//! to a known contact, so a relay of wallet control frames has no MITM resistance.

use rand::RngCore;
use zeroize::{Zeroize, Zeroizing};

use super::types::SENDER_LEN;

#[derive(Clone)]
pub struct MeshIdentity {
    secret: Zeroizing<[u8; 32]>,
    pub epoch: u64,
}

impl MeshIdentity {
    pub fn generate() -> Self {
        let mut secret = Zeroizing::new([0u8; 32]);
        rand::rngs::OsRng.fill_bytes(&mut secret[..]);
        Self { secret, epoch: 0 }
    }

    pub fn from_secret(secret: [u8; 32], epoch: u64) -> Self {
        Self {
            secret: Zeroizing::new(secret),
            epoch,
        }
    }

    pub fn rotate(&mut self) {
        self.secret.zeroize();
        rand::rngs::OsRng.fill_bytes(&mut self.secret[..]);
        self.epoch = self.epoch.saturating_add(1);
    }

    /// 8-byte on-air id: first 8 bytes of blake3(secret || epoch).
    pub fn peer_id(&self) -> [u8; SENDER_LEN] {
        let mut h = blake3::Hasher::new();
        h.update(&self.secret[..]);
        h.update(&self.epoch.to_be_bytes());
        let out = h.finalize();
        let mut id = [0u8; SENDER_LEN];
        id.copy_from_slice(&out.as_bytes()[..SENDER_LEN]);
        id
    }

    pub fn secret_bytes(&self) -> Zeroizing<[u8; 32]> {
        self.secret.clone()
    }

    /// X25519 public corresponding to the mesh static. Never put this on
    /// `NH_ANNOUNCE` — handshake ciphertexts only.
    ///
    /// `None` if the secret bytes are not a valid scalar encoding. Failure is
    /// not reported as 32 zero bytes.
    pub fn static_public(&self) -> Option<[u8; 32]> {
        static_public_from_bytes(&self.secret[..])
    }

    pub fn wipe(&mut self) {
        self.secret.zeroize();
        self.epoch = 0;
        *self = Self::generate();
    }

    pub fn secret_is_zero(&self) -> bool {
        self.secret.iter().all(|&b| b == 0)
    }
}

fn static_public_from_bytes(secret: &[u8]) -> Option<[u8; 32]> {
    crypto_box::SecretKey::from_slice(secret)
        .ok()
        .map(|sk| sk.public_key().to_bytes())
}

impl Drop for MeshIdentity {
    fn drop(&mut self) {
        self.secret.zeroize();
        self.epoch = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn static_public_error_does_not_yield_zero_bytes() {
        assert_eq!(static_public_from_bytes(&[]), None);
        assert_eq!(static_public_from_bytes(&[0u8; 31]), None);
        assert_eq!(static_public_from_bytes(&[0u8; 33]), None);
        assert_ne!(static_public_from_bytes(&[1u8; 31]), Some([0u8; 32]));

        let id = MeshIdentity::from_secret([0u8; 32], 0);
        let pk = id.static_public();
        assert!(pk.is_some());
        assert_ne!(pk, Some([0u8; 32]));

        let secret = [9u8; 32];
        let id = MeshIdentity::from_secret(secret, 1);
        assert_eq!(&*id.secret_bytes(), &secret);
        assert_eq!(id.static_public(), static_public_from_bytes(&secret));
    }
}
