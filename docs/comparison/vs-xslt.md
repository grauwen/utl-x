# UTL-X vs XSLT

This guide compares UTL-X with XSLT, showing how UTL-X builds on XSLT's strengths while addressing its limitations.

## Overview

XSLT (eXtensible Stylesheet Language Transformations) is the most mature transformation language, with 25+ years of development. UTL-X draws inspiration from XSLT while modernizing it for today's multi-format world.

## Quick Comparison

| Feature | XSLT | UTL-X |
|---------|------|-------|
| **License** | W3C Standard (Open) | AGPL-3.0 / Commercial dual-license |
| **First Release** | 1999 | 2025 |
| **Formats** | XML only | XML, JSON, CSV, YAML, XSD, JSON Schema, Avro, Protobuf, OData |
| **Paradigm** | Declarative templates | Functional, declarative |
| **Syntax** | XML-based (verbose) | Custom DSL (concise) |
| **Stdlib** | ~100 XPath functions | 652 functions across 18 categories |
| **Learning Curve** | Steep | Medium |
| **Performance** | Excellent (compiled) | Excellent (JVM or GraalVM native binary) |
| **Tooling** | Mature (25+ years) | Growing (CLI, LSP, VS Code) |
| **Runtime** | Many (JVM, .NET, C++) | JVM or GraalVM native binary |
| **Schema support** | XSD only | XSD, JSON Schema, Avro, Protobuf, OData/EDMX |
| **Multi-input** | Via `document()` function | Native multi-input with named sources |
| **CLI piping** | Not a CLI tool | `cat data.xml \| utlx` (smart format flip) |

## XSLT's Strengths

1. **XML transformations**: 25 years of XML-specific optimization
2. **Template matching**: Declarative, pattern-based approach
3. **Maturity**: Battle-tested in enterprise environments
4. **Tooling**: Excellent IDE support, debuggers, profilers
5. **Standards**: W3C-backed specification
6. **Multiple implementations**: Saxon, Xalan, libxslt, MSXML

## XSLT's Limitations

1. **XML-only**: Cannot handle JSON, CSV, YAML, OData, or schema formats natively
2. **Verbose syntax**: XML-based syntax requires significant boilerplate
3. **Learning curve**: Complex for beginners
4. **No CLI piping**: Cannot be used as a shell tool like jq or UTL-X
5. **Limited stdlib**: ~100 XPath functions vs UTL-X's 652
6. **No schema transformation**: Cannot convert between XSD, JSON Schema, Avro, Protobuf

## Side-by-Side Examples

### Example 1: Simple Transformation

**XSLT:**
```xml
<?xml version="1.0"?>
<xsl:stylesheet version="1.0" 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  
  <xsl:template match="/Order">
    <invoice>
      <id><xsl:value-of select="@id"/></id>
      <customer><xsl:value-of select="Customer/Name"/></customer>
      <total><xsl:value-of select="sum(Items/Item/@price * Items/Item/@quantity)"/></total>
    </invoice>
  </xsl:template>
  
</xsl:stylesheet>
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output xml
---
{
  invoice: {
    id: $input.Order.@id,
    customer: $input.Order.Customer.Name,
    total: sum($input.Order.Items.Item.(parseNumber(@price) * parseNumber(@quantity)))
  }
}
```

### Example 2: Array Mapping

**XSLT:**
```xml
<xsl:template match="/Orders">
  <invoices>
    <xsl:for-each select="Order">
      <invoice>
        <id><xsl:value-of select="@id"/></id>
        <total><xsl:value-of select="@total"/></total>
      </invoice>
    </xsl:for-each>
  </invoices>
</xsl:template>
```

**UTL-X:**
```utlx
{
  invoices: $input.Orders.Order |> map(order => {
    invoice: {
      id: order.@id,
      total: order.@total
    }
  })
}
```

### Example 3: Conditional Logic

**XSLT:**
```xml
<xsl:template match="Customer">
  <customer>
    <name><xsl:value-of select="Name"/></name>
    <xsl:choose>
      <xsl:when test="@type='VIP'">
        <discount>20</discount>
      </xsl:when>
      <xsl:when test="OrderTotal > 1000">
        <discount>10</discount>
      </xsl:when>
      <xsl:otherwise>
        <discount>0</discount>
      </xsl:otherwise>
    </xsl:choose>
  </customer>
</xsl:template>
```

**UTL-X:**
```utlx
{
  customer: {
    name: $input.Customer.Name,
    discount: if ($input.Customer.@type == "VIP") 
                20 
              else if ($input.Customer.OrderTotal > 1000) 
                10 
              else 
                0
  }
}
```

## What UTL-X adds beyond XSLT

### 1. Format-Agnostic Transformations

The same transformation works regardless of input/output format:

```bash
# Same script, different formats
utlx transform order.utlx order.xml -o invoice.json
utlx transform order.utlx order.json -o invoice.xml
utlx transform order.utlx order.csv -o invoice.yaml
```

XSLT can only process XML input and produce XML/text output.

### 2. Instant Format Conversion (CLI)

```bash
# Zero-script format conversion
cat data.xml | utlx                # XML to JSON
cat data.json | utlx               # JSON to XML
cat data.xml | utlx --to yaml     # XML to YAML
cat data.csv | utlx --to xml      # CSV to XML
```

XSLT has no CLI piping equivalent.

### 3. 652 Standard Library Functions

UTL-X provides 652 functions across 18 categories vs XSLT's ~100 XPath functions:

| Category | UTL-X | XSLT/XPath |
|----------|-------|------------|
| String | 83 | ~15 |
| Array | 67 | ~10 (XSLT 2.0+) |
| Date | 68 | ~10 (XSLT 2.0+) |
| Math | 37 | ~5 |
| XML | 60 | N/A (built into language) |
| Encoding | 30 | None |
| Binary | 47 | None |
| Financial | 16 | None |
| Geospatial | 8 | None |
| Security | 16 | None |
| CSV | 12 | None |
| YAML | 22 | None |

### 4. Schema Transformation

UTL-X can transform between schema formats — something XSLT cannot do:

```bash
# Convert XSD to JSON Schema
utlx transform xsd-to-jsch.utlx schema.xsd -o schema.json

# Convert Avro schema to Protobuf
utlx transform avro-to-proto.utlx schema.avsc -o schema.proto
```

Supported schema formats: XSD, JSON Schema, Avro, Protobuf, OData/EDMX.

### 5. Multi-Input from Different Formats

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

XSLT's `document()` function only loads additional XML files.

### 6. OData Integration

UTL-X natively parses and serializes OData JSON and EDMX metadata, enabling integration with SAP, Microsoft Dynamics, and other enterprise OData services. XSLT has no OData awareness.

## Migration from XSLT to UTL-X

### XPath to UTL-X Selectors

| XSLT XPath | UTL-X Selector |
|------------|----------------|
| `Order/@id` | `Order.@id` |
| `Order/Customer/Name` | `Order.Customer.Name` |
| `Order/Items/Item` | `Order.Items.Item` |
| `Order/Items/Item[@price > 100]` | `Order.Items.Item[price > 100]` |
| `//ProductCode` | `..ProductCode` |

### Function Mapping

| XSLT/XPath | UTL-X Equivalent |
|------------|------------------|
| `<xsl:value-of select="..."/>` | Direct selector |
| `<xsl:for-each select="...">` | `... \|> map(...)` |
| `<xsl:if test="...">` | `if (...) ... else ...` |
| `<xsl:choose>` | `if/else` or `match` |
| `sum(...)` | `sum(...)` |
| `count(...)` | `count(...)` |
| `concat(...)` | `concat(...)` or `+` |
| `substring(...)` | `substring(...)` |
| `translate(...)` | `replace(...)` |
| `document(...)` | Multi-input declaration |

## When to Choose UTL-X

- Need multi-format support (not just XML)
- Need schema transformation (XSD, JSON Schema, Avro, Protobuf)
- Need CLI piping and instant format conversion
- Need a rich standard library (652 functions)
- Need OData/enterprise integration
- Building new transformations
- Want concise, readable syntax

## When to Choose XSLT

- Work exclusively with XML
- Have existing XSLT transformations
- Need maximum tooling support and IDE integration
- Require W3C standard compliance
- Have XSLT expertise on your team
- Need .NET or C++ runtime support

## Coexistence Strategy

You don't have to choose one or the other:

1. Keep existing XSLT for stable XML-only transformations
2. Write new multi-format transformations in UTL-X
3. Migrate XSLT transformations opportunistically
4. Use UTL-X for format conversion, XSLT for complex XML-specific work

## Conclusion

UTL-X honors XSLT's proven declarative approach while modernizing it for today's multi-format data landscape. With 652 functions, 10+ supported formats, CLI piping, and schema transformation capabilities, UTL-X addresses the gaps that make XSLT insufficient for modern integration needs. For pure XML work with existing stylesheets, XSLT remains excellent. For everything else, UTL-X is the natural evolution.
