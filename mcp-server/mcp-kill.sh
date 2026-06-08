#!/bin/bash
#
# Kill the UTL-X LLM MCP server (e.g. an orphan that survived a Theia restart).
# Default port 7780; override with: ./mcp-kill.sh <port>
#
PORT="${1:-7780}"

# 1) Kill the LISTENER on the port. NOT a plain `lsof -ti:$PORT` — that also matches
#    *clients connected to* the port (the Theia backend holds a client connection to the
#    MCP), so killing that set would take Theia down too. `-sTCP:LISTEN` = server only.
PIDS=$(lsof -nP -iTCP:"$PORT" -sTCP:LISTEN -t 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    echo "Killing PID(s) on port $PORT: $PIDS"
    echo "$PIDS" | xargs kill -9 2>/dev/null || true
else
    echo "Port $PORT is already free."
fi

# 2) Backstop: kill any lingering UTLX MCP process on ANY port. The server sets
# its title to "utlx-mcp-http-<port>" (replacing the node/dist cmdline), so match
# the title — this also clears legacy orphans (e.g. an old utlx-mcp-http-3001).
PIDS2=$(pgrep -f "utlx-mcp-http" 2>/dev/null || true)
if [ -n "$PIDS2" ]; then
    echo "Killing lingering UTLX MCP process(es): $PIDS2"
    echo "$PIDS2" | xargs kill -9 2>/dev/null || true
fi

echo "Done."
