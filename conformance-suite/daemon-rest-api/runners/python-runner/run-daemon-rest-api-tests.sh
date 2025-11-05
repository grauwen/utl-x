#!/bin/bash
#
# Daemon REST API Conformance Test Runner Wrapper
#
# This script wraps the Python test runner with daemon lifecycle management.
#

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/../../../../"

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

# Build daemon JAR if needed
JAR_PATH="$PROJECT_ROOT/modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${YELLOW}Daemon JAR not found, building...${NC}"
    cd "$PROJECT_ROOT"
    ./gradlew :modules:server:jar --console=plain
    cd "$SCRIPT_DIR"
fi

# Run tests
echo -e "${GREEN}Running Daemon REST API conformance tests...${NC}"
python3 "$SCRIPT_DIR/daemon-rest-api-runner.py" "$@"
exit_code=$?

# Cleanup any lingering daemon processes on port 7779
pkill -f "utlxd.*--daemon-rest-port 7779" 2>/dev/null || true

exit $exit_code
