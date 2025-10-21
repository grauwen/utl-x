# Multiple Inputs Quick Reference

## Quick Syntax Cheat Sheet

### Script Header Syntax

```utlx
# Single input (backward compatible)
input xml
output json

# Multiple inputs - compact
input: input1 xml, input2 json, input3 csv
output json

# Multiple inputs - with options
input:
  sapData xml {encoding: "ISO-8859-1"},
  restAPI json,
  csvFile csv {headers: true}
output xml {encoding: "UTF-8"}
```

### CLI Command Syntax

```bash
# Single input (backward compatible)
utlx transform script.utlx input.xml -o output.json

# Multiple named inputs
utlx transform script.utlx \
  --input input1=file1.xml \
  --input input2=file2.json \
  -o output.json

# Short form
utlx transform script.utlx \
  -i input1=sap.xml \
  -i input2=rest.json \
  -o result.xml
```

### Accessing Inputs in Transformation

```utlx
# Access by name
@input1.Customer.Name
@input2.order.total
@input3.rows[0]

# Backward compatible
@input  # Always refers to first input
```

## Common Patterns

### Pattern 1: Merge Two XML Files

```utlx
%utlx 1.0
input: source1 xml, source2 xml
output xml
---
{
  Merged: {
    FromSource1: @source1,
    FromSource2: @source2
  }
}
```

### Pattern 2: Enrich JSON with XML Lookup

```utlx
%utlx 1.0
input: mainData json, lookupData xml
output json
---
{
  EnrichedData: @mainData.items |> map(item => {
    let lookup = @lookupData.Lookup.Item
      |> filter(l => l.@id == item.id)
      |> first()

    {
      ...item,
      enrichedValue: lookup.Value
    }
  })
}
```

### Pattern 3: Multi-Source Integration

```utlx
%utlx 1.0
input: sales csv {headers: true}, inventory xml, pricing json
output json
---
{
  Report: {
    SalesData: @sales.rows,
    InventoryData: @inventory.Items.Item,
    PricingData: @pricing.prices
  }
}
```

### Pattern 4: Encoding Detection

```utlx
%utlx 1.0
input: legacy xml, modern xml
output json
---
{
  Metadata: {
    legacyEncoding: detectXMLEncoding(@legacy),
    modernEncoding: detectXMLEncoding(@modern)
  },
  Data: {
    Legacy: @legacy,
    Modern: @modern
  }
}
```

## CLI Examples

```bash
# Example 1: SAP + REST API integration
utlx transform integrate.utlx \
  --input sap=materials.xml \
  --input api=pricing.json \
  -o catalog.xml

# Example 2: Three-way merge
utlx transform merge.utlx \
  -i customers=crm.json \
  -i orders=erp.xml \
  -i inventory=wms.csv \
  -o integrated.json

# Example 3: With format override
utlx transform process.utlx \
  --input input1=data1.txt \
  --input input2=data2.txt \
  --input-format json \
  -o output.xml

# Example 4: Verbose mode
utlx transform script.utlx \
  -i in1=file1.xml \
  -i in2=file2.json \
  -v \
  -o out.xml
```

## Encoding Scenarios

### Scenario 1: Mixed Encodings to UTF-8

```utlx
%utlx 1.0
input: sapData xml, modernAPI json
output xml {encoding: "UTF-8"}
---
{
  Normalized: {
    SAP: @sapData,
    API: @modernAPI,
    SAPOriginalEncoding: detectXMLEncoding(@sapData)
  }
}
```

**CLI:**
```bash
utlx transform normalize.utlx \
  --input sapData=legacy_iso88591.xml \
  --input modernAPI=utf8_api.json \
  -o normalized_utf8.xml
```

### Scenario 2: Preserve Original Encodings

```utlx
%utlx 1.0
input: source1 xml, source2 xml
output json
---
{
  Sources: [
    {
      data: @source1,
      encoding: detectXMLEncoding(@source1)
    },
    {
      data: @source2,
      encoding: detectXMLEncoding(@source2)
    }
  ]
}
```

## Error Handling Patterns

### Pattern 1: Safe Lookup

```utlx
let enrichment = @lookupData.items
  |> filter(item => item.id == currentId)
  |> first()

let value = enrichment?.value ?? "DEFAULT"
```

### Pattern 2: Validation Check

```utlx
{
  Results: @mainData.items |> map(item => {
    let ref = @refData.refs |> filter(r => r.key == item.key) |> first()

    {
      id: item.id,
      valid: ref != null,
      data: if (ref != null) item.data else null,
      error: if (ref == null) "Reference not found" else null
    }
  })
}
```

### Pattern 3: Input Validation

```utlx
{
  Validation: {
    input1Present: count(@input1) > 0,
    input2Present: count(@input2) > 0
  },
  Data: if (count(@input1) > 0 && count(@input2) > 0) {
    # Process data
    Combined: {...}
  } else {
    Error: "Missing input data"
  }
}
```

## Performance Tips

1. **Use specific paths:** `@input1.Customer.Name` vs `@input1..Name`
2. **Filter early:** Filter before mapping to reduce iterations
3. **Avoid nested lookups:** Use let bindings to cache lookups
4. **Minimize deep copies:** Reference data when possible

## Troubleshooting

### Error: "Expected input/output name"

**Problem:** Used colon syntax with format keyword
```utlx
output: xml {encoding: "UTF-8"}  # ❌ Wrong
```

**Solution:** Only use colon for multiple named outputs
```utlx
output xml {encoding: "UTF-8"}   # ✅ Correct (single)
output: summary json, details xml  # ✅ Correct (multiple)
```

### Error: "Undefined variable: input2"

**Problem:** Input name mismatch between header and CLI

**Header:**
```utlx
input: sapData xml, apiData json
```

**CLI:**
```bash
utlx transform script.utlx \
  --input input1=sap.xml \  # ❌ Wrong - should be sapData
  --input input2=api.json    # ❌ Wrong - should be apiData
```

**Solution:** Match names exactly
```bash
utlx transform script.utlx \
  --input sapData=sap.xml \  # ✅ Correct
  --input apiData=api.json   # ✅ Correct
```

### Error: "Input file not found"

Check file paths are correct:
```bash
# Use absolute paths if unsure
utlx transform script.utlx \
  --input input1=/full/path/to/file1.xml \
  --input input2=/full/path/to/file2.json \
  -o output.json
```

## See Also

- [Complete Multi-Input Documentation](multiple-inputs-outputs.md)
- [UTL-X Syntax Guide](syntax.md)
- [XML Encoding Guide](../formats/xml.md)
- [Functions Reference](functions.md)
