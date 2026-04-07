# UTL-X vs JSONata

## Overview

| Feature | JSONata | UTL-X |
|---------|---------|-------|
| **License** | MIT (permissive) | AGPL-3.0 / Commercial dual-license |
| **Formats** | JSON only | XML, JSON, CSV, YAML, XSD, JSON Schema, Avro, Protobuf, OData |
| **Paradigm** | Functional, XPath-inspired | Functional, declarative |
| **Runtime** | JavaScript only | JVM or GraalVM native binary |
| **Type System** | Dynamic | Strong, inferred |
| **Stdlib** | ~50 functions | 652 functions across 18 categories |
| **CLI piping** | Not a CLI tool | `cat data.xml \| utlx` (smart format flip) |
| **Multi-input** | No | Yes (join multiple files/formats) |
| **Schema support** | No | XSD, JSON Schema, Avro, Protobuf |

## Side-by-Side Example

**JSONata:**
```jsonata
{
  "invoice": {
    "id": Order.id,
    "customer": Order.customer.name,
    "total": $sum(Order.items.(price * quantity))
  }
}
```

**UTL-X:**
```utlx
{
  invoice: {
    id: $input.Order.id,
    customer: $input.Order.customer.name,
    total: sum($input.Order.items.(price * quantity))
  }
}
```

## Key Differences

### 1. Format Support

**JSONata**: JSON only. Cannot read or write XML, CSV, YAML, or any other format.

**UTL-X**: 10+ formats — XML, JSON, CSV, YAML, XSD, JSON Schema, Avro, Protobuf, OData, EDMX. The same transformation logic works regardless of input/output format.

```bash
# UTL-X: instant format conversion without a script
cat data.xml | utlx                # XML to JSON
cat data.json | utlx               # JSON to XML
cat data.csv | utlx                # CSV to JSON
cat data.xml | utlx --to yaml     # XML to YAML
```

JSONata has no equivalent — it cannot process non-JSON input.

### 2. Standard Library

**JSONata**: ~50 built-in functions focused on JSON manipulation.

**UTL-X**: 652 functions across 18 categories including String (83), Array (67), Date (68), Math (37), XML (60), Encoding (30), Binary (47), CSV (12), YAML (22), Financial (16), Geospatial (8), Security (16).

### 3. Schema Awareness

**JSONata**: No schema support.

**UTL-X**: First-class support for XSD, JSON Schema, Avro Schema, and Protocol Buffers. Can validate input/output against schemas and transform between schema formats.

### 4. Multi-Input Transformations

**JSONata**: Single input only.

**UTL-X**: Multiple named inputs from different formats in one transformation:

```utlx
%utlx 1.0
input: orders xml, customers json, rates csv
output json
---
{
  enriched: $orders.Order |> map(order => {
    customer: $customers[order.customerId],
    rate: $rates[order.currency]
  })
}
```

### 5. Type System

**JSONata**: Dynamic typing — errors discovered at runtime.

**UTL-X**: Strong, inferred type system — catches type mismatches at compile time.

### 6. OData Support

**JSONata**: No OData support.

**UTL-X**: Native OData JSON and EDMX metadata parsing and serialization, enabling integration with SAP, Microsoft Dynamics, and other enterprise systems.

## When to Choose UTL-X

- Need multi-format support (XML, CSV, YAML, not just JSON)
- Need format conversion without writing transformation logic
- Need schema validation (XSD, JSON Schema, Avro, Protobuf)
- Need multi-input transformations (joining data from different sources/formats)
- Need a rich standard library (652 functions)
- Need OData/enterprise integration
- Want type safety and compile-time error checking

## When to Choose JSONata

- Only working with JSON
- Need a lightweight JavaScript-embeddable solution
- Prefer XPath-style syntax
- Have existing JSONata transformations
- Need a permissive (MIT) license
- Want browser-side transformations (JSONata runs in-browser)
