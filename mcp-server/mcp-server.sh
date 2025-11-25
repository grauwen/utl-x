#!/bin/bash
#
# UTL-X MCP Server Startup Script
#
# Usage: ./mcp-server.sh [options]
#
# Options:
#   --transport <stdio|http>   Transport mode (default: http)
#   --port <port>              HTTP port (default: 3001)
#   --daemon-url <url>         UTLXD REST API URL (default: http://localhost:7779)
#   --log-level <level>        Log level (default: info)
#   -h, --help                 Show this help message
#

# Find script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default configuration
TRANSPORT="http"
PORT="3001"
DAEMON_URL="http://localhost:7779"
LOG_LEVEL="debug"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
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
            echo "  --transport <stdio|http>   Transport mode (default: http)"
            echo "  --port <port>              HTTP port (default: 3001)"
            echo "  --daemon-url <url>         UTLXD REST API URL (default: http://localhost:7779)"
            echo "  --log-level <level>        Log level (default: info)"
            echo "  -h, --help                 Show this help message"
            echo ""
            echo "LLM Configuration (via environment variables):"
            echo "  Default: Ollama with codellama-34b-16k:latest (16K context) on localhost:11434"
            echo ""
            echo "  For Claude API (cloud):"
            echo "    export UTLX_LLM_PROVIDER=claude"
            echo "    export ANTHROPIC_API_KEY=sk-ant-your-key"
            echo "    export UTLX_LLM_MODEL=claude-3-sonnet-20240229  # optional"
            echo ""
            echo "  For Ollama (local):"
            echo "    export UTLX_LLM_PROVIDER=ollama"
            echo "    export OLLAMA_ENDPOINT=http://localhost:11434      # optional"
            echo "    export OLLAMA_MODEL=codellama:13b                  # optional (codellama:34b-16k is default)"
            echo ""
            echo "Examples:"
            echo "  $0                                          # Start with defaults (Ollama codellama-34b-16k:latest)"
            echo "  $0 --transport stdio                        # Start with stdio transport"
            echo "  $0 --port 3002                              # Start HTTP on custom port"
            echo ""
            echo "  # With Claude API:"
            echo "  export UTLX_LLM_PROVIDER=claude && export ANTHROPIC_API_KEY=sk-ant-xxx && $0"
            echo ""
            echo "  # With different Ollama model:"
            echo "  export OLLAMA_MODEL=codellama:7b && $0"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Check if dist/index.js exists
if [ ! -f "$SCRIPT_DIR/dist/index.js" ]; then
    echo "ERROR: MCP server not built. dist/index.js not found."
    echo "Please run: npm run build"
    exit 1
fi

# Set environment variables
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
if [ -n "$UTLX_LLM_PROVIDER" ]; then
    if [ "$UTLX_LLM_PROVIDER" = "claude" ]; then
        echo "  Provider:    Claude API (Anthropic)"
        if [ -n "$ANTHROPIC_API_KEY" ]; then
            echo "  API Key:     ✓ Configured"
        else
            echo "  API Key:     ✗ NOT SET (will fail)"
        fi
        echo "  Model:       ${UTLX_LLM_MODEL:-claude-3-sonnet-20240229}"
    elif [ "$UTLX_LLM_PROVIDER" = "ollama" ]; then
        echo "  Provider:    Ollama (Local)"
        echo "  Endpoint:    ${OLLAMA_ENDPOINT:-http://localhost:11434}"
        echo "  Model:       ${OLLAMA_MODEL:-codellama}"
    else
        echo "  Provider:    Unknown ($UTLX_LLM_PROVIDER)"
    fi
else
    echo "  Provider:    Ollama (Default)"
    echo "  Endpoint:    http://localhost:11434"
    echo "  Model:       codellama-34b-16k:latest"
    echo "  Context:     16,384 tokens (16K)"
    echo ""
    echo "  💡 To use Claude instead, set:"
    echo "     export UTLX_LLM_PROVIDER=claude"
    echo "     export ANTHROPIC_API_KEY=sk-ant-your-key"
    echo "     export UTLX_LLM_MODEL=claude-3-sonnet-20240229  # optional, this is the default"
    echo ""
    echo "  💡 To use a different Ollama model, set:"
    echo "     export UTLX_LLM_PROVIDER=ollama"
    echo "     export OLLAMA_MODEL=codellama:13b"
fi
echo ""
echo "========================================"
echo ""

# Start the server
exec node "$SCRIPT_DIR/dist/index.js" &
