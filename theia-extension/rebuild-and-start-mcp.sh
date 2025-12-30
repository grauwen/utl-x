#!/bin/bash

# UTLX Theia Extension - Rebuild and Start with Chrome MCP Browser
# This script rebuilds everything, starts Theia, and launches Chrome with remote debugging for MCP server access

set -e  # Exit on any error
set -x  # Print each command before executing (verbose mode)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXTENSION_DIR="$SCRIPT_DIR/utlx-theia-extension"
BROWSER_APP_DIR="$SCRIPT_DIR/browser-app"
MCP_SERVER_DIR="$SCRIPT_DIR/../playwright-mcp-server"

# CDP port for remote debugging (default 9223 for UTLX, to avoid conflict with port 9222)
CDP_PORT="${CDP_PORT:-9223}"

echo "================================"
echo "UTLX Theia Rebuild & Start (MCP)"
echo "================================"
echo "Script Dir: $SCRIPT_DIR"
echo "Extension Dir: $EXTENSION_DIR"
echo "Browser App Dir: $BROWSER_APP_DIR"
echo "MCP Server Dir: $MCP_SERVER_DIR"
echo "CDP Port: $CDP_PORT"
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

# Step 2: Kill MCP server and Chrome browsers with remote debugging
echo "Step 2/8: Stopping MCP server and Chrome browsers..."

# Kill MCP server processes
MCP_PIDS=$(pgrep -f "playwright-mcp-server" 2>/dev/null || true)
if [ -n "$MCP_PIDS" ]; then
    echo "Killing MCP server processes: $MCP_PIDS"
    echo "$MCP_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 1
else
    echo "No MCP server processes found"
fi

# Kill any Chrome with remote debugging port
CDP_PIDS=$(lsof -ti:$CDP_PORT 2>/dev/null || true)
if [ -n "$CDP_PIDS" ]; then
    echo "Killing processes on CDP port $CDP_PORT: $CDP_PIDS"
    echo "$CDP_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 2
else
    echo "CDP port $CDP_PORT is clear"
fi

echo "✓ MCP server and Chrome browsers stopped at $(date '+%H:%M:%S')"
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

# Step 8: Start Theia and launch Chrome with remote debugging
echo "Step 8/8: Starting Theia and launching Chrome with remote debugging..."
echo ""

LOG_FILE="$BROWSER_APP_DIR/theia-server.log"
echo "Starting Theia server on port 4000..."
echo "Server command: nohup npx theia start --hostname=0.0.0.0 --port=4000 ~/data/utlx-workspace"
echo "Output will be logged to: $LOG_FILE"
echo ""

set +x  # Disable command echo for cleaner output

# Start Theia in background (without remote debugging - Chrome will provide it)
nohup npx theia start --hostname=0.0.0.0 --port=4000 ~/data/utlx-workspace > "$LOG_FILE" 2>&1 &
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
echo ""

# Wait additional time for services to initialize
echo "Waiting 5 seconds for services to initialize..."
sleep 5

# Launch Chrome Canary with remote debugging
echo "Launching Chrome Canary with remote debugging on port $CDP_PORT..."
echo ""

# Find Chrome Canary path on macOS (try Canary first, fall back to regular Chrome)
CHROME_CANARY_PATH="/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"
CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

if [ -f "$CHROME_CANARY_PATH" ]; then
    BROWSER_PATH="$CHROME_CANARY_PATH"
    BROWSER_NAME="Chrome Canary"
elif [ -f "$CHROME_PATH" ]; then
    BROWSER_PATH="$CHROME_PATH"
    BROWSER_NAME="Chrome"
    echo "⚠️  Chrome Canary not found, using regular Chrome instead"
    echo "   To install Chrome Canary: https://www.google.com/chrome/canary/"
    echo ""
else
    echo "✗ Neither Chrome Canary nor Chrome found"
    echo "Please install Chrome Canary: https://www.google.com/chrome/canary/"
    echo "Or regular Chrome: https://www.google.com/chrome/"
    exit 1
fi

# Launch Chrome Canary with remote debugging, clean profile
"$BROWSER_PATH" \
    --remote-debugging-port=$CDP_PORT \
    --user-data-dir="$HOME/.utlx-chrome-canary-profile" \
    --no-first-run \
    --no-default-browser-check \
    http://localhost:4000 &

CHROME_PID=$!

echo "✓ $BROWSER_NAME launched (PID: $CHROME_PID)"
echo ""

# Wait for Chrome to fully start and connect to Theia
echo "Waiting 3 seconds for Chrome to connect..."
sleep 3

# Start Playwright MCP Server
echo "Starting Playwright MCP server..."
echo ""

if [ ! -d "$MCP_SERVER_DIR" ]; then
    echo "✗ MCP server directory not found: $MCP_SERVER_DIR"
    echo "Skipping MCP server startup"
else
    cd "$MCP_SERVER_DIR"

    # Check if node_modules exists
    if [ ! -d "node_modules" ]; then
        echo "Installing MCP server dependencies..."
        npm install
    fi

    # Start MCP server in background with custom CDP port
    MCP_LOG_FILE="$MCP_SERVER_DIR/mcp-server.log"
    echo "MCP server command: CDP_URL=http://localhost:$CDP_PORT npm start"
    echo "Output will be logged to: $MCP_LOG_FILE"
    echo ""

    CDP_URL="http://localhost:$CDP_PORT" nohup npm start > "$MCP_LOG_FILE" 2>&1 &
    MCP_PID=$!

    echo "✓ Playwright MCP server started (PID: $MCP_PID)"
    echo ""
fi

echo ""
echo "================================"
echo "✓ Rebuild and Start Complete"
echo "================================"
echo ""
echo "Theia Status:"
echo "  PID: $SERVER_PID"
echo "  URL: http://localhost:4000"
echo "  Logs: tail -f $LOG_FILE"
echo ""
echo "$BROWSER_NAME:"
echo "  PID: $CHROME_PID"
echo "  Remote Debugging: http://localhost:$CDP_PORT"
echo "  Profile: ~/.utlx-chrome-canary-profile"
echo ""
if [ -n "$MCP_PID" ]; then
    echo "Playwright MCP Server:"
    echo "  PID: $MCP_PID"
    echo "  Connected to: http://localhost:$CDP_PORT (CDP)"
    echo "  Logs: tail -f $MCP_LOG_FILE"
    echo ""
fi
echo "To stop:"
echo "  Theia: kill $SERVER_PID"
echo "  $BROWSER_NAME: Close browser window or kill $CHROME_PID"
if [ -n "$MCP_PID" ]; then
    echo "  MCP Server: kill $MCP_PID"
fi
echo ""
echo "Finished at: $(date '+%H:%M:%S')"
echo "================================"
echo ""
