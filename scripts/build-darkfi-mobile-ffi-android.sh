#!/usr/bin/env bash
# Cross-compile darkfi-mobile-ffi (UniFFI cdylib) for Android ABIs.
# Prerequisite: third_party/darkfi git submodule (./scripts/vendor-darkfi.sh, or
# `git submodule update --init`). Tip bin/drk = turso + aegis256; no SQLCipher.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS="$ROOT/artifacts/mobile-ffi"
JNILIBS="$ROOT/darkfi-android-sdk/src/main/jniLibs"
RUST="$ROOT/rust"
ANDROID_API="${ANDROID_API:-27}"

resolve_ndk_home() {
  # Portable resolution for local machines and F-Droid ($$NDK$$ / ANDROID_NDK_ROOT).
  # Never hardcode a developer home directory.
  local candidates=()
  [[ -n "${ANDROID_NDK_HOME:-}" ]] && candidates+=("$ANDROID_NDK_HOME")
  [[ -n "${ANDROID_NDK_ROOT:-}" ]] && candidates+=("$ANDROID_NDK_ROOT")
  [[ -n "${ANDROID_HOME:-}" ]] && candidates+=("$ANDROID_HOME/ndk-bundle" "$ANDROID_HOME/ndk")
  [[ -n "${ANDROID_SDK_ROOT:-}" ]] && candidates+=("$ANDROID_SDK_ROOT/ndk-bundle" "$ANDROID_SDK_ROOT/ndk")

  local c newest
  for c in "${candidates[@]}"; do
    if [[ -f "$c/source.properties" ]]; then
      printf '%s' "$c"
      return
    fi
    if [[ -d "$c" ]]; then
      newest="$(find "$c" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
      if [[ -n "$newest" && -f "$newest/source.properties" ]]; then
        printf '%s' "$newest"
        return
      fi
    fi
  done

  echo "error: Android NDK not found. Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT to an NDK install." >&2
  exit 1
}

target_env_prefix() {
  echo "$1" | tr '[:lower:]' '[:upper:]' | tr '-' '_'
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

configure_ndk_link_for_abi() {
  local abi="$1"
  local rust_triple clang_triple target_prefix ndk_lib_dir bin_dir
  case "$abi" in
    arm64-v8a)
      rust_triple="aarch64-linux-android"
      clang_triple="aarch64-linux-android"
      ;;
    armeabi-v7a)
      rust_triple="armv7-linux-androideabi"
      clang_triple="armv7a-linux-androideabi"
      ;;
    x86)
      rust_triple="i686-linux-android"
      clang_triple="i686-linux-android"
      ;;
    x86_64)
      rust_triple="x86_64-linux-android"
      clang_triple="x86_64-linux-android"
      ;;
    *)
      echo "unsupported abi: $abi" >&2
      exit 1
      ;;
  esac
  target_prefix="$(target_env_prefix "$rust_triple")"
  ndk_lib_dir="$(ndk_sysroot_lib_dir "$abi")"
  bin_dir="$(ndk_prebuilt_dir)/bin"

  export "AR_${target_prefix}=${bin_dir}/llvm-ar"
  export "CARGO_TARGET_${target_prefix}_AR=${bin_dir}/llvm-ar"
  export "CC_${target_prefix}=${bin_dir}/${clang_triple}${ANDROID_API}-clang"
  export "CXX_${target_prefix}=${bin_dir}/${clang_triple}${ANDROID_API}-clang++"

  # NDK sysroot for -laaudio (rodio/drk); 16KB ELF segment alignment for modern Android.
  export "CARGO_TARGET_${target_prefix}_RUSTFLAGS=-L native=${ndk_lib_dir} -C link-arg=-Wl,-z,max-page-size=16384"
}

if [[ "$(id -u)" -eq 0 ]]; then
  echo "warning: running as root will create root-owned build artifacts; prefer running without sudo." >&2
fi

if [[ ! -d "$ROOT/third_party/darkfi/bin/drk" ]]; then
  echo "Expected vendored DarkFi at third_party/darkfi — run ./scripts/vendor-darkfi.sh" >&2
  echo "or: git submodule update --init -- third_party/darkfi" >&2
  exit 1
fi

"$ROOT/scripts/compile-darkfi-zkas-proofs.sh"

export ANDROID_NDK_HOME="$(resolve_ndk_home)"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export NDK="$ANDROID_NDK_HOME"
export CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}"

# F-Droid / release default: all four ABIs. Override locally with MOBILE_FFI_ABIS.
abis="${MOBILE_FFI_ABIS:-arm64-v8a armeabi-v7a x86_64 x86}"

for abi in $abis; do
  configure_ndk_link_for_abi "$abi"
done

mkdir -p "$CARGO_HOME"
mkdir -p "$ARTIFACTS/arm64-v8a" "$ARTIFACTS/armeabi-v7a" "$ARTIFACTS/x86" "$ARTIFACTS/x86_64"
mkdir -p "$JNILIBS/arm64-v8a" "$JNILIBS/armeabi-v7a" "$JNILIBS/x86" "$JNILIBS/x86_64"

ndk_args=()
for abi in $abis; do
  ndk_args+=(-t "$abi")
done

(
  cd "$RUST"
  # Build library only (skip workspace `uniffi-bindgen` bin).
  cargo ndk "${ndk_args[@]}" build --release -p darkfi-mobile-ffi --lib
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

abi_to_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7-linux-androideabi" ;;
    x86) echo "i686-linux-android" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported abi: $1" >&2; return 1 ;;
  esac
}

for abi in $abis; do
  triple="$(abi_to_triple "$abi")" || exit 1
  copy_one "$triple" "$abi"
done

# Kotlin UniFFI bindings are committed. Skip regeneration on CI/F-Droid unless asked.
if [[ "${SKIP_UNIFFI_BINDGEN:-0}" != "1" && "${FDROID_BUILD:-0}" != "1" ]]; then
  echo "Regenerating Kotlin UniFFI bindings..."
  (
    cd "$RUST"
    cargo build -p darkfi-mobile-ffi >/dev/null
    cargo run --bin uniffi-bindgen generate \
      darkfi-mobile-ffi/src/darkfi_mobile_ffi.udl \
      --language kotlin \
      --crate darkfi_mobile_ffi \
      --metadata-no-deps \
      --out-dir "$ROOT/darkfi-android-sdk/src/main/java" \
      --no-format
  )

  # UniFFI 0.32 can fuse `}` with the next top-level `fun` (e.g. `} fun bridgePing`).
  KT_GEN="$ROOT/darkfi-android-sdk/src/main/java/com/nighthawkapps/lib/uniffi/darkfi_mobile_ffi/darkfi_mobile_ffi.kt"
  if [[ -f "$KT_GEN" ]]; then
    perl -i -pe 's/^\} fun `/}\n\nfun `/g' "$KT_GEN"
  fi
else
  echo "Skipping UniFFI Kotlin bindgen (FDROID_BUILD/SKIP_UNIFFI_BINDGEN set; using committed bindings)."
fi

echo "UniFFI libs installed under artifacts/mobile-ffi/ and darkfi-android-sdk/src/main/jniLibs/"
