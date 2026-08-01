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

## Roles

1. **TopBar** — back/close + optional title  
2. **BrandHeader** — logo on hubs  
3. **Heading** — symbol + title; `compact: true` when TopBar present  

iOS: `UIComponents/NighthawkTopBar.swift`  
Android: `ui-design-lib/.../component/TopBar.kt`
