#!/bin/bash
# Start UTLXD daemon for development

set -e

DAEMON_JAR="../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar"
DAEMON_LOG="/tmp/utlxd.log"
DAEMON_PID="/tmp/utlxd.pid"

echo "🚀 Starting UTLXD Daemon..."

# Check if daemon JAR exists
if [ ! -f "$DAEMON_JAR" ]; then
    echo "❌ UTLXD daemon not found at: $DAEMON_JAR"
    echo "   Please build it first: ./gradlew :modules:server:build"
    exit 1
fi

# Check if already running
if [ -f "$DAEMON_PID" ]; then
    PID=$(cat "$DAEMON_PID")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "⚠️  UTLXD daemon already running (PID: $PID)"
        echo "   Stop it first: ./dev-stop-daemon.sh"
        exit 1
    fi
fi

# Start daemon
java -jar "$DAEMON_JAR" start \
  --daemon-lsp \
  --daemon-rest \
  --daemon-rest-port 7779 \
  > "$DAEMON_LOG" 2>&1 &

DAEMON_PID_VALUE=$!
echo $DAEMON_PID_VALUE > "$DAEMON_PID"

# Wait for startup
echo "⏳ Waiting for daemon to start..."
sleep 3

# Verify it's running
if curl -s http://localhost:7779/api/health > /dev/null 2>&1; then
    echo "✅ UTLXD daemon started successfully (PID: $DAEMON_PID_VALUE)"
    echo "   REST API: http://localhost:7779"
    echo "   LSP: stdio"
    echo "   Logs: $DAEMON_LOG"
else
    echo "❌ Failed to start daemon. Check logs: $DAEMON_LOG"
    exit 1
fi
