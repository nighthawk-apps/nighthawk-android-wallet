//! Golomb-coded set over blake3 event / packet ids (BitChat math, Nighthawk ids).

const TARGET_FPR: f64 = 0.01;

pub fn golomb_p() -> u8 {
    TARGET_FPR.log2().abs().ceil() as u8
}

fn h64(id: &[u8]) -> u64 {
    let hash = blake3::hash(id);
    u64::from_be_bytes(hash.as_bytes()[0..8].try_into().unwrap())
}

pub fn encode_gcs(ids: &[[u8; 16]]) -> (u8, u32, Vec<u8>) {
    let p = golomb_p();
    let n = ids.len().max(1);
    let m = (n as u64).saturating_mul(1u64 << p).min(u32::MAX as u64) as u32;
    let mut mapped: Vec<u32> = ids
        .iter()
        .map(|id| (h64(id) % m as u64) as u32)
        .collect();
    mapped.sort_unstable();
    mapped.dedup();
    let mut bits = BitWriter::default();
    let mut prev = 0u32;
    for v in mapped {
        let delta = v.saturating_sub(prev);
        prev = v;
        let q = delta >> p;
        let r = delta & ((1u32 << p) - 1);
        for _ in 0..q {
            bits.push(true);
        }
        bits.push(false);
        bits.push_n(r, p);
    }
    (p, m, bits.finish())
}

pub fn gcs_contains(p: u8, m: u32, data: &[u8], id: &[u8; 16]) -> bool {
    if m == 0 {
        return false;
    }
    let target = (h64(id) % m as u64) as u32;
    let mut bits = BitReader::new(data);
    let mut acc = 0u32;
    while let Some(delta) = read_golomb(&mut bits, p) {
        acc = acc.saturating_add(delta);
        if acc == target {
            return true;
        }
        if acc > target {
            return false;
        }
    }
    false
}

fn read_golomb(bits: &mut BitReader<'_>, p: u8) -> Option<u32> {
    let mut q = 0u32;
    loop {
        match bits.next() {
            Some(true) => q += 1,
            Some(false) => break,
            None => return None,
        }
    }
    let r = bits.read_n(p)?;
    Some((q << p) | r)
}

#[derive(Default)]
struct BitWriter {
    buf: Vec<u8>,
    bit: u8,
}

impl BitWriter {
    fn push(&mut self, one: bool) {
        if self.buf.is_empty() || self.bit == 8 {
            self.buf.push(0);
            self.bit = 0;
        }
        if one {
            let i = self.buf.len() - 1;
            self.buf[i] |= 1 << (7 - self.bit);
        }
        self.bit += 1;
    }

    fn push_n(&mut self, v: u32, n: u8) {
        for i in (0..n).rev() {
            self.push(((v >> i) & 1) == 1);
        }
    }

    fn finish(self) -> Vec<u8> {
        self.buf
    }
}

struct BitReader<'a> {
    data: &'a [u8],
    byte: usize,
    bit: u8,
}

impl<'a> BitReader<'a> {
    fn new(data: &'a [u8]) -> Self {
        Self {
            data,
            byte: 0,
            bit: 0,
        }
    }

    fn next(&mut self) -> Option<bool> {
        if self.byte >= self.data.len() {
            return None;
        }
        let v = (self.data[self.byte] >> (7 - self.bit)) & 1 == 1;
        self.bit += 1;
        if self.bit == 8 {
            self.bit = 0;
            self.byte += 1;
        }
        Some(v)
    }

    fn read_n(&mut self, n: u8) -> Option<u32> {
        let mut v = 0u32;
        for _ in 0..n {
            v = (v << 1) | u32::from(self.next()?);
        }
        Some(v)
    }
}
