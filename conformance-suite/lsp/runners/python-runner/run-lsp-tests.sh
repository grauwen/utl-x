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

# Check if CLI JAR exists (daemon is invoked via 'design daemon' subcommand)
CLI_JAR="$SCRIPT_DIR/../../../../modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar"
if [ ! -f "$CLI_JAR" ]; then
    echo "Error: CLI JAR not found: $CLI_JAR"
    echo "Run: ./gradlew :modules:cli:jar"
    exit 1
fi

# Run Python test runner
cd "$SCRIPT_DIR"
exec python3 lsp-runner.py "$@"
