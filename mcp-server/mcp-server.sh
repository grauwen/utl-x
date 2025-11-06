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
LOG_LEVEL="info"

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
            echo "Examples:"
            echo "  $0                                          # Start with defaults (HTTP on port 3001)"
            echo "  $0 --transport stdio                        # Start with stdio transport"
            echo "  $0 --port 3002                              # Start HTTP on custom port"
            echo "  $0 --daemon-url http://localhost:8080       # Use custom UTLXD URL"
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

echo "Starting UTL-X MCP Server..."
echo "  Transport:   $TRANSPORT"
if [ "$TRANSPORT" = "http" ]; then
    echo "  Port:        $PORT"
fi
echo "  Daemon URL:  $DAEMON_URL"
echo "  Log Level:   $LOG_LEVEL"
echo ""

# Start the server
exec node "$SCRIPT_DIR/dist/index.js"
