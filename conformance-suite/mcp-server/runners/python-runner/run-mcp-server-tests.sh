#!/bin/bash
#
# MCP Server Conformance Test Runner Wrapper
#
# This script wraps the Python test runner with build checks and dependency management.
#

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/../../../../"
MCP_SERVER_DIR="$PROJECT_ROOT/mcp-server"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check Python dependencies
if ! python3 -c "import yaml" 2>/dev/null; then
    echo -e "${YELLOW}Installing Python dependencies...${NC}"
    pip3 install -r "$SCRIPT_DIR/requirements.txt"
fi

# Build MCP server if needed
if [ ! -f "$MCP_SERVER_DIR/dist/index.js" ]; then
    echo -e "${YELLOW}MCP server not built, building...${NC}"
    cd "$MCP_SERVER_DIR"

    # Install dependencies if needed
    if [ ! -d "node_modules" ]; then
        echo -e "${YELLOW}Installing npm dependencies...${NC}"
        npm install --legacy-peer-deps
    fi

    # Build
    npm run build
    cd "$SCRIPT_DIR"

    if [ ! -f "$MCP_SERVER_DIR/dist/index.js" ]; then
        echo -e "${RED}Failed to build MCP server${NC}"
        exit 1
    fi
fi

# Check daemon JAR (MCP server dependency)
DAEMON_JAR="$PROJECT_ROOT/modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar"
if [ ! -f "$DAEMON_JAR" ]; then
    echo -e "${YELLOW}Daemon JAR not found. MCP server requires daemon REST API.${NC}"
    echo -e "${YELLOW}Building daemon...${NC}"
    cd "$PROJECT_ROOT"
    ./gradlew :modules:server:jar --console=plain
    cd "$SCRIPT_DIR"
fi

# Start daemon if not already running
if ! lsof -i:7779 >/dev/null 2>&1; then
    echo -e "${YELLOW}Starting daemon REST API on port 7779...${NC}"
    java -jar "$DAEMON_JAR" start --daemon-rest --daemon-rest-port 7779 > /tmp/utlxd-mcp-test.log 2>&1 &
    DAEMON_PID=$!

    # Wait for daemon to be ready
    sleep 3

    if ! lsof -i:7779 >/dev/null 2>&1; then
        echo -e "${RED}Failed to start daemon${NC}"
        cat /tmp/utlxd-mcp-test.log
        exit 1
    fi

    echo -e "${GREEN}Daemon started (PID: $DAEMON_PID)${NC}"
    DAEMON_STARTED=1
else
    echo -e "${GREEN}Daemon already running on port 7779${NC}"
    DAEMON_STARTED=0
fi

# Run tests
echo -e "${GREEN}Running MCP Server conformance tests...${NC}"
python3 "$SCRIPT_DIR/mcp-server-runner.py" "$@"
exit_code=$?

# Cleanup: Stop daemon if we started it
if [ "$DAEMON_STARTED" -eq 1 ]; then
    echo -e "${YELLOW}Stopping daemon...${NC}"
    pkill -f "utlxd.*--daemon-rest-port 7779" 2>/dev/null || true
fi

exit $exit_code
