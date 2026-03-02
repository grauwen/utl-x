#!/bin/bash

set -e

echo "📦 Installing GraalVM"
echo "===================="

# Detect OS
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Linux*)
        OS_TYPE="linux"
        ;;
    Darwin*)
        OS_TYPE="macos"
        ;;
    *)
        echo "❌ Unsupported OS: $OS"
        exit 1
        ;;
esac

case "$ARCH" in
    x86_64)
        ARCH_TYPE="amd64"
        ;;
    arm64|aarch64)
        ARCH_TYPE="aarch64"
        ;;
    *)
        echo "❌ Unsupported architecture: $ARCH"
        exit 1
        ;;
esac

echo "🔍 Detected: $OS_TYPE-$ARCH_TYPE"

# On macOS, prefer Homebrew if available
if [ "$OS_TYPE" = "macos" ] && command -v brew &> /dev/null; then
    echo ""
    echo "🍺 Homebrew detected! Installing GraalVM CE via Homebrew (recommended)..."
    echo ""
    brew install --cask graalvm/tap/graalvm-community-jdk22

    # Determine installed version
    GRAALVM_DIR=$(ls -d /Library/Java/JavaVirtualMachines/graalvm-community-openjdk-* 2>/dev/null | sort -V | tail -1)
    if [ -z "$GRAALVM_DIR" ]; then
        echo "❌ GraalVM installation not found in /Library/Java/JavaVirtualMachines/"
        exit 1
    fi
    GRAALVM_HOME="$GRAALVM_DIR/Contents/Home"

    echo ""
    echo "✅ GraalVM installed successfully via Homebrew!"
    echo ""
    echo "Add to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
    echo ""
    echo "export GRAALVM_HOME=$GRAALVM_HOME"
    echo "export JAVA_HOME=\$GRAALVM_HOME"
    echo "export PATH=\$GRAALVM_HOME/bin:\$PATH"
    echo ""
    echo "Then run: source ~/.zshrc  (or restart your shell)"
    exit 0
fi

# Manual download path (Linux and CI environments, or macOS without Homebrew)
GRAALVM_VERSION="21.0.1"
JAVA_VERSION="21"

echo "📥 Downloading GraalVM $GRAALVM_VERSION (Java $JAVA_VERSION)..."

# Download URL
DOWNLOAD_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_${OS_TYPE}-${ARCH_TYPE}_bin.tar.gz"

# Download and extract
INSTALL_DIR="$HOME/.graalvm"
mkdir -p "$INSTALL_DIR"

curl -L "$DOWNLOAD_URL" | tar xz -C "$INSTALL_DIR" --strip-components=1

# Note: native-image is bundled with GraalVM JDK 21+, no need for 'gu install native-image'

# Setup environment
echo ""
echo "✅ GraalVM installed successfully!"
echo ""
echo "Add to your shell profile (~/.bashrc, ~/.zshrc, etc.):"
echo ""
echo "export GRAALVM_HOME=$INSTALL_DIR"
echo "export JAVA_HOME=\$GRAALVM_HOME"
echo "export PATH=\$GRAALVM_HOME/bin:\$PATH"
echo ""
echo "Then run: source ~/.bashrc  (or restart your shell)"
