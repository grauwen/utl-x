#!/bin/bash
#
# UTL-X CLI Interface Test Suite
#
# Tests all CLI modes, flags, piping, exit codes, and option combinations.
# This is NOT a language/transformation test — it tests the CLI interface itself.
#
# Usage: ./scripts/test-cli-interface.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
UTLX="$PROJECT_ROOT/utlx"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
TOTAL=0

# Test helper: check exit code and output
run_test() {
    local name="$1"
    local expected_exit="$2"
    local expected_pattern="$3"
    shift 3
    local cmd="$@"

    TOTAL=$((TOTAL + 1))

    # Run command, capture stdout+stderr and exit code
    local output
    output=$(eval "$cmd" 2>&1) || true
    local actual_exit=${PIPESTATUS[0]:-$?}

    # Check exit code
    if [ "$actual_exit" != "$expected_exit" ]; then
        FAIL=$((FAIL + 1))
        echo -e "${RED}FAIL${NC} [$name] Exit code: expected=$expected_exit actual=$actual_exit"
        echo "  Command: $cmd"
        echo "  Output: $(echo "$output" | head -3)"
        return
    fi

    # Check output pattern (if provided)
    if [ -n "$expected_pattern" ]; then
        if echo "$output" | grep -qE "$expected_pattern"; then
            PASS=$((PASS + 1))
            echo -e "${GREEN}PASS${NC} [$name]"
        else
            FAIL=$((FAIL + 1))
            echo -e "${RED}FAIL${NC} [$name] Output doesn't match pattern: $expected_pattern"
            echo "  Command: $cmd"
            echo "  Output: $(echo "$output" | head -3)"
        fi
    else
        PASS=$((PASS + 1))
        echo -e "${GREEN}PASS${NC} [$name]"
    fi
}

# Test helper: check exact output
run_test_exact() {
    local name="$1"
    local expected_output="$2"
    shift 2
    local cmd="$@"

    TOTAL=$((TOTAL + 1))

    local output
    output=$(eval "$cmd" 2>/dev/null) || true

    if [ "$output" = "$expected_output" ]; then
        PASS=$((PASS + 1))
        echo -e "${GREEN}PASS${NC} [$name]"
    else
        FAIL=$((FAIL + 1))
        echo -e "${RED}FAIL${NC} [$name]"
        echo "  Expected: $expected_output"
        echo "  Actual:   $output"
    fi
}

echo "============================================"
echo "  UTL-X CLI Interface Test Suite"
echo "============================================"
echo ""

# Check CLI exists
if [ ! -f "$UTLX" ]; then
    echo -e "${RED}Error: UTL-X CLI not found at $UTLX${NC}"
    echo "Build it first: ./gradlew :modules:cli:jar"
    exit 1
fi

# =============================================
echo "--- Version & Help ---"
# =============================================

run_test "version flag" 0 "UTL-X CLI v" \
    "$UTLX --version"

run_test "version short flag" 0 "UTL-X CLI v" \
    "$UTLX -v"

run_test "help flag" 0 "Usage:" \
    "$UTLX --help"

run_test "help short flag" 0 "Usage:" \
    "$UTLX -h"

run_test "transform help" 0 "Transform data" \
    "$UTLX transform --help"

echo ""

# =============================================
echo "--- Identity Mode (Smart Flip) ---"
# =============================================

run_test "identity: JSON to XML (smart flip)" 0 "<" \
    "echo '{\"a\":1}' | $UTLX"

run_test "identity: XML to JSON (smart flip)" 0 "\"person\"" \
    "echo '<person><name>Alice</name></person>' | $UTLX"

run_test "identity: CSV to JSON (smart flip)" 0 "\"Name\"" \
    "printf 'Name,Age\nAlice,30' | $UTLX --from csv"

run_test "identity: YAML to JSON (smart flip)" 0 "\"name\"" \
    "printf 'name: Alice\nage: 30' | $UTLX --from yaml"

run_test "identity: JSON flip produces valid XML" 0 "<name>Alice</name>" \
    "echo '{\"name\":\"Alice\"}' | $UTLX"

run_test "identity: XML flip produces valid JSON" 0 "\"name\"" \
    "echo '<root><name>Bob</name></root>' | $UTLX"

run_test "identity: --to override (yaml)" 0 "name:" \
    "echo '{\"name\":\"Alice\"}' | $UTLX --to yaml"

run_test "identity: --to override (csv)" 0 "name" \
    "echo '[{\"name\":\"Alice\",\"age\":30}]' | $UTLX --to csv"

run_test "identity: --to json (override flip)" 0 "\"name\"" \
    "echo '{\"name\":\"Alice\"}' | $UTLX --to json"

run_test "identity: --from csv --to xml" 0 "<" \
    "printf 'Name,Age\nAlice,30' | $UTLX --from csv --to xml"

run_test "identity: --from yaml --to xml" 0 "<" \
    "printf 'name: Alice\nage: 30' | $UTLX --from yaml --to xml"

echo ""

# =============================================
echo "--- Expression Mode (-e) ---"
# =============================================

run_test_exact "expr: dot shorthand" '"Alice"' \
    "echo '{\"name\":\"Alice\",\"age\":30}' | $UTLX -e '.name'"

run_test_exact "expr: explicit \$input" '"Alice"' \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '\$input.name'"

run_test_exact "expr: dot identity" '{"name":"Alice"}' \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.' --no-pretty"

run_test_exact "expr: nested access" '"alice@example.com"' \
    "echo '{\"user\":{\"profile\":{\"email\":\"alice@example.com\"}}}' | $UTLX -e '.user.profile.email'"

run_test "expr: function with dot" 0 "ALICE" \
    "echo '{\"name\":\"alice\"}' | $UTLX -e 'upper(.name)' -r"

run_test_exact "expr: count" '5' \
    "echo '[1,2,3,4,5]' | $UTLX -e 'count(.)'"

run_test_exact "expr: sum" '60' \
    "echo '{\"items\":[{\"price\":10},{\"price\":20},{\"price\":30}]}' | $UTLX -e 'sum(.items |> map(i => i.price))'"

run_test "expr: filter" 0 "\"active\": true" \
    "echo '[{\"id\":1,\"active\":true},{\"id\":2,\"active\":false}]' | $UTLX -e '. |> filter(x => x.active)'"

run_test "expr: map" 0 "\"id\"" \
    "echo '[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]' | $UTLX -e '. |> map(x => {id: x.id})'"

run_test "expr: XML input" 0 "Alice" \
    "echo '<person><name>Alice</name></person>' | $UTLX -e '.person.name' -r"

run_test "expr: default output is JSON" 0 "\"" \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.name'"

run_test "expr: --to yaml override" 0 "name:" \
    "echo '{\"name\":\"Alice\",\"age\":30}' | $UTLX -e '.' --to yaml"

run_test "expr: --to xml override" 0 "<" \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.' --to xml"

echo ""

# =============================================
echo "--- Expression Long Form (--expression) ---"
# =============================================

run_test_exact "expr long form" '"Alice"' \
    "echo '{\"name\":\"Alice\"}' | $UTLX --expression '.name'"

echo ""

# =============================================
echo "--- Raw Output (-r / --raw-output) ---"
# =============================================

run_test_exact "raw: strip quotes" 'Alice' \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.name' -r"

run_test_exact "raw: long form" 'Alice' \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.name' --raw-output"

run_test_exact "raw: number unchanged" '42' \
    "echo '{\"value\":42}' | $UTLX -e '.value' -r"

run_test_exact "raw: boolean unchanged" 'true' \
    "echo '{\"active\":true}' | $UTLX -e '.active' -r"

run_test "raw: with function" 0 "HELLO WORLD" \
    "echo '{\"msg\":\"hello world\"}' | $UTLX -e 'upper(.msg)' -r"

echo ""

# =============================================
echo "--- Implicit Transform (no subcommand) ---"
# =============================================

# Create temp files
TMPDIR=$(mktemp -d)
cat > "$TMPDIR/test.utlx" << 'EOF'
%utlx 1.0
input json
output json
---
{result: $input.value * 2}
EOF
cat > "$TMPDIR/test-raw.utlx" << 'EOF'
%utlx 1.0
input json
output json
---
$input.name
EOF
echo '{"value":21}' > "$TMPDIR/input.json"

run_test "implicit: .utlx as first arg" 0 "42" \
    "$UTLX $TMPDIR/test.utlx $TMPDIR/input.json"

run_test "implicit: with -o flag" 0 "" \
    "$UTLX $TMPDIR/test.utlx $TMPDIR/input.json -o $TMPDIR/output.json"

run_test "implicit: output file created" 0 "42" \
    "cat $TMPDIR/output.json"

echo ""

# =============================================
echo "--- Script Mode (explicit transform) ---"
# =============================================

run_test "script: explicit transform" 0 "42" \
    "$UTLX transform $TMPDIR/test.utlx $TMPDIR/input.json"

run_test "script: short alias t" 0 "42" \
    "$UTLX t $TMPDIR/test.utlx $TMPDIR/input.json"

run_test "script: stdin pipe" 0 "42" \
    "echo '{\"value\":21}' | $UTLX transform $TMPDIR/test.utlx"

echo ""

# =============================================
echo "--- Format Flags (--to / --from) ---"
# =============================================

run_test "format: --to xml" 0 "<" \
    "echo '{\"name\":\"Alice\"}' | $UTLX --to xml"

run_test "format: --to yaml" 0 "name:" \
    "echo '{\"name\":\"Alice\"}' | $UTLX --to yaml"

run_test "format: --to csv" 0 "name" \
    "echo '[{\"name\":\"Alice\",\"age\":30}]' | $UTLX --to csv"

run_test "format: --output-format (long)" 0 "<" \
    "echo '{\"name\":\"Alice\"}' | $UTLX --output-format xml"

run_test "format: --from csv" 0 "\"Name\"" \
    "printf 'Name,Age\nAlice,30' | $UTLX --from csv --to json"

run_test "format: --input-format (long)" 0 "\"Name\"" \
    "printf 'Name,Age\nAlice,30' | $UTLX --input-format csv --to json"

echo ""

# =============================================
echo "--- Formatting Flags ---"
# =============================================

run_test "format: --no-pretty compact" 0 '{\"name\":\"Alice\"}' \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.' --no-pretty"

echo ""

# =============================================
echo "--- Error Handling ---"
# =============================================

run_test "error: missing script file" 0 "not found" \
    "$UTLX transform nonexistent.utlx input.json"

run_test "error: unknown command" 0 "Unknown command" \
    "$UTLX foobar"

run_test "error: unknown flag" 0 "Unknown option" \
    "$UTLX transform --nonexistent-flag"

echo ""

# =============================================
echo "--- Other Commands ---"
# =============================================

run_test "functions: list" 0 "Functions" \
    "$UTLX functions stats"

run_test "functions: search" 0 "upper" \
    "$UTLX functions search upper"

run_test "validate: valid script" 0 "" \
    "$UTLX validate $TMPDIR/test.utlx"

echo ""

# =============================================
echo "--- Combination Tests ---"
# =============================================

run_test "combo: -e with -r and --to" 0 "Alice" \
    "echo '{\"name\":\"Alice\"}' | $UTLX -e '.name' -r --to json"

run_test "combo: -e with -i file" 0 "21" \
    "$UTLX -e '.value' -i $TMPDIR/input.json"

run_test "combo: -e with -o file" 0 "" \
    "echo '{\"x\":99}' | $UTLX -e '.x' -o $TMPDIR/expr-out.json"

run_test "combo: -e output file content" 0 "99" \
    "cat $TMPDIR/expr-out.json"

run_test "combo: -e verbose" 0 "Expression Mode" \
    "echo '{\"a\":1}' | $UTLX -e '.' --verbose"

echo ""

# =============================================
echo "--- Edge Cases & Invalid Combinations ---"
# =============================================

run_test "edge: empty expression" 0 "cannot be empty" \
    "echo '{}' | $UTLX -e ''"

run_test "edge: -e with script file" 0 "Cannot use" \
    "echo '{}' | $UTLX -e '.name' $TMPDIR/test.utlx"

run_test "edge: -r without -e (identity)" 0 "" \
    "echo '{\"name\":\"Alice\"}' | $UTLX -r --to json"

run_test "edge: -r on script mode" 0 "Alice" \
    "echo '{\"name\":\"Alice\"}' | $UTLX transform $TMPDIR/test-raw.utlx -r"

echo ""

# Cleanup
rm -rf "$TMPDIR"

# =============================================
echo "============================================"
echo "  Results: $PASS passed, $FAIL failed (total: $TOTAL)"
echo "============================================"

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}SOME TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
fi
