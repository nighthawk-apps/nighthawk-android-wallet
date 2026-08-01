#!/usr/bin/env bash
set -e

# Base directories
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUST_DIR="$PROJECT_ROOT/rust/darkfi-mobile-ffi"
DARKIRC_DIR="$PROJECT_ROOT/third_party/darkfi/bin/darkirc"

OUT_DIR="$PROJECT_ROOT/nighthawk-ios-wallet/DarkFi"
XCFRAMEWORK_DIR="$OUT_DIR/DarkfiMobile.xcframework"

echo "Building darkfi-mobile-ffi for iOS..."

cd "$RUST_DIR"
# cargo build --release --target aarch64-apple-ios
cargo build --release --target aarch64-apple-ios-sim

# echo "Building darkirc for iOS..."
# 
# cd "$DARKIRC_DIR"
# cargo build --release --target aarch64-apple-ios
# cargo build --release --target aarch64-apple-ios-sim

echo "Creating Swift Bindings..."
cd "$RUST_DIR"
cargo run --bin uniffi-bindgen generate src/darkfi_mobile_ffi.udl --language swift --out-dir "$OUT_DIR/Sources"

echo "Lipoing libraries..."
# Create lipo for simulator (if we had x86_64, but we only have aarch64-apple-ios-sim here, which is fine for M1+ macs)
# We will just copy them directly for now

mkdir -p "$OUT_DIR/libs/ios"
mkdir -p "$OUT_DIR/libs/ios-sim"

# cp "$PROJECT_ROOT/rust/target/aarch64-apple-ios/release/libdarkfi_mobile_ffi.a" "$OUT_DIR/libs/ios/"
cp "$PROJECT_ROOT/rust/target/aarch64-apple-ios-sim/release/libdarkfi_mobile_ffi.a" "$OUT_DIR/libs/ios-sim/"

# cp "$DARKIRC_DIR/target/aarch64-apple-ios/release/darkirc" "$OUT_DIR/libs/ios/darkirc_exec"
# cp "$DARKIRC_DIR/target/aarch64-apple-ios-sim/release/darkirc" "$OUT_DIR/libs/ios-sim/darkirc_exec"

echo "Generating XCFramework..."
rm -rf "$XCFRAMEWORK_DIR"
xcodebuild -create-xcframework \
    -library "$OUT_DIR/libs/ios-sim/libdarkfi_mobile_ffi.a" \
    -output "$XCFRAMEWORK_DIR"

echo "iOS build complete! Output is at $OUT_DIR"
