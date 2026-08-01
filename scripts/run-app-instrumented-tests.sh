#!/usr/bin/env bash
# Install and run :app instrumented tests via adb (avoids Gradle result issues on low-storage emulators).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FLAVOR="${1:-darkfitestnet}"
case "$FLAVOR" in
  darkfitestnet) GRADLE_FLAVOR="Darkfitestnet" ;;
  darkfimainnet) GRADLE_FLAVOR="Darkfimainnet" ;;
  *)
    echo "Unknown flavor: $FLAVOR (use darkfitestnet or darkfimainnet)" >&2
    exit 1
    ;;
esac

SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB+=( -s "$SERIAL" )
fi

if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "No adb device. Set ANDROID_SERIAL or start an emulator." >&2
  exit 1
fi

./gradlew ":app:install${GRADLE_FLAVOR}Debug" ":app:install${GRADLE_FLAVOR}DebugAndroidTest"

PKG="com.nighthawkwallet.android"
if [[ "$FLAVOR" == "darkfitestnet" ]]; then
  PKG="${PKG}.testnet"
fi

"${ADB[@]}" shell am instrument -w \
  "${PKG}.test/com.nighthawkapps.lib.android.test.NighthawkUiTestRunner"
