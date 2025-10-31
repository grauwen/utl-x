#!/bin/bash

# UTLX Conformance Suite Kotlin Runner
# Pure Kotlin/JVM implementation - no Python dependency!

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFORMANCE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
PROJECT_ROOT="$(cd "$CONFORMANCE_ROOT/.." && pwd)"
UTLX_CLI="$PROJECT_ROOT/utlx"
TESTS_PATH="$CONFORMANCE_ROOT/utlx/tests"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $0 [OPTIONS] [CATEGORY] [TEST_NAME]

Run UTLX conformance tests (Kotlin runner - no Python dependency!)

OPTIONS:
    -h, --help              Show this help message
    -b, --build             Force rebuild of test runner
    -v, --verbose           Verbose output
    --cli PATH              Path to UTL-X CLI binary (default: $UTLX_CLI)
    --check-performance     Enable performance limit checking (disabled by default)

CATEGORY:
    core, stdlib, formats, integration, edge-cases, etc.

EXAMPLES:
    $0                          # Run all tests
    $0 stdlib                   # Run all stdlib tests
    $0 stdlib/array             # Run array function tests
    $0 core arithmetic_basic    # Run specific test
    $0 --check-performance core # Run core tests with performance checking

EOF
}

log() {
    local level="$1"
    shift
    case "$level" in
        "INFO")  echo -e "${BLUE}[INFO]${NC} $*" ;;
        "PASS")  echo -e "${GREEN}[PASS]${NC} $*" ;;
        "FAIL")  echo -e "${RED}[FAIL]${NC} $*" ;;
        "WARN")  echo -e "${YELLOW}[WARN]${NC} $*" ;;
        "ERROR") echo -e "${RED}[ERROR]${NC} $*" >&2 ;;
    esac
}

# Parse arguments
FORCE_BUILD=false
CHECK_PERFORMANCE=false
CATEGORY=""
TEST_NAME=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -b|--build)
            FORCE_BUILD=true
            shift
            ;;
        -v|--verbose)
            # Could enable more verbose logging
            shift
            ;;
        --cli)
            UTLX_CLI="$2"
            shift 2
            ;;
        --check-performance)
            CHECK_PERFORMANCE=true
            shift
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            if [[ -z "$CATEGORY" ]]; then
                CATEGORY="$1"
            elif [[ -z "$TEST_NAME" ]]; then
                TEST_NAME="$1"
            else
                echo "Too many arguments" >&2
                usage >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Check if CLI exists
if [[ ! -x "$UTLX_CLI" ]]; then
    log "ERROR" "UTL-X CLI not found at: $UTLX_CLI"
    log "INFO" "Build it first: cd $PROJECT_ROOT && ./gradlew :modules:cli:jar"
    exit 1
fi

# Build test runner if needed
JAR_FILE="$SCRIPT_DIR/build/libs/kotlin-runner-1.0.0-SNAPSHOT.jar"
if [[ ! -f "$JAR_FILE" ]] || [[ "$FORCE_BUILD" == "true" ]]; then
    log "INFO" "Building test runner..."
    cd "$SCRIPT_DIR"

    if ! ./gradlew jar; then
        log "ERROR" "Failed to build test runner"
        exit 1
    fi

    log "PASS" "Test runner built successfully"
fi

# Run tests
log "INFO" "Starting UTLX Conformance Suite (Kotlin Runner)"
log "INFO" "UTL-X CLI: $UTLX_CLI"
log "INFO" "Tests: $TESTS_PATH"

if [[ -n "$CATEGORY" ]]; then
    log "INFO" "Category: $CATEGORY"
fi

if [[ -n "$TEST_NAME" ]]; then
    log "INFO" "Test: $TEST_NAME"
fi

echo ""

# Run the test runner
export UTLX_CLI
cd "$SCRIPT_DIR"

# Build command with only non-empty optional arguments
JAVA_ARGS=("$UTLX_CLI" "$TESTS_PATH")
[[ "$CHECK_PERFORMANCE" == "true" ]] && JAVA_ARGS+=("--check-performance")
[[ -n "$CATEGORY" ]] && JAVA_ARGS+=("$CATEGORY")
[[ -n "$TEST_NAME" ]] && JAVA_ARGS+=("$TEST_NAME")

if java -jar "$JAR_FILE" "${JAVA_ARGS[@]}"; then
    log "PASS" "All tests passed!"
    exit 0
else
    log "FAIL" "Some tests failed"
    exit 1
fi
