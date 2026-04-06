#!/bin/bash
# Development setup script for UTL-X Theia Extension
# Run this script once after cloning the repository

set -e

echo "🚀 UTL-X Theia Extension - Development Setup"
echo "=============================================="
echo ""

# Check prerequisites
echo "📋 Checking prerequisites..."

if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found. Please install Node.js 18+ first."
    exit 1
fi

if ! command -v yarn &> /dev/null; then
    echo "❌ Yarn not found. Please install Yarn first."
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 11+ first."
    exit 1
fi

echo "✅ Node.js $(node --version)"
echo "✅ Yarn $(yarn --version)"
echo "✅ Java $(java -version 2>&1 | head -n 1)"
echo ""

# Build UTLXD daemon
echo "🔨 Building UTLXD daemon..."
cd ..
if [ -f "gradlew" ]; then
    ./gradlew :modules:server:build -x test
    echo "✅ UTLXD daemon built successfully"
else
    echo "⚠️  Gradle wrapper not found. Skipping daemon build."
    echo "   Please build manually: ./gradlew :modules:server:build"
fi
cd theia-extension
echo ""

# Install extension dependencies
echo "📦 Installing extension dependencies..."
cd utlx-theia-extension
yarn install
echo "✅ Extension dependencies installed"
echo ""

# Build extension
echo "🔨 Building extension..."
yarn build
echo "✅ Extension built successfully"
echo ""

# Link extension
echo "🔗 Creating global link..."
yarn link
echo "✅ Extension linked"
echo ""

# Install browser-app dependencies
echo "📦 Installing browser app dependencies..."
cd ../browser-app
yarn link utlx-theia-extension
yarn install
echo "✅ Browser app dependencies installed"
echo ""

echo "✨ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Start UTLXD daemon: ./dev-start-daemon.sh"
echo "  2. Start development: ./dev-start.sh"
echo "  3. Open browser: http://localhost:3000"
echo ""
