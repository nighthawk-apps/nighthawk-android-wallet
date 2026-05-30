#!/usr/bin/env bash
# Cross-compile darkirc for Android ABIs and copy into this repo's assets/.
# Prerequisite: clone https://github.com/darkrenaissance/darkfi and set DARKFI_SRC.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Gradle syncDarkircArtifacts merges this tree into APK assets (`darkirc/<abi>/darkirc_exec`).
OUT="$ROOT/artifacts/darkirc"
: "${DARKFI_SRC:?Set DARKFI_SRC to your darkfi checkout (contains bin/darkirc)}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK path}"

if [[ ! -d "$DARKFI_SRC/bin/darkirc" ]]; then
  echo "Expected $DARKFI_SRC/bin/darkirc — is DARKFI_SRC correct?"
  exit 1
fi

build_one() {
  local abi="$1"
  (
    cd "$DARKFI_SRC"
    cargo ndk -t "$abi" -o "$OUT/$abi" build --release -p darkirc
  )
  local found
  found="$(find "$OUT/$abi" -type f \( -name darkirc -o -name darkirc.exe \) 2>/dev/null | head -1 || true)"
  if [[ -z "$found" ]]; then
    found="$(find "$OUT/$abi" -type f -perm -111 2>/dev/null | head -1 || true)"
  fi
  if [[ -z "$found" || ! -f "$found" ]]; then
    echo "Binary not found under $OUT/$abi — inspect cargo-ndk output."
    exit 1
  fi
  mv -f "$found" "$OUT/$abi/darkirc_exec"
  echo "OK $abi -> $OUT/$abi/darkirc_exec"
}

mkdir -p "$OUT/arm64-v8a" "$OUT/x86_64"

echo "Building darkirc (this can take a long time)..."
build_one arm64-v8a
# build_one x86_64

echo "Done. Rebuild the Android app to package assets."
