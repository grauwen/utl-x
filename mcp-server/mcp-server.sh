#!/bin/bash
#
# UTL-X MCP Server Startup Script
#
# Usage: ./mcp-server.sh [options]
#
# Options:
#   --provider <claude-code|ollama>  LLM provider (default: claude-code)
#   --transport <stdio|http>         Transport mode (default: http)
#   --port <port>                    HTTP port (default: 7780)
#   --daemon-url <url>               UTLXD REST API URL (default: http://localhost:7779)
#   --log-level <level>              Log level (default: debug)
#   -h, --help                       Show this help message
#

# Find script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default configuration
PROVIDER="${UTLX_LLM_PROVIDER:-claude-code}"
TRANSPORT="http"
PORT="7780"
DAEMON_URL="http://localhost:7779"
LOG_LEVEL="debug"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --provider)
            PROVIDER="$2"
            shift 2
            ;;
        --transport)
            TRANSPORT="$2"
            shift 2
            ;;
        --port)
            PORT="$2"
            shift 2
            ;;
        --daemon-url)
            DAEMON_URL="$2"
            shift 2
            ;;
        --log-level)
            LOG_LEVEL="$2"
            shift 2
            ;;
        -h|--help)
            echo "UTL-X MCP Server Startup Script"
            echo ""
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --provider <claude-code|ollama>  LLM provider (default: claude-code)"
            echo "  --transport <stdio|http>         Transport mode (default: http)"
            echo "  --port <port>                    HTTP port (default: 7780)"
            echo "  --daemon-url <url>               UTLXD REST API URL (default: http://localhost:7779)"
            echo "  --log-level <level>              Log level (default: debug)"
            echo "  -h, --help                       Show this help message"
            echo ""
            echo "LLM Providers:"
            echo ""
            echo "  claude-code (default) — agentic Claude Code session"
            echo "    Auth:  uses your Claude Code login (run 'claude' then '/login')."
            echo "           No API key needed."
            echo "    Self-corrects against the UTLXD daemon (needs daemon on $DAEMON_URL)."
            echo "    Optional env:"
            echo "      export UTLX_LLM_MODEL=claude-sonnet-4-6        # else session default"
            echo "      export UTLX_CLAUDE_CODE_SELF_CORRECT=false     # disable validate loop"
            echo "      export UTLX_CLAUDE_CODE_MAX_TURNS=8            # cap agentic turns"
            echo "      export UTLX_CLAUDE_CODE_PATH=/path/to/claude   # if not on PATH"
            echo ""
            echo "  ollama — local model"
            echo "    export OLLAMA_ENDPOINT=http://localhost:11434     # optional"
            echo "    export OLLAMA_MODEL=codellama-34b-16k:latest      # optional (default)"
            echo ""
            echo "Examples:"
            echo "  $0                                          # claude-code over HTTP on :7780"
            echo "  $0 --provider ollama                        # use local Ollama instead"
            echo "  $0 --transport stdio                        # stdio transport"
            echo "  $0 --port 3002                              # custom HTTP port"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Validate provider
if [ "$PROVIDER" != "claude-code" ] && [ "$PROVIDER" != "ollama" ]; then
    echo "ERROR: Unknown provider '$PROVIDER' (expected: claude-code or ollama)"
    exit 1
fi

# Check if dist/index.js exists
if [ ! -f "$SCRIPT_DIR/dist/index.js" ]; then
    echo "ERROR: MCP server not built. dist/index.js not found."
    echo "Please run: npm run build"
    exit 1
fi

# Free the port first — kill any prior UTLX MCP instance (incl. an orphan that
# survived a Theia restart) so we don't hit EADDRINUSE and silently keep an old build.
EXISTING=$(lsof -ti:"$PORT" 2>/dev/null || true)
if [ -n "$EXISTING" ]; then
    echo "Port $PORT in use by PID(s): $EXISTING — killing before start..."
    echo "$EXISTING" | xargs kill -9 2>/dev/null || true
    sleep 1
fi

# Set environment variables
export UTLX_LLM_PROVIDER="$PROVIDER"
export UTLX_DAEMON_URL="$DAEMON_URL"
export UTLX_MCP_TRANSPORT="$TRANSPORT"
export UTLX_MCP_PORT="$PORT"
export UTLX_LOG_LEVEL="$LOG_LEVEL"
export NODE_ENV="production"

echo "========================================"
echo "  UTL-X MCP Server Starting"
echo "========================================"
echo ""
echo "Configuration:"
echo "  Transport:   $TRANSPORT"
if [ "$TRANSPORT" = "http" ]; then
    echo "  Port:        $PORT"
fi
echo "  Daemon URL:  $DAEMON_URL"
echo "  Log Level:   $LOG_LEVEL"
echo ""

# Display LLM configuration
echo "LLM Provider:"
if [ "$PROVIDER" = "claude-code" ]; then
    echo "  Provider:    Claude Code (agentic session)"
    echo "  Model:       ${UTLX_LLM_MODEL:-session default}"
    if [ "${UTLX_CLAUDE_CODE_SELF_CORRECT}" = "false" ]; then
        echo "  Self-correct: disabled"
    else
        echo "  Self-correct: enabled (validates against UTLXD)"
    fi
    # Auth precheck: is the Claude Code CLI available?
    CLAUDE_BIN="${UTLX_CLAUDE_CODE_PATH:-claude}"
    if command -v "$CLAUDE_BIN" > /dev/null 2>&1; then
        echo "  CLI:         ✓ found ($CLAUDE_BIN)"
    else
        echo "  CLI:         ✗ NOT FOUND — install Claude Code and run '/login'"
    fi
    # Self-correction needs the daemon reachable.
    if curl -s "$DAEMON_URL/api/health" > /dev/null 2>&1; then
        echo "  Daemon:      ✓ reachable"
    else
        echo "  Daemon:      ✗ unreachable — self-correction will be skipped"
    fi
else
    echo "  Provider:    Ollama (Local)"
    echo "  Endpoint:    ${OLLAMA_ENDPOINT:-http://localhost:11434}"
    echo "  Model:       ${OLLAMA_MODEL:-codellama-34b-16k:latest}"
fi
echo ""
echo "========================================"
echo ""

# Start the server
exec node "$SCRIPT_DIR/dist/index.js" &
