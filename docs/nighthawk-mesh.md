# Nighthawk Mesh — encrypted EventGraph hop (BLE)

Nearby phones can carry **DarkIRC EventGraph** traffic when Tor/clearnet P2P is slow or unavailable. Mesh is **not** a second chat protocol: the UI still only reads `start_darkirc` / `event_pub`. If DarkIRC is not running, mesh does not invent messages.

This pass is **EventGraph-only**. SoftAP internet sharing, BLE lightwalletd control, unsigned gateway announce/caps, and UnifOMR bulk over radio are **disabled**.

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
| Replay | Sequence in envelope **and** inside AEAD plaintext; drop if `seq <= recv_seq` |
| Payload cap | 64 KiB inner; cache 256 events / 256 KiB; DagSync replies max 32 |
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
- Settings hide “Share internet over Wi-Fi”; `gatewayOptIn` forced **false**.
- JNA: new C symbols (`neighbor_up` / `neighbor_down` / `peer_id`) load via **`NhMeshNeighborLib`** so an older `.so` still loads `NhMeshLib`. Do not add those symbols to the main JNA interface until every shipped ABI includes them.
- Do not log Bluetooth addresses.

## Rebuild native lib (mesh C ABI, no UniFFI regen)

Mesh neighbor APIs are C ABI, not UniFFI UDL. After `rust/darkfi-mobile-ffi/src/mesh/` changes:

```bash
SKIP_UNIFFI_BINDGEN=1 MOBILE_FFI_ABIS=arm64-v8a ./scripts/build-darkfi-mobile-ffi-android.sh
```

Keep Android and iOS `rust/darkfi-mobile-ffi/src/mesh/` **lockstep** (`rsync` after edits). Do not regenerate UniFFI Kotlin/Swift unless `darkfi_mobile_ffi.udl` changed.

**Native artifacts (this pass):** `jniLibs/arm64-v8a/libdarkfi_mobile_ffi.so` includes `nh_mesh_neighbor_up` / `down`. Emulator `x86_64` `.so` is older — `NhMeshNeighborLib` fails closed (mesh neighbors off) until that ABI is rebuilt.

Host tests (after `make contracts` / proofs once in the vendored DarkFi tree):

```bash
cd rust && cargo test -p darkfi-mobile-ffi --release --all-features --lib mesh
```

Gradle: `./gradlew :darkfi-android-sdk:testDebugUnitTest` (inbox ingest, plaintext DAG ignored, `publishChat` no-op).

## Out of scope this pass

- BLE LWD control / plaintext `NH_LWD_CTRL` originate
- SoftAP / UnifOMR bulk / share-internet
- Public identifiers on air
- Mobile RLN proofs
- Inventing chat when DarkIRC is stopped
