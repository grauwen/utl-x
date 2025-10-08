# UTL-X vs XSLT

This guide compares UTL-X with XSLT, showing how UTL-X builds on XSLT's strengths while addressing its limitations.

## Overview

XSLT (eXtensible Stylesheet Language Transformations) is the most mature transformation language, with 25+ years of development. UTL-X draws heavy inspiration from XSLT while modernizing it for today's multi-format world.

## Quick Comparison

| Feature | XSLT | UTL-X |
|---------|------|-------|
| **License** | W3C Standard (Open) | Apache 2.0 (Open) |
| **First Release** | 1999 | 2025 |
| **Formats** | XML only | XML, JSON, CSV, YAML, extensible |
| **Paradigm** | Declarative templates | Templates + Functional |
| **Syntax** | XML-based | Custom DSL |
| **Learning Curve** | Steep | Medium |
| **Performance** | Excellent (compiled) | Excellent (optimized) |
| **Tooling** | Mature (25+ years) | Growing |
| **Community** | Large, aging | Small, growing |
| **Runtime** | Many (JVM, .NET, C++) | JVM, JavaScript, Native |

## XSLT's Strengths

XSLT excels at:

1. **XML transformations**: Designed specifically for XML
2. **Template matching**: Declarative, pattern-based approach
3. **Maturity**: 25 years of refinement
4. **Tooling**: Excellent IDE support, debuggers, profilers
5. **Standards**: W3C-backed specification

## XSLT's Limitations

1. **XML-only**: Cannot handle JSON, CSV, or other formats natively
2. **Verbose syntax**: XML-based syntax is wordy
3. **Learning curve**: Complex for beginners
4. **Modern features**: Lacks modern FP features (map, filter, reduce in XSLT 2.0+, but syntax is clunky)

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
template match="Order" {
  invoice: {
    id: @id,
    customer: Customer.Name,
    total: sum(Items.Item.(parseNumber(@price) * parseNumber(@quantity)))
  }
}
```

**Key Differences:**
- UTL-X is more concise
- No XML namespace declarations
- Cleaner syntax for calculations

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
  invoices: input.Orders.Order |> map(order => {
    invoice: {
      id: order.@id,
      total: order.@total
    }
  })
}
```

**Key Differences:**
- UTL-X uses functional `map` instead of `for-each`
- More concise and readable
- Easier to compose with other operations

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
template match="Customer" {
  customer: {
    name: Name,
    discount: if (@type == "VIP") 
                20 
              else if (OrderTotal > 1000) 
                10 
              else 
                0
  }
}
```

**Key Differences:**
- UTL-X uses expression-based `if/else`
- XSLT uses element-based `choose/when/otherwise`
- UTL-X is more compact

### Example 4: Recursive Templates

**XSLT:**
```xml
<xsl:template match="Category">
  <category>
    <name><xsl:value-of select="@name"/></name>
    <xsl:if test="Subcategory">
      <subcategories>
        <xsl:apply-templates select="Subcategory"/>
      </subcategories>
    </xsl:if>
  </category>
</xsl:template>

<xsl:template match="Subcategory">
  <xsl:apply-templates select="."/>
</xsl:template>
```

**UTL-X:**
```utlx
template match="Category" {
  category: {
    name: @name,
    subcategories: if (Subcategory) apply(Subcategory) else null
  }
}
```

**Key Differences:**
- UTL-X handles recursion implicitly
- No need for separate subcategory template
- Cleaner conditional handling

## Format Independence

The biggest difference: **UTL-X works with multiple formats**.

### Same Transformation, Different Formats

**UTL-X Transformation:**
```utlx
%utlx 1.0
input auto
output json
---
{
  summary: {
    total: sum(input.orders.*.total),
    count: count(input.orders)
  }
}
```

**Works with XML Input:**
```xml
<data>
  <orders>
    <order><total>100</total></order>
    <order><total>200</total></order>
  </orders>
</data>
```

**Works with JSON Input:**
```json
{
  "data": {
    "orders": [
      {"total": 100},
      {"total": 200}
    ]
  }
}
```

**Works with CSV Input:**
```csv
total
100
200
```

**XSLT**: Would only work with XML input.

## Migration from XSLT to UTL-X

### Step 1: Header Conversion

**XSLT:**
```xml
<?xml version="1.0"?>
<xsl:stylesheet version="1.0" 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output xml
---
```

### Step 2: Template Conversion

**XSLT:**
```xml
<xsl:template match="/Order">
  <invoice>
    <id><xsl:value-of select="@id"/></id>
  </invoice>
</xsl:template>
```

**UTL-X:**
```utlx
template match="Order" {
  invoice: {
    id: @id
  }
}
```

### Step 3: XPath to UTL-X Selectors

| XSLT XPath | UTL-X Selector |
|------------|----------------|
| `Order/@id` | `Order.@id` |
| `Order/Customer/Name` | `Order.Customer.Name` |
| `Order/Items/Item` | `Order.Items.Item` or `Order.Items.Item[*]` |
| `Order/Items/Item[@price > 100]` | `Order.Items.Item[price > 100]` |
| `//ProductCode` | `..ProductCode` |

### Step 4: Convert Functions

| XSLT Function | UTL-X Equivalent |
|---------------|------------------|
| `<xsl:value-of select="..."/>` | Just use selector |
| `<xsl:for-each select="...">` | `... |> map(...)` |
| `<xsl:if test="...">` | `if (...) ... else ...` |
| `<xsl:choose>` | `if/else` or `match` |
| `sum(...)` | `sum(...)` |
| `count(...)` | `count(...)` |
| `concat(...)` | `concat(...)` or `+` |
| `substring(...)` | `substring(...)` |

### Automated Migration Tool

```bash
utlx migrate xslt-stylesheet.xsl --output utlx-script.utlx
```

Current status: 85% XSLT 1.0 coverage, 70% XSLT 2.0 coverage (v1.0)

## When to Choose UTL-X

✅ **Choose UTL-X if:**
- You need **multi-format** support (not just XML)
- You want **modern functional** programming features
- You prefer **concise syntax**
- You need **JavaScript or native** runtimes
- You're building **new transformations**

## When to Choose XSLT

✅ **Choose XSLT if:**
- You work **exclusively with XML**
- You have **existing XSLT transformations**
- You need **maximum tooling** support
- You require **W3C standard** compliance
- You have **XSLT expertise** on your team

## Coexistence Strategy

You don't have to choose one or the other!

**Gradual Migration:**
1. Keep existing XSLT for stable transformations
2. Write new transformations in UTL-X
3. Migrate XSLT transformations opportunistically
4. Run both side-by-side

**Hybrid Approach:**
```utlx
// Call XSLT from UTL-X (planned v1.2)
{
  result: xslt("legacy-transform.xsl", input.xmlData)
}
```

## Learning Path

**If you know XSLT:**
- ✅ You already understand template matching
- ✅ You're familiar with declarative transformations
- ➕ Learn UTL-X's functional features (map, filter, reduce)
- ➕ Learn UTL-X's expression-based syntax
- ➕ Learn multi-format handling

**Estimated learning time**: 1-2 days for proficient XSLT developers

## Community Resources

- **XSLT to UTL-X Guide**: Step-by-step migration
- **Syntax Cheat Sheet**: Side-by-side comparison
- **Pattern Library**: Common XSLT patterns in UTL-X
- **Migration Tool**: Automated XSLT → UTL-X conversion

## Conclusion

UTL-X honors XSLT's proven template-matching approach while modernizing it for today's multi-format data landscape. If you love XSLT's declarative power but need to work with more than just XML, UTL-X is the natural evolution.

---
