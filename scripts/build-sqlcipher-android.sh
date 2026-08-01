#!/usr/bin/env bash
# Build static libsqlcipher.a (+ libcrypto.a) for Android ABIs (required by drk/rusqlite).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS="$ROOT/artifacts/sqlcipher"
DEFAULT_WORK="$ROOT/.build/sqlcipher-android"
WORK="${SQLCIPHER_WORK:-$DEFAULT_WORK}"
SQLCIPHER_VER="${SQLCIPHER_VER:-4.5.6}"
OPENSSL_VER="${OPENSSL_VER:-3.0.15}"
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

ndk_prebuilt_bin() {
  find "$NDK/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1
}

openssl_target_for_abi() {
  case "$1" in
    arm64-v8a) echo android-arm64 ;;
    armeabi-v7a) echo android-arm ;;
    x86_64) echo android-x86_64 ;;
    x86) echo android-x86 ;;
    *) echo "unsupported abi: $1" >&2; exit 1 ;;
  esac
}

ensure_writable_workdir() {
  if [[ -e "$WORK" && ! -w "$WORK" ]]; then
    if [[ "$WORK" == "$DEFAULT_WORK" ]]; then
      WORK="${TMPDIR:-/tmp}/nighthawk-sqlcipher-android"
      echo "warning: $DEFAULT_WORK is not writable; using $WORK" >&2
      echo "To reclaim the default path: sudo rm -rf $DEFAULT_WORK" >&2
    else
      cat >&2 <<EOF
error: $WORK is not writable.

Fix:
  rm -rf "$WORK"
  ./scripts/build-sqlcipher-android.sh
EOF
      exit 1
    fi
  fi
  mkdir -p "$WORK"
}

export ANDROID_NDK_HOME="$(resolve_ndk_home)"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export NDK="$ANDROID_NDK_HOME"

if [[ ! -d "$NDK" ]]; then
  echo "error: ANDROID_NDK_HOME not found ($NDK)" >&2
  exit 1
fi

NDK_BIN="$(ndk_prebuilt_bin)/bin"
if [[ ! -d "$NDK_BIN" ]]; then
  echo "error: NDK llvm prebuilt not found under $NDK/toolchains/llvm/prebuilt" >&2
  exit 1
fi
export PATH="$NDK_BIN:$PATH"

JOBS="$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 4)"

ensure_writable_workdir
mkdir -p "$ARTIFACTS"
cd "$WORK"

fetch_sqlcipher_amalgamation() {
  if [[ -f sqlcipher/build/sqlite3.c ]]; then
    return
  fi
  echo "Fetching SQLCipher ${SQLCIPHER_VER} amalgamation..."
  rm -rf "sqlcipher-${SQLCIPHER_VER}" sqlcipher sqlcipher.zip
  curl -fsSL "https://github.com/sqlcipher/sqlcipher/archive/refs/tags/v${SQLCIPHER_VER}.zip" -o sqlcipher.zip
  unzip -q sqlcipher.zip
  mv "sqlcipher-${SQLCIPHER_VER}" sqlcipher
  rm sqlcipher.zip
  (
    cd sqlcipher
    # Codec backend is selected at compile time via -DSQLCIPHER_CRYPTO_OPENSSL (ndk-build).
    ./configure --with-crypto-lib=none
    make sqlite3.c
    mkdir -p build
    mv sqlite3.c sqlite3.h sqlite3ext.h build/
  )
}

fetch_openssl_sources() {
  if [[ -d openssl-src ]]; then
    return
  fi
  echo "Fetching OpenSSL ${OPENSSL_VER}..."
  curl -fsSL "https://www.openssl.org/source/openssl-${OPENSSL_VER}.tar.gz" -o "openssl-${OPENSSL_VER}.tar.gz"
  tar xzf "openssl-${OPENSSL_VER}.tar.gz"
  mv "openssl-${OPENSSL_VER}" openssl-src
  rm "openssl-${OPENSSL_VER}.tar.gz"
}

install_openssl_headers() {
  local build_dir="$1"
  local include_root="$WORK/openssl/include"
  mkdir -p "$include_root/openssl"
  cp -R "$build_dir/include/openssl/." "$include_root/openssl/"
  cp -R "openssl-src/include/openssl/." "$include_root/openssl/"
}

build_openssl_abi() {
  local abi="$1"
  local target
  local build_dir
  local out
  target="$(openssl_target_for_abi "$abi")"
  out="$WORK/openssl/$abi"
  mkdir -p "$out"

  if [[ -f "$out/libcrypto.a" ]]; then
    echo "OpenSSL already built for $abi"
    return
  fi

  echo "Building OpenSSL ${OPENSSL_VER} for ${abi} target ${target}..."
  build_dir="$WORK/openssl-build/$abi"
  rm -rf "$build_dir"
  mkdir -p "$build_dir"
  (
    cd "$build_dir"
    export ANDROID_NDK_HOME="$NDK"
    export ANDROID_NDK_ROOT="$NDK"
    "$WORK/openssl-src/Configure" \
      "$target" \
      -D__ANDROID_API__="$ANDROID_API" \
      no-shared \
      no-tests \
      no-ui-console \
      no-ssl3 \
      no-comp
    make -j"$JOBS" build_libs
    cp libcrypto.a "$out/libcrypto.a"
  )

  if [[ ! -f "$WORK/openssl/include/openssl/opensslv.h" ]]; then
    install_openssl_headers "$build_dir"
  fi
}

write_ndk_makefiles() {
  mkdir -p "$WORK/jni"
  cat > "$WORK/jni/Application.mk" <<EOF
APP_ABI := arm64-v8a armeabi-v7a x86 x86_64
APP_PLATFORM := android-${ANDROID_API}
APP_CPPFLAGS += -fexceptions -frtti
APP_STL := c++_shared
EOF
  cat > "$WORK/jni/Android.mk" <<'EOF'
LOCAL_PATH := $(call my-dir)/..
include $(CLEAR_VARS)
LOCAL_MODULE := sqlcipher
LOCAL_MODULE_FILENAME := libsqlcipher
LOCAL_SRC_FILES := sqlcipher/build/sqlite3.c
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/openssl/include \
    $(LOCAL_PATH)/sqlcipher/build
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/sqlcipher/build
LOCAL_CFLAGS := \
    -DSQLITE_THREADSAFE=1 \
    -DSQLITE_HAS_CODEC \
    -DSQLCIPHER_CRYPTO_OPENSSL \
    -DSQLITE_TEMP_STORE=2
LOCAL_STATIC_LIBRARIES := sqlcipher_crypto
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := sqlcipher_crypto
LOCAL_SRC_FILES := openssl/$(TARGET_ARCH_ABI)/libcrypto.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/openssl/include
include $(PREBUILT_STATIC_LIBRARY)
EOF
}

build_sqlcipher_all_abis() {
  echo "Building SQLCipher static libs (all ABIs)..."
  write_ndk_makefiles
  "$NDK/ndk-build" \
    -C "$WORK" \
    NDK_PROJECT_PATH="$WORK" \
    APP_BUILD_SCRIPT="$WORK/jni/Android.mk" \
    NDK_APPLICATION_MK="$WORK/jni/Application.mk"

  for abi in arm64-v8a armeabi-v7a x86_64 x86; do
    mkdir -p "$ARTIFACTS/$abi"
    cp "$WORK/obj/local/$abi/libsqlcipher.a" "$ARTIFACTS/$abi/libsqlcipher.a"
    cp "$WORK/openssl/$abi/libcrypto.a" "$ARTIFACTS/$abi/libcrypto.a"
    echo "OK sqlcipher $abi"
  done

  mkdir -p "$ARTIFACTS/include"
  cp "$WORK/sqlcipher/build/sqlite3.h" "$WORK/sqlcipher/build/sqlite3ext.h" "$ARTIFACTS/include/"
}

fetch_sqlcipher_amalgamation
fetch_openssl_sources

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  build_openssl_abi "$abi"
done

build_sqlcipher_all_abis

echo "SQLCipher static libs installed under $ARTIFACTS (libsqlcipher.a + libcrypto.a per ABI)"
