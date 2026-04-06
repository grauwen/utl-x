#!/bin/bash
# ============================================================================
# Run all utlxe example tests
#
# Runs each example bundle through the utlxe engine and reports pass/fail.
#
# Usage:
#   ./run-all-tests.sh            Run all examples
#   ./run-all-tests.sh --verbose  Show full output for each example
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENGINE_JAR="$PROJECT_ROOT/modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar"

VERBOSE=false
if [ "${1:-}" = "--verbose" ] || [ "${1:-}" = "-v" ]; then
    VERBOSE=true
fi

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

TOTAL=0
PASSED=0
FAILED=0

# Track used ports to avoid conflicts
NEXT_PORT=18090

if [ ! -f "$ENGINE_JAR" ]; then
    echo -e "${RED}Engine jar not found at $ENGINE_JAR${NC}"
    echo "Build it with: ./gradlew :modules:engine:jar"
    exit 1
fi

run_engine_test() {
    local name="$1"
    local bundle="$2"
    local input="$3"
    local port=$NEXT_PORT
    NEXT_PORT=$((NEXT_PORT + 1))
    TOTAL=$((TOTAL + 1))

    OUTPUT=$(jq -c . "$input" | java -jar "$ENGINE_JAR" --bundle "$bundle" --port $port 2>/dev/null)

    if [ -n "$OUTPUT" ] && echo "$OUTPUT" | jq . > /dev/null 2>&1; then
        PASSED=$((PASSED + 1))
        echo -e "  ${GREEN}✓${NC} $name"
        if [ "$VERBOSE" = true ]; then
            echo "$OUTPUT" | jq . | sed 's/^/    /'
            echo ""
        fi
    else
        FAILED=$((FAILED + 1))
        echo -e "  ${RED}✗${NC} $name"
        if [ -n "$OUTPUT" ]; then
            echo "    Output: $OUTPUT"
        else
            echo "    No output produced"
        fi
    fi
}

echo -e "${BLUE}utlxe Example Tests${NC}"
echo "Engine: $ENGINE_JAR"
echo ""

run_engine_test \
    "customer-api — single input, invoice normalization" \
    "$SCRIPT_DIR/customer-api.utlxp" \
    "$SCRIPT_DIR/customer-api.utlxp/transformations/normalize-order/test-input.json"

run_engine_test \
    "order-enrichment — 3 inputs, customer+inventory join" \
    "$SCRIPT_DIR/order-enrichment.utlxp" \
    "$SCRIPT_DIR/order-enrichment.utlxp/transformations/enrich-order/test-input-envelope.json"

run_engine_test \
    "fulfillment-pipeline — 5 inputs, multi-source fulfillment" \
    "$SCRIPT_DIR/fulfillment-pipeline.utlxp" \
    "$SCRIPT_DIR/fulfillment-pipeline.utlxp/transformations/fulfill-order/test-input-envelope.json"

echo ""
echo "Results: $PASSED/$TOTAL passed"
if [ $FAILED -gt 0 ]; then
    echo -e "${RED}$FAILED test(s) failed${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed${NC}"
fi
