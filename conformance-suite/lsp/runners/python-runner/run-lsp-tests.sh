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
# CB15: was pinned to utlxd-1.4.0.jar. Once the version moved on the path stopped existing, so
# this suite could not start — which is why a fixture asserting the daemon's 1.4.0 version sat
# stale and unverified. Resolve the newest built jar; UTLXD_JAR overrides.
LIB_DIR="$SCRIPT_DIR/../../../../modules/daemon/build/libs"
SERVER_JAR="${UTLXD_JAR:-$(ls -t "$LIB_DIR"/utlxd-*.jar 2>/dev/null | head -n 1)}"
if [ ! -f "$SERVER_JAR" ]; then
    echo "Error: Server JAR not found: $SERVER_JAR"
    echo "Run: ./gradlew :modules:daemon:jar"
    exit 1
fi

# Run Python test runner
cd "$SCRIPT_DIR"
exec python3 lsp-runner.py "$@"
