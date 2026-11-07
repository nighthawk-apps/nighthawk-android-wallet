# Nighthawk shared UI tokens (Phase 1)

Cross-platform chrome contract.

| Token | Value | Notes |
|-------|-------|--------|
| Back / close icon | 24×24 | Template tint |
| Hit target | 44×44 | Minimum |
| Top bar horizontal inset | 13 / spacingTiny–Small | |
| Content below TopBar | 8 | No status-bar-sized pads |
| Brand heading (no TopBar) | 16 top | Never magic 44 |
| Tab logo header | ~105 (iOS `.tabHeader`) | Match Android branding |
| Title color | parmaviolet / secondaryTitleText | |
| Fold / Duo expanded gate | 600dp / 600pt | `NighthawkExpandedWidth` / `NighthawkLayout.expandedWidth` |
| Fold rail width | 80dp / 80pt | Leading nav |
| Wallpaper wordmark clearance | **48dp / 48pt** | Top inset for rail + wallet QR stack |

## Roles

1. **TopBar** — back/close + optional title  
2. **BrandHeader** — logo on hubs  
3. **Heading** — symbol + title; `compact: true` when TopBar present  

iOS: `UIComponents/NighthawkTopBar.swift`  
Android: `ui-design-lib/.../component/TopBar.kt`

## Selected-pill language (Chat vs Money)

Chat and Wallet share the same **selected-chip** shape language (translucent fill ≈ 0.38, stroke ≈ 0.55, **8dp corners** like Material FilterChip / Channels·Direct — not capsules), but **hue must follow domain**:

| Surface | Hue | Token | Do not use |
|---------|-----|-------|------------|
| Chat — Channels / Direct / `#channel` | Magenta / pink | `secondaryContainer` (Cyberpunk `#6B1A6B`) | Teal/cyan |
| Wallet — **Request / Receive / Send** | Teal / cyan | `primary` (Cyberpunk `#5CE1FF`, Stealth accent teal) | Chat magenta |

**Why:** Magenta reads as messaging/chrome on the Void wallpaper; teal/cyan is the money accent (balance CTAs, fees, transfers). Mixing them makes Wallet feel like Chat.

Wallet hub labels are short: **Request**, **Receive**, **Send** (one horizontal row of three equal chips, that order). Keep longer “Send money” / “Receive money” copy for dedicated flow titles, not the hub chips.

Wallet QR: receive glyph stacked **above** scan on the **top-right**, with standard screen margin on top and trailing (`screen_standard_margin` / 25).
