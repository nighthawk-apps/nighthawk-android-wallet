#!/usr/bin/env bash
# Cross-compile darkfi-mobile-ffi (UniFFI cdylib) for Android ABIs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS="$ROOT/artifacts/mobile-ffi"
SQLCIPHER_ARTIFACTS="$ROOT/artifacts/sqlcipher"
SQLCIPHER_HEADERS="$ROOT/artifacts/sqlcipher/include"
JNILIBS="$ROOT/darkfi-android-sdk/src/main/jniLibs"
RUST="$ROOT/rust"
ANDROID_API="${ANDROID_API:-27}"

resolve_ndk_home() {
  local base="${ANDROID_NDK_HOME:-/Users/adi/Library/Android/sdk/ndk}"
  if [[ -f "$base/source.properties" ]]; then
    printf '%s' "$base"
    return
  fi
  local newest
  newest="$(find "$base" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)"
  if [[ -n "$newest" && -f "$newest/source.properties" ]]; then
    printf '%s' "$newest"
    return
  fi
  printf '%s' "$base"
}

target_env_prefix() {
  echo "$1" | tr '[:lower:]' '[:upper:]' | tr '-' '_'
}

sqlcipher_ready() {
  local abi="$1"
  [[ -f "$SQLCIPHER_ARTIFACTS/$abi/libsqlcipher.a" && -f "$SQLCIPHER_ARTIFACTS/$abi/libcrypto.a" ]]
}

ensure_sqlcipher_artifacts() {
  local abi missing=0
  for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    if ! sqlcipher_ready "$abi"; then
      missing=1
      break
    fi
  done
  if [[ "$missing" -eq 1 ]]; then
    echo "Building SQLCipher for Android (required by drk/rusqlite)..."
    "$ROOT/scripts/build-sqlcipher-android.sh"
  fi
  if [[ ! -f "$SQLCIPHER_HEADERS/sqlite3.h" ]]; then
    echo "error: missing SQLCipher headers at $SQLCIPHER_HEADERS" >&2
    echo "Re-run: ./scripts/build-sqlcipher-android.sh" >&2
    exit 1
  fi
}

ndk_prebuilt_dir() {
  find "$NDK/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1
}

ndk_sysroot_lib_dir() {
  local abi="$1"
  local triple
  case "$abi" in
    arm64-v8a) triple="aarch64-linux-android" ;;
    armeabi-v7a) triple="arm-linux-androideabi" ;;
    x86) triple="i686-linux-android" ;;
    x86_64) triple="x86_64-linux-android" ;;
    *) echo "unsupported abi: $abi" >&2; exit 1 ;;
  esac
  echo "$(ndk_prebuilt_dir)/sysroot/usr/lib/${triple}/${ANDROID_API}"
}

configure_sqlcipher_link_for_target() {
  local triple="$1"
  local abi="$2"
  local target_prefix lib_dir ndk_lib_dir
  target_prefix="$(target_env_prefix "$triple")"
  lib_dir="$SQLCIPHER_ARTIFACTS/$abi"
  ndk_lib_dir="$(ndk_sysroot_lib_dir "$abi")"

  if ! sqlcipher_ready "$abi"; then
    echo "error: missing SQLCipher libs for $abi under $lib_dir" >&2
    exit 1
  fi

  # libsqlite3-sys (sqlcipher feature) reads TARGET-prefixed vars when cross-compiling.
  export "${target_prefix}_SQLCIPHER_LIB_DIR=$lib_dir"
  export "${target_prefix}_SQLCIPHER_INCLUDE_DIR=$SQLCIPHER_HEADERS"
  export "${target_prefix}_SQLCIPHER_STATIC=1"

  # Cargo link search/lib for dependency graph (drk/rodio needs NDK sysroot for -laaudio).
  export "CARGO_TARGET_${target_prefix}_RUSTFLAGS=-L native=${lib_dir} -L native=${ndk_lib_dir} -l static=sqlcipher -l static=crypto"
}

if [[ "$(id -u)" -eq 0 ]]; then
  echo "warning: running as root will create root-owned build artifacts; prefer running without sudo." >&2
fi

export ANDROID_NDK_HOME="$(resolve_ndk_home)"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export NDK="$ANDROID_NDK_HOME"
export CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}"

ensure_sqlcipher_artifacts

configure_sqlcipher_link_for_target aarch64-linux-android arm64-v8a
configure_sqlcipher_link_for_target armv7-linux-androideabi armeabi-v7a
configure_sqlcipher_link_for_target i686-linux-android x86
configure_sqlcipher_link_for_target x86_64-linux-android x86_64

mkdir -p "$CARGO_HOME"
mkdir -p "$ARTIFACTS/arm64-v8a" "$ARTIFACTS/armeabi-v7a" "$ARTIFACTS/x86" "$ARTIFACTS/x86_64"
mkdir -p "$JNILIBS/arm64-v8a" "$JNILIBS/armeabi-v7a" "$JNILIBS/x86" "$JNILIBS/x86_64"

(
  cd "$RUST"
  cargo ndk \
    -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 \
    build --release -p darkfi-mobile-ffi
)

T="$RUST/target"
copy_one() {
  local triple="$1"
  local abi="$2"
  local src="$T/$triple/release/libdarkfi_mobile_ffi.so"
  if [[ ! -f "$src" ]]; then
    echo "error: missing $src" >&2
    exit 1
  fi
  mkdir -p "$ARTIFACTS/$abi" "$JNILIBS/$abi"
  if ! cp -f "$src" "$ARTIFACTS/$abi/libdarkfi_mobile_ffi.so" 2>/dev/null; then
    echo "warning: could not write $ARTIFACTS/$abi (fix: sudo chown -R \"\$(whoami)\" $ARTIFACTS)" >&2
  fi
  cp -f "$src" "$JNILIBS/$abi/libdarkfi_mobile_ffi.so"
  echo "OK $abi"
}

copy_one aarch64-linux-android arm64-v8a
copy_one armv7-linux-androideabi armeabi-v7a
copy_one i686-linux-android x86
copy_one x86_64-linux-android x86_64

echo "UniFFI libs installed under artifacts/mobile-ffi/ and darkfi-android-sdk/src/main/jniLibs/"
