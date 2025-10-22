# Multiple Inputs in UTL-X

## Overview

UTL-X supports multiple named inputs, enabling you to:
- Merge data from multiple sources (e.g., combining XML from SAP with JSON from REST APIs)
- Handle complex enterprise integration scenarios

This feature provides similar capabilities to DataWeave but with a cleaner, more intuitive syntax.

**Note on Outputs:** Like DataWeave, UTL-X follows the principle that **one transformation produces one output**. If you need multiple output files, use external orchestration (shell scripts, workflow tools) to run multiple transformations or split a compound output.

## Table of Contents

1. [Multiple Inputs](#multiple-inputs)
2. [CLI Usage](#cli-usage)
3. [Real-World Examples](#real-world-examples)
4. [Comparison with DataWeave](#comparison-with-dataweave)
5. [Best Practices](#best-practices)
6. [External Orchestration for Multiple Outputs](#external-orchestration-for-multiple-outputs)

---

## Multiple Inputs

### Syntax

**Single Input (Backward Compatible):**
```utlx
%utlx 1.0
input xml
output json
---
{ data: @input.Customer }
```

**Multiple Named Inputs:**
```utlx
%utlx 1.0
input: input1 xml, input2 json, input3 csv
output json
---
{
  Combined: {
    fromXML: @input1.Customer,
    fromJSON: @input2.order,
    fromCSV: @input3.rows[0]
  }
}
```

### Accessing Named Inputs

Each named input is accessible via `@` prefix:
- `@input1` - First input
- `@input2` - Second input
- `@input3` - Third input
- `@input` - Alias for first input (backward compatibility)

### Format-Specific Options

You can specify format options for each input:

```utlx
%utlx 1.0
input:
  sapData xml {encoding: "ISO-8859-1"},
  restAPI json,
  inventory csv {headers: true, delimiter: ";"}
output json
---
{
  Integration: {
    SAP: @sapData.Material,
    REST: @restAPI.products,
    Inventory: @inventory.rows
  }
}
```

### Encoding Detection Per Input

The `detectXMLEncoding()` function works on each input independently:

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

---

## CLI Usage

### Single Input/Output (Backward Compatible)

```bash
# Positional arguments
utlx transform script.utlx input.xml -o output.json

# With flags
utlx transform script.utlx -i input.xml -o output.json

# From stdin
cat input.xml | utlx transform script.utlx > output.json
```

### Multiple Named Inputs

```bash
# Named inputs with --input flag
utlx transform script.utlx \
  --input input1=customer.xml \
  --input input2=orders.json \
  -o output.xml

# Short form
utlx transform script.utlx \
  -i input1=sap.xml \
  -i input2=rest.json \
  -o integrated.json
```

### Format Override

You can override the input format for all inputs:

```bash
utlx transform script.utlx \
  --input input1=data1.txt \
  --input input2=data2.txt \
  --input-format json \
  -o output.xml
```

---

## Real-World Examples

### Example 1: SAP Integration with Multiple Systems

**Scenario:** Integrate SAP material data (XML, ISO-8859-1) with REST API pricing (JSON, UTF-8)

**Input Files:**

`sap_materials.xml`:
```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<Materials>
  <Material>
    <Number>MAT-001</Number>
    <Description>Müller AG Product</Description>
  </Material>
</Materials>
```

`pricing_api.json`:
```json
{
  "prices": [
    {"sku": "MAT-001", "price": 99.99, "currency": "EUR"}
  ]
}
```

**Transformation Script:**

`integrate.utlx`:
```utlx
%utlx 1.0
input: sap xml {encoding: "ISO-8859-1"}, pricing json
output xml {encoding: "UTF-8"}
---
{
  IntegratedCatalog: {
    Products: @sap.Materials.Material |> map(material => {
      let sku = material.Number
      let priceData = @pricing.prices |> filter(p => p.sku == sku) |> first()

      Product: {
        SKU: sku,
        Description: material.Description,
        Price: priceData.price,
        Currency: priceData.currency,
        SourceEncoding: detectXMLEncoding(@sap)
      }
    })
  }
}
```

**Command:**
```bash
utlx transform integrate.utlx \
  --input sap=sap_materials.xml \
  --input pricing=pricing_api.json \
  -o catalog.xml
```

**Output:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<IntegratedCatalog>
  <Products>
    <Product>
      <SKU>MAT-001</SKU>
      <Description>Müller AG Product</Description>
      <Price>99.99</Price>
      <Currency>EUR</Currency>
      <SourceEncoding>ISO-8859-1</SourceEncoding>
    </Product>
  </Products>
</IntegratedCatalog>
```

### Example 2: Customer Master Data Merge

**Scenario:** Merge customer data from CRM (JSON) and ERP (XML)

**Transformation:**
```utlx
%utlx 1.0
input: crm json, erp xml
output json
---
{
  Customers: @crm.customers |> map(customer => {
    let erpData = @erp.Customers.Customer
      |> filter(c => c.@id == customer.id)
      |> first()

    {
      id: customer.id,
      name: customer.name,
      email: customer.email,
      creditLimit: erpData.CreditLimit,
      accountBalance: erpData.Balance,
      sources: {
        crm: "Active",
        erp: erpData != null ? "Found" : "NotFound"
      }
    }
  })
}
```

### Example 3: Multi-System Reporting

**Scenario:** Generate unified report from Sales (CSV), Inventory (XML), and Analytics (JSON)

**Transformation:**
```utlx
%utlx 1.0
input: sales csv {headers: true}, inventory xml, analytics json
output json
---
{
  Report: {
    GeneratedAt: now(),
    Summary: {
      TotalSales: sum(@sales.rows |> map(row => parseNumber(row.Amount))),
      InventoryCount: count(@inventory.Items.Item),
      AverageOrderValue: @analytics.metrics.avgOrderValue
    },
    DetailedData: {
      TopProducts: @sales.rows
        |> groupBy(row => row.ProductID)
        |> map(group => {
            ProductID: group.key,
            SalesCount: count(group.items),
            TotalRevenue: sum(group.items |> map(item => parseNumber(item.Amount))),
            InStock: hasKey(@inventory.Items.Item, ProductID)
          })
        |> sortBy(item => -item.TotalRevenue)
        |> take(10)
    }
  }
}
```

---

## Comparison with DataWeave

### DataWeave Syntax

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

### UTL-X Syntax (Cleaner)

```utlx
%utlx 1.0
input: input1 json, input2 xml
output xml
---
{
  data: @input1.customer,
  orders: @input2.Order
}
```

### Key Differences

| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| **Multiple Inputs** | `%input in0 application/json` | `input: input1 json, input2 xml` |
| **Input Access** | `in0.customer` | `@input1.customer` |
| **Single Output** | One Transform = One Output | One Transform = One Output |
| **Syntax Style** | Directive-based (`%input`) | Declarative (`:` separator) |
| **Line-by-line** | Each input on separate line | Comma-separated or multi-line |

### UTL-X Advantages

✅ **Cleaner comma-separated syntax** for compact declarations
✅ **Consistent `@` prefix** for all inputs
✅ **Per-input format options** inline
✅ **Backward compatible** with single input/output scripts
✅ **Same functional purity as DataWeave** (no file I/O, single output)

---

## Best Practices

### 1. Use Meaningful Input Names

❌ **Bad:**
```utlx
input: in1 xml, in2 json, in3 csv
```

✅ **Good:**
```utlx
input: sapMaterials xml, restPricing json, inventory csv
```

### 2. Document Input Sources

```utlx
%utlx 1.0
# Inputs:
#   sapMaterials - SAP Material Master (ISO-8859-1 encoding)
#   restPricing  - REST API pricing endpoint (UTF-8 JSON)
input: sapMaterials xml, restPricing json
output xml {encoding: "UTF-8"}
---
```

### 3. Handle Missing Data

```utlx
{
  Products: @sapMaterials.Materials.Material |> map(material => {
    let priceData = @restPricing.prices
      |> filter(p => p.sku == material.Number)
      |> first()

    Product: {
      SKU: material.Number,
      Price: priceData?.price ?? 0.00,  # Default if not found
      Status: priceData != null ? "Priced" : "NoPricing"
    }
  })
}
```

### 4. Validate Input Formats

Use format-specific options to ensure correct parsing:

```utlx
input:
  sapData xml {encoding: "ISO-8859-1"},
  csvData csv {headers: true, delimiter: ";", quote: "\""}
output json
```

### 5. Encoding Normalization

Always specify output encoding when mixing inputs with different encodings:

```utlx
input: legacy xml, modern xml
output xml {encoding: "UTF-8"}  # Normalize to UTF-8
---
{
  Combined: {
    Legacy: @legacy,
    Modern: @modern
  }
}
```

### 6. Use Let Bindings for Complex Lookups

```utlx
{
  Orders: @orders.Orders.Order |> map(order => {
    let customerId = order.CustomerID
    let customerData = @customers.Customers.Customer
      |> filter(c => c.@id == customerId)
      |> first()

    Order: {
      OrderID: order.@id,
      CustomerName: customerData.Name,
      Total: order.Total
    }
  })
}
```

### 7. Error Handling Strategy

```utlx
{
  Results: @input1.data |> map(item => {
    let enrichment = @input2.lookup
      |> filter(e => e.key == item.id)
      |> first()

    if (enrichment != null) {
      {
        id: item.id,
        data: item.value,
        enriched: enrichment.value,
        status: "Success"
      }
    } else {
      {
        id: item.id,
        data: item.value,
        enriched: null,
        status: "EnrichmentMissing"
      }
    }
  })
}
```

---

## External Orchestration for Multiple Outputs

UTL-X follows DataWeave's design principle: **one transformation = one output**. This keeps transformations pure and functional. If you need multiple output files, use external orchestration.

### Approach 1: Run Multiple Transformations

Create separate transformation scripts for each output:

**summary.utlx:**
```utlx
%utlx 1.0
input xml
output json
---
{
  totalOrders: count(@input.Orders.Order),
  totalValue: sum(@input.Orders.Order.*.Total)
}
```

**details.utlx:**
```utlx
%utlx 1.0
input xml
output xml
---
{
  Report: {
    Orders: @input.Orders.Order
  }
}
```

**Shell orchestration:**
```bash
# Run each transformation separately
utlx transform summary.utlx data.xml > summary.json
utlx transform details.utlx data.xml > details.xml
utlx transform report.utlx data.xml > report.csv
```

### Approach 2: Single Output + External Split

Create a compound output and split it:

**combined.utlx:**
```utlx
%utlx 1.0
input xml
output json
---
{
  summary: {
    totalOrders: count(@input.Orders.Order),
    totalValue: sum(@input.Orders.Order.*.Total)
  },
  details: {
    Orders: @input.Orders.Order
  },
  report: {
    headers: ["OrderID", "Customer", "Total"],
    rows: @input.Orders.Order |> map(order => [
      order.@id,
      order.Customer.Name,
      order.Total
    ])
  }
}
```

**Split with external tools:**
```bash
# Transform once, extract parts with jq
utlx transform combined.utlx data.xml -o combined.json

# Split into separate files
jq '.summary' combined.json > summary.json
jq '.details' combined.json > details.json
jq '.report' combined.json > report.json

# Convert report to CSV (using jq + jq2csv or similar)
jq -r '.report | [.headers] + .rows | .[] | @csv' combined.json > report.csv
```

### Approach 3: Workflow Orchestration

Use workflow tools (Make, Airflow, Prefect, MuleSoft):

**Makefile:**
```makefile
all: summary.json details.xml report.csv

summary.json: data.xml summary.utlx
	utlx transform summary.utlx data.xml > $@

details.xml: data.xml details.utlx
	utlx transform details.utlx data.xml > $@

report.csv: data.xml report.utlx
	utlx transform report.utlx data.xml > $@
```

**Run:**
```bash
make -j3  # Run 3 transformations in parallel
```

### Why This Approach is Better

✅ **Separation of Concerns**: Transformation logic stays pure, orchestration handles I/O
✅ **DTAP Portability**: File paths managed by orchestration, not hardcoded in transformations
✅ **Testability**: Each transformation can be tested independently with known inputs/outputs
✅ **Reusability**: Individual transformations can be reused in different contexts
✅ **Functional Purity**: No side effects in transformation code
✅ **Industry Alignment**: Matches DataWeave's design philosophy

---

## Limitations and Future Enhancements

### Current Limitations

1. **Streaming**: Large inputs are fully loaded into memory
2. **Dynamic Inputs**: Cannot determine input names at runtime
3. **Test Capture**: Multi-input transformations not yet fully captured for conformance tests

### Planned Enhancements

- [ ] Streaming support for large files
- [ ] Input validation rules in header
- [ ] Per-input error handling
- [ ] Parallel input loading
- [ ] Input dependencies (load order)

---

## Migration Guide

### From Single to Multiple Inputs

**Before:**
```utlx
%utlx 1.0
input xml
output json
---
{ data: @input.Customer }
```

**After:**
```utlx
%utlx 1.0
input: mainData xml, referenceData xml
output json
---
{
  data: @mainData.Customer,
  reference: @referenceData.Lookup
}
```

**CLI Before:**
```bash
utlx transform script.utlx input.xml -o output.json
```

**CLI After:**
```bash
utlx transform script.utlx \
  --input mainData=input.xml \
  --input referenceData=reference.xml \
  -o output.json
```

### Backward Compatibility

All existing single-input scripts continue to work without changes:
- `input xml` is treated as `input: input xml`
- `-i file.xml` is treated as `--input input=file.xml`
- `@input` works in all scripts

---

## See Also

- [UTL-X Syntax Guide](syntax.md)
- [Format Specifications](../formats/)
- [XML Encoding Guide](../formats/xml.md#encoding-handling)
- [Selectors and Navigation](selectors.md)
- [Functions Reference](functions.md)

---

**Last Updated:** 2025-10-22
**Status:** Multiple Inputs - ✅ Implemented | Single Output Philosophy - ✅ Aligned with DataWeave
