#!/bin/bash
# Start UTLXD daemon for development

set -e

# Resolve the newest utlxd jar rather than naming one. This line read
#   ../modules/server/build/libs/utlxd-1.3.0.jar
# which could never work: there is no `modules/server` (the module is `modules/daemon`), and
# 1.3.0 was two releases stale. Wrong directory AND wrong version, so the script always exited
# with "UTLXD daemon not found". UTLXD_JAR overrides, matching the Theia extension.
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../modules/daemon/build/libs" 2>/dev/null && pwd)"
DAEMON_JAR="${UTLXD_JAR:-$(ls -t "$LIB_DIR"/utlxd-*.jar 2>/dev/null | head -n 1)}"
DAEMON_LOG="/tmp/utlxd.log"
DAEMON_PID="/tmp/utlxd.pid"

echo "🚀 Starting UTLXD Daemon..."

# Check if daemon JAR exists
if [ -z "$DAEMON_JAR" ] || [ ! -f "$DAEMON_JAR" ]; then
    echo "❌ UTLXD daemon not found at: $DAEMON_JAR"
    echo "   Please build it first: ./gradlew :modules:daemon:jar"
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
