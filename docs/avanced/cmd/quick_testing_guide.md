# Quick Testing Guide for UTL-X

A practical guide for quickly testing UTL-X transformations from the command line.

---

## Basic Testing Pattern

The fastest way to test a UTL-X transformation:

```bash
# 1. Create transformation file
cat > /tmp/test.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  result: $input.value * 2
}
EOF

# 2. Test with inline data
echo '{"value": 21}' | utlx transform /tmp/test.utlx

# Output: {"result": 42}
```

---

## Testing Patterns

### Pattern 1: Echo Inline (Simplest)

**Use when:** Testing with simple JSON data

```bash
# Simple object
echo '{"name": "Alice"}' | utlx transform script.utlx

# Array
echo '[1, 2, 3]' | utlx transform script.utlx

# Empty object (useful for testing without input data)
echo '{}' | utlx transform script.utlx
```

### Pattern 2: Here Document (Multi-line)

**Use when:** Testing with formatted/complex data

```bash
# Create transformation
cat > /tmp/transform.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  fullName: $input.firstName + " " + $input.lastName,
  email: lower($input.email)
}
EOF

# Create input data
cat > /tmp/input.json << 'EOF'
{
  "firstName": "Alice",
  "lastName": "Johnson",
  "email": "ALICE@EXAMPLE.COM"
}
EOF

# Run transformation
utlx transform /tmp/transform.utlx /tmp/input.json
```

**Output:**
```json
{
  "fullName": "Alice Johnson",
  "email": "alice@example.com"
}
```

### Pattern 3: File-Based (Reusable)

**Use when:** Testing repeatedly with same data

```bash
# Create files once
cat > test-transform.utlx << 'EOF'
%utlx 1.0
input xml
output json
---
{
  order: $input.Order.@id,
  total: parseNumber($input.Order.Total)
}
EOF

cat > test-input.xml << 'EOF'
<Order id="ORD-001">
  <Total>42.50</Total>
</Order>
EOF

# Test multiple times
utlx transform test-transform.utlx test-input.xml

# Save output
utlx transform test-transform.utlx test-input.xml -o output.json

# Modify and retest (files persist)
# ... edit test-transform.utlx ...
utlx transform test-transform.utlx test-input.xml
```

---

## Testing Different Formats

### JSON Input
```bash
echo '{"key": "value"}' | utlx transform script.utlx
```

### XML Input
```bash
cat > /tmp/input.xml << 'EOF'
<Root>
  <Item>Value</Item>
</Root>
EOF

utlx transform script.utlx /tmp/input.xml
```

### CSV Input
```bash
cat > /tmp/input.csv << 'EOF'
name,age,city
Alice,30,NYC
Bob,25,LA
EOF

utlx transform script.utlx /tmp/input.csv
```

### YAML Input
```bash
cat > /tmp/input.yaml << 'EOF'
person:
  name: Alice
  age: 30
EOF

utlx transform script.utlx /tmp/input.yaml
```

---

## Quick Testing Workflows

### Workflow 1: Rapid Iteration

```bash
# Setup once
cat > /tmp/test.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  // Your transformation here
  result: $input.value
}
EOF

# Test repeatedly (just re-run)
echo '{"value": 10}' | utlx transform /tmp/test.utlx

# Modify /tmp/test.utlx in your editor, then re-run
echo '{"value": 20}' | utlx transform /tmp/test.utlx
```

### Workflow 2: Test Multiple Scenarios

```bash
# Create transformation once
cat > /tmp/calc.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  sum: $input.a + $input.b,
  product: $input.a * $input.b
}
EOF

# Test different inputs
echo '{"a": 5, "b": 3}' | utlx transform /tmp/calc.utlx
echo '{"a": 10, "b": 20}' | utlx transform /tmp/calc.utlx
echo '{"a": -5, "b": 5}' | utlx transform /tmp/calc.utlx
```

### Workflow 3: Test with Real File, Output to File

```bash
# Transform existing file, save output
utlx transform my-transform.utlx data/real-input.json -o results/output.json

# View output
cat results/output.json

# Or pipe to jq for pretty printing
utlx transform my-transform.utlx data/real-input.json | jq
```

---

## Advanced Testing Techniques

### Test with Multiple Inputs

```bash
# Create multi-input transformation
cat > /tmp/join.utlx << 'EOF'
%utlx 1.0
input: customers json, orders json
output json
---
{
  enriched: $customers |> map(c => {
    name: c.name,
    orders: $orders |> filter(o => o.customerId == c.id)
  })
}
EOF

# Create input files
echo '[{"id": 1, "name": "Alice"}]' > /tmp/customers.json
echo '[{"customerId": 1, "total": 100}]' > /tmp/orders.json

# Run with multiple inputs
utlx transform /tmp/join.utlx \
  --input customers=/tmp/customers.json \
  --input orders=/tmp/orders.json
```

### Test with Different Output Formats

```bash
# Same transformation, different outputs
cat > /tmp/convert.utlx << 'EOF'
%utlx 1.0
input json
output json  // Change this line for different outputs
---
{
  name: $input.name,
  value: $input.value
}
EOF

# Test as JSON
echo '{"name": "test", "value": 42}' | utlx transform /tmp/convert.utlx

# Change output to CSV (edit the file)
# output csv
echo '{"name": "test", "value": 42}' | utlx transform /tmp/convert.utlx

# Change output to YAML
# output yaml
echo '{"name": "test", "value": 42}' | utlx transform /tmp/convert.utlx
```

### Test Edge Cases

```bash
# Create test transformation
cat > /tmp/test-edge-cases.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  hasValue: $input.value != null,
  isEmpty: count($input.items ?? []) == 0,
  safeDivide: if ($input.b != 0) $input.a / $input.b else 0
}
EOF

# Test normal case
echo '{"value": 10, "items": [1, 2], "a": 10, "b": 2}' | utlx transform /tmp/test-edge-cases.utlx

# Test null value
echo '{"value": null, "items": [], "a": 10, "b": 0}' | utlx transform /tmp/test-edge-cases.utlx

# Test missing fields
echo '{}' | utlx transform /tmp/test-edge-cases.utlx
```

---

## Debugging Tips

### 1. Use Empty Input for Testing Logic

```bash
# Test transformation logic without needing real data
cat > /tmp/test-logic.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  // Test pure logic
  result: map([1, 2, 3], n => n * 2),
  sorted: sortBy([{v: 30}, {v: 10}], x => -x.v)
}
EOF

echo '{}' | utlx transform /tmp/test-logic.utlx
```

### 2. Add Debug Output

```bash
cat > /tmp/debug.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  // Debug: show intermediate values
  input_value: $input.value,
  doubled: $input.value * 2,
  final: ($input.value * 2) + 10
}
EOF

echo '{"value": 5}' | utlx transform /tmp/debug.utlx
```

### 3. Test Individual Functions

```bash
cat > /tmp/test-function.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  // Test a specific function
  original: "HELLO WORLD",
  lowercased: lower("HELLO WORLD"),

  // Test with different inputs
  test1: contains("hello", "ell"),
  test2: contains("hello", "xyz")
}
EOF

echo '{}' | utlx transform /tmp/test-function.utlx
```

### 4. Isolate Problems

```bash
# If complex transformation fails, simplify step by step

# Step 1: Test input parsing
cat > /tmp/step1.utlx << 'EOF'
%utlx 1.0
input json
output json
---
$input  // Just echo input
EOF

# Step 2: Test first transformation
cat > /tmp/step2.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  step1: $input.value
}
EOF

# Step 3: Add next transformation
cat > /tmp/step3.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  step1: $input.value,
  step2: $input.value * 2
}
EOF

# Test each step
echo '{"value": 10}' | utlx transform /tmp/step1.utlx
echo '{"value": 10}' | utlx transform /tmp/step2.utlx
echo '{"value": 10}' | utlx transform /tmp/step3.utlx
```

---

## Useful Aliases

Add these to your `~/.bashrc` or `~/.zshrc`:

```bash
# Quick test with empty input
alias utlx-test='echo "{}" | utlx transform'

# Test and pretty-print
alias utlx-pretty='utlx transform | jq'

# Create test transformation template
utlx-new() {
  cat > "$1" << 'EOF'
%utlx 1.0
input json
output json
---
{
  // Your transformation here
  result: $input
}
EOF
  echo "Created $1"
}

# Usage:
# utlx-test /tmp/my-transform.utlx
# utlx-new /tmp/new-test.utlx
```

---

## Testing Checklist

When testing transformations, verify:

- [ ] **Happy path** - Works with expected input
- [ ] **Empty input** - Handles `{}` or `[]` gracefully
- [ ] **Null values** - Doesn't crash on `null`
- [ ] **Missing fields** - Uses safe navigation `?.` or nullish coalescing `??`
- [ ] **Edge cases** - Zero, negative numbers, empty strings
- [ ] **Large data** - Performance with realistic data sizes
- [ ] **Different formats** - Works with XML, JSON, CSV as expected

---

## Common Testing Patterns

### Test Array Operations
```bash
cat > /tmp/test-arrays.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  mapped: map([1, 2, 3], n => n * 2),
  filtered: filter([1, 2, 3, 4], n => n > 2),
  reduced: reduce([1, 2, 3], (sum, n) => sum + n, 0),
  sorted: sortBy([{v: 30}, {v: 10}, {v: 20}], x => x.v)
}
EOF

echo '{}' | utlx transform /tmp/test-arrays.utlx
```

### Test String Operations
```bash
cat > /tmp/test-strings.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  original: "Hello World",
  upper: upper("Hello World"),
  lower: lower("Hello World"),
  substring: substring("Hello World", 0, 5),
  split: split("a,b,c", ","),
  join: join(["a", "b", "c"], "-")
}
EOF

echo '{}' | utlx transform /tmp/test-strings.utlx
```

### Test Conditionals
```bash
cat > /tmp/test-conditionals.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  simple: if ($input.value > 10) "high" else "low",
  nested: if ($input.value > 100) "very high"
          else if ($input.value > 10) "high"
          else "low",
  nullish: $input.missing ?? "default value"
}
EOF

echo '{"value": 50}' | utlx transform /tmp/test-conditionals.utlx
echo '{"value": 5}' | utlx transform /tmp/test-conditionals.utlx
echo '{}' | utlx transform /tmp/test-conditionals.utlx
```

---

## Performance Testing

### Time a transformation
```bash
# Simple timing
time utlx transform script.utlx input.json

# More detailed with multiple runs
for i in {1..10}; do
  time utlx transform script.utlx large-input.json > /dev/null
done
```

### Test with large data
```bash
# Generate large test file
node -e "console.log(JSON.stringify(Array(1000).fill({name: 'test', value: 42})))" > /tmp/large.json

# Test transformation performance
time utlx transform your-script.utlx /tmp/large.json
```

---

## Tips for Effective Testing

1. **Use `/tmp/` for test files** - Automatically cleaned up on reboot
2. **Name files descriptively** - `test-sort-descending.utlx` vs `test1.utlx`
3. **Keep test data small** - Use minimal data that demonstrates the issue
4. **Test one thing at a time** - Isolate what you're testing
5. **Use comments** - Document what you're testing in the transformation
6. **Save successful tests** - Build a library of working examples
7. **Test error cases** - Don't just test happy paths

---

## Example: Complete Testing Session

```bash
# 1. Create a transformation to test
cat > /tmp/order-summary.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{
  orderId: $input.id,
  customerName: $input.customer.name,
  itemCount: count($input.items),
  total: sum($input.items |> map(item => item.price * item.quantity)),
  status: if ($input.shipped) "Shipped" else "Pending"
}
EOF

# 2. Test with minimal data
echo '{"id": "ORD-001", "customer": {"name": "Alice"}, "items": [{"price": 10, "quantity": 2}], "shipped": false}' | utlx transform /tmp/order-summary.utlx

# 3. Test edge case - empty items
echo '{"id": "ORD-002", "customer": {"name": "Bob"}, "items": [], "shipped": true}' | utlx transform /tmp/order-summary.utlx

# 4. Test with more realistic data
cat > /tmp/order-input.json << 'EOF'
{
  "id": "ORD-003",
  "customer": {
    "name": "Charlie Brown",
    "email": "charlie@example.com"
  },
  "items": [
    {"name": "Widget", "price": 29.99, "quantity": 2},
    {"name": "Gadget", "price": 49.99, "quantity": 1}
  ],
  "shipped": true
}
EOF

utlx transform /tmp/order-summary.utlx /tmp/order-input.json

# 5. Save output for inspection
utlx transform /tmp/order-summary.utlx /tmp/order-input.json -o /tmp/result.json
cat /tmp/result.json

# 6. Pretty print with jq
utlx transform /tmp/order-summary.utlx /tmp/order-input.json | jq
```

---

## Summary

**Quick Test Pattern:**
```bash
cat > /tmp/test.utlx << 'EOF'
%utlx 1.0
input json
output json
---
{ result: $input.value }
EOF

echo '{"value": 42}' | utlx transform /tmp/test.utlx
```

This pattern gives you:
- ✅ Fast iteration
- ✅ No file clutter (uses `/tmp/`)
- ✅ Easy to modify and retest
- ✅ Works with any input format
- ✅ Can save output when needed

**Happy testing!** 🚀
