# QR code generation

1. Install the wallet build under test and derive a receive URI / DRK address from the Receive screen (or fixture data for QA builds).
2. Encode that payload into a QR image using any trusted generator.
3. Scan with a second device running the same build and confirm the decoded text matches the source material shown under the on-device QR preview.
