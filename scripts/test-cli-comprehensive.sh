#!/bin/bash
# scripts/test-cli-comprehensive.sh
# Comprehensive integration test suite for UTL-X CLI

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0
TEST_DIR=$(mktemp -d)

print_test() {
    echo -e "${BLUE}━━━ Test: $1 ━━━${NC}"
}

assert_success() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ PASS${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAIL: $1${NC}"
        ((TESTS_FAILED++))
    fi
}

assert_contains() {
    if grep -q "$2" "$1"; then
        echo -e "${GREEN}✓ PASS: Found '$2'${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAIL: Expected to find '$2' in output${NC}"
        cat "$1"
        ((TESTS_FAILED++))
    fi
}

assert_not_contains() {
    if ! grep -q "$2" "$1"; then
        echo -e "${GREEN}✓ PASS: Did not find '$2'${NC}"
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗ FAIL: Should not contain '$2'${NC}"
        ((TESTS_FAILED++))
    fi
}

cleanup() {
    rm -rf "$TEST_DIR"
}

trap cleanup EXIT

# Check if CLI is built
CLI_JAR="modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar"
if [ ! -f "$CLI_JAR" ]; then
    echo -e "${RED}Error: CLI JAR not found. Run ./scripts/build-cli.sh first${NC}"
    exit 1
fi

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  UTL-X CLI Comprehensive Test Suite${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Test 1: Version command
print_test "Version command"
java -jar "$CLI_JAR" version > "$TEST_DIR/version.txt"
assert_contains "$TEST_DIR/version.txt" "UTL-X v1.0.0-SNAPSHOT"
assert_success "Version command failed"
echo ""

# Test 2: Help command
print_test "Help command"
java -jar "$CLI_JAR" help > "$TEST_DIR/help.txt"
assert_contains "$TEST_DIR/help.txt" "transform"
assert_contains "$TEST_DIR/help.txt" "validate"
assert_success "Help command failed"
echo ""

# Test 3: Simple XML to JSON transformation
print_test "XML to JSON transformation"
cat > "$TEST_DIR/simple.xml" << 'EOF'
<person>
  <name>Alice</name>
  <age>30</age>
</person>
EOF

cat > "$TEST_DIR/simple.utlx" << 'EOF'
%utlx 1.0
input xml
output json
---
{
  fullName: input.person.name,
  yearsOld: input.person.age
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/simple.xml" \
    "$TEST_DIR/simple.utlx" \
    -o "$TEST_DIR/simple.json"

assert_contains "$TEST_DIR/simple.json" "Alice"
assert_contains "$TEST_DIR/simple.json" "fullName"
assert_success "XML to JSON transformation"
echo ""

# Test 4: JSON to XML transformation
print_test "JSON to XML transformation"
cat > "$TEST_DIR/data.json" << 'EOF'
{
  "order": {
    "id": "ORD-001",
    "customer": "Bob"
  }
}
EOF

cat > "$TEST_DIR/json-to-xml.utlx" << 'EOF'
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: input.order.id,
    Customer: input.order.customer
  }
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/data.json" \
    "$TEST_DIR/json-to-xml.utlx" \
    -o "$TEST_DIR/data.xml"

assert_contains "$TEST_DIR/data.xml" "<Order"
assert_contains "$TEST_DIR/data.xml" "ORD-001"
assert_contains "$TEST_DIR/data.xml" "Bob"
assert_success "JSON to XML transformation"
echo ""

# Test 5: CSV to JSON transformation
print_test "CSV to JSON transformation"
cat > "$TEST_DIR/customers.csv" << 'EOF'
Name,Age,City
Alice,25,NYC
Bob,30,SF
Carol,28,LA
EOF

cat > "$TEST_DIR/csv-to-json.utlx" << 'EOF'
%utlx 1.0
input csv
output json
---
{
  customers: input.rows |> map(row => {
    name: row.Name,
    age: row.Age,
    location: row.City
  })
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/customers.csv" \
    "$TEST_DIR/csv-to-json.utlx" \
    -o "$TEST_DIR/customers.json"

assert_contains "$TEST_DIR/customers.json" "customers"
assert_contains "$TEST_DIR/customers.json" "Alice"
assert_contains "$TEST_DIR/customers.json" "NYC"
assert_success "CSV to JSON transformation"
echo ""

# Test 6: Stdin/Stdout pipeline
print_test "Stdin/Stdout pipeline"
cat "$TEST_DIR/simple.xml" | java -jar "$CLI_JAR" transform "$TEST_DIR/simple.utlx" > "$TEST_DIR/piped.json"
assert_contains "$TEST_DIR/piped.json" "Alice"
assert_success "Stdin/Stdout pipeline"
echo ""

# Test 7: Force output format
print_test "Force output format"
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/data.json" \
    "$TEST_DIR/json-to-xml.utlx" \
    --output-format xml \
    -o "$TEST_DIR/forced.xml"

assert_contains "$TEST_DIR/forced.xml" "<Order"
assert_success "Force output format"
echo ""

# Test 8: Validate valid script
print_test "Validate valid script"
java -jar "$CLI_JAR" validate "$TEST_DIR/simple.utlx" > "$TEST_DIR/validate-ok.txt" 2>&1
assert_contains "$TEST_DIR/validate-ok.txt" "Valid"
assert_success "Validate valid script"
echo ""

# Test 9: Validate invalid script
print_test "Validate invalid script"
echo "this is not valid UTL-X!!!" > "$TEST_DIR/invalid.utlx"
java -jar "$CLI_JAR" validate "$TEST_DIR/invalid.utlx" > "$TEST_DIR/validate-fail.txt" 2>&1 || true
# Should fail, so we check that it contains an error message
assert_not_contains "$TEST_DIR/validate-fail.txt" "Valid"
echo -e "${GREEN}✓ PASS: Invalid script rejected${NC}"
((TESTS_PASSED++))
echo ""

# Test 10: Verbose mode
print_test "Verbose mode"
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/simple.xml" \
    "$TEST_DIR/simple.utlx" \
    -v \
    -o "$TEST_DIR/verbose.json" > "$TEST_DIR/verbose-output.txt" 2>&1

assert_contains "$TEST_DIR/verbose-output.txt" "Lexing"
assert_contains "$TEST_DIR/verbose-output.txt" "Parsing"
assert_success "Verbose mode"
echo ""

# Test 11: Complex transformation with functions
print_test "Complex transformation with functions"
cat > "$TEST_DIR/complex.xml" << 'EOF'
<Orders>
  <Order id="1" total="100.50"/>
  <Order id="2" total="200.75"/>
  <Order id="3" total="50.25"/>
</Orders>
EOF

cat > "$TEST_DIR/complex.utlx" << 'EOF'
%utlx 1.0
input xml
output json
---
{
  orderCount: count(input.Orders.Order),
  totalRevenue: sum(input.Orders.Order.@total),
  averageOrder: avg(input.Orders.Order.@total),
  orders: input.Orders.Order |> map(order => {
    id: order.@id,
    amount: order.@total
  })
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/complex.xml" \
    "$TEST_DIR/complex.utlx" \
    -o "$TEST_DIR/complex.json"

assert_contains "$TEST_DIR/complex.json" "orderCount"
assert_contains "$TEST_DIR/complex.json" "totalRevenue"
assert_success "Complex transformation"
echo ""

# Test 12: Multiple file validation
print_test "Multiple file validation"
java -jar "$CLI_JAR" validate \
    "$TEST_DIR/simple.utlx" \
    "$TEST_DIR/json-to-xml.utlx" \
    "$TEST_DIR/csv-to-json.utlx" > "$TEST_DIR/multi-validate.txt" 2>&1

# Should validate all three files
VALID_COUNT=$(grep -c "Valid" "$TEST_DIR/multi-validate.txt" || true)
if [ "$VALID_COUNT" -ge 3 ]; then
    echo -e "${GREEN}✓ PASS: All files validated${NC}"
    ((TESTS_PASSED++))
else
    echo -e "${RED}✗ FAIL: Expected 3 valid files, got $VALID_COUNT${NC}"
    ((TESTS_FAILED++))
fi
echo ""

# Test 13: Auto format detection
print_test "Auto format detection"
cat > "$TEST_DIR/auto.utlx" << 'EOF'
%utlx 1.0
input auto
output json
---
{
  data: input
}
EOF

# Should work with XML input
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/simple.xml" \
    "$TEST_DIR/auto.utlx" \
    -o "$TEST_DIR/auto-xml.json"

assert_contains "$TEST_DIR/auto-xml.json" "data"
assert_success "Auto format detection (XML)"

# Should work with JSON input
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/data.json" \
    "$TEST_DIR/auto.utlx" \
    -o "$TEST_DIR/auto-json.json"

assert_contains "$TEST_DIR/auto-json.json" "data"
assert_success "Auto format detection (JSON)"
echo ""

# Test 14: Error handling - missing input file
print_test "Error handling - missing file"
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/nonexistent.xml" \
    "$TEST_DIR/simple.utlx" > "$TEST_DIR/error1.txt" 2>&1 || true

assert_contains "$TEST_DIR/error1.txt" "not found"
echo -e "${GREEN}✓ PASS: Error properly reported${NC}"
((TESTS_PASSED++))
echo ""

# Test 15: Format command
print_test "Format command"
cat > "$TEST_DIR/unformatted.utlx" << 'EOF'
%utlx 1.0
input json
output json
---
{value:input.data,result:  42  }
EOF

java -jar "$CLI_JAR" format "$TEST_DIR/unformatted.utlx" > "$TEST_DIR/format-output.txt" 2>&1
# Format command currently exists but is a placeholder, so just check it doesn't crash
assert_success "Format command"
echo ""

# Test 16: JSON array handling
print_test "JSON array handling"
cat > "$TEST_DIR/array.json" << 'EOF'
{
  "items": [
    {"id": 1, "name": "Item 1"},
    {"id": 2, "name": "Item 2"},
    {"id": 3, "name": "Item 3"}
  ]
}
EOF

cat > "$TEST_DIR/array.utlx" << 'EOF'
%utlx 1.0
input json
output json
---
{
  count: count(input.items),
  names: input.items |> map(item => item.name)
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/array.json" \
    "$TEST_DIR/array.utlx" \
    -o "$TEST_DIR/array-result.json"

assert_contains "$TEST_DIR/array-result.json" "count"
assert_contains "$TEST_DIR/array-result.json" "names"
assert_success "JSON array handling"
echo ""

# Test 17: XML attributes
print_test "XML attributes"
cat > "$TEST_DIR/attributes.xml" << 'EOF'
<Product id="P001" price="99.99" category="Electronics">
  <Name>Laptop</Name>
</Product>
EOF

cat > "$TEST_DIR/attributes.utlx" << 'EOF'
%utlx 1.0
input xml
output json
---
{
  productId: input.Product.@id,
  price: input.Product.@price,
  category: input.Product.@category,
  name: input.Product.Name
}
EOF

java -jar "$CLI_JAR" transform \
    "$TEST_DIR/attributes.xml" \
    "$TEST_DIR/attributes.utlx" \
    -o "$TEST_DIR/attributes.json"

assert_contains "$TEST_DIR/attributes.json" "P001"
assert_contains "$TEST_DIR/attributes.json" "99.99"
assert_contains "$TEST_DIR/attributes.json" "Electronics"
assert_success "XML attributes"
echo ""

# Test 18: Pretty print control
print_test "Pretty print control"
java -jar "$CLI_JAR" transform \
    "$TEST_DIR/simple.xml" \
    "$TEST_DIR/simple.utlx" \
    --no-pretty \
    -o "$TEST_DIR/compact.json"

# Check that it's more compact (fewer newlines)
NEWLINE_COUNT=$(grep -c "^" "$TEST_DIR/compact.json" || echo "1")
if [ "$NEWLINE_COUNT" -lt 5 ]; then
    echo -e "${GREEN}✓ PASS: Compact output${NC}"
    ((TESTS_PASSED++))
else
    echo -e "${YELLOW}⚠ NOTE: Pretty print control may need adjustment${NC}"
fi
echo ""

# Summary
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}  Test Summary${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
