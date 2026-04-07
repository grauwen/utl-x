# Language Overview

UTL-X is a format-agnostic functional transformation language for converting data between XML, JSON, CSV, YAML, OData, and schema formats (XSD, JSON Schema, Avro, Protobuf, OData/EDMX, Table Schema).

---

## Design Philosophy

### 1. Format Agnostic

Write transformation logic once, use it with any supported format.

```utlx
%utlx 1.0
input auto
output json
---
{
  name: $input.person.name,
  age: $input.person.age
}
```

This works whether input is XML, JSON, CSV, or YAML.

### 2. Functional Programming

Pure functions, immutable data, composability.

```utlx
$input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

### 3. Declarative

Describe what you want, not how to get it.

```utlx
{
  total: sum($input.items.*.price)
}
```

### 4. Type Safe

Strong typing with inference. Catch errors at compile time.

```utlx
let count: Number = 42
let name: String = "Alice"
let wrong: Number = "hello"  // Type error caught at compile time
```

---

## Supported Formats

### Tier 1 — Data Formats

| Format | Input | Output |
|--------|-------|--------|
| XML | Yes | Yes |
| JSON | Yes | Yes |
| CSV | Yes | Yes |
| YAML | Yes | Yes |
| OData | Yes | Yes |

### Tier 2 — Schema/Metadata Formats

| Format | Input | Output |
|--------|-------|--------|
| XSD | Yes | Yes |
| JSCH (JSON Schema) | Yes | Yes |
| Avro | Yes | Yes |
| Protobuf | Yes | Yes |
| OSCH (OData/EDMX) | Yes | Yes |
| TSCH (Table Schema) | Yes | Yes |

---

## CLI Usage

### Identity Mode (No Script Needed)

```bash
cat data.xml | utlx                # XML to JSON (smart flip)
cat data.json | utlx               # JSON to XML (smart flip)
cat data.csv | utlx                # CSV to JSON
cat data.xml | utlx --to yaml     # Override output format
```

### Script-Based Transformation

```bash
utlx transform script.utlx input.xml -o output.json
utlx script.utlx input.xml -o output.json    # implicit transform
```

---

## Language Features

- **Selectors** — XPath-like data navigation
- **Pipeline Operator** (`|>`) — chain operations left-to-right
- **Pattern Matching** — match expressions with multiple cases
- **Higher-Order Functions** — map, filter, reduce, groupBy, sortBy, etc.
- **Type Inference** — automatic type detection with optional annotations
- **Immutability** — data safety, no side effects
- **User-Defined Functions** — reusable logic (PascalCase naming)
- **Multi-Input** — combine data from multiple sources/formats
- **652 Stdlib Functions** — string, array, math, date, encoding, XML, CSV, YAML, financial, geospatial, and more

---

## Document Structure

Every UTL-X script follows this structure:

```utlx
%utlx 1.0             // Version declaration
input <format>         // Input format (or auto)
output <format>        // Output format
---                    // Separator
<transformation>       // Transformation body
```

---

## Core Concepts

### Expressions

Everything returns a value:

```utlx
42                                          // Literal
10 + 20                                     // Arithmetic
if (x > 10) "big" else "small"              // Conditional
match status { "active" => 1, _ => 0 }      // Pattern match
```

### Selectors

Navigate data structures:

```utlx
$input.order.customer.name        // Deep navigation
$input.items[0]                   // Index access
$input.items[*]                   // All elements
$input.order.@id                  // XML attribute
$input..productCode               // Recursive search
$input.items[price > 100]         // Predicate filter
```

### Pipeline

Chain operations left-to-right:

```utlx
$input.items
  |> filter(item => item.active)
  |> map(item => item.price * 1.1)
  |> sum()
```

### Functions

```utlx
// Built-in (652 functions)
sum([1, 2, 3])               // 6
upper("hello")               // "HELLO"
formatDate(now(), "yyyy-MM-dd")

// User-defined (PascalCase required)
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}
```

### Variables

```utlx
{
  let subtotal = sum($input.items.*.price),
  let tax = subtotal * 0.08,

  total: subtotal + tax
}
```

### Multi-Input

```utlx
%utlx 1.0
input: orders xml, customers json
output json
---
{
  enriched: $orders.Order |> map(order => {
    id: order.@id,
    customer: $customers[order.customerId]
  })
}
```

---

## Data Types

```utlx
"Hello"          // String
42               // Number (integer)
3.14             // Number (decimal)
true             // Boolean
null             // Null
[1, 2, 3]        // Array
{name: "Alice"}  // Object
```

---

## Control Flow

### If-Else

```utlx
if (score >= 90) "A"
else if (score >= 80) "B"
else if (score >= 70) "C"
else "F"
```

### Pattern Matching

```utlx
match orderType {
  "express" => { shipping: 15.00, delivery: "1-2 days" },
  "standard" => { shipping: 5.00, delivery: "3-5 days" },
  _ => { shipping: 0, delivery: "unknown" }
}
```

---

## Common Patterns

### Rename Fields

```utlx
{
  newName: $input.oldName,
  newEmail: $input.oldEmail
}
```

### Flatten Structure

```utlx
{
  orderId: $input.order.id,
  customerName: $input.order.customer.name,
  customerEmail: $input.order.customer.email
}
```

### Transform Array

```utlx
{
  products: $input.products |> map(p => {
    id: p.id,
    name: upper(p.name),
    price: p.price * 1.1
  })
}
```

### Filter and Aggregate

```utlx
{
  expensiveItems: $input.items |> filter(i => i.price > 100),
  totalValue: sum($input.items.*.price),
  itemCount: count($input.items)
}
```

### Group By

```utlx
{
  byCategory: $input.products
    |> groupBy(p => p.category)
}
```

---

## Language Comparison

| Aspect | UTL-X | XSLT | DataWeave | jq | JSONata |
|--------|-------|------|-----------|-----|---------|
| License | AGPL-3.0 / Commercial | W3C (Open) | Proprietary | MIT | MIT |
| Formats | 11 (data + schema) | XML only | XML, JSON, CSV, Java | JSON only | JSON only |
| Stdlib | 652 functions | ~100 XPath | ~80-100 | ~50 | ~50 |
| CLI piping | Yes | No | No | Yes | No |
| Type system | Strong, inferred | XSD-aware | Strong, inferred | Dynamic | Dynamic |
| Runtime | JVM / GraalVM native | JVM, .NET, C++ | JVM (MuleSoft) | Native C | JavaScript |

See detailed comparisons: [vs XSLT](../comparison/vs-xslt.md) | [vs DataWeave](../comparison/vs-dataweave.md) | [vs jq](../comparison/vs-jq.md) | [vs JSONata](../comparison/vs-jsonata.md)

---

## Learning Path

### Beginner

1. [Installation](../getting-started/installation.md)
2. [Your First Transformation](../getting-started/your-first-transformation.md)
3. [Basic Concepts](../getting-started/basic-concepts.md)
4. [Quick Reference](../getting-started/quick-reference.md)

### Intermediate

1. [Syntax Guide](syntax.md)
2. [Functions](functions.md)
3. [Selectors](selectors.md)
4. [Examples](../examples/)

### Reference

1. [Stdlib Reference (652 functions)](../stdlib/stdlib-complete-reference.md)
2. [Comparison Guides](../comparison/)

---

## Next Steps

- [Your First Transformation](../getting-started/your-first-transformation.md)
- [Quick Reference](../getting-started/quick-reference.md)
- [Examples](../examples/)
- [Stdlib Reference](../stdlib/stdlib-complete-reference.md)
