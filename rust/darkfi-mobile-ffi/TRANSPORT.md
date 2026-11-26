# TransportManager (shared FFI)

Atomic privacy route for wallet + DarkIRC + fiat:

| Mode | SOCKS | Notes |
|------|-------|-------|
| `Nym` | `127.0.0.1:1080` | Default; mixnet DNS |
| `Tor` | Arti / external | `.onion` preferred for DarkIRC |
| `Direct` | none | Session-only override from “Continue without Nym” |

## State machine

`Idle → Bootstrapping → Connecting → Ready → Degraded(retryIn)`

Switch path is mutex-serialized: tear down old proxies, rebuild `DarkfiWalletHandle` with full `tls_pins`, restart DarkIRC, update fiat OkHttp/URLSession proxy — **without** unmounting navigation.

## Hooks

- `on_network_changed()` — invalidate Nym `LAST_HEALTHY` / Arti circuits
- Background suspend/resume — pause cover traffic where OS allows
- Local SOCKS5 auth (RFC 1929) on loopback proxies; method `0x00` still accepted
- SOCKS username and password are lowercase hex (`0-9a-f`), with no `@`, `:`, `%`, or other URL delimiters, so they are copied into `socks5://user:pass@host` without percent-encoding. They rotate when the route changes (`apply`, `continue_without_nym`). `on_network_changed` does not rotate them: DarkIRC keeps that proxy URL until a transport switch restarts the daemon.

Wallet clients call `WalletTransportReprepare` (iOS) / coordinator reopen (Android) after mode changes.
