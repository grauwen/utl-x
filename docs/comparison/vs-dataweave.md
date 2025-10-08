
# UTL-X vs DataWeave

This guide compares UTL-X with DataWeave, highlighting similarities, differences, and migration strategies.

## Overview

Both UTL-X and DataWeave are format-agnostic functional transformation languages. The key difference: **UTL-X is open source with community governance**, while **DataWeave is proprietary and controlled by Salesforce/MuleSoft**.

## Side-by-Side Comparison

### Basic Transformation

**DataWeave:**
```dataweave
%dw 2.0
output application/json
---
{
  invoice: {
    id: payload.Order.@id,
    customer: payload.Order.Customer.Name,
    total: sum(payload.Order.Items.*Item map ($.@price * $.@quantity))
  }
}
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    id: input.Order.@id,
    customer: input.Order.Customer.Name,
    total: sum(input.Order.Items.Item.(parseNumber(@price) * parseNumber(@quantity)))
  }
}
```

### Key Differences

| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| **License** | Proprietary (MuleSoft/Salesforce) |AGPL-3.0 (Open Source) |
| **Governance** | Vendor-controlled | Community-driven  |
| **Cost** | Part of MuleSoft (expensive) | Free or licenced according AGPL-3.0 |
| **Runtime** | JVM only | JVM, JavaScript, Native |
| **Input Variable** | `payload` | `input` |
| **Template Matching** | No | Yes (XSLT-style) |
| **Type Inference** | Yes | Yes |
| **Pattern Matching** | Yes (match/case) | Yes (match) |
| **Modules** | Yes | Yes (v1.1+) |

## Detailed Feature Comparison

### 1. Format Support

**Both Support:**
- XML
- JSON
- CSV
- Java objects (DataWeave) / Native objects (UTL-X)

**DataWeave Additional:**
- Flat File (fixed-width, positional)
- Excel (commercial plugin)

**UTL-X Additional:**
- YAML (v1.1+)
- Extensible format plugin system

### 2. Syntax Differences

#### Variable Declaration

**DataWeave:**
```dataweave
var subtotal = sum(payload.items.price)
```

**UTL-X:**
```utlx
let subtotal = sum(input.items.*.price)
```

#### Function Definition

**DataWeave:**
```dataweave
fun calculateTax(amount: Number): Number = amount * 0.08
```

**UTL-X:**
```utlx
function calculateTax(amount: Number): Number {
  amount * 0.08
}
```

#### Array Operations

**DataWeave:**
```dataweave
payload.items map ((item, index) -> {
  name: item.name,
  price: item.price
})
```

**UTL-X:**
```utlx
input.items |> map(item => {
  name: item.name,
  price: item.price
})
```

#### Conditionals

**DataWeave:**
```dataweave
if (payload.customer.type == "VIP") 
  payload.total * 0.80 
else 
  payload.total
```

**UTL-X:**
```utlx
if (input.customer.type == "VIP") 
  input.total * 0.80 
else 
  input.total
```

### 3. Template Matching

**DataWeave**: Does not support template matching. You must explicitly navigate and map structures.

**UTL-X**: Supports XSLT-style template matching:

```utlx
template match="Order" {
  invoice: {
    id: @id,
    customer: apply(Customer),
    items: apply(Items/Item)
  }
}

template match="Customer" {
  name: Name,
  email: Email
}

template match="Item" {
  sku: @sku,
  price: @price
}
```

This declarative approach can be more elegant for complex XML transformations.

### 4. Type System

**Both** have strong type systems with inference.

**DataWeave:**
```dataweave
type Person = {
  name: String,
  age: Number
}

var person: Person = {name: "Alice", age: 30}
```

**UTL-X:**
```utlx
// Type inference
let person = {name: "Alice", age: 30}

// Explicit types in functions
function greet(person: Object): String {
  "Hello, " + person.name
}
```

### 5. Standard Library

**DataWeave** has a rich standard library:
- Core functions
- Strings module
- Arrays module
- Objects module
- DateTimes module

**UTL-X** has similar coverage:
- String functions: `upper`, `lower`, `substring`, `split`, etc.
- Array functions: `map`, `filter`, `reduce`, `sum`, etc.
- Math functions: `abs`, `round`, `pow`, etc.
- Date functions: `now`, `formatDate`, `addDays`, etc.

## Performance Comparison

| Metric | DataWeave | UTL-X (JVM) | UTL-X (Native) |
|--------|-----------|-------------|----------------|
| **Init Time** | ~100ms | ~50-200ms | ~1ms |
| **Transform Time** | 5-10ms | 5-12ms | 2-6ms |
| **Memory Usage** | Medium | Medium | Low |
| **Startup Overhead** | JVM warm-up | JVM warm-up | None |

**Note**: Performance depends heavily on transformation complexity and data size.

## Migration from DataWeave to UTL-X

### Step 1: Header Conversion

**DataWeave:**
```dataweave
%dw 2.0
output application/json
```

**UTL-X:**
```utlx
%utlx 1.0
output json
```

### Step 2: Replace `payload` with `input`

**Find and replace**: `payload` → `input`

### Step 3: Update Function Syntax

**DataWeave:**
```dataweave
fun double(x: Number): Number = x * 2
```

**UTL-X:**
```utlx
function double(x: Number): Number {
  x * 2
}
```

### Step 4: Update Array Operations

**DataWeave:**
```dataweave
items map (item, index) -> item.price
```

**UTL-X:**
```utlx
items |> map(item => item.price)
```

### Step 5: Convert match/case

**DataWeave:**
```dataweave
payload.status match {
  case "pending" -> "Awaiting"
  case "shipped" -> "In Transit"
  else -> "Unknown"
}
```

**UTL-X:**
```utlx
match input.status {
  "pending" => "Awaiting",
  "shipped" => "In Transit",
  _ => "Unknown"
}
```

### Automated Migration Tool

We're developing a migration tool:

```bash
utlx migrate dataweave-script.dwl --output utlx-script.utlx
```

Current status: 80% feature coverage (v1.0)

## When to Choose UTL-X

✅ **Choose UTL-X if:**
- You want **open source** with no vendor lock-in
- You need **community governance**
- You want to avoid **MuleSoft licensing costs**
- You need **multiple runtimes** (JVM, JS, Native)
- You prefer **template matching** for complex transformations
- You want **active community support**

## When to Choose DataWeave

✅ **Choose DataWeave if:**
- You're heavily invested in **MuleSoft ecosystem**
- You need **commercial support** from Salesforce
- You use MuleSoft-specific features (connectors, etc.)
- You have existing DataWeave transformations
- You prefer vendor-backed enterprise support

## Compatibility Layer (Experimental)

We're working on a DataWeave compatibility layer:

```utlx
%utlx 1.0
compatibility dataweave 2.0
---
// Write DataWeave syntax, runs on UTL-X
```

Status: Experimental (v1.2+)

## Community Resources

- **Migration Guide**: Full step-by-step guide
- **Cheat Sheet**: Side-by-side syntax comparison
- **Examples**: DataWeave → UTL-X transformations
- **Support**: Ask migration questions on Discord

## Conclusion

UTL-X and DataWeave are similar in capabilities but differ fundamentally in **licensing and governance**. If you value open source, community control, and avoiding vendor lock-in, UTL-X is the clear choice.

---
