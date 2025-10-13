#!/bin/bash

set -e

echo "🚀 Building UTL-X Native Binary"
echo "================================"

# Check for GraalVM
if ! command -v native-image &> /dev/null; then
    echo "❌ GraalVM native-image not found!"
    echo "Please install GraalVM and run: gu install native-image"
    exit 1
fi

echo "✅ GraalVM found: $(native-image --version)"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
./gradlew clean

# Build with Gradle
echo "🔨 Building native image..."
./gradlew :modules:cli:nativeCompile

# Test the binary
echo "🧪 Testing native binary..."
BINARY="./modules/cli/build/native/nativeCompile/utlx"

if [ -f "$BINARY" ]; then
    echo "✅ Binary created successfully!"
    echo "📦 Size: $(du -h $BINARY | cut -f1)"
    echo "🏃 Testing execution..."
    $BINARY --version
    echo "✅ Binary works!"
else
    echo "❌ Binary not found at $BINARY"
    exit 1
fi

echo ""
echo "✨ Build complete! Binary location:"
echo "   $BINARY"
echo ""
echo "To install system-wide:"
echo "   sudo cp $BINARY /usr/local/bin/"
