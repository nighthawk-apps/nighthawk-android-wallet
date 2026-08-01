#!/usr/bin/env bash
# Cross-compile darkirc for Android ABIs and copy into this repo's artifacts/.
# Prerequisite: run scripts/vendor-darkfi.sh (or set DARKFI_SRC to a darkfi checkout).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Gradle syncDarkircArtifacts merges this tree into APK assets (`darkirc/<abi>/darkirc_exec`).
OUT="$ROOT/artifacts/darkirc"
: "${DARKFI_SRC:=$ROOT/third_party/darkfi}"
SQLCIPHER_ARTIFACTS="$ROOT/artifacts/sqlcipher"
SQLCIPHER_HEADERS="$SQLCIPHER_ARTIFACTS/include"
DARKIRC_CRATE="$DARKFI_SRC/bin/darkirc"
DARKIRC_CARGO="$DARKIRC_CRATE/Cargo.toml"
DARKIRC_BUILD_RS="$DARKIRC_CRATE/build.rs"
DARKIRC_SQLCIPHER_DIR="$DARKIRC_CRATE/sqlcipher"
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

sqlcipher_ready() {
  local abi="$1"
  [[ -f "$SQLCIPHER_ARTIFACTS/$abi/libsqlcipher.a" && -f "$SQLCIPHER_ARTIFACTS/$abi/libcrypto.a" ]]
}

ensure_sqlcipher_artifacts() {
  local abi missing=0
  for abi in arm64-v8a x86_64; do
    if ! sqlcipher_ready "$abi"; then
      missing=1
      break
    fi
  done
  if [[ "$missing" -eq 1 ]]; then
    echo "Building SQLCipher for Android (required for darkirc)..."
    "$ROOT/scripts/build-sqlcipher-android.sh"
  fi
  if [[ ! -f "$SQLCIPHER_HEADERS/sqlite3.h" ]]; then
    echo "error: missing SQLCipher headers at $SQLCIPHER_HEADERS" >&2
    exit 1
  fi
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

# Upstream darkirc build.rs adds -L sqlcipher/ on Android; linker expects libsqlite3.a there.
stage_darkirc_sqlcipher_libs() {
  local abi="$1"
  local src="$SQLCIPHER_ARTIFACTS/$abi"
  rm -rf "$DARKIRC_SQLCIPHER_DIR"
  mkdir -p "$DARKIRC_SQLCIPHER_DIR"
  cp "$src/libsqlcipher.a" "$DARKIRC_SQLCIPHER_DIR/libsqlite3.a"
  cp "$src/libsqlcipher.a" "$DARKIRC_SQLCIPHER_DIR/libsqlcipher.a"
  cp "$src/libcrypto.a" "$DARKIRC_SQLCIPHER_DIR/libcrypto.a"
  echo "Staged SQLCipher for $abi -> $DARKIRC_SQLCIPHER_DIR"
}

configure_sqlcipher_link_for_abi() {
  local abi="$1"
  local triple lib_dir ndk_lib_dir target_prefix
  triple="$(abi_to_triple "$abi")"
  target_prefix="$(target_env_prefix "$triple")"
  lib_dir="$SQLCIPHER_ARTIFACTS/$abi"
  ndk_lib_dir="$(ndk_sysroot_lib_dir "$abi")"

  export "${target_prefix}_SQLCIPHER_LIB_DIR=$lib_dir"
  export "${target_prefix}_SQLCIPHER_INCLUDE_DIR=$SQLCIPHER_HEADERS"
  export "${target_prefix}_SQLCIPHER_STATIC=1"

  export "CARGO_TARGET_${target_prefix}_RUSTFLAGS=-L native=${DARKIRC_SQLCIPHER_DIR} -L native=${lib_dir} -L native=${ndk_lib_dir} -l static=sqlite3 -l static=sqlcipher -l static=crypto -C link-arg=-Wl,-z,max-page-size=16384"
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

apply_android_darkirc_patches() {
  if [[ ! -f "$DARKIRC_CARGO.nighthawk-android.bak" ]]; then
    cp "$DARKIRC_CARGO" "$DARKIRC_CARGO.nighthawk-android.bak"
    cp "$DARKIRC_BUILD_RS" "$DARKIRC_BUILD_RS.nighthawk-android.bak"
  fi
  cp "$DARKIRC_CARGO.nighthawk-android.bak" "$DARKIRC_CARGO"
  cp "$DARKIRC_BUILD_RS.nighthawk-android.bak" "$DARKIRC_BUILD_RS"

  # rusqlite "bundled" does not link on x86_64-linux-android; use repo SQLCipher static libs.
  perl -i -pe 's/features = \["bundled"\]/features = ["sqlcipher"]/ if /rusqlite/' "$DARKIRC_CARGO"

  if ! grep -q 'rustc-link-lib=static=crypto' "$DARKIRC_BUILD_RS"; then
    perl -i -pe '
      if (/cargo:rustc-link-search=.*sqlcipher/) {
        $_ .= qq{        println!("cargo:rustc-link-lib=static=crypto");\n};
      }
    ' "$DARKIRC_BUILD_RS"
  fi
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

ensure_sqlcipher_artifacts
apply_android_darkirc_patches
compile_event_graph_zkas_proofs

build_one() {
  local abi="$1"
  local triple
  triple="$(abi_to_triple "$abi")"
  stage_darkirc_sqlcipher_libs "$abi"
  configure_sqlcipher_link_for_abi "$abi"
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
