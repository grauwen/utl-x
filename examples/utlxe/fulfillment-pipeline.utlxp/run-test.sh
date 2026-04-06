#!/bin/bash
# ============================================================================
# Run script for fulfillment-pipeline example (5 inputs via envelope)
#
# This example demonstrates a realistic fulfillment transformation that
# combines data from multiple source types:
#   - currentPayload, orderIngress, fraudCheck → from an Open-M pipeline envelope
#   - pricingResponse → from a separate Pulsar topic
#   - shippingConfig  → from a static JSON file (constant pipe)
#
# The transformation declares 5 named inputs. The engine receives a single
# JSON envelope on stdin and splits it into the named inputs automatically.
# In production, the pipe system would wire each source type separately.
#
# Usage:
#   ./run-test.sh                 Run once with test data, then exit
#   ./run-test.sh --interactive   Start engine, send test data, keep running
#                                 (paste JSON envelope lines to process more,
#                                  Ctrl+D to stop)
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
ENGINE_JAR="$PROJECT_ROOT/modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar"
BUNDLE="$SCRIPT_DIR"
TEST_INPUT="$SCRIPT_DIR/transformations/fulfill-order/test-input-envelope.json"

PORT=18092

if [ ! -f "$ENGINE_JAR" ]; then
    echo "Engine jar not found at $ENGINE_JAR"
    echo "Build it with: ./gradlew :modules:engine:jar"
    exit 1
fi

if [ "${1:-}" = "--interactive" ]; then
    echo "Starting engine in interactive mode on port $PORT..."
    echo "Send a JSON envelope with keys: currentPayload, orderIngress, fraudCheck, pricingResponse, shippingConfig"
    echo "Ctrl+D to stop."
    echo ""
    (jq -c . "$TEST_INPUT"; cat) | java -jar "$ENGINE_JAR" --bundle "$BUNDLE" --port $PORT
else
    echo "Running fulfillment-pipeline example (5 inputs via envelope)..."
    echo ""
    OUTPUT=$(jq -c . "$TEST_INPUT" | java -jar "$ENGINE_JAR" --bundle "$BUNDLE" --port $PORT 2>/dev/null)
    echo "$OUTPUT" | jq .
    echo ""
    echo "Done. To run interactively: $0 --interactive"
fi
