#!/bin/bash
#
# Theia Extension Conformance Test Runner
#
# This script manages Theia startup, runs Playwright tests, and handles cleanup.
#

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]})" )" && pwd )"
SUITE_DIR="$SCRIPT_DIR/../.."
PROJECT_ROOT="$SUITE_DIR/../.."
THEIA_DIR="$PROJECT_ROOT/theia-extension/browser-app"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Theia is already running
if lsof -nP -i:3000 > /dev/null 2>&1; then
    echo -e "${GREEN}Theia already running on port 3000${NC}"
    THEIA_STARTED=0
else
    echo -e "${YELLOW}Starting Theia IDE...${NC}"
    cd "$THEIA_DIR"
    yarn start > /tmp/theia-e2e-tests.log 2>&1 &
    THEIA_PID=$!
    THEIA_STARTED=1

    # Wait for Theia to start (max 60 seconds)
    echo -e "${YELLOW}Waiting for Theia to start...${NC}"
    for i in {1..60}; do
        if lsof -nP -i:3000 > /dev/null 2>&1; then
            echo -e "${GREEN}Theia started successfully (PID: $THEIA_PID)${NC}"
            break
        fi
        sleep 1
    done

    # Verify Theia started
    if ! lsof -nP -i:3000 > /dev/null 2>&1; then
        echo -e "${RED}Failed to start Theia${NC}"
        cat /tmp/theia-e2e-tests.log
        exit 1
    fi

    # Wait an additional 10 seconds for services to fully initialize
    echo -e "${YELLOW}Waiting for services to initialize...${NC}"
    sleep 10
fi

# Run tests
echo -e "${GREEN}Running Theia E2E conformance tests...${NC}"
cd "$SUITE_DIR"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}Installing dependencies...${NC}"
    npm install
fi

# Run Playwright tests
npm test
exit_code=$?

# Cleanup: Stop Theia if we started it
if [ "$THEIA_STARTED" -eq 1 ]; then
    echo -e "${YELLOW}Stopping Theia...${NC}"
    pkill -P $THEIA_PID 2>/dev/null || true
    kill $THEIA_PID 2>/dev/null || true
    sleep 2
fi

exit $exit_code
