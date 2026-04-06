#!/bin/bash
# Stop UTLXD daemon

DAEMON_PID="/tmp/utlxd.pid"

echo "🛑 Stopping UTLXD Daemon..."

if [ ! -f "$DAEMON_PID" ]; then
    echo "⚠️  No PID file found. Daemon may not be running."
    exit 0
fi

PID=$(cat "$DAEMON_PID")

if ps -p "$PID" > /dev/null 2>&1; then
    kill "$PID"
    echo "✅ UTLXD daemon stopped (PID: $PID)"
    rm "$DAEMON_PID"
else
    echo "⚠️  Daemon process not found (PID: $PID)"
    rm "$DAEMON_PID"
fi
