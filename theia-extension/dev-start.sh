#!/bin/bash
# Start development environment with watch mode

echo "🚀 Starting UTL-X Development Environment"
echo "=========================================="
echo ""

# Check if daemon is running
if ! curl -s http://localhost:7779/api/health > /dev/null 2>&1; then
    echo "⚠️  UTLXD daemon not running. Starting it now..."
    ./dev-start-daemon.sh
    echo ""
fi

echo "Starting development servers..."
echo ""
echo "Terminal 1: Extension watch mode"
echo "Terminal 2: Browser app"
echo ""
echo "Press Ctrl+C to stop"
echo ""

# Function to cleanup on exit
cleanup() {
    echo ""
    echo "🛑 Stopping development servers..."
    kill $WATCH_PID 2>/dev/null
    kill $APP_PID 2>/dev/null
    exit 0
}

trap cleanup INT TERM

# Start extension watch mode in background
cd utlx-theia-extension
yarn watch &
WATCH_PID=$!
cd ..

# Wait a bit for initial compilation
sleep 5

# Start browser app
cd browser-app
yarn start &
APP_PID=$!
cd ..

echo "✅ Development servers started"
echo "   Extension watch: PID $WATCH_PID"
echo "   Browser app: PID $APP_PID"
echo "   Open: http://localhost:3000"
echo ""

# Wait for user interrupt
wait
