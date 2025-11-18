#!/bin/bash
#
# Build VS Code Extension for Theia Integration
# This script compiles and packages the UTLX VS Code extension
# before Theia build so it can be loaded as a plugin.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VSCODE_EXT_DIR="$SCRIPT_DIR/../vscode-extension"

echo "[Build] Building UTLX VS Code extension..."

# Check if VS Code extension directory exists
if [ ! -d "$VSCODE_EXT_DIR" ]; then
    echo "[Build] Error: VS Code extension directory not found at $VSCODE_EXT_DIR"
    exit 1
fi

cd "$VSCODE_EXT_DIR"

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "[Build] Installing VS Code extension dependencies..."
    npm install
fi

# Compile TypeScript
echo "[Build] Compiling TypeScript..."
npm run compile

# Package extension
echo "[Build] Packaging VS Code extension..."
npm run package

# Verify .vsix was created
if [ -f "utlx-language-support-1.0.0.vsix" ]; then
    echo "[Build] ✓ VS Code extension built successfully: utlx-language-support-1.0.0.vsix"
else
    echo "[Build] ✗ Failed to create VS Code extension package"
    exit 1
fi

echo "[Build] VS Code extension ready for Theia integration"
