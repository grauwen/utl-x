#!/bin/bash

# IDE UDM Conformance Test Suite Runner
#
# Runs all UDM conformance tests:
# 1. TypeScript unit tests (parser, serializer, navigator)
# 2. Kotlin ↔ TypeScript interop tests
# 3. Comprehensive test suite
# 4. Example generation validation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "════════════════════════════════════════════════════════════════════════════════"
echo "IDE UDM Conformance Test Suite"
echo "════════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Project Root: $PROJECT_ROOT"
echo "Test Suite:   $SCRIPT_DIR"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test counters
TOTAL_SUITES=0
PASSED_SUITES=0
FAILED_SUITES=0

run_test_suite() {
    local name=$1
    local command=$2

    TOTAL_SUITES=$((TOTAL_SUITES + 1))

    echo ""
    echo "────────────────────────────────────────────────────────────────────────────────"
    echo -e "${BLUE}[$TOTAL_SUITES] Running: $name${NC}"
    echo "────────────────────────────────────────────────────────────────────────────────"
    echo ""

    if eval "$command"; then
        echo ""
        echo -e "${GREEN}✅ PASSED: $name${NC}"
        PASSED_SUITES=$((PASSED_SUITES + 1))
        return 0
    else
        echo ""
        echo -e "${RED}❌ FAILED: $name${NC}"
        FAILED_SUITES=$((FAILED_SUITES + 1))
        return 1
    fi
}

# ============================================================================
# Test Suite 1: TypeScript Unit Tests
# ============================================================================
run_test_suite \
    "TypeScript UDM Unit Tests" \
    "cd $PROJECT_ROOT/theia-extension/utlx-theia-extension && node lib/browser/udm/__tests__/udm-roundtrip.test.js"

# ============================================================================
# Test Suite 2: Kotlin ↔ TypeScript Interop Tests
# ============================================================================
run_test_suite \
    "Kotlin ↔ TypeScript Interop Tests" \
    "cd $PROJECT_ROOT && ./gradlew :modules:core:test --tests 'org.apache.utlx.core.udm.TypeScriptInteropTest' --quiet"

# ============================================================================
# Test Suite 3: Generate Node.js Examples
# ============================================================================
run_test_suite \
    "Generate Node.js UDM Examples" \
    "cd $PROJECT_ROOT && node conformance-suite/ide-udm/scripts/generate-nodejs-examples.js"

# ============================================================================
# Test Suite 4: Generate USDL Examples
# ============================================================================
run_test_suite \
    "Generate USDL Feature Examples" \
    "cd $PROJECT_ROOT && node conformance-suite/ide-udm/scripts/test-usdl-features.js"

# ============================================================================
# Test Suite 5: Comprehensive Test Suite
# ============================================================================
if [ -f "$SCRIPT_DIR/tests/comprehensive-test-suite.js" ]; then
    run_test_suite \
        "Comprehensive Integration Tests" \
        "cd $PROJECT_ROOT && node conformance-suite/ide-udm/tests/comprehensive-test-suite.js"
else
    echo -e "${YELLOW}⚠️  Skipping: Comprehensive test suite not compiled${NC}"
fi

# ============================================================================
# Test Suite 6: CLI Examples (Optional)
# ============================================================================
if [ -f "$PROJECT_ROOT/utlxd" ]; then
    echo ""
    echo "────────────────────────────────────────────────────────────────────────────────"
    echo -e "${BLUE}[Optional] Generate CLI UDM Examples (8 formats)${NC}"
    echo "────────────────────────────────────────────────────────────────────────────────"
    echo ""

    if bash "$SCRIPT_DIR/scripts/generate-cli-examples.sh"; then
        echo -e "${GREEN}✅ CLI examples generated${NC}"
    else
        echo -e "${YELLOW}⚠️  CLI examples generation had issues (non-critical)${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Skipping: CLI not built (run ./gradlew assemble first)${NC}"
fi

# ============================================================================
# Summary
# ============================================================================
echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "CONFORMANCE TEST SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════════"
echo ""
echo "Total Test Suites:  $TOTAL_SUITES"
echo -e "${GREEN}✅ Passed:          $PASSED_SUITES${NC}"
if [ $FAILED_SUITES -gt 0 ]; then
    echo -e "${RED}❌ Failed:          $FAILED_SUITES${NC}"
else
    echo "❌ Failed:          $FAILED_SUITES"
fi
echo ""

SUCCESS_RATE=$((PASSED_SUITES * 100 / TOTAL_SUITES))
echo "Success Rate:       $SUCCESS_RATE%"
echo ""

# Check for example files
if [ -d "$SCRIPT_DIR/examples/nodejs-generated" ]; then
    NODE_EXAMPLES=$(ls -1 "$SCRIPT_DIR/examples/nodejs-generated"/*.udm 2>/dev/null | wc -l)
    echo "Node.js Examples:   $NODE_EXAMPLES files"
fi

if [ -d "$SCRIPT_DIR/examples/usdl-examples" ]; then
    USDL_EXAMPLES=$(ls -1 "$SCRIPT_DIR/examples/usdl-examples"/*.usdl 2>/dev/null | wc -l)
    echo "USDL Examples:      $USDL_EXAMPLES files"
fi

if [ -d "$SCRIPT_DIR/examples/cli-generated" ]; then
    CLI_EXAMPLES=$(ls -1 "$SCRIPT_DIR/examples/cli-generated"/*.udm 2>/dev/null | wc -l)
    echo "CLI Examples:       $CLI_EXAMPLES files"
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"

if [ $FAILED_SUITES -gt 0 ]; then
    echo -e "${RED}❌ Some conformance tests failed!${NC}"
    exit 1
else
    echo -e "${GREEN}✅ All conformance tests passed!${NC}"
    exit 0
fi
