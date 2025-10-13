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

# GraalVM version
GRAALVM_VERSION="21.0.1"
JAVA_VERSION="21"

echo "🔍 Detected: $OS_TYPE-$ARCH_TYPE"
echo "📥 Downloading GraalVM $GRAALVM_VERSION (Java $JAVA_VERSION)..."

# Download URL
DOWNLOAD_URL="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_${OS_TYPE}-${ARCH_TYPE}_bin.tar.gz"

# Download and extract
INSTALL_DIR="$HOME/.graalvm"
mkdir -p "$INSTALL_DIR"

curl -L "$DOWNLOAD_URL" | tar xz -C "$INSTALL_DIR" --strip-components=1

# Install native-image
echo "🔧 Installing native-image component..."
"$INSTALL_DIR/bin/gu" install native-image

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
