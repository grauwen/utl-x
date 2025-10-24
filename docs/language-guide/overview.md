# Language Overview

UTL-X is a modern, format-agnostic transformation language designed to convert data between XML, JSON, CSV, YAML, and other formats.

---

## Design Philosophy

### 1. Format Agnostic

**One language, any format.**

Write transformation logic once, use it with any input/output format combination.

```utlx
// Same transformation works for XML, JSON, CSV, YAML
%utlx 1.0
input auto
output json
---
{
  name: $input.person.name,
  age: $input.person.age
}
```

### 2. Functional Programming

**Pure functions, immutable data, composability.**

- Functions have no side effects
- Data cannot be modified
- Operations compose naturally

```utlx
input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

### 3. Declarative

**Describe what you want, not how to get it.**

Focus on the transformation, not the mechanics.

```utlx
// Declarative: what you want
{
  total: sum(items.*.price)
}

// vs Imperative (traditional programming):
// let total = 0
// for each item in items:
//   total += item.price
```

### 4. Type Safe

**Strong typing with inference.**

Catch errors at compile time, not runtime.

```utlx
let count: Number = 42
let name: String = "Alice"

// Type error caught at compile time:
let wrong: Number = "hello"  // ❌ ERROR
```

### 5. Performance First

**Optimized compilation, efficient runtime.**

- Compile-time optimization
- Lazy evaluation
- Minimal memory footprint
- Streaming where possible

---

## Key Features

### Format Support

| Format | Input | Output | Status |
|--------|-------|--------|--------|
| **XML** | ✅ | ✅ | Alpha |
| **JSON** | ✅ | ✅ | Alpha |
| **CSV** | 🚧 | 🚧 | Planned |
| **YAML** | 🚧 | 🚧 | Planned |
| **Custom** | 🔌 | 🔌 | Plugin API |

### Language Features

- ✅ **Selectors** - XPath-like navigation
- ✅ **Pipeline Operator** - Chain operations
- ✅ **Pattern Matching** - Match expressions
- ✅ **Template Matching** - XSLT-style templates
- ✅ **Higher-Order Functions** - map, filter, reduce
- ✅ **Type Inference** - Automatic type detection
- ✅ **Immutability** - Data safety
- ✅ **User-Defined Functions** - Reusable logic

---

## Language Comparison

### UTL-X vs XSLT

```xml
<!-- XSLT: Verbose, XML-based -->
<xsl:template match="Order">
  <json>
    <orderId><xsl:value-of select="@id"/></orderId>
    <total>
      <xsl:value-of select="sum(Items/Item/@price * Items/Item/@quantity)"/>
    </total>
  </json>
</xsl:template>
```

```utlx
// UTL-X: Concise, modern syntax
template match="Order" {
  orderId: $id,
  total: sum(Items/Item/($price * $quantity))
}
```

### UTL-X vs DataWeave

```dataweave
%dw 2.0
output application/json
---
{
  orderId: payload.Order.@id,
  total: sum(payload.Order.Items.*Item map ($.@price * $.@quantity))
}
```

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  total: sum($input.Order.Items.Item.($price * $quantity))
}
```

**Differences:**
- UTL-X: Open source (AGPL-3.0)
- DataWeave: Proprietary (Salesforce/MuleSoft)
- Similar syntax and capabilities
- UTL-X has template matching (XSLT-inspired)

---

## Document Structure

Every UTL-X document follows this structure:

```utlx
%utlx <version>           // 1. Version declaration
[directive...]            // 2. Configuration directives
---                       // 3. Separator
<transformation-body>     // 4. Transformation logic
```

### Example

```utlx
// 1. Version
%utlx 1.0

// 2. Directives
input xml
output json

// 3. Separator
---

// 4. Transformation
{
  result: $input.data
}
```

---

## Core Concepts

### 1. Expressions

Everything in UTL-X is an expression that returns a value:

```utlx
// Literals are expressions
42
"Hello"
true

// Operations are expressions
10 + 20

// Conditionals are expressions
if (x > 10) "big" else "small"

// Blocks are expressions (return last value)
{
  let x = 10,
  let y = 20,
  x + y  // Returns 30
}
```

### 2. Selectors

Navigate through data structures:

```utlx
input.order.customer.name        // Deep navigation
input.items[0]                   // Index access
input.items[*]                   // All elements
input.order.@id                  // Attribute
input..productCode               // Recursive search
input.items[price > 100]         // Predicate filter
```

### 3. Pipeline

Chain operations left-to-right:

```utlx
input.items
  |> filter(item => item.active)
  |> map(item => item.price * 1.1)
  |> sum()
```

### 4. Functions

Built-in and user-defined:

```utlx
// Built-in functions
sum([1, 2, 3])              // 6
upper("hello")              // "HELLO"
count($input.items)          // Number of items

// User-defined functions
function calculateTax(amount: Number, rate: Number): Number {
  amount * rate
}
```

### 5. Templates

XSLT-style template matching:

```utlx
template match="Order" {
  order: {
    id: $id,
    customer: apply(Customer),
    items: apply(Items/Item)
  }
}

template match="Customer" {
  name: Name,
  email: Email
}
```

---

## Data Types

### Scalar Types

```utlx
"Hello"          // String
'World'          // String (single quotes)
42               // Number (integer)
3.14             // Number (decimal)
1.5e10           // Number (scientific notation)
true             // Boolean
false            // Boolean
null             // Null
```

### Composite Types

```utlx
// Object
{
  name: "Alice",
  age: 30,
  active: true
}

// Array
[1, 2, 3]
["red", "green", "blue"]
[{id: 1}, {id: 2}]
```

---

## Control Flow

### Conditionals

```utlx
// If-else
if (condition) expression1 else expression2

// Multi-way
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

### 1. Rename Fields

```utlx
{
  newName: $input.oldName,
  newEmail: $input.oldEmail
}
```

### 2. Flatten Structure

```utlx
{
  orderId: $input.order.id,
  customerName: $input.order.customer.name,
  customerEmail: $input.order.customer.email
}
```

### 3. Transform Array

```utlx
{
  products: $input.products |> map(p => {
    id: p.id,
    name: upper(p.name),
    price: p.price * 1.1
  })
}
```

### 4. Filter and Aggregate

```utlx
{
  expensiveItems: $input.items |> filter(i => i.price > 100),
  totalValue: sum($input.items.*.price),
  itemCount: count($input.items)
}
```

### 5. Group By

```utlx
{
  byCategory: $input.products 
    |> groupBy(p => p.category)
    |> map((category, products) => {
        category: category,
        count: count(products),
        total: sum(products.*.price)
      })
}
```

---

## Advantages Over Alternatives

### vs XSLT

| Aspect | XSLT | UTL-X |
|--------|------|-------|
| Syntax | XML-based (verbose) | Modern, concise |
| Formats | XML only | XML, JSON, CSV, YAML |
| Learning Curve | Steep | Moderate |
| Type System | XSD-aware | Strong inference |

### vs DataWeave

| Aspect | DataWeave | UTL-X |
|--------|-----------|-------|
| License | Proprietary | Open Source (AGPL-3.0) |
| Vendor | Salesforce/MuleSoft | Community/Glomidco |
| Templates | Limited | XSLT-style matching |
| Cost | MuleSoft license | Free (OSS) or commercial |

### vs JSONata

| Aspect | JSONata | UTL-X |
|--------|---------|-------|
| Formats | JSON only | Multiple formats |
| Templates | No | Yes |
| Compilation | Interpreted | Compiled |
| Type System | Dynamic | Static with inference |

### vs Custom Code (Java/JavaScript)

| Aspect | Custom Code | UTL-X |
|--------|-------------|-------|
| Development Time | Slow | Fast |
| Maintenance | Complex | Simple |
| Format Changes | Manual updates | Automatic |
| Testing | Extensive | Declarative = less bugs |

---

## Evolution Path

### Current (v0.1.0 - Alpha)

- ✅ Core language features
- ✅ XML and JSON support
- ✅ Basic transformations
- ✅ CLI tool

### Near Future (v0.2.0 - Beta)

- 🚧 CSV support
- 🚧 YAML support
- 🚧 Standard library expansion
- 🚧 IDE plugins (VS Code, IntelliJ)

### v1.0.0 (Stable)

- 🎯 Production-ready
- 🎯 Full format support
- 🎯 Performance optimizations
- 🎯 Comprehensive documentation
- 🎯 Plugin ecosystem

---

## Use Cases

### 1. API Integration

Transform between different API formats:

```utlx
// SOAP XML → REST JSON
%utlx 1.0
input xml
output json
---
{
  user: {
    id: $input.soap:Envelope.soap:Body.GetUserResponse.User.@id,
    name: $input.soap:Envelope.soap:Body.GetUserResponse.User.Name
  }
}
```

### 2. Data Migration

Convert legacy formats to modern:

```utlx
// Legacy CSV → Modern JSON
%utlx 1.0
input csv { headers: true }
output json
---
{
  records: $input.rows |> map(row => {
    id: parseNumber(row.ID),
    fullName: row.FirstName + " " + row.LastName,
    email: lower(row.Email)
  })
}
```

### 3. ETL Pipelines

Extract, transform, load workflows:

```utlx
// Extract from XML, aggregate, output to JSON
%utlx 1.0
input xml
output json
---
{
  summary: {
    totalOrders: count($input.Orders.Order),
    totalRevenue: sum($input.Orders.Order.*.Total),
    avgOrderValue: avg($input.Orders.Order.*.Total)
  },
  byRegion: $input.Orders.Order 
    |> groupBy(o => o.Region)
    |> map((region, orders) => {
        region: region,
        count: count(orders),
        revenue: sum(orders.*.Total)
      })
}
```

### 4. Configuration Management

Transform config files:

```utlx
// XML config → YAML config
%utlx 1.0
input xml
output yaml
---
{
  application: {
    name: $input.config.app.@name,
    version: $input.config.app.@version,
    settings: $input.config.settings.* |> map(s => {
      key: s.@key,
      value: s.text()
    })
  }
}
```

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

### Advanced

1. [Templates](templates.md)
2. [Language Specification](../reference/language-spec.md)
3. [Architecture](../architecture/overview.md)
4. [Custom Formats](../formats/custom-formats.md)

---

## Community & Support

- 💬 [Discussions](https://github.com/grauwen/utl-x/discussions) - Ask questions
- 🐛 [Issues](https://github.com/grauwen/utl-x/issues) - Report bugs
- 📧 [Email](mailto:community@glomidco.com) - Contact us
- 🐦 [Twitter](https://twitter.com/UTLXLang) - Follow updates

---

## Contributing

UTL-X is open source! Contributions welcome:

- 📝 Improve documentation
- 🐛 Fix bugs
- ✨ Add features
- 💡 Suggest improvements

See [Contributing Guide](../../CONTRIBUTING.md)

---

## License

UTL-X is dual-licensed:
- **AGPL-3.0** - Open source use
- **Commercial** - Proprietary use without AGPL obligations

See [LICENSE.md](../../LICENSE.md)

---

## Next Steps

Ready to dive deeper?

- 📖 [Syntax Guide](syntax.md) - Detailed syntax reference
- 🔧 [Functions](functions.md) - Built-in functions
- 💡 [Examples](../examples/) - Practical examples
- 📚 [Language Spec](../reference/language-spec.md) - Complete specification

**Happy transforming!** 🚀
