#!/usr/bin/env bash
# Cross-compile darkfid for Android ABIs and copy into artifacts/darkfid/.
# Prerequisite: vendored DarkFi at third_party/darkfi or set DARKFI_SRC.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/artifacts/darkfid"
: "${DARKFI_SRC:=$ROOT/third_party/darkfi}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK path}"

if [[ ! -d "$DARKFI_SRC/bin/darkfid" ]]; then
  echo "Expected $DARKFI_SRC/bin/darkfid — run scripts/vendor-darkfi.sh first."
  exit 1
fi

build_one() {
  local abi="$1"
  (
    cd "$DARKFI_SRC"
    cargo ndk -t "$abi" -o "$OUT/$abi" build --release -p darkfid
  )
  local found
  found="$(find "$OUT/$abi" -type f \( -name darkfid -o -name darkfid.exe \) 2>/dev/null | head -1 || true)"
  if [[ -z "$found" || ! -f "$found" ]]; then
    found="$(find "$OUT/$abi" -type f -perm -111 2>/dev/null | head -1 || true)"
  fi
  if [[ -z "$found" || ! -f "$found" ]]; then
    echo "Binary not found under $OUT/$abi — inspect cargo-ndk output."
    exit 1
  fi
  mv -f "$found" "$OUT/$abi/darkfid_exec"
  echo "OK $abi -> $OUT/$abi/darkfid_exec"
}

mkdir -p "$OUT/arm64-v8a" "$OUT/x86_64"

echo "Building darkfid (this can take a long time — full validator node)..."
build_one arm64-v8a
# Uncomment for emulator builds:
# build_one x86_64

echo "Done. Rebuild the Android app to package assets."
echo "See docs/darkfid-embedded-android.md for verification steps."
