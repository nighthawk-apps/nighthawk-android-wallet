#!/bin/bash
# Build darkfi-mobile-ffi static library for iOS simulator (aarch64-apple-ios-sim).
#
# This script solves the sqlcipher cross-compilation issue by:
# 1. Setting the CC/AR toolchain to the iOS simulator SDK
# 2. Setting CFLAGS for the simulator target
# 3. Building with bundled-sqlcipher-vendored-openssl (C sources compile in-tree)
#
# Usage: ./build_ios_sim.sh [--release]
#
# Prerequisites:
#   - Xcode with iOS SDK installed
#   - Rust target: rustup target add aarch64-apple-ios-sim

set -euo pipefail

PROFILE="${1:---release}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FFI_DIR="$SCRIPT_DIR/../rust/darkfi-mobile-ffi"
XCFW_DIR="$SCRIPT_DIR/../nighthawk-ios-wallet/modules/Sources/DarkfiCore/DarkfiCore.xcframework"

# Find the iOS Simulator SDK
IOS_SIM_SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
echo "📱 iOS Simulator SDK: $IOS_SIM_SDK"

# Cross-compilation environment for C code (sqlcipher, openssl)
export TARGET_CC="$(xcrun --sdk iphonesimulator --find clang)"
export TARGET_AR="$(xcrun --sdk iphonesimulator --find ar)"
export CC_aarch64_apple_ios_sim="$TARGET_CC"
export AR_aarch64_apple_ios_sim="$TARGET_AR"

# CFLAGS for iOS simulator aarch64
export CFLAGS_aarch64_apple_ios_sim="-target aarch64-apple-ios-simulator -isysroot $IOS_SIM_SDK -mios-simulator-version-min=16.0 -fembed-bitcode"

# For vendored OpenSSL (used by bundled-sqlcipher-vendored-openssl)
export OPENSSL_NO_VENDOR=0

# Cargo configuration
export CARGO_BUILD_TARGET="aarch64-apple-ios-sim"

echo "🔨 Building darkfi-mobile-ffi for aarch64-apple-ios-sim..."
cd "$FFI_DIR"
cargo build \
    --target aarch64-apple-ios-sim \
    --no-default-features \
    $PROFILE \
    2>&1

LIB_PATH="$FFI_DIR/../../rust/target/aarch64-apple-ios-sim"
if [ "$PROFILE" = "--release" ]; then
    LIB_PATH="$LIB_PATH/release"
else
    LIB_PATH="$LIB_PATH/debug"
fi

STATIC_LIB="$LIB_PATH/libdarkfi_mobile_ffi.a"

if [ -f "$STATIC_LIB" ]; then
    echo "✅ Built: $STATIC_LIB"
    echo "   Size: $(du -h "$STATIC_LIB" | cut -f1)"

    # Copy to XCFramework if the directory exists
    if [ -d "$XCFW_DIR/ios-arm64-simulator" ]; then
        cp "$STATIC_LIB" "$XCFW_DIR/ios-arm64-simulator/universal-sim-libdarkfi_mobile_ffi.a"
        echo "📦 Updated XCFramework at $XCFW_DIR"
    fi
else
    echo "❌ Build failed — static library not found at $STATIC_LIB"
    exit 1
fi

echo ""
echo "To build the iOS app:"
echo "  cd nighthawk-ios-wallet && xcodebuild -scheme stealth -sdk iphonesimulator -arch arm64 build"
