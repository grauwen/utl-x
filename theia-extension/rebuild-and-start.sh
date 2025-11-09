#!/bin/bash

# UTLX Theia Extension - Rebuild and Restart Script
# This script rebuilds everything from scratch after code changes

set -e  # Exit on any error
set -x  # Print each command before executing (verbose mode)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXTENSION_DIR="$SCRIPT_DIR/utlx-theia-extension"
BROWSER_APP_DIR="$SCRIPT_DIR/browser-app"

echo "================================"
echo "UTLX Theia Rebuild & Restart"
echo "================================"
echo "Script Dir: $SCRIPT_DIR"
echo "Extension Dir: $EXTENSION_DIR"
echo "Browser App Dir: $BROWSER_APP_DIR"
echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# Step 1: Kill old server FIRST
echo "Step 1/6: Stopping all Theia processes..."

echo "Killing all 'theia start' processes..."
pkill -9 -f "theia start" 2>/dev/null || true

echo "Killing all node processes with 'theia' in command..."
pkill -9 -f "node.*theia" 2>/dev/null || true

echo "Killing ALL processes using port 4000 (server + any browser connections)..."
PIDS=$(lsof -ti:4000 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    echo "Found PIDs on port 4000: $PIDS"
    # Show what we're killing
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
echo "✓ All servers stopped at $(date '+%H:%M:%S')"
echo ""

# Step 2: Clean TypeScript output
echo "Step 2/6: Cleaning TypeScript output..."
cd "$EXTENSION_DIR"
echo "Changed to: $(pwd)"
echo "Removing: lib/ *.tsbuildinfo"
rm -rfv lib *.tsbuildinfo 2>/dev/null | head -20
echo "✓ TypeScript output cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 3: Build extension (TypeScript + copy CSS)
echo "Step 3/7: Building extension (TypeScript + CSS)..."
echo "Working directory: $(pwd)"
npx tsc && mkdir -p lib/browser && cp -r src/browser/style lib/browser/
echo "✓ Extension built at $(date '+%H:%M:%S')"
echo ""

# Step 4: Force refresh extension in browser-app's node_modules
echo "Step 4/7: Refreshing extension in browser-app..."
cd "$BROWSER_APP_DIR"
echo "Changed to: $(pwd)"
echo "Removing stale extension from node_modules..."
rm -rf node_modules/utlx-theia-extension
echo "Forcing yarn to re-install extension from ../utlx-theia-extension..."
yarn install --check-files --network-timeout 100000
echo "✓ Extension refreshed at $(date '+%H:%M:%S')"
echo ""

# Step 5: Clean webpack cache AND frontend bundle (MORE AGGRESSIVE)
echo "Step 5/7: Cleaning webpack cache and frontend bundle..."
echo "Removing: .theia lib node_modules/.cache"
rm -rfv .theia lib node_modules/.cache 2>/dev/null | head -20
# Also remove webpack's persistent cache if it exists
if [ -d "node_modules/.cache/webpack" ]; then
    echo "Removing webpack persistent cache..."
    rm -rf node_modules/.cache/webpack
fi
echo "✓ Cache and bundle cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 6: Build browser app (with cache disabled)
echo "Step 6/7: Building browser app with webpack..."
echo "Build command: NODE_ENV=production npx theia build --mode production"
# Disable webpack caching to force full rebuild
export NODE_ENV=production
npx theia build --mode production
echo "✓ Browser app built at $(date '+%H:%M:%S')"
echo ""

# Step 7: Start new server in background
echo "Step 7/7: Starting Theia server on port 4000..."
echo "Server command: nohup npx theia start --hostname=0.0.0.0 --port=4000 ~/data/utlx-workspace"
echo ""
LOG_FILE="$BROWSER_APP_DIR/theia-server.log"
echo "Output will be logged to: $LOG_FILE"
echo ""
set +x  # Disable command echo for server output
nohup npx theia start --hostname=0.0.0.0 --port=4000 ~/data/utlx-workspace > "$LOG_FILE" 2>&1 &
SERVER_PID=$!
echo ""
echo "================================"
echo "✓ Server started in background"
echo "PID: $SERVER_PID"
echo "Access at: http://localhost:4000"
echo "View logs: tail -f $LOG_FILE"
echo "Stop server: kill $SERVER_PID"
echo "Started at: $(date '+%H:%M:%S')"
echo "================================"
echo ""
