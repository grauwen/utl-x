#!/bin/bash

# UTL-X Conformance Suite CLI Test Runner
# Executes test cases using the UTL-X CLI tool

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUITE_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
UTLX_CLI="$SUITE_ROOT/../utlx"
TEST_ROOT="$SUITE_ROOT/tests"
DATA_ROOT="$SUITE_ROOT/data"
REPORTS_ROOT="$SUITE_ROOT/reports"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Global counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

usage() {
    cat << EOF
Usage: $0 [OPTIONS] [CATEGORY] [TEST_NAME]

Run UTL-X conformance tests

OPTIONS:
    -h, --help          Show this help message
    -v, --verbose       Verbose output
    -q, --quiet         Quiet output (errors only)
    -f, --filter TAG    Run tests with specific tag
    -p, --parallel      Run tests in parallel
    -r, --report FORMAT Report format (text, json, xml, html)
    --timeout SECONDS   Test timeout (default: 30)
    --performance       Include performance tests
    --no-cleanup        Don't cleanup temporary files

CATEGORY:
    core, stdlib, formats, integration, performance, edge-cases
    Or specific subcategories like stdlib/array, core/syntax

EXAMPLES:
    $0                          # Run all tests
    $0 stdlib                   # Run all stdlib tests  
    $0 stdlib/array             # Run array function tests
    $0 --filter core            # Run tests tagged 'core'
    $0 -v stdlib/array map_basic # Run specific test with verbose output
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

validate_test_case() {
    local test_file="$1"
    
    # Check if test file exists and is valid YAML
    if ! command -v yq >/dev/null 2>&1; then
        log "WARN" "yq not found, skipping test case validation"
        return 0
    fi
    
    if ! yq eval '.' "$test_file" >/dev/null 2>&1; then
        log "ERROR" "Invalid YAML in test file: $test_file"
        return 1
    fi
    
    # Validate required fields
    local name category transformation
    name=$(yq eval '.name' "$test_file")
    category=$(yq eval '.category' "$test_file") 
    transformation=$(yq eval '.transformation' "$test_file")
    
    if [[ "$name" == "null" || "$category" == "null" || "$transformation" == "null" ]]; then
        log "ERROR" "Missing required fields in: $test_file"
        return 1
    fi
    
    return 0
}

create_temp_files() {
    local test_name="$1"
    local input_data="$2"
    local input_format="$3"
    
    local temp_dir
    temp_dir=$(mktemp -d)
    local input_file="$temp_dir/input.$input_format"
    local output_file="$temp_dir/output.$input_format"
    local transform_file="$temp_dir/transform.utlx"
    
    echo "$input_data" > "$input_file"
    echo "$temp_dir"
}

run_single_test() {
    local test_file="$1"
    local test_name="$2"
    
    log "INFO" "Running test: $test_name"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    # Validate test case
    if ! validate_test_case "$test_file"; then
        log "FAIL" "Test validation failed: $test_name"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        return 1
    fi
    
    # Extract test data
    local transformation input_data input_format expected_data expected_format
    transformation=$(yq eval '.transformation' "$test_file")
    input_data=$(yq eval '.input.data' "$test_file")
    input_format=$(yq eval '.input.format' "$test_file")
    expected_data=$(yq eval '.expected.data' "$test_file")
    expected_format=$(yq eval '.expected.format' "$test_file")
    
    # Create temporary files
    local temp_dir
    temp_dir=$(create_temp_files "$test_name" "$input_data" "$input_format")
    
    local input_file="$temp_dir/input.$input_format"
    local output_file="$temp_dir/output.$expected_format"
    local transform_file="$temp_dir/transform.utlx"
    local expected_file="$temp_dir/expected.$expected_format"
    
    echo "$transformation" > "$transform_file"
    echo "$expected_data" > "$expected_file"
    
    # Run UTL-X transformation
    local start_time end_time duration
    start_time=$(date +%s%N)
    
    if timeout 30 "$UTLX_CLI" transform "$transform_file" < "$input_file" > "$output_file" 2>/dev/null; then
        end_time=$(date +%s%N)
        duration=$(( (end_time - start_time) / 1000000 )) # Convert to milliseconds
        
        # Compare output with expected
        if diff -q "$output_file" "$expected_file" >/dev/null; then
            log "PASS" "$test_name (${duration}ms)"
            PASSED_TESTS=$((PASSED_TESTS + 1))
            local result=0
        else
            log "FAIL" "$test_name - Output mismatch"
            if [[ "${VERBOSE:-false}" == "true" ]]; then
                echo "Expected:"
                cat "$expected_file"
                echo "Actual:"
                cat "$output_file"
                echo "Diff:"
                diff "$expected_file" "$output_file" || true
            fi
            FAILED_TESTS=$((FAILED_TESTS + 1))
            local result=1
        fi
    else
        log "FAIL" "$test_name - Execution failed or timed out"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        local result=1
    fi
    
    # Cleanup
    if [[ "${NO_CLEANUP:-false}" != "true" ]]; then
        rm -rf "$temp_dir"
    fi
    
    return $result
}

run_test_variants() {
    local test_file="$1" 
    local base_name="$2"
    
    # Check if test has variants
    local variants_count
    variants_count=$(yq eval '.variants | length' "$test_file")
    
    if [[ "$variants_count" == "0" || "$variants_count" == "null" ]]; then
        return 0
    fi
    
    log "INFO" "Running $variants_count variants for $base_name"
    
    for ((i=0; i<variants_count; i++)); do
        local variant_name
        variant_name=$(yq eval ".variants[$i].name" "$test_file")
        
        # Create temporary variant test file
        local temp_variant_file
        temp_variant_file=$(mktemp)
        
        # Copy base test and override with variant data
        yq eval '
            .name = .name + "_" + .variants['$i'].name |
            .input = .variants['$i'].input |
            .expected = .variants['$i'].expected |
            del(.variants)
        ' "$test_file" > "$temp_variant_file"
        
        run_single_test "$temp_variant_file" "${base_name}_${variant_name}"
        
        rm -f "$temp_variant_file"
    done
}

find_tests() {
    local category="${1:-}"
    local test_name="${2:-}"
    local filter_tag="${3:-}"
    
    local find_path="$TEST_ROOT"
    
    if [[ -n "$category" ]]; then
        find_path="$TEST_ROOT/$category"
    fi
    
    local test_files=()
    while IFS= read -r -d '' file; do
        # Filter by test name if specified
        if [[ -n "$test_name" ]]; then
            local file_name
            file_name=$(yq eval '.name' "$file" 2>/dev/null || echo "")
            if [[ "$file_name" != "$test_name" ]]; then
                continue
            fi
        fi
        
        # Filter by tag if specified  
        if [[ -n "$filter_tag" ]]; then
            local tags
            tags=$(yq eval '.tags[]' "$file" 2>/dev/null || echo "")
            if [[ ! "$tags" =~ $filter_tag ]]; then
                continue
            fi
        fi
        
        test_files+=("$file")
    done < <(find "$find_path" -name "*.yaml" -o -name "*.yml" -print0 2>/dev/null)
    
    printf '%s\n' "${test_files[@]}"
}

generate_report() {
    local format="${1:-text}"
    local report_file="$REPORTS_ROOT/test-results-$(date +%Y%m%d_%H%M%S).$format"
    
    mkdir -p "$REPORTS_ROOT"
    
    case "$format" in
        "text")
            cat > "$report_file" << EOF
UTL-X Conformance Suite Test Results
====================================

Summary:
  Total Tests:  $TOTAL_TESTS
  Passed:       $PASSED_TESTS
  Failed:       $FAILED_TESTS
  Skipped:      $SKIPPED_TESTS
  Success Rate: $(( TOTAL_TESTS > 0 ? (PASSED_TESTS * 100) / TOTAL_TESTS : 0 ))%

Generated: $(date)
EOF
            ;;
        "json")
            cat > "$report_file" << EOF
{
  "summary": {
    "total": $TOTAL_TESTS,
    "passed": $PASSED_TESTS,
    "failed": $FAILED_TESTS,
    "skipped": $SKIPPED_TESTS,
    "success_rate": $(( TOTAL_TESTS > 0 ? (PASSED_TESTS * 100) / TOTAL_TESTS : 0 ))
  },
  "generated": "$(date -Iseconds)"
}
EOF
            ;;
    esac
    
    echo "Report generated: $report_file"
}

main() {
    local category="" test_name="" filter_tag="" report_format="text"
    local verbose=false quiet=false parallel=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                usage
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                verbose=true
                shift
                ;;
            -q|--quiet)
                quiet=true
                shift
                ;;
            -f|--filter)
                filter_tag="$2"
                shift 2
                ;;
            -p|--parallel)
                parallel=true
                shift
                ;;
            -r|--report)
                report_format="$2"
                shift 2
                ;;
            --timeout)
                # TODO: Implement timeout override
                shift 2
                ;;
            --performance)
                # TODO: Include performance tests
                shift
                ;;
            --no-cleanup)
                NO_CLEANUP=true
                shift
                ;;
            -*)
                echo "Unknown option: $1" >&2
                usage >&2
                exit 1
                ;;
            *)
                if [[ -z "$category" ]]; then
                    category="$1"
                elif [[ -z "$test_name" ]]; then
                    test_name="$1"
                else
                    echo "Too many arguments" >&2
                    usage >&2
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Check if UTL-X CLI exists
    if [[ ! -x "$UTLX_CLI" ]]; then
        log "ERROR" "UTL-X CLI not found at: $UTLX_CLI"
        exit 1
    fi
    
    log "INFO" "Starting UTL-X Conformance Suite"
    log "INFO" "UTL-X CLI: $UTLX_CLI"
    
    # Find and run tests
    local test_files
    test_files=()
    while IFS= read -r file; do
        test_files+=("$file")
    done < <(find_tests "$category" "$test_name" "$filter_tag")
    
    if [[ ${#test_files[@]} -eq 0 ]]; then
        log "WARN" "No tests found matching criteria"
        exit 0
    fi
    
    log "INFO" "Found ${#test_files[@]} test files"
    
    # Run tests
    for test_file in "${test_files[@]}"; do
        local base_name
        base_name=$(yq eval '.name' "$test_file")
        
        run_single_test "$test_file" "$base_name"
        run_test_variants "$test_file" "$base_name"
    done
    
    # Generate summary
    log "INFO" "Test execution completed"
    log "INFO" "Results: $PASSED_TESTS passed, $FAILED_TESTS failed, $SKIPPED_TESTS skipped"
    
    # Generate report
    generate_report "$report_format"
    
    # Exit with error code if any tests failed
    if [[ $FAILED_TESTS -gt 0 ]]; then
        exit 1
    fi
}

# Run main function with all arguments
main "$@"