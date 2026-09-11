# DarkFi-app UX parity — Nighthawk implementation plan

Cross-client update plan for **Android**, **iOS**, and **Desktop** (Tauri `nighthawk-desktop`). Inspired by upstream DarkFi `bin/app` (miniquad GPU wallet + DarkIRC), recast in **Nighthawk Stealth** chrome — similar density and HUD structure, not a clone, and **not** a video background.

Canonical DarkFi-app review source: emulator run of `darkfi.darkfi_app` (2026-09-11).  
Existing Nighthawk task IDs in [`implementation-plan.md`](implementation-plan.md) stay; this file is a **new track** (`N-U*`, `N-P0*`, `N-P1*`, `N-P2*`).

---

## Visual language (all clients)

Keep the Stealth palette. Do not introduce DarkFi cyan (`#00F0FF`), forest video, CRT shaders, or miniquad.

| Token | Hex | Role in this work |
|-------|-----|-------------------|
| Moonlit | `#0E1012` | Screen / overlay scrim |
| Charcoal | `#171C22` | Cards, HUD panel fill |
| Border | `#343D47` | 1 px hairlines (DarkFi-like frames, Nighthawk steel) |
| Accent | `#5E9BAF` | Connected state, primary actions, send chevron |
| Accent muted | `#7DADB9` | Secondary outlines, selected chip |
| Steel | `#8F98A3` | Captions, timestamps |
| Parmaviolet | `#C5CED6` | Body / titles |
| Disabled fill | `#1F252D` | Idle peer slots |
| Bright green / red | `#9DEA79` / `#F53C3C` | Nick hash colors, errors |

**Look:** charcoal glass panels, 1 px `#343D47` frames, 4–8 dp inner glow on accent when P2P is up, monospace **only** for peer URLs and addresses. Headings stay Pulp Display / existing TitleLarge. Tab bar, TopBar, BrandHeader, 44×44 hits — unchanged ([`nighthawk_ui_tokens.md`](nighthawk_ui_tokens.md)).

Themes: Stealth (default), Light, Midnight still apply; HUD uses surface + outline tokens so Light does not get a fake night overlay.

---

## Skipped (do not implement)

| Skip | Why |
|------|-----|
| Miniquad engine + forest / CRT video background | Battery, GPU, not Nighthawk chrome |
| Hardcoded wallet password / keys on external storage | Keep PIN vault, encrypted DB, UniFFI `drk` |
| Direct `darkfid` JSON-RPC as default sync | Keep **UnifOMR → darkfi-lightwalletd** |

---

## Item list

### N-U — Splash + chrome

| ID | Item | Android | iOS | Desktop |
|----|------|---------|-----|---------|
| **N-U1** | **App version on splash, bottom-center** | Android 12+ splash host: keep “Built on DarkFi”; add `versionName` (`3.00.008`) **below** tagline, `#A8B2BD` / parmaviolet, 12–13 sp, `Gravity.BOTTOM \| CENTER_HORIZONTAL`, ~24 dp above home indicator (shift Tor status up). Pre-12: same on Compose splash if used. | `SplashView` `ZStack(alignment: .bottom)`: `Text` of `CFBundleShortVersionString` + short build, centered, ~48 pt from safe bottom. Keep logo/subtitle in the vertical center. | Optional: first-launch window footer `app.getVersion()` — nice-to-have, not blocking |
| **N-U2** | HUD component kit | Compose: `NighthawkHudPanel`, `PeerSlotRow`, `TransportSegment` | SwiftUI: same in `UIComponents` | Lit: shared CSS variables from Stealth tokens |

### N-P0 — Chat network HUD (implement now)

| ID | Item | Android | iOS | Desktop |
|----|------|---------|-----|---------|
| **N-P0-1** | **Live outbound peer overlay** | Tap existing chat connection chrome (status row / net glyph) → charcoal panel over chat. Rows: slot index, URL or `connecting` / `sleeping`, accent vs steel. Bind UniFFI `darkirc` outbound slots (add FFI if missing). | Same overlay on Chat; extend `DarkircDaemon` / UniFFI status. | Chat pane: same overlay, keyboard shortcut optional |
| **N-P0-2** | **TCP / Tor toggle on the overlay** | Segmented **tcp \| tor** in the HUD (not a new Settings page). Persist; restart EventGraph; SOCKS still from Settings → Tor. Connected-via-Tor only after real handshake (existing rule). | Same; Arti stays the Tor engine. Overlay is the switch users see in Chat. | Same; prefer Tor for non-loopback (existing desktop guidance) |

P0 exit: tester can open Chat, see three outbound slots update live, flip tcp/tor, and the indicator matches handshake — on Android, iOS, and desktop.

### N-P1 — Wallet home + chat readability + channel secrets (implement now)

| ID | Item | Android | iOS | Desktop |
|----|------|---------|-----|---------|
| **N-P1-1** | **Token portfolio on Wallet home** | Below sync + DRK hero: hairlined **Tokens** list (`listTokenBalances`) — symbol, alias, amount. Empty = single DRK 0 row. Tap row → send with that `tokenId`. Send dropdown stays. | `WalletView` same band; reuse `DarkfiTokenBalance`. | Wallet tab: same list |
| **N-P1-2** | **Chat readability** | Date separators (steel, full-width hairline); nick color from pubkey/name hash using existing bright palette (not DarkFi magenta); gutter timestamps; tappable http(s); emoji picker on compose (system grid is OK). | Same in `ChatView`. | Same in Chat pane |
| **N-P1-3** | **Generate encrypted-channel secret in channel UI** | Channel overflow / “+”: generate 32-byte secret, show once, copy, store `DarkircChannelCryptoConfig.secretBase58`. Public channels remain unencrypted. | Same on channel settings sheet. | Same |

P1 exit: Wallet shows per-token balances; `#dev` is scannable (dates, nicks, links); a user can create a secret channel from Chat without opening Settings.

### N-P2 — Keep-alive + fud (this track’s P2)

Former plan P2 (DAO / mint / OTC) is **unchanged** in [`implementation-plan.md`](implementation-plan.md). This track’s P2:

| ID | Item | Android | iOS | Desktop |
|----|------|---------|-----|---------|
| **N-P2-1** | **Foreground / keep-alive for DarkIRC** | Harden `DarkircDaemonService`: reliable promote, notification copy, type that survives Android 15 `dataSync` caps where possible (`remoteMessaging` if Play policy allows). Re-bind UniFFI after process death. Compose must resume (upstream GPU app went black after background). | `BGProcessingTask` + `scenePhase` reconnect already sketched; make DAG/P2P survive lock for a documented window; no fake “running” if the socket is dead. | Keep chat sidecar while the window lives; optional tray |
| **N-P2-2** | **`fud://` in-chat files** | UniFFI fud plugin (or scoped FFI): detect `fud://` in messages, progress row, image preview, download to app storage. **Off until FFI lands.** | Same API, Photos/Files share sheet. | Same; native file picker |

P2-1 before P2-2 (files need a live daemon).

---

## Suggested order

1. **N-U1** splash version (Android + iOS) — small, visible.  
2. **N-U2** HUD kit (shared tokens).  
3. **N-P0-1 → N-P0-2** peer overlay + transport.  
4. **N-P1-1** token list.  
5. **N-P1-2** chat readability.  
6. **N-P1-3** channel secret.  
7. **N-P2-1** then **N-P2-2**.

---

## What this updates vs the DarkFi-app review

| Previous review item | This plan |
|----------------------|-----------|
| P0 peer overlay + tcp/tor | **N-P0-1 / N-P0-2** — Nighthawk HUD (charcoal/accent), all three clients |
| P1 token list, chat polish, channel secret | **N-P1-1 … N-P1-3** — Compose / SwiftUI / Lit, not miniquad |
| P2 foreground service + fud | **N-P2-1 / N-P2-2** (replaces “DAO/mint/OTC” as *this* P2) |
| Skip video, `changeme` wallet, direct darkfid | **Skipped** (table above) |
| Video background as DarkFi look | **Dropped.** Static Stealth surfaces only |
| Splash | **New N-U1:** version **bottom-center** on Android and iOS (Desktop optional) |
| Copy DarkFi cyan + forest | **Dropped.** Stealth `#5E9BAF` / `#0E1012` / `#343D47` |
| Android-only recs | **Android + iOS + Desktop** rows on every item |

Not in this track (leave in the old plan): UnifOMR, memos, QR, PIN, DAO Hub, fee estimate, change-server, Fiat.
