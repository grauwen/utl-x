#!/bin/bash

# UTLX Theia Extension - Rebuild and Test Script
# This script rebuilds everything and runs Playwright conformance tests

set -e  # Exit on any error
set -x  # Print each command before executing (verbose mode)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXTENSION_DIR="$SCRIPT_DIR/utlx-theia-extension"
BROWSER_APP_DIR="$SCRIPT_DIR/browser-app"
CONFORMANCE_DIR="$SCRIPT_DIR/../conformance-suite/theia-extension"

# Parse arguments
HEADED=true  # Default: headed (visible browser)
if [ "$1" = "--headless" ]; then
    HEADED=false
fi

echo "================================"
echo "UTLX Theia Rebuild & Test"
echo "================================"
echo "Script Dir: $SCRIPT_DIR"
echo "Extension Dir: $EXTENSION_DIR"
echo "Browser App Dir: $BROWSER_APP_DIR"
echo "Conformance Dir: $CONFORMANCE_DIR"
echo "Headed Mode: $HEADED"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Step 1: Kill old Theia server
echo "Step 1/8: Stopping all Theia processes..."

echo "Killing all 'theia start' processes..."
pkill -9 -f "theia start" 2>/dev/null || true

echo "Killing all node processes with 'theia' in command..."
pkill -9 -f "node.*theia" 2>/dev/null || true

echo "Killing ALL processes using port 4000..."
PIDS=$(lsof -ti:4000 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    echo "Found PIDs on port 4000: $PIDS"
    for PID in $PIDS; do
        PROC_NAME=$(ps -p $PID -o comm= 2>/dev/null || echo "unknown")
        echo "  Killing PID $PID ($PROC_NAME)"
    done
    echo "$PIDS" | xargs kill -9 2>/dev/null || true
else
    echo "Port 4000 is clear"
fi

echo "Waiting for clean shutdown..."
sleep 3

# Verify port 4000 is free
REMAINING=$(lsof -ti:4000 2>/dev/null || true)
if [ -n "$REMAINING" ]; then
    echo "WARNING: Port 4000 still in use by PID $REMAINING - forcing kill..."
    echo "$REMAINING" | xargs kill -9 2>/dev/null || true
    sleep 1
fi
echo "✓ Theia stopped at $(date '+%H:%M:%S')"
echo ""

# Step 2: Kill Playwright Chrome test browsers
echo "Step 2/8: Stopping Playwright Chrome test browsers..."

# Kill Chromium processes launched by Playwright
CHROME_PIDS=$(pgrep -f "chromium.*playwright" 2>/dev/null || true)
if [ -n "$CHROME_PIDS" ]; then
    echo "Killing Playwright Chromium processes: $CHROME_PIDS"
    echo "$CHROME_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 2
else
    echo "No Playwright Chromium processes found"
fi

# Kill any Chrome with remote debugging port
CDP_PIDS=$(lsof -ti:9222 2>/dev/null || true)
if [ -n "$CDP_PIDS" ]; then
    echo "Killing processes on CDP port 9222: $CDP_PIDS"
    echo "$CDP_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 1
else
    echo "CDP port 9222 is clear"
fi

echo "✓ Chrome test browsers stopped at $(date '+%H:%M:%S')"
echo ""

# Step 3: Clean TypeScript output
echo "Step 3/8: Cleaning TypeScript output..."
cd "$EXTENSION_DIR"
echo "Changed to: $(pwd)"
echo "Removing: lib/ *.tsbuildinfo"
rm -rfv lib *.tsbuildinfo 2>/dev/null | head -20
echo "✓ TypeScript output cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 4: Build extension (TypeScript + copy CSS)
echo "Step 4/8: Building extension (TypeScript + CSS)..."
echo "Working directory: $(pwd)"
npx tsc && mkdir -p lib/browser && cp -r src/browser/style lib/browser/
echo "✓ Extension built at $(date '+%H:%M:%S')"
echo ""

# Step 5: Force refresh extension in browser-app's node_modules
echo "Step 5/8: Refreshing extension in browser-app..."
cd "$BROWSER_APP_DIR"
echo "Changed to: $(pwd)"
echo "Removing stale extension from node_modules..."
rm -rf node_modules/utlx-theia-extension
echo "Forcing yarn to re-install extension from ../utlx-theia-extension..."
yarn install --check-files --network-timeout 100000
echo "✓ Extension refreshed at $(date '+%H:%M:%S')"
echo ""

# Step 6: Clean webpack cache AND frontend bundle
echo "Step 6/8: Cleaning webpack cache and frontend bundle..."
echo "Removing: .theia lib node_modules/.cache"
rm -rfv .theia lib node_modules/.cache 2>/dev/null | head -20
if [ -d "node_modules/.cache/webpack" ]; then
    echo "Removing webpack persistent cache..."
    rm -rf node_modules/.cache/webpack
fi
echo "✓ Cache and bundle cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 7: Build browser app
echo "Step 7/8: Building browser app with webpack..."
echo "Build command: NODE_ENV=production npx theia build --mode production --progress --stats verbose"
export NODE_ENV=production
npx theia build --mode production --progress --stats verbose
echo "✓ Browser app built at $(date '+%H:%M:%S')"
echo ""

# Step 8: Start Theia with remote debugging + Run Playwright tests
echo "Step 8/8: Starting Theia and running Playwright tests..."
echo ""

LOG_FILE="$BROWSER_APP_DIR/theia-server.log"
echo "Starting Theia server on port 4000 with remote debugging..."
echo "Server command: nohup npx theia start --hostname=0.0.0.0 --port=4000 --remote-debugging-port=9222 ~/data/utlx-workspace"
echo "Output will be logged to: $LOG_FILE"
echo ""

set +x  # Disable command echo for cleaner output

# Start Theia in background with remote debugging
nohup npx theia start --hostname=0.0.0.0 --port=4000 --remote-debugging-port=9222 ~/data/utlx-workspace > "$LOG_FILE" 2>&1 &
SERVER_PID=$!

echo "Theia started (PID: $SERVER_PID)"
echo "Waiting for Theia to be ready on port 4000..."

# Health check with timeout
TIMEOUT=60
ELAPSED=0
READY=false

while [ $ELAPSED -lt $TIMEOUT ]; do
    if curl -s http://localhost:4000 > /dev/null 2>&1; then
        READY=true
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo -n "."
done

echo ""

if [ "$READY" = false ]; then
    echo "✗ Theia failed to start within $TIMEOUT seconds"
    echo "Check logs: cat $LOG_FILE"
    exit 1
fi

echo "✓ Theia is ready on http://localhost:4000"
echo "✓ Remote debugging available on port 9222"
echo ""

# Wait additional time for services to initialize
echo "Waiting 10 seconds for services to initialize..."
sleep 10

# Run Playwright tests
echo "Running Playwright conformance tests..."
echo "Changed to: $CONFORMANCE_DIR"
cd "$CONFORMANCE_DIR"

set -x  # Re-enable command echo for test execution

if [ "$HEADED" = true ]; then
    echo "Running tests in headed mode (visible browser)..."
    npm run test:headed
else
    echo "Running tests in headless mode (no browser window)..."
    npm test
fi

TEST_EXIT=$?

set +x

echo ""
if [ $TEST_EXIT -eq 0 ]; then
    echo "✓ All Playwright tests passed!"
else
    echo "✗ Some tests failed (exit code: $TEST_EXIT)"
    echo "Check test output above for details"
fi

echo ""
echo "================================"
echo "✓ Rebuild and Test Complete"
echo "================================"
echo ""
echo "Theia Status:"
echo "  PID: $SERVER_PID"
echo "  URL: http://localhost:4000"
echo "  Remote Debugging: http://localhost:9222"
echo "  Logs: tail -f $LOG_FILE"
echo ""
echo "Test Results:"
if [ $TEST_EXIT -eq 0 ]; then
    echo "  Status: ✓ PASSED"
else
    echo "  Status: ✗ FAILED (exit code: $TEST_EXIT)"
fi
echo ""
echo "To stop Theia:"
echo "  kill $SERVER_PID"
echo ""
echo "Finished at: $(date '+%H:%M:%S')"
echo "================================"
echo ""

# Exit with test result
exit $TEST_EXIT
