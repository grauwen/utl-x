# UTL-X vs JSONata

Comparison between UTL-X and JSONata.

## Overview

| Feature | JSONata | UTL-X |
|---------|---------|-------|
| **License** | MIT (Open) |  GNU Affero General Public License v3.0 (AGPL-3.0) COPYLEFT|
| **Formats** | JSON only | XML, JSON, CSV, YAML, extensible |
| **Paradigm** | Functional, XPath-inspired | Functional + Templates |
| **Runtime** | JavaScript only | JVM, JavaScript, Native |
| **Type System** | Dynamic | Strong, inferred |
| **Templates** | No | Yes (XSLT-style) |

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

**JSONata**: JSON only

**UTL-X**: Multiple formats (XML, JSON, CSV, YAML)

### 2. Template Matching

**JSONata**: Not supported

**UTL-X**: XSLT-style templates

### 3. Type System

**JSONata**: Dynamic typing

**UTL-X**: Strong, static typing with inference

## When to Choose UTL-X

- Need multi-format support
- Want template matching
- Need type safety
- Want multiple runtimes

## When to Choose JSONata

- Only working with JSON
- Need lightweight JavaScript solution
- Prefer XPath-style syntax
- Have existing JSONata transformations

---
