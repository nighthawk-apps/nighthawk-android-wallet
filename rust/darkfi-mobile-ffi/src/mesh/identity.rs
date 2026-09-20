//! Mesh identity is independent of the wallet spend key.

use rand::RngCore;

use super::types::SENDER_LEN;

#[derive(Clone)]
pub struct MeshIdentity {
    secret: [u8; 32],
    pub epoch: u64,
}

impl MeshIdentity {
    pub fn generate() -> Self {
        let mut secret = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut secret);
        Self { secret, epoch: 0 }
    }

    pub fn from_secret(secret: [u8; 32], epoch: u64) -> Self {
        Self { secret, epoch }
    }

    pub fn rotate(&mut self) {
        use zeroize::Zeroize;
        self.secret.zeroize();
        rand::thread_rng().fill_bytes(&mut self.secret);
        self.epoch = self.epoch.saturating_add(1);
    }

    /// 8-byte on-air id: first 8 bytes of blake3(secret || epoch).
    pub fn peer_id(&self) -> [u8; SENDER_LEN] {
        let mut h = blake3::Hasher::new();
        h.update(&self.secret);
        h.update(&self.epoch.to_be_bytes());
        let out = h.finalize();
        let mut id = [0u8; SENDER_LEN];
        id.copy_from_slice(&out.as_bytes()[..SENDER_LEN]);
        id
    }

    pub fn secret_bytes(&self) -> [u8; 32] {
        self.secret
    }

    /// X25519 public corresponding to the mesh static. Never put this on
    /// `NH_ANNOUNCE` — handshake ciphertexts only.
    pub fn static_public(&self) -> [u8; 32] {
        crypto_box::SecretKey::from_slice(&self.secret)
            .map(|sk| sk.public_key().to_bytes())
            .unwrap_or([0u8; 32])
    }

    pub fn wipe(&mut self) {
        use zeroize::Zeroize;
        self.secret.zeroize();
        self.epoch = 0;
        *self = Self::generate();
    }

    pub fn secret_is_zero(&self) -> bool {
        self.secret.iter().all(|&b| b == 0)
    }
}

impl Drop for MeshIdentity {
    fn drop(&mut self) {
        use zeroize::Zeroize;
        self.secret.zeroize();
        self.epoch = 0;
    }
}
