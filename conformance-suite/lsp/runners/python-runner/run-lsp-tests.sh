#!/bin/bash
#
# Wrapper script for Python LSP conformance test runner
#
# Usage:
#   ./run-lsp-tests.sh              # Run all tests
#   ./run-lsp-tests.sh hover        # Run tests matching 'hover'
#   ./run-lsp-tests.sh -v           # Verbose mode (show JSON-RPC messages)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if server JAR exists (daemon is now in the server module)
SERVER_JAR="$SCRIPT_DIR/../../../../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar"
if [ ! -f "$SERVER_JAR" ]; then
    echo "Error: Server JAR not found: $SERVER_JAR"
    echo "Run: ./gradlew :modules:server:jar"
    exit 1
fi

# Run Python test runner
cd "$SCRIPT_DIR"
exec python3 lsp-runner.py "$@"
