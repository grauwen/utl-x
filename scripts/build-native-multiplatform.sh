#!/bin/bash

set -e

echo "🌍 Building UTL-X for Multiple Platforms"
echo "========================================="

PLATFORMS=("linux-x64" "linux-arm64" "macos-x64" "macos-arm64" "windows-x64")
OUTPUT_DIR="dist"

mkdir -p "$OUTPUT_DIR"

for PLATFORM in "${PLATFORMS[@]}"; do
    echo ""
    echo "🔨 Building for $PLATFORM..."
    
    case $PLATFORM in
        linux-x64)
            ./gradlew :modules:cli:nativeCompile \
                -Dtarget.platform=linux-amd64
            cp modules/cli/build/native/nativeCompile/utlx \
                "$OUTPUT_DIR/utlx-$PLATFORM"
            ;;
        linux-arm64)
            ./gradlew :modules:cli:nativeCompile \
                -Dtarget.platform=linux-aarch64
            cp modules/cli/build/native/nativeCompile/utlx \
                "$OUTPUT_DIR/utlx-$PLATFORM"
            ;;
        macos-x64)
            ./gradlew :modules:cli:nativeCompile \
                -Dtarget.platform=darwin-amd64
            cp modules/cli/build/native/nativeCompile/utlx \
                "$OUTPUT_DIR/utlx-$PLATFORM"
            ;;
        macos-arm64)
            ./gradlew :modules:cli:nativeCompile \
                -Dtarget.platform=darwin-aarch64
            cp modules/cli/build/native/nativeCompile/utlx \
                "$OUTPUT_DIR/utlx-$PLATFORM"
            ;;
        windows-x64)
            ./gradlew :modules:cli:nativeCompile \
                -Dtarget.platform=windows-amd64
            cp modules/cli/build/native/nativeCompile/utlx.exe \
                "$OUTPUT_DIR/utlx-$PLATFORM.exe"
            ;;
    esac
    
    echo "✅ $PLATFORM build complete"
done

echo ""
echo "✨ All builds complete! Binaries in $OUTPUT_DIR/"
ls -lh "$OUTPUT_DIR/"
