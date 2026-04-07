# XML Encoding and Content Merging Tests

This directory contains tests demonstrating XML encoding handling and the critical distinction between property assignment and spread operator for content merging.

## Test Files

### Encoding Tests

1. **encoding_precedence_rules.yaml** ✅
   - Tests encoding precedence: explicit > metadata > UTF-8 default
   - Demonstrates `detectXMLEncoding()` function
   - Uses spread operator to merge child elements directly

2. **multi_input_default_encoding.yaml** ✅
   - Tests default UTF-8 encoding with multiple XML inputs
   - Shows encoding metadata preservation per input

3. **multi_input_explicit_encoding.yaml** ✅
   - Tests explicit output encoding override (UTF-16)
   - Demonstrates encoding conversion capabilities

4. **multi_input_no_encoding.yaml** ✅
   - Tests `encoding: "NONE"` to suppress encoding declaration
   - Produces `<?xml version="1.0"?>` without encoding attribute

### Content Merging Tests

5. **property_assignment_wrapping.yaml** ✅ **NEW**
   - Demonstrates property assignment creates **wrapper element**
   - Syntax: `OriginContent: $input`
   - Use case: Envelope pattern, wrapping original content

6. **spread_operator_merging.yaml** ✅ **NEW**
   - Demonstrates spread operator merges **child elements directly**
   - Syntax: `...$input`
   - Use case: Flattening structures, combining elements at same level

## Key Concepts

### Property Assignment vs Spread Operator

#### Property Assignment: `OriginContent: $input`

**Creates a wrapper element:**

```xml
<Envelope>
  <Metadata>...</Metadata>
  <OriginContent>        <!-- Wrapper element created -->
    <Customer>...</Customer>
  </OriginContent>
</Envelope>
```

**When to use:**
- Need to preserve original structure in named container
- Implementing envelope pattern
- Creating nested hierarchies

#### Spread Operator: `...$input`

**Merges child elements directly:**

```xml
<Envelope>
  <Metadata>...</Metadata>
  <Customer>...</Customer>  <!-- No wrapper, merged directly -->
</Envelope>
```

**When to use:**
- Flattening XML structures
- Combining elements at the same level
- Avoiding unnecessary nesting

## Common Mistakes

### ❌ Wrong: Using spread when wrapper is needed

```utlx
{
  Envelope: {
    ...$input  // Merges children directly - loses structure
  }
}
```

Result: Original root element lost, children scattered

### ❌ Wrong: Using property when merge is needed

```utlx
{
  Integration: {
    Data: $input  // Creates wrapper - adds unwanted nesting
  }
}
```

Result: Extra `<Data>` wrapper element added

### ✅ Correct: Match syntax to desired output structure

**For wrapping:**
```utlx
{
  Envelope: {
    OriginContent: $input  // Preserves original in named wrapper
  }
}
```

**For merging:**
```utlx
{
  Integration: {
    ...$input  // Merges children directly
  }
}
```

## Test Results

All 6 tests pass with 100% success rate:

```bash
$ python3 runners/cli-runner/simple-runner.py examples/xml-encoding

Results: 6/6 tests passed
Success rate: 100.0%
✓ All tests passed!
```

## Related Documentation

- **Language Guide**: `docs/language-guide/spread-operator.md`
- **XML Serialization**: `formats/xml/README.md`
- **Encoding Functions**: `stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/`

## Test History

- **2025-10-21**: Created initial 4 encoding tests
- **2025-10-24**: Fixed spread operator syntax in all 4 tests
- **2025-10-24**: Added property_assignment_wrapping.yaml and spread_operator_merging.yaml tests

All tests demonstrate correct behavior of UTL-X XML handling and encoding support.
