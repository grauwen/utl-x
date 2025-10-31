#!/bin/bash

# UTL-X LSP Conformance Suite Runner
# Builds and runs the LSP conformance tests

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFORMANCE_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
PROJECT_ROOT="$(cd "$CONFORMANCE_ROOT/.." && pwd)"
UTLX_DAEMON="$PROJECT_ROOT/utlx"
TESTS_PATH="$CONFORMANCE_ROOT/lsp/tests"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $0 [OPTIONS] [CATEGORY] [TEST_NAME]

Run UTL-X LSP conformance tests

OPTIONS:
    -h, --help          Show this help message
    -b, --build         Force rebuild of test runner
    -v, --verbose       Verbose output
    --daemon PATH       Path to UTL-X daemon binary (default: $UTLX_DAEMON)

CATEGORY:
    protocol/initialization, protocol/lifecycle, document-sync,
    features/completion, features/hover, features/diagnostics, workflows

EXAMPLES:
    $0                                    # Run all tests
    $0 protocol/initialization            # Run initialization tests
    $0 features/completion                # Run completion tests
    $0 features/completion path_completion_basic  # Run specific test

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
        --daemon)
            UTLX_DAEMON="$2"
            shift 2
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

# Check if daemon exists
if [[ ! -x "$UTLX_DAEMON" ]]; then
    log "ERROR" "UTL-X daemon not found at: $UTLX_DAEMON"
    log "INFO" "Build the daemon first: cd $PROJECT_ROOT && ./gradlew :modules:daemon:build"
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
log "INFO" "Starting LSP Conformance Suite"
log "INFO" "Daemon: $UTLX_DAEMON"
log "INFO" "Tests: $TESTS_PATH"

if [[ -n "$CATEGORY" ]]; then
    log "INFO" "Category: $CATEGORY"
fi

if [[ -n "$TEST_NAME" ]]; then
    log "INFO" "Test: $TEST_NAME"
fi

echo ""

# Run the test runner
export UTLX_DAEMON
cd "$SCRIPT_DIR"

# Build command with only non-empty optional arguments
JAVA_ARGS=("$UTLX_DAEMON" "$TESTS_PATH")
[[ -n "$CATEGORY" ]] && JAVA_ARGS+=("$CATEGORY")
[[ -n "$TEST_NAME" ]] && JAVA_ARGS+=("$TEST_NAME")

if java -jar "$JAR_FILE" "${JAVA_ARGS[@]}"; then
    log "PASS" "All tests passed!"
    exit 0
else
    log "FAIL" "Some tests failed"
    exit 1
fi
