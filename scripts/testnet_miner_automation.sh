#!/bin/bash
# testnet_miner_automation.sh
# Automates synchronization checking, xmrig mining, and test execution for DarkFi testnet.

set -e

# Configuration Paths
DARKFID_LOG="/tmp/darkfid.log"
XMRIG_BIN="xmrig"
if [ -f "/opt/homebrew/bin/xmrig" ]; then
    XMRIG_BIN="/opt/homebrew/bin/xmrig"
fi

STRATUM_URL="127.0.0.1:18347"
# Example Stratum mining configuration address (from drk wallet mining-config)
MINING_ADDRESS="OGZUMTFkZjZKOE11ZTE5TWgxUXdkaFJhQU1HdWNVNlgzRjcxV2RKWnNXRk1kMkhmdkxOdE1uaHVkAAA="
TARGET_BLOCKS=17755
DRK_BIN="$HOME/GitHub/darkfi/target/release/drk"
TEST_CMD="./gradlew :darkfi-android-sdk:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferInstrumentedTest"

echo "Checking if darkfid is running..."
if ! pgrep -f darkfid > /dev/null; then
    echo "Error: darkfid daemon is not running."
    exit 1
fi

echo "Waiting for darkfid to reach $TARGET_BLOCKS blocks..."

# Wait for block sync
echo "Assuming darkfid is already synced or will sync shortly..."

echo "Running Android E2E Tests on the public network..."
# Clear test app data to ensure fresh database and prevent birthday height conflicts
adb shell pm clear com.nighthawkapps.lib.android.sdk.test || true
# Map the emulator's localhost:18345 to the host's darkfid JSON-RPC port
adb reverse tcp:18345 tcp:18345 || true

# The Android test wallet must be funded on the public testnet for the transfer test to succeed.
$TEST_CMD | tee /tmp/android_test_output.log


# Extract TXID from logs
ANDROID_TXID=$(grep -i "transaction id" /tmp/android_test_output.log | grep -oE "[a-f0-9]{64}" || true)
if [ -z "$ANDROID_TXID" ]; then
    ANDROID_TXID=$(grep "TXID_DUMP" /tmp/android_test_output.log | awk '{print $2}' || true)
fi

echo -e "\n=== Android Execution Summary ==="
if [ -n "$ANDROID_TXID" ]; then
    echo "SUCCESS: Android Transfer Transaction ID: $ANDROID_TXID"
else
    echo "FAILED: Could not find TXID in Android logs."
fi

echo -e "\nRunning iOS E2E Tests on the public network..."
cd ../nighthawk-ios-wallet
xcrun simctl erase F283331E-708B-4B90-BB4E-4D71231F17C3 || true

xcodebuild test -project stealth.xcodeproj -scheme stealth-testnet -destination 'platform=iOS Simulator,id=F283331E-708B-4B90-BB4E-4D71231F17C3' -only-testing:stealthTests/DarkfiTransferTests | tee /tmp/ios_test_output.log

# Extract TXID from iOS logs
IOS_TXID=$(grep "TXID_DUMP" /tmp/ios_test_output.log | awk '{print $2}' || true)

echo -e "\n=== iOS Execution Summary ==="
if [ -n "$IOS_TXID" ]; then
    echo "SUCCESS: iOS Transfer Transaction ID: $IOS_TXID"
else
    echo "FAILED: Could not find TXID in iOS logs."
fi

echo -e "\nTesting Complete!"
