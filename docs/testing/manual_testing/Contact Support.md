# Contact support

Some form factors (Android TV, kiosk builds) lack mail clients — failures there are expected unless a handler is installed.

## Happy path
1. Configure a default email application on the device under test.
2. Cold start the wallet, open Support from settings/profile surfaces (exact navigation depends on build flavor).
3. Enter a short message, confirm the dialog, and verify the mail intent opens with:
   - Correct recipient (`WALLET_SUPPORT_EMAIL_ADDRESS` / runtime configuration).
   - Subject referencing **Nighthawk Wallet** (not legacy vendor branding).
   - Body containing both the user message and diagnostic appendix blocks when enabled.

## Lifecycle
1. After launching mail, task-switch back to the wallet — you should land past the confirmation gate without duplicate dialogs stacking.

## Crash appendix
1. On debug builds that synthesize crashes, repeat the flow and confirm optional exception excerpts respect `CrashInfo.MAX_EXCEPTIONS_TO_REPORT` caps.
