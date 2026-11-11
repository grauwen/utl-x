# Release Notes: Multiple Inputs Support

**Release Date:** 2025-10-21
**Version:** v0.2.0 (Milestone: Multi-Input Support)
**Status:** ✅ Implemented and Tested

---

## 🎉 Major New Feature: Multiple Named Inputs

UTL-X now supports **multiple named inputs**, enabling enterprise integration scenarios that combine data from multiple sources in a single transformation. This was identified as a **MAJOR GAP** and is now fully implemented.

### What's New

#### 1. Multiple Input Declaration Syntax

**Before (Single Input):**
```utlx
%utlx 1.0
input xml
output json
---
{ data: $input.Customer }
```

**Now (Multiple Inputs):**
```utlx
%utlx 1.0
input: sapData xml, restAPI json, inventory csv
output json
---
{
  integrated: {
    sap: $sapData.Material,
    pricing: $restAPI.prices,
    stock: $inventory.rows
  }
}
```

#### 2. CLI Support for Named Inputs

**Command Line:**
```bash
utlx transform script.utlx \
  --input sapData=materials.xml \
  --input restAPI=pricing.json \
  --input inventory=stock.csv \
  -o integrated.json
```

#### 3. Per-Input Format Options

```utlx
input:
  sapData xml {encoding: "ISO-8859-1"},
  restAPI json,
  inventory csv {headers: true, delimiter: ";"}
output json
```

#### 4. Per-Input Encoding Detection

```utlx
{
  encodings: {
    sap: detectXMLEncoding($sapData),
    modern: detectXMLEncoding($modernAPI)
  }
}
```

### Key Benefits

✅ **Enterprise Integration** - Merge data from SAP, REST APIs, databases, files
✅ **Format Mixing** - Combine XML, JSON, CSV, YAML in single transformation
✅ **Encoding Handling** - Detect and normalize different character encodings
✅ **Backward Compatible** - All existing single-input scripts work unchanged
✅ **Clean Syntax** - Simpler than DataWeave's `%input` directives
✅ **No External Orchestration** - Everything in one UTL-X script

---

## 📋 Implementation Details

### Components Modified

1. **AST (`ast_nodes.kt`)**
   - `Header` now supports `List<Pair<String, FormatSpec>>` for inputs/outputs
   - Added backward compatibility properties
   - Added `hasMultipleInputs` and `hasMultipleOutputs` helpers

2. **Parser (`parser_impl.kt`)**
   - New `parseInputsOrOutputs()` method
   - Supports both `: name format, ...` and single `format` syntax
   - Handles format options per input

3. **Interpreter (`interpreter.kt`)**
   - Overloaded `execute()` method for `Map<String, UDM>`
   - Binds all named inputs to environment
   - Maintains `$input` for backward compatibility

4. **CLI (`TransformCommand.kt`)**
   - `TransformOptions` now has `namedInputs: Map<String, File>`
   - Supports `--input name=file` syntax (multiple)
   - Backward compatible with `-i file` (single)

### Syntax Rules

**Single Input (Backward Compatible):**
- `input xml` → Treated as `input: input xml`
- Accessible as `$input`

**Multiple Inputs:**
- `input: name1 format1, name2 format2, ...`
- Each accessible as `@name1`, `@name2`, etc.
- `$input` always refers to first input

**Format Options:**
- Inline: `input: data xml {encoding: "ISO-8859-1"}`
- Applied when parsing that specific input

---

## 🧪 Testing

### Test Coverage

✅ **Functional Tests:**
- Multiple XML files with different encodings
- XML + JSON combination
- Encoding detection per input
- Format options per input

✅ **Integration Tests:**
- CLI with multiple `--input` flags
- Named input access in transformations
- Backward compatibility with single input

✅ **Real-World Scenario:**
```bash
# Successfully tested with:
./utlx transform multi_input_test.utlx \
  --input input1=input1.xml \  # ISO-8859-1 encoding
  --input input2=input2.xml \  # UTF-16 encoding
  -o output.xml                # UTF-8 encoding

# Output correctly:
# - Detected both input encodings
# - Combined data from both files
# - Preserved special characters (Müller)
# - Normalized to UTF-8 output
```

### Test Files Created

- `conformance-suite/tests/examples/xml-encoding/input1.xml`
- `conformance-suite/tests/examples/xml-encoding/input2.xml`
- `conformance-suite/tests/examples/xml-encoding/multi_input_test.utlx`

---

## 📚 Documentation

### New Documentation Files

1. **`docs/language-guide/multiple-inputs-outputs.md`**
   - Comprehensive guide with 50+ examples
   - Real-world integration scenarios
   - Comparison with DataWeave
   - Best practices and patterns
   - Error handling strategies

2. **`docs/language-guide/quick-reference-multi-$input.md`**
   - Quick syntax cheat sheet
   - Common patterns
   - CLI examples
   - Troubleshooting guide

3. **Updated `CLAUDE.md`**
   - Implementation status markers
   - Syntax examples
   - Comparison with DataWeave

### Documentation Topics Covered

- ✅ Syntax and semantics
- ✅ CLI usage
- ✅ Encoding handling
- ✅ Error patterns
- ✅ Performance tips
- ✅ Migration guide
- ✅ Real-world examples
- ✅ Troubleshooting

---

## 🔄 Migration Guide

### No Changes Required for Existing Scripts

All existing UTL-X scripts continue to work without modification:

```utlx
# This still works exactly as before
%utlx 1.0
input xml
output json
---
{ data: $input.Customer }
```

### Adding Multiple Inputs to Existing Script

**Step 1:** Update header
```utlx
# Before
input xml

# After
input: mainData xml, referenceData xml
```

**Step 2:** Update CLI command
```bash
# Before
utlx transform script.utlx $input.xml -o output.json

# After
utlx transform script.utlx \
  --input mainData=$input.xml \
  --input referenceData=reference.xml \
  -o output.json
```

**Step 3:** Update input references
```utlx
# Before
$input.Customer

# After
$mainData.Customer
$referenceData.Lookup
```

---

## 🆚 Comparison with DataWeave

### DataWeave Approach

```dataweave
%dw 2.0
%input in0 application/json
%input in1 application/xml
%output application/xml
---
{
  data: in0.customer,
  orders: in1.Order
}
```

**Limitations:**
- Separate directive lines (`%input`)
- Cannot do multiple outputs in single script
- Verbose for multiple inputs

### UTL-X Approach

```utlx
%utlx 1.0
input: customers json, orders xml
output xml
---
{
  data: $customers.customer,
  orders: $orders.Order
}
```

**Advantages:**
- ✅ Cleaner comma-separated syntax
- ✅ Inline in header (no separate directives)
- ✅ Consistent `@` prefix
- ✅ Multiple outputs planned (DataWeave can't)
- ✅ Backward compatible
- ✅ Per-input format options inline

---

## 🐛 Known Issues and Limitations

### Current Limitations

1. **Multiple Outputs:** Syntax designed but not yet implemented
2. **Test Capture:** Multi-input transformations not captured for conformance tests
3. **Streaming:** All inputs fully loaded into memory (no streaming support yet)
4. **Dynamic Inputs:** Input names must be statically defined in header

### Future Enhancements Planned

- [ ] Multiple outputs implementation
- [ ] Streaming support for large files
- [ ] Input validation rules in header
- [ ] Per-input error handling
- [ ] Parallel input loading
- [ ] Input dependencies (load order control)

---

## 💡 Usage Examples

### Example 1: SAP Integration

```utlx
%utlx 1.0
input: sapMaterials xml {encoding: "ISO-8859-1"}, apiPricing json
output xml {encoding: "UTF-8"}
---
{
  Catalog: {
    Products: $sapMaterials.Materials.Material |> map(mat => {
      let price = $apiPricing.prices
        |> filter(p => p.sku == mat.Number)
        |> first()

      Product: {
        SKU: mat.Number,
        Description: mat.Description,
        Price: price.value,
        SourceEncoding: detectXMLEncoding($sapMaterials)
      }
    })
  }
}
```

```bash
utlx transform integrate.utlx \
  --input sapMaterials=sap_iso88591.xml \
  --input apiPricing=prices_utf8.json \
  -o catalog_utf8.xml
```

### Example 2: Multi-System Report

```utlx
%utlx 1.0
input: sales csv {headers: true}, inventory xml, analytics json
output json
---
{
  Report: {
    TotalSales: sum($sales.rows |> map(r => parseNumber(r.Amount))),
    TotalInventory: count($inventory.Items.Item),
    AvgOrderValue: $analytics.metrics.avgOrderValue,
    TopProducts: $sales.rows
      |> groupBy(r => r.ProductID)
      |> sortBy(g => -sum(g.items |> map(i => parseNumber(i.Amount))))
      |> take(10)
  }
}
```

---

## 🔗 See Also

- [Complete Multi-Input Documentation](docs/language-guide/multiple-inputs-outputs.md)
- [Quick Reference Guide](docs/language-guide/quick-reference-multi-$input.md)
- [XML Encoding Guide](docs/formats/xml.md)
- [CLAUDE.md - Project Overview](CLAUDE.md)

---

## 👥 Credits

**Implementation:** Claude Code (Anthropic)
**Concept & Requirements:** Ir. Marcel A. Grauwen
**Project:** UTL-X - Universal Transformation Language Extended

---

**Next Milestone:** Multiple Outputs Implementation 🎯
