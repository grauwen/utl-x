#!/bin/bash
# ============================================================================
# Run script for customer-api example (single-input JSON → JSON)
#
# This example normalizes an incoming REST API order into a standard invoice.
#
# Usage:
#   ./run-test.sh              Run once with test data, then exit
#   ./run-test.sh --interactive   Start engine, send test data, keep running
#                                 (type/paste JSON lines to process more messages,
#                                  Ctrl+D to stop)
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ENGINE_JAR="$PROJECT_ROOT/modules/engine/build/libs/utlxe-1.0.0.jar"
BUNDLE="$SCRIPT_DIR"
TEST_INPUT="$SCRIPT_DIR/transformations/normalize-order/test-input.json"

# Find a free port starting from 18090
PORT=18090

if [ ! -f "$ENGINE_JAR" ]; then
    echo "Engine jar not found at $ENGINE_JAR"
    echo "Build it with: ./gradlew :modules:engine:jar"
    exit 1
fi

if [ ! -f "$TEST_INPUT" ]; then
    echo "Test input not found at $TEST_INPUT"
    exit 1
fi

if [ "${1:-}" = "--interactive" ]; then
    echo "Starting engine in interactive mode on port $PORT..."
    echo "The engine will process one JSON message per line from stdin."
    echo "Paste or type JSON, press Enter to process. Ctrl+D to stop."
    echo ""

    # Send test message first, then hand over stdin to the terminal
    (jq -c . "$TEST_INPUT"; cat) | java -jar "$ENGINE_JAR" --bundle "$BUNDLE" --port $PORT
else
    echo "Running customer-api example (single-shot)..."
    echo ""

    # Compact the test input to a single line and pipe it in
    OUTPUT=$(jq -c . "$TEST_INPUT" | java -jar "$ENGINE_JAR" --bundle "$BUNDLE" --port $PORT 2>/dev/null)

    echo "$OUTPUT" | jq .
    echo ""
    echo "Done. To run interactively: $0 --interactive"
fi
