# Nym Mixnet — shipped architecture (status)

Nighthawk routes **clearnet** wallet and DarkIRC traffic through an **in-process Nym SOCKS5** proxy. This document replaces the older “future integration plan” with what is actually shipping.

Mesh BLE is **orthogonal** — see [nighthawk-mesh.md](./nighthawk-mesh.md). Mixnet never carries BLE frames; BLE never tunnels through Nym.

## Planes

| Plane | Underlay | Carries |
|-------|----------|---------|
| **Nym SOCKS** | `nym_smolmix` tunnel → IPR exit | Remote lightwalletd TLS, DarkIRC P2P when transport = Nym |
| **Tor (Arti)** | Local SOCKS | Same as above when privacy network = Tor |
| **Direct** | Clearnet | Loopback LWD; optional remote without privacy hop |
| **Nighthawk Mesh** | BLE GATT | Encrypted EventGraph hops only (offline/nearby) |

```
Wallet LWD / DarkIRC P2P
        │
        ├─ PrivacyNetwork.Nym ──► AppNymCoordinator ──► 127.0.0.1:1080 SOCKS
        │                              │
        │                              └─ nym_smolmix Tunnel (ephemeral client)
        │
        ├─ PrivacyNetwork.Tor ──► Arti SOCKS
        │
        └─ Direct ──────────────► clearnet / loopback

Nearby EventGraph (independent):
  DarkIRC insert ──► mesh_publish_event ──► MeshEngine ──► NighthawkBleLink
```

## What shipped

- **Embedded SOCKS** (`rust/darkfi-mobile-ffi/src/nym.rs`): localhost listener (default **1080**), DNS over the mixnet tunnel, no bandwidth credential (ephemeral client).
- **Android coordinator** (`AppNymCoordinator`): warm-start at app launch; status for splash / HUD.
- **Wallet open**: when `use_nym` and LWD host is not loopback, start Nym and set the default SOCKS proxy before sync.
- **DarkIRC**: `DarkircTransport::Nym` dials seeds through the same SOCKS.
- **Instrumented**: `NymDarkircCoexistenceInstrumentedTest` (Nym HTTPS + DarkIRC coexist).

## What did **not** ship (backlog)

- Separate Fast dVPN vs 5-hop Mixnet UX toggle (current path is the embedded `nym_smolmix` tunnel).
- User-imported zk-nym bandwidth credentials / shared credential pool UX.
- External NymVPN app as the primary path (SOCKS-to-external remains a Tor-era pattern if needed).

## BitChat

Nym integration does **not** involve BitChat. Nearby radio is Nighthawk Mesh only (anti-interop wire magic / GATT UUIDs). See mesh doc.

## Ops notes

- First bootstrap can take ~tens of seconds; splash / HUD should wait on `nym_status`, not assume Connected.
- Loopback lightwalletd stays **direct** so local labs are not blocked on mixnet bootstrap.
