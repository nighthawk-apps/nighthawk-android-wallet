# QR scan (DRK-oriented)

Hardware cameras behave differently than emulators; prefer physical devices for QR regression passes.

## Prerequisites
- Install a debug or internal build with camera hardware available (or configure an emulator virtual scene per Android Studio docs).
- Prepare **two** QR payloads: one syntactically valid DRK-style URI / address your parser accepts, and a malformed variant that should surface the invalid-address copy from `scan_validation_invalid_address`.
- Ensure you can reset Camera permission between runs (settings reset, reinstall, or toggling permission).

## Permission flows
1. Launch Scan from the home shortcut with Camera denied — expect rationale UI / settings affordance.
2. Grant permission — preview should render with framing overlay.
3. Deny after reset — black preview plus deep-link into system settings when offered.

## Functional checks
1. Scan the **valid** QR — expect navigation or callback consistent with the mocked send/receive pipeline.
2. Scan the **invalid** QR — expect inline error without leaving the scanner stuck in a bad state.
3. Rotate or background during permission prompts — dialogs should reappear deterministically without leaking camera handles.
