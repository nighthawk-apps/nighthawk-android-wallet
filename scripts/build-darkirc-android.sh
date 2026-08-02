#!/usr/bin/env bash
# Cross-compile darkirc for Android ABIs and copy into this repo's artifacts/.
# Prerequisite: run scripts/vendor-darkfi.sh (or set DARKFI_SRC to a darkfi checkout).
#
# Tip darkirc uses sled-overlay only (no rusqlite/SQLCipher).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Gradle syncDarkircArtifacts merges this tree into APK assets (`darkirc/<abi>/darkirc_exec`).
OUT="$ROOT/artifacts/darkirc"
: "${DARKFI_SRC:=$ROOT/third_party/darkfi}"
DARKIRC_CRATE="$DARKFI_SRC/bin/darkirc"
ANDROID_API="${ANDROID_API:-27}"

resolve_ndk_home() {
  local base="${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk}"
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

abi_to_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7-linux-androideabi" ;;
    x86) echo "i686-linux-android" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported abi: $1" >&2; exit 1 ;;
  esac
}

ndk_prebuilt_dir() {
  find "$NDK/toolchains/llvm/prebuilt" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -1
}

ndk_sysroot_lib_dir() {
  local abi="$1"
  local triple
  triple="$(abi_to_triple "$abi")"
  echo "$(ndk_prebuilt_dir)/sysroot/usr/lib/${triple}/${ANDROID_API}"
}

configure_ndk_link_for_abi() {
  local abi="$1"
  local triple ndk_lib_dir target_prefix bin_dir
  triple="$(abi_to_triple "$abi")"
  target_prefix="$(target_env_prefix "$triple")"
  ndk_lib_dir="$(ndk_sysroot_lib_dir "$abi")"
  bin_dir="$(ndk_prebuilt_dir)/bin"

  export "AR_${target_prefix}=${bin_dir}/llvm-ar"
  export "CARGO_TARGET_${target_prefix}_AR=${bin_dir}/llvm-ar"
  export "CC_${target_prefix}=${bin_dir}/${triple}${ANDROID_API}-clang"
  export "CXX_${target_prefix}=${bin_dir}/${triple}${ANDROID_API}-clang++"
  # Play requires 16KB ELF segment alignment for native libs (targetSdk 35+).
  export "CARGO_TARGET_${target_prefix}_RUSTFLAGS=-L native=${ndk_lib_dir} -C link-arg=-Wl,-z,max-page-size=16384"
}

compile_event_graph_zkas_proofs() {
  local zkas_bin="$DARKFI_SRC/target/release/zkas"
  if [[ ! -x "$zkas_bin" ]]; then
    echo "Building host zkas (compiles RLN .zk → .zk.bin embedded in darkirc)..."
    (cd "$DARKFI_SRC" && cargo build --release -p zkas)
  fi
  local zk
  for zk in "$DARKFI_SRC"/src/event_graph/proof/*.zk; do
    [[ -f "$zk" ]] || continue
    local out="${zk}.bin"
    if [[ ! -f "$out" ]] || [[ "$zk" -nt "$out" ]]; then
      echo "zkas: $(basename "$zk")"
      "$zkas_bin" "$zk" -o "$out"
    fi
  done
}

export ANDROID_NDK_HOME="$(resolve_ndk_home)"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"
export NDK="$ANDROID_NDK_HOME"
export CARGO_HOME="${CARGO_HOME:-$ROOT/.cargo-home}"
export RUSTUP_TOOLCHAIN="${RUSTUP_TOOLCHAIN:-nightly}"
mkdir -p "$CARGO_HOME"

for _target in aarch64-linux-android x86_64-linux-android; do
  rustup target add "$_target" --toolchain "$RUSTUP_TOOLCHAIN" 2>/dev/null || true
done

if [[ ! -d "$DARKIRC_CRATE" ]]; then
  echo "Expected $DARKIRC_CRATE — run scripts/vendor-darkfi.sh or set DARKFI_SRC."
  exit 1
fi

if [[ ! -f "$ANDROID_NDK_HOME/source.properties" ]]; then
  echo "ANDROID_NDK_HOME not found: $ANDROID_NDK_HOME"
  exit 1
fi

compile_event_graph_zkas_proofs

build_one() {
  local abi="$1"
  local triple
  triple="$(abi_to_triple "$abi")"
  configure_ndk_link_for_abi "$abi"
  (
    cd "$DARKFI_SRC"
    # Build the [[bin]] target — package [lib] cdylib has entry point 0 and must not be exec'd.
    cargo ndk -t "$abi" build --release -p darkirc --bin darkirc
  )
  local found="$DARKFI_SRC/target/$triple/release/darkirc"
  if [[ ! -f "$found" ]]; then
    echo "darkirc bin not found at $found"
    exit 1
  fi
  rm -rf "$OUT/$abi"/*
  mkdir -p "$OUT/$abi"
  cp -f "$found" "$OUT/$abi/darkirc_exec"
  chmod +x "$OUT/$abi/darkirc_exec"
  echo "OK $abi -> $OUT/$abi/darkirc_exec"
}

mkdir -p "$OUT/arm64-v8a" "$OUT/x86_64"

echo "Building darkirc with DARKFI_SRC=$DARKFI_SRC"
echo "ANDROID_NDK_HOME=$ANDROID_NDK_HOME"
echo "CARGO_HOME=$CARGO_HOME"
echo "(rebuilds may be incremental; first build is slow)"

build_one arm64-v8a
build_one x86_64

echo "Done. Rebuild the Android app to package assets (./gradlew :app:assembleDebug)."
