#!/bin/bash
# Quick test script to verify setup

echo "🧪 Testing UTL-X Setup"
echo "======================"
echo ""

# Test 1: Check extension build
echo "Test 1: Extension build..."
if [ -d "utlx-theia-extension/lib" ]; then
    echo "✅ Extension compiled"
else
    echo "❌ Extension not compiled. Run: cd utlx-theia-extension && yarn build"
fi

# Test 2: Check daemon
echo ""
echo "Test 2: UTLXD daemon..."
if curl -s http://localhost:7779/api/health > /dev/null 2>&1; then
    RESPONSE=$(curl -s http://localhost:7779/api/health)
    echo "✅ Daemon running: $RESPONSE"
else
    echo "❌ Daemon not running. Run: ./dev-start-daemon.sh"
fi

# Test 3: Check dependencies
echo ""
echo "Test 3: Dependencies..."
if [ -d "utlx-theia-extension/node_modules/@theia/core" ]; then
    VERSION=$(node -p "require('./utlx-theia-extension/node_modules/@theia/core/package.json').version")
    echo "✅ Theia installed: $VERSION"
else
    echo "❌ Dependencies not installed. Run: ./dev-setup.sh"
fi

# Test 4: Check browser-app
echo ""
echo "Test 4: Browser app..."
if [ -d "browser-app/.theia" ]; then
    echo "✅ Browser app built"
else
    echo "⚠️  Browser app not built yet (will build on first start)"
fi

echo ""
echo "Setup verification complete!"
