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

# Kill Playwright MCP server processes
MCP_PIDS=$(pgrep -f "playwright-mcp-server" 2>/dev/null || true)
if [ -n "$MCP_PIDS" ]; then
    echo "Killing Playwright MCP server processes: $MCP_PIDS"
    echo "$MCP_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 1
else
    echo "No Playwright MCP server processes found"
fi

# Kill the UTL-X LLM MCP server (port 7780) — including an ORPHAN left over from
# a previous Theia run. Without this the Theia backend's auto-start hits
# EADDRINUSE and the stale (old-build) MCP keeps serving the AI Assist panel.
UTLX_MCP_PORT_NUM="${UTLX_MCP_PORT:-7780}"
# LISTENER only — a plain `lsof -ti:$PORT` also matches *clients connected to* the MCP
# (the Theia backend holds such a connection), so it would kill Theia too. `-sTCP:LISTEN`
# restricts to the server socket; the title-based backstop below covers orphans.
UTLX_MCP_PIDS=$(lsof -nP -iTCP:"$UTLX_MCP_PORT_NUM" -sTCP:LISTEN -t 2>/dev/null || true)
if [ -n "$UTLX_MCP_PIDS" ]; then
    echo "Killing UTL-X MCP server on port $UTLX_MCP_PORT_NUM: $UTLX_MCP_PIDS"
    echo "$UTLX_MCP_PIDS" | xargs kill -9 2>/dev/null || true
    sleep 1
else
    echo "UTL-X MCP port $UTLX_MCP_PORT_NUM is clear"
fi
# Backstop: any lingering UTLX MCP process on ANY port. The server sets its
# process title to "utlx-mcp-http-<port>", which replaces the node/dist cmdline,
# so we must match the title (this also clears legacy orphans, e.g. on old 3001).
pgrep -f "utlx-mcp-http" 2>/dev/null | xargs kill -9 2>/dev/null || true

# NOTE: UTLXD is intentionally NOT killed here. It is the heavy JVM daemon and is
# handled idempotently in the "start backend services" step below — reused if
# already healthy, started only if down — so frequent rebuilds don't needlessly
# restart it. (The MCP above IS killed, since it's rebuilt every cycle.)

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

# Step 3: Install extension dependencies (if needed)
echo "Step 3/9: Checking extension dependencies..."
cd "$EXTENSION_DIR"
echo "Changed to: $(pwd)"
if [ ! -d "node_modules" ] || [ "package.json" -nt "node_modules/.yarn-integrity" ]; then
    echo "Dependencies changed or missing — running yarn install..."
    yarn install --network-timeout 100000
    echo "✓ Extension dependencies installed at $(date '+%H:%M:%S')"
else
    echo "✓ Extension dependencies up to date (skipped yarn install)"
fi
echo ""

# Step 4: Clean TypeScript output
echo "Step 4/9: Cleaning TypeScript output..."
echo "Removing: lib/ *.tsbuildinfo"
rm -rfv lib *.tsbuildinfo 2>/dev/null | head -20
echo "✓ TypeScript output cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 5: Build extension (TypeScript + copy CSS)
echo "Step 5/9: Building extension (TypeScript + CSS)..."
echo "Working directory: $(pwd)"
npx tsc && mkdir -p lib/browser && cp -r src/browser/style lib/browser/
echo "✓ Extension built at $(date '+%H:%M:%S')"
echo ""

# Step 6: Force refresh extension in browser-app's node_modules
echo "Step 6/9: Refreshing extension in browser-app..."
cd "$BROWSER_APP_DIR"
echo "Changed to: $(pwd)"
echo "Removing stale extension from node_modules..."
rm -rf node_modules/utlx-theia-extension
echo "Forcing yarn to re-install extension from ../utlx-theia-extension..."
yarn install --check-files --network-timeout 100000
echo "✓ Extension refreshed at $(date '+%H:%M:%S')"
echo ""

# Step 7: Clean webpack cache AND frontend bundle
echo "Step 7/9: Cleaning webpack cache and frontend bundle..."
echo "Removing: .theia lib node_modules/.cache"
rm -rfv .theia lib node_modules/.cache 2>/dev/null | head -20
if [ -d "node_modules/.cache/webpack" ]; then
    echo "Removing webpack persistent cache..."
    rm -rf node_modules/.cache/webpack
fi
echo "✓ Cache and bundle cleaned at $(date '+%H:%M:%S')"
echo ""

# Step 8: Build browser app
echo "Step 8/9: Building browser app with webpack..."
echo "Build command: NODE_ENV=production npx theia build --mode production --progress --stats verbose"
export NODE_ENV=production
npx theia build --mode production --progress --stats verbose
echo "✓ Browser app built at $(date '+%H:%M:%S')"
echo ""

# Step 8.5: Start backend services (utlxd + MCP) — owned by THIS script.
# Theia's own auto-start is disabled (AUTO_START_SERVICES=false at Step 9) so
# there is exactly one owner and no port fights.
echo "Step 8.5/9: Starting backend services (utlxd + MCP)..."

REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
UTLXD_API_PORT="${UTLXD_REST_PORT:-7779}"
UTLXD_LSP_PORT="${UTLXD_LSP_PORT:-7777}"
UTLXD_JAR="${UTLXD_JAR_PATH:-$REPO_ROOT/modules/daemon/build/libs/utlxd-1.0.0-SNAPSHOT.jar}"
MCP_PORT="${UTLX_MCP_PORT:-7780}"

# --- utlxd: ensure running (idempotent — reuse if already healthy, don't restart the heavy JVM) ---
if curl -s "http://localhost:$UTLXD_API_PORT/api/health" > /dev/null 2>&1; then
    echo "  ✓ UTLXD already healthy on :$UTLXD_API_PORT — reusing (no restart)"
else
    if [ ! -f "$UTLXD_JAR" ]; then
        echo "  ✗ UTLXD jar not found: $UTLXD_JAR"
        echo "    Build it first (e.g. ./gradlew :modules:daemon:build)."
        exit 1
    fi
    echo "  Starting UTLXD: java -jar <jar> start --lsp --lsp-transport socket --lsp-port $UTLXD_LSP_PORT --api --api-port $UTLXD_API_PORT"
    nohup java -jar "$UTLXD_JAR" start \
        --lsp --lsp-transport socket --lsp-port "$UTLXD_LSP_PORT" \
        --api --api-port "$UTLXD_API_PORT" > /tmp/utlxd-theia.log 2>&1 &
    utlxd_ok=false
    for i in $(seq 1 60); do
        if curl -s "http://localhost:$UTLXD_API_PORT/api/health" > /dev/null 2>&1; then
            utlxd_ok=true; break
        fi
        sleep 0.5
    done
    if [ "$utlxd_ok" != true ]; then
        echo "  ✗ UTLXD did not become healthy (see /tmp/utlxd-theia.log)"
        exit 1
    fi
    echo "  ✓ UTLXD healthy on :$UTLXD_API_PORT"
fi

# --- MCP: rebuild dist from src, THEN (re)start so it reflects the latest code (claude-code, :$MCP_PORT) ---
echo "  Building UTLX MCP server (tsc + assets) ..."
if ( cd "$REPO_ROOT/mcp-server" && npm run build ) > /tmp/mcp-build-theia.log 2>&1; then
    echo "  ✓ MCP server built (dist up to date)"
else
    echo "  ✗ MCP server build FAILED (see /tmp/mcp-build-theia.log):"
    tail -20 /tmp/mcp-build-theia.log
    exit 1
fi
echo "  (Re)starting UTLX MCP on :$MCP_PORT via mcp-server.sh (claude-code)..."
"$REPO_ROOT/mcp-server/mcp-server.sh" > /tmp/mcp-server-theia.log 2>&1
mcp_ok=false
for i in $(seq 1 30); do
    if curl -s "http://localhost:$MCP_PORT/health" > /dev/null 2>&1; then
        mcp_ok=true; break
    fi
    sleep 0.5
done
if [ "$mcp_ok" = true ]; then
    echo "  ✓ MCP healthy on :$MCP_PORT"
else
    echo "  ⚠ MCP not healthy yet (see /tmp/mcp-server-theia.log) — continuing"
fi
echo "✓ Backend services started at $(date '+%H:%M:%S')"
echo ""

# Step 9: Start Theia and launch Chrome with remote debugging
echo "Step 9/9: Starting Theia and launching Chrome with remote debugging..."
echo ""

LOG_FILE="$BROWSER_APP_DIR/theia-server.log"
echo "Starting Theia server on port 4000..."
echo "Server command: nohup npx theia start --hostname=0.0.0.0 --port=4000 ~/data/utlx-workspace"
echo "Output will be logged to: $LOG_FILE"
echo ""

set +x  # Disable command echo for cleaner output

# Start Theia in background (without remote debugging - Chrome will provide it).
# AUTO_START_SERVICES=false: this script owns utlxd + MCP (Step 8.5), so Theia's
# ServiceLifecycleManager must NOT also try to start them (avoids the Ollama MCP
# and port fights).
export AUTO_START_SERVICES=false
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

# Launch Chrome with remote debugging
echo "Launching Chrome with remote debugging on port $CDP_PORT..."
echo ""

# Find Chrome on macOS (prefer regular Chrome; fall back to Canary if present)
CHROME_PATH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
CHROME_CANARY_PATH="/Applications/Google Chrome Canary.app/Contents/MacOS/Google Chrome Canary"

if [ -f "$CHROME_PATH" ]; then
    BROWSER_PATH="$CHROME_PATH"
    BROWSER_NAME="Chrome"
elif [ -f "$CHROME_CANARY_PATH" ]; then
    BROWSER_PATH="$CHROME_CANARY_PATH"
    BROWSER_NAME="Chrome Canary"
else
    echo "✗ Google Chrome not found"
    echo "Please install Chrome: https://www.google.com/chrome/"
    exit 1
fi

# Bust the service-worker + HTTP cache in the launch profile so a freshly rebuilt frontend is
# actually loaded. Theia registers a service worker that caches the app shell, so WITHOUT this a
# rebuild keeps serving the OLD bundle even on a hard reload (a new window on the same profile
# isn't enough — the SW lives in the profile). We delete only the caches; Local Storage / IndexedDB
# (Theia layout, open editors) are preserved.
# NOTE: the "-canary" in the profile name is historical — this is a DEDICATED profile used by
# whichever browser we launch ($BROWSER_NAME, regular Chrome by default), not Chrome Canary. The
# path is kept stable on purpose so the profile (and its Theia layout) survives across runs.
CHROME_PROFILE="$HOME/.utlx-chrome-canary-profile"
echo "Clearing service-worker + HTTP cache in $CHROME_PROFILE (so the new build loads)..."
rm -rf "$CHROME_PROFILE/Default/Service Worker" \
       "$CHROME_PROFILE/Default/Cache" \
       "$CHROME_PROFILE/Default/Code Cache" \
       "$CHROME_PROFILE/Default/GPUCache" 2>/dev/null || true

# Launch the selected browser ($BROWSER_NAME — regular Chrome by default; Canary only as fallback)
# with remote debugging, into the dedicated profile above.
"$BROWSER_PATH" \
    --remote-debugging-port=$CDP_PORT \
    --user-data-dir="$HOME/.utlx-chrome-canary-profile" \
    --no-first-run \
    --no-default-browser-check \
    --disable-background-networking \
    --disable-sync \
    --disable-component-update \
    --disable-domain-reliability \
    http://localhost:4000 > /tmp/utlx-chrome.log 2>&1 &

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
