#!/usr/bin/env bash
# Run darkfi-android-sdk instrumented tests via adb (works on 16KB-page emulators where
# Gradle connectedAndroidTest may report "No compatible devices").
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB+=( -s "$SERIAL" )
fi

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "No adb device. Set ANDROID_SERIAL or start an emulator." >&2
  exit 1
fi

./gradlew :darkfi-android-sdk:assembleDebugAndroidTest

TEST_APK="$ROOT/darkfi-android-sdk/build/outputs/apk/androidTest/debug/darkfi-android-sdk-debug-androidTest.apk"

"${ADB[@]}" install -r "$TEST_APK"

DEVICE="$("${ADB[@]}" devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
echo "Running instrumented tests on ${DEVICE:-unknown device}…"
"${ADB[@]}" shell am instrument -w \
  com.nighthawkapps.lib.android.sdk.test/androidx.test.runner.AndroidJUnitRunner
