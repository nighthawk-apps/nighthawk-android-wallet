# Nighthawk Mesh — encrypted EventGraph hop (BLE)

Nearby phones can carry **DarkIRC EventGraph** traffic when Tor/clearnet P2P is slow or unavailable. Mesh is **not** a second chat protocol: the UI still only reads `start_darkirc` / `event_pub`. If DarkIRC is not running, mesh does not invent messages.

This pass is **EventGraph-only**. SoftAP internet sharing, BLE lightwalletd control, unsigned gateway announce/caps, and UnifOMR bulk over radio are **disabled**.

## Architecture vs Nym and BitChat

```
┌─────────────────────────────────────────────────────────────┐
│                     Nighthawk Android                         │
│                                                               │
│  Chat UI ──► DarkIRC EventGraph                               │
│                 │                    │                        │
│                 │ online             │ nearby                 │
│                 ▼                    ▼                        │
│         P2P (Nym / Tor / Direct)   MeshEngine (Rust)          │
│                 │                    │ Noise HS/ENC + frag    │
│                 ▼                    ▼                        │
│            clearnet / SOCKS     NighthawkBleLink (Kotlin)     │
│                                      │ GATT dual-role         │
└──────────────────────────────────────┼────────────────────────┘
                                       ▼
                                  peer phones (NH magic)
```

| Stack | Role | Interop |
|-------|------|---------|
| **Nym mixnet** | Online privacy SOCKS for LWD + DarkIRC | Independent of BLE |
| **Nighthawk Mesh** | Offline/nearby encrypted EventGraph hop | Nighthawk-only |
| **BitChat** | **Not used** | Wire magic `NH`, opcodes `0xA*`, GATT `6e686d73-…` deliberately **reject** BitChat (`F47B5E2D-…`, opcodes `0x01`/`0x21`). GCS math is inspired by BitChat; packets are not compatible. “Share APK” UX is similar in spirit only. |

## What a sniffer sees

BLE advertisements carry a Nighthawk-only GATT UUID plus a rotating 8-byte link id (`blake3(secret‖epoch)[0..8]`). After neighbor handshake, payloads are `NoiseEnc` ciphertext. An observer does not see plaintext `NH_DAG_EVENT` / `NH_DAG_SYNC`, IRC `channel\nnick\nbody`, announce caps, or LWD JSON.

A decrypted neighbor is an EventGraph peer (same observer model as DarkIRC p2p). Public `#` channels remain plaintext **inside** `Event.content`. DMs stay saltbox **inside** `Privmsg` before `Event::new`.

## Wire (Rust `darkfi-mobile-ffi` mesh engine)

| Piece | Behavior |
|-------|----------|
| Presence | Handshake only. `announce()` is a no-op; no capability bits; `last_gateway` unused |
| Neighbor crypto | Per-GATT-neighbor `crypto_box` XX-*shape* (not claimed as Noise XX). Simultaneous open: lexicographically **smaller** id stays initiator |
| After Ready | Inner kinds `EventPut` (`0x20`) and GCS `DagSync` (`0x21`) |
| Relays | Decrypt, cache, **re-seal** to other Ready neighbors (do not TTL-flood ciphertext) |
| Replay | Sequence in envelope **and** inside AEAD plaintext; drop if `seq <= recv_seq`. Dedup hashes type, sender, and payload (not the timestamp). Frames outside ±120s are dropped before dedup |
| Payload cap | Per-type outer caps (ping/pong 64, handshake/announce/bulk-join 256, fragment 482, sealed event and DAG `EVENT_INNER_MAX + 256`, LWD ctrl 32 KiB, bulk offer 4 KiB). Inner event cap 64 KiB; cache 256 events / 256 KiB; DagSync replies max 32 per request, paginated via per-peer `sync_sent` |
| Handshake | Lexicographic initiator; `HANDSHAKE_TIMEOUT_MS = 8_000` drops stuck sessions. A Ready session older than 24h is dropped so the next send handshakes again |
| Fragments | `FRAGMENT_CHUNK=469`, `MAX_FRAGMENTS=256`, `FRAGMENT_TTL_SECS=30`; assembler drops stale / oversized |
| GCS | Over **event ids** (16-byte), not mesh `packet_id` |
| Forbidden on BLE | Plaintext DAG/sync, LWD ctrl, bulk SSID/PSK, announce caps |
| RLN | Mobile blob is empty (RLN off) |

Chat UI must not grow a second mesh parser. Daemon path:

1. After `insert_signal_with_blob`, gossip `Event` + empty blob onto mesh (`encode_mesh_event`).
2. Inbound: decrypt → deserialize `Event` → `validate_new` → **`header_dag_insert` then `ingest_mesh_event`**. Do not Tor-broadcast mesh-ingested events.
3. `publish_event` skips fanout if the id is already cached (stops Tor `event_pub` re-mesh loops).

## Android radio / OS

- `NighthawkMeshService` starts `MeshNative.start()` **before** radio.
- Ingest only `NH_NOISE_HS` / `NH_NOISE_ENC` / `NH_FRAGMENT` / `NH_PING` / `NH_PONG` (`MeshFrameInbox.shouldIngest`).
- GATT: no characteristic **READ** (CCCD still READ+WRITE). `onCharacteristicReadRequest` denied.
- Unicast GATT by mesh-id map; session hint from `MeshNative.peerId()`.
- A mesh identity that speaks from a new Bluetooth address replaces the old radio. The newest connection is kept. The stale address is disconnected and the Noise session is opened again. Cancelling the new address instead reconnects in a loop.
- A fragment train is queued only when every piece encodes. One rejected piece drops the train.
- Settings hide “Share internet over Wi-Fi”; `gatewayOptIn` forced **false**.
- JNA: new C symbols (`neighbor_up` / `neighbor_down` / `peer_id`) load via **`NhMeshNeighborLib`** so an older `.so` still loads `NhMeshLib`. Do not add those symbols to the main JNA interface until every shipped ABI includes them.
- Do not log Bluetooth addresses.

## Lab / multi-device testing (virtual Bluetooth)

There is **no** Android Virtual BLE / 3-AVD GATT harness (emulator radio is not honest dual-role). Use these labs instead:

### 1. Rust — authoritative 3-phone encrypted payloads (preferred)

In-process star topology `A — relay — G` with real handshake, reseal, EventPut, DagSync, link loss, and relay wipe:

```bash
cd rust && cargo test -p darkfi-mobile-ffi --release --all-features --lib mesh::tests::three_device
```

Covers: `three_device_connect_handshake`, `three_device_chat_via_relay`, `three_device_relay_reseals_not_forwards_ciphertext`, `three_device_periodic_dag_sync`, `three_device_link_lost_and_reconnect`, `three_device_relay_reset`.

### 2. Kotlin — OS / policy byte-pipe (“virtual radio”)

`MeshRadioScenario` moves **Noise ENC / Ping** frames across three in-memory phones and applies the same `MeshFrameInbox` gate as production (plaintext DAG dropped):

```bash
./gradlew :darkfi-android-sdk:testDebugUnitTest --tests '*MeshRadioScenario*'
```

This does **not** prove crypto reseal — that is the Rust suite.

### 3. Hardware

`MeshTwoPhoneInstrumentedTest` gates live two-adapter radio; it skips on emulator. Physical phones (≥2, ideally 3 in a star) remain required for GATT.

## Rebuild native lib (mesh C ABI, no UniFFI regen)

Mesh neighbor APIs are C ABI, not UniFFI UDL. After `rust/darkfi-mobile-ffi/src/mesh/` changes:

```bash
SKIP_UNIFFI_BINDGEN=1 MOBILE_FFI_ABIS=arm64-v8a ./scripts/build-darkfi-mobile-ffi-android.sh
# emulator mesh: rebuild x86_64 so neighbor symbols exist
SKIP_UNIFFI_BINDGEN=1 MOBILE_FFI_ABIS=x86_64 ./scripts/build-darkfi-mobile-ffi-android.sh
```

Keep Android and iOS `rust/darkfi-mobile-ffi/src/mesh/` **lockstep** (`rsync` after edits). Do not regenerate UniFFI Kotlin/Swift unless `darkfi_mobile_ffi.udl` changed.

Host tests (after `make contracts` / proofs once in the vendored DarkFi tree):

```bash
cd rust && cargo test -p darkfi-mobile-ffi --release --all-features --lib mesh
```

Gradle: `./gradlew :darkfi-android-sdk:testDebugUnitTest` (inbox ingest, plaintext DAG ignored, `publishChat` no-op, `MeshRadioScenario`).

## Out of scope this pass

- BLE LWD control / plaintext `NH_LWD_CTRL` originate
- SoftAP / UnifOMR bulk / share-internet
- Public identifiers on air
- Mobile RLN proofs
- Inventing chat when DarkIRC is stopped
- BitChat wire compatibility
