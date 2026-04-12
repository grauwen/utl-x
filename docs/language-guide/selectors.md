# Selectors Guide

Master data navigation with UTL-X selectors.

---

## Overview

Selectors allow you to navigate and extract data from input structures. They work consistently across XML, JSON, CSV, and YAML.

**Think of selectors as:**
- XPath for XML
- JSONPath for JSON  
- But unified across all formats!

---

## Basic Path Navigation

### Simple Property Access

```utlx
$input.property
```

**Example - JSON:**
```json
{"name": "Alice", "age": 30}
```
```utlx
$input.name    // "Alice"
$input.age     // 30
```

**Example - XML:**
```xml
<person><name>Alice</name><age>30</age></person>
```
```utlx
$input.person.name    // "Alice"
$input.person.age     // "30" (string)
```

### Nested Navigation

**Deep property access:**
```utlx
$input.level1.level2.level3
```

**Example:**
```json
{
  "order": {
    "customer": {
      "name": "Alice",
      "address": {
        "city": "Springfield"
      }
    }
  }
}
```
```utlx
$input.order.customer.name                // "Alice"
$input.order.customer.address.city        // "Springfield"
```

---

## Attribute Access (XML)

### @ Syntax

Access XML attributes with `@`:

```utlx
element.@attributeName
```

**Example:**
```xml
<Order id="12345" date="2026-01-15">
  <Customer email="alice@example.com">
    <Name>Alice</Name>
  </Customer>
</Order>
```

```utlx
$input.Order.@id                      // "12345"
$input.Order.@date                    // "2026-01-15"
$input.Order.Customer.@email          // "alice@example.com"
```

### All Attributes

Get all attributes as an object:

```utlx
$input.Order.@*
```

**Result:**
```json
{
  "id": "12345",
  "date": "2026-01-15"
}
```

---

## Array Access

### Index Access

**Zero-based indexing:**
```utlx
array[0]     // First element
array[1]     // Second element
array[2]     // Third element
```

**Example:**
```json
{
  "items": [
    {"name": "Widget", "price": 10.00},
    {"name": "Gadget", "price": 25.00},
    {"name": "Doohickey", "price": 15.00}
  ]
}
```

```utlx
$input.items[0]           // {"name": "Widget", "price": 10.00}
$input.items[0].name      // "Widget"
$input.items[0].price     // 10.00
$input.items[1].name      // "Gadget"
```

### Working with All Elements

UTL-X uses functional operators to work with array elements:

```utlx
// Get all prices from items
$input.items |> map(i => i.price)        // [10.00, 25.00, 15.00]

// Get first and last
first($input.items)                       // First element
last($input.items)                        // Last element
```

---

## Recursive Descent

### Recursive Descent (Future)

The `..` operator for finding properties at any depth is planned for a future release.

In the meantime, use explicit path navigation or user-defined recursive functions.

---

## Filtering Arrays

UTL-X uses functional operators for filtering — this is clearer and more composable than inline predicates.

### filter() — Select Matching Elements

```utlx
$input.items |> filter(i => i.price > 100)         // Price greater than 100
$input.items |> filter(i => i.price >= 100)        // Price 100 or more
$input.items |> filter(i => i.price < 50)          // Price less than 50
$input.items |> filter(i => i.price <= 50)         // Price 50 or less
$input.items |> filter(i => i.price == 100)        // Price exactly 100
$input.items |> filter(i => i.price != 100)        // Price not 100
```

**Example:**
```json
{
  "items": [
    {"name": "Widget", "price": 10},
    {"name": "Gadget", "price": 150},
    {"name": "Thing", "price": 75}
  ]
}
```

```utlx
$input.items |> filter(i => i.price > 100)
// Result: [{"name": "Gadget", "price": 150}]

$input.items |> filter(i => i.price <= 75)
// Result: [
//   {"name": "Widget", "price": 10},
//   {"name": "Thing", "price": 75}
// ]
```

### String Comparisons

```utlx
$input.items |> filter(i => i.category == "Electronics")
$input.items |> filter(i => i.status != "cancelled")
$input.items |> filter(i => i.name == "Widget")
```

### Logical Operators

**AND operator:**
```utlx
$input.items |> filter(i => i.price > 50 && i.price < 150)
$input.items |> filter(i => i.category == "Electronics" && i.inStock == true)
```

**OR operator:**
```utlx
$input.items |> filter(i => i.status == "pending" || i.status == "processing")
$input.items |> filter(i => i.priority == "high" || i.priority == "urgent")
```

**NOT operator:**
```utlx
$input.items |> filter(i => i.status != "cancelled")
$input.items |> filter(i => !i.inStock)
```

**Complex combinations:**
```utlx
$input.items |> filter(i => (i.price > 100 || i.featured == true) && i.inStock == true)
```

### Function Calls in Filters

```utlx
$input.items |> filter(i => upper(i.category) == "ELECTRONICS")
$input.items |> filter(i => contains(i.description, "premium"))
$input.items |> filter(i => length(i.name) > 10)
```

---

## Working with Object Properties

### Getting Keys and Values

Use stdlib functions to work with object properties:

```utlx
keys($input)               // All property names
values($input)             // All property values
entries($input)            // Key-value pairs
```

**Example:**
```json
{
  "product1": {"name": "Widget", "price": 10},
  "product2": {"name": "Gadget", "price": 25},
  "product3": {"name": "Thing", "price": 15}
}
```

```utlx
values($input) |> map(p => p.name)
// Result: ["Widget", "Gadget", "Thing"]

values($input) |> map(p => p.price)
// Result: [10, 25, 15]
```

---

## Chaining Selectors and Operators

### Combining Path Navigation with Functional Operators

```utlx
$input.order.items[price > 100].name
```

**Breakdown:**
1. `$input.order` - Navigate to order
2. `.items` - Get items array
3. `|> filter(...)` - Filter by condition
4. `|> map(...)` - Extract property

**Example:**
```json
{
  "order": {
    "items": [
      {"name": "Widget", "price": 50},
      {"name": "Gadget", "price": 150},
      {"name": "Thing", "price": 25},
      {"name": "Gizmo", "price": 200}
    ]
  }
}
```

```utlx
$input.order.items |> filter(i => i.price > 100) |> map(i => i.name)
// Result: ["Gadget", "Gizmo"]
```

### Multiple Filters

Chain filters with pipeline:

```utlx
$input.products
  |> filter(p => p.category == "Electronics")
  |> filter(p => p.price < 1000)
  |> filter(p => p.inStock == true)
```

Or combine in one filter:
```utlx
$input.products |> filter(p =>
  p.category == "Electronics" &&
  p.price < 1000 &&
  p.inStock == true
)
```

---

## Using Functions in Filters

### Check Property Existence

```utlx
$input.items |> filter(i => i.discount != null)     // Items with discount
$input.items |> filter(i => i.discount == null)     // Items without discount
```

### String Functions

```utlx
$input.items |> filter(i => contains(i.description, "premium"))
$input.items |> filter(i => startsWith(i.sku, "ELEC"))
$input.items |> filter(i => endsWith(i.name, "Pro"))
```

---

## Format-Specific Selectors

### XML Namespaces

**With namespace configuration:**
```utlx
input xml {
  namespaces: {
    "soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "app": "http://example.com/app"
  }
}
---
{
  body: $input.{"soap:Envelope"}.{"soap:Body"}.{"app:Data"}
}
```

### XML Text Content

**Get text content:**
```utlx
$input.element.text()
```

**Example:**
```xml
<product>
  <name>Widget</name>
  <description>A useful widget</description>
</product>
```

```utlx
$input.product.name.text()              // "Widget"
$input.product.description.text()       // "A useful widget"
```

### CSV Column Access

**Access by header name:**
```utlx
input csv { headers: true }
---
{
  names: $input.rows[*].Name,
  emails: $input.rows[*].Email
}
```

**Access by index:**
```utlx
input csv { headers: false }
---
{
  firstColumn: $input.rows[*][0],
  secondColumn: $input.rows[*][1]
}
```

---

## Advanced Patterns

### Flattening Nested Arrays

```utlx
// Get all items from all orders
$input.orders |> map(o => o.items) |> flatten()
```

### Conditional Navigation

```utlx
// Only get items if order is active
if ($input.order.status == "active") $input.order.items else []
```

### Multi-Level Filtering

```utlx
// Filter products within each category
$input.categories |> map(cat =>
  cat.products |> filter(p => p.price > 100)
) |> flatten()
```

---

## Performance: Cache Repeated Selectors

**❌ Bad — repeated navigation:**
```utlx
{
  let items = $input.order.items,
  count: count(items |> filter(i => i.active)),
  total: sum(items |> filter(i => i.active) |> map(i => i.price)),
  names: items |> filter(i => i.active) |> map(i => i.name)
}
```

**✅ Good — compute once:**
```utlx
{
  let activeItems = $input.order.items |> filter(i => i.active),

  count: count(activeItems),
  total: sum(activeItems |> map(i => i.price)),
  names: activeItems |> map(i => i.name)
}
```

---

## Common Patterns

### Pattern 1: Extract All Values

```utlx
$input.orders[*].total
// Get all order totals
```

### Pattern 2: Filter Then Transform

```utlx
$input.products |> filter(p => p.price > 100) |> map(p => {
  name: p.name,
  discountPrice: p.price * 0.9
})
```

### Pattern 3: Nested Filtering

```utlx
$input.categories |> map(c => c.products |> filter(p => p.featured)) |> flatten()
// Featured products from all categories
```

### Pattern 4: Conditional Access

```utlx
$input.customer?.premium?.benefits ?? $input.customer?.standard?.benefits
// Try premium first, fallback to standard
```

---

## Troubleshooting

### Selector Returns null

**Problem:**
```utlx
$input.order.customer.name  // Returns null
```

**Causes:**
1. Property doesn't exist
2. Typo in property name
3. Wrong path

**Solutions:**
```utlx
// Add default value
$input.order.customer.name || "Unknown"

// Check existence
if ($input.order.customer != null) 
  $input.order.customer.name 
else 
  "No customer"

// Use safe navigation
$input.order?.customer?.name ?? "Unknown"
```

### Filter Returns Empty

**Problem:**
```utlx
$input.items |> filter(i => i.price > 100)  // Returns empty array
```

**Causes:**
1. Price is a string, not a number
2. Property name is wrong
3. Logic is inverted

**Solutions:**
```utlx
// Convert string to number
$input.items |> filter(i => parseNumber(i.price) > 100)

// Debug: check what you're comparing
{
  _debug: $input.items |> map(i => i.price),
  result: $input.items |> filter(i => i.price > 100)
}

// Check property names
{
  _keys: keys($input.items[0]),
  result: $input.items |> filter(i => i.Price > 100)  // Capital P?
}
```

### Attribute Not Found (XML)

**Problem:**
```utlx
$input.Order.id  // Should be @id
```

**Solution:**
```utlx
$input.Order.@id  // Use @ for attributes
```

---

## Examples

### Example 1: E-commerce Filtering

**Input:**
```json
{
  "products": [
    {"name": "Laptop", "price": 999, "category": "Electronics", "inStock": true},
    {"name": "Mouse", "price": 29, "category": "Electronics", "inStock": false},
    {"name": "Desk", "price": 299, "category": "Furniture", "inStock": true},
    {"name": "Chair", "price": 199, "category": "Furniture", "inStock": true}
  ]
}
```

**Transformations:**
```utlx
// Electronics under $1000 that are in stock
$input.products |> filter(p =>
  p.category == "Electronics" &&
  p.price < 1000 &&
  p.inStock == true
)

// Just the names
$input.products
  |> filter(p => p.category == "Electronics" && p.price < 1000 && p.inStock)
  |> map(p => p.name)

// Count matching products
count($input.products |> filter(p =>
  p.category == "Electronics" && p.price < 1000 && p.inStock
))
```

### Example 2: Order Processing

**Input:**
```xml
<Orders>
  <Order id="001" status="pending" total="150.00"/>
  <Order id="002" status="complete" total="75.00"/>
  <Order id="003" status="pending" total="200.00"/>
  <Order id="004" status="cancelled" total="50.00"/>
</Orders>
```

**Transformations:**
```utlx
// Pending orders over $100
$input.Orders.Order |> filter(o =>
  o.@status == "pending" &&
  parseNumber(o.@total) > 100
)

// Their IDs
$input.Orders.Order
  |> filter(o => o.@status == "pending" && parseNumber(o.@total) > 100)
  |> map(o => o.@id)

// Total of pending orders
sum($input.Orders.Order
  |> filter(o => o.@status == "pending")
  |> map(o => parseNumber(o.@total))
)
```

---

## Selector Cheat Sheet

| Selector | Description | Example |
|----------|-------------|---------|
| Syntax | Description | Example |
|--------|-------------|---------|
| `.property` | Property access | `$input.name` |
| `.@attribute` | Attribute (XML) | `$input.Order.@id` |
| `[index]` | Array index | `$input.items[0]` |
| `?.property` | Safe navigation | `$input.order?.customer?.name` |
| `?? default` | Nullish coalescing | `$input.name ?? "Unknown"` |
| `\|> filter()` | Filter array | `$input.items \|> filter(i => i.price > 100)` |
| `\|> map()` | Transform array | `$input.items \|> map(i => i.name)` |
| `keys()` | Object property names | `keys($input)` |
| `values()` | Object property values | `values($input)` |
| `first()` | First element | `first($input.items)` |
| `last()` | Last element | `last($input.items)` |
| `.text()` | Text content (XML) | `$input.element.text()` |

---

## Next Steps

- **Learn functions:** [Functions Guide](functions.md)
- **Practice with examples:** [Examples](../examples/)
- **Stdlib reference:** [652 Functions](../stdlib/stdlib-complete-reference.md)
