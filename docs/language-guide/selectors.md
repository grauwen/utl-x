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
input.property
```

**Example - JSON:**
```json
{"name": "Alice", "age": 30}
```
```utlx
input.name    // "Alice"
input.age     // 30
```

**Example - XML:**
```xml
<person><name>Alice</name><age>30</age></person>
```
```utlx
input.person.name    // "Alice"
input.person.age     // "30" (string)
```

### Nested Navigation

**Deep property access:**
```utlx
input.level1.level2.level3
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
input.order.customer.name                // "Alice"
input.order.customer.address.city        // "Springfield"
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
input.Order.@id                      // "12345"
input.Order.@date                    // "2026-01-15"
input.Order.Customer.@email          // "alice@example.com"
```

### All Attributes

Get all attributes as an object:

```utlx
input.Order.@*
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
input.items[0]           // {"name": "Widget", "price": 10.00}
input.items[0].name      // "Widget"
input.items[0].price     // 10.00
input.items[1].name      // "Gadget"
```

### Negative Indexing (Future)

```utlx
array[-1]    // Last element
array[-2]    // Second-to-last element
```

### Wildcard Selection

**All elements:**
```utlx
array[*]
```

**Example:**
```json
{
  "items": [
    {"price": 10.00},
    {"price": 25.00},
    {"price": 15.00}
  ]
}
```

```utlx
input.items[*]              // All items
input.items[*].price        // [10.00, 25.00, 15.00]
input.items.*.price         // Same as above (shorthand)
```

A step after a sequence **projects** across its members and flattens one level, so
repeated XML elements navigate the way XPath's `r/i/n` does:

```utlx
input.r.i.n                 // every <n>, across every repeated <i>
```

A member that cannot answer the step **contributes nothing** rather than a null — whether it
is an object lacking the property, or a value with no properties at all. That second case is
what makes projection survive mixed content:

```utlx
// <order><title>ACME</title><line><sku>A1</sku></line><line><sku>B2</sku></line></order>
$input.order.*.sku          // ["A1", "B2"] — <title> is text and is skipped
```

Skipping applies to **projection** only. Asking one named scalar for a property is still an
error, because that is a mistake in the transformation rather than a shape of the data:

```utlx
$input.order.title.sku      // Error: Cannot access property on non-object UDM type
```

And `*` applied to a sequence is that sequence — `items[*]` is every item, not every *value*
of each item, so stacking wildcards changes nothing.

Fixed in UTL-X 1.4; see
[B48](../bugs-fixed/B48-path-steps-do-not-project-across-a-sequence.md) for the projection
itself and
[B57](../bugs-fixed/B57-projection-throws-on-a-non-object-member-but-skips-a-missing-key.md)
for the mixed-content half.

---

## Recursive Descent — not supported

`input..name` does not parse, and **will not be implemented**. The decision and its reasoning
are in
[B49](../bugs-fixed/B49-documented-wildcard-and-descent-selectors-are-parse-errors.md); the
short version is that descent is a *query* convenience while UTL-X is a *mapping* language,
and the cases it would serve are already covered:

| what you want | use |
|---|---|
| a value over repeated elements | an ordinary path — a step **projects** across repeats: `$input.order.lines.line.sku` |
| every child's value | `.*` — `$input.order.*.sku` |
| nested data from flat records linked by a key (SAP IDoc segments, EDI, CSV with foreign keys) | **`nestBy()`** |
| a value at a known depth | name the steps |

Writing `..` gives an error that says the same thing:

```
Recursive descent ('..') is not supported in UTL-X. To reach a value at a known depth,
name the steps: $input.order.lines.line.sku — a step over repeated elements already
projects across them. To build nested data from flat records linked by a key (SAP IDoc
segments, EDI, CSV exports), use nestBy().
```

The trade being made deliberately: an explicit path **breaks loudly** when a source document
changes shape, and for a mapping contract that is usually what you want — a descent path would
keep silently matching something else.

---

## Predicates

> ⚠️ **NOT IMPLEMENTED (verified 2026-10-22).** The `array[condition]` predicate syntax in this section is
> **aspirational** — it is not implemented in the current engine (no `parsePredicate` in the parser, no
> conformance coverage) and **errors at runtime** (`Undefined variable: <field>`). Until it lands, express
> the same filtering with a lambda, which is the only supported form:
>
> ```utlx
> // documented-but-unimplemented:   input.items[price > 100]
> // use instead (works today):
> filter(input.items, x => x.price > 100)
> ```
>
> The remainder of this section describes the intended future syntax, not current behaviour.

### Filter by Condition

```utlx
array[condition]
```

### Comparison Operators

**Numeric comparisons:**
```utlx
input.items[price > 100]         // Price greater than 100
input.items[price >= 100]        // Price 100 or more
input.items[price < 50]          // Price less than 50
input.items[price <= 50]         // Price 50 or less
input.items[price == 100]        // Price exactly 100
input.items[price != 100]        // Price not 100
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
input.items[price > 100]
// Result: [{"name": "Gadget", "price": 150}]

input.items[price <= 75]
// Result: [
//   {"name": "Widget", "price": 10},
//   {"name": "Thing", "price": 75}
// ]
```

### String Comparisons

```utlx
input.items[category == "Electronics"]
input.items[status != "cancelled"]
input.items[name == "Widget"]
```

### Logical Operators

**AND operator:**
```utlx
input.items[price > 50 && price < 150]
input.items[category == "Electronics" && inStock == true]
```

**OR operator:**
```utlx
input.items[status == "pending" || status == "processing"]
input.items[priority == "high" || priority == "urgent"]
```

**NOT operator:**
```utlx
input.items[!(status == "cancelled")]
input.items[!inStock]
```

**Complex combinations:**
```utlx
input.items[(price > 100 || featured == true) && inStock == true]
```

### Function Calls in Predicates

```utlx
input.items[upper(category) == "ELECTRONICS"]
input.items[contains(description, "premium")]
input.items[length(name) > 10]
```

---

## Wildcard Patterns

### Property Wildcard

Match any property name:

```utlx
input.*                    // All direct children
input.order.*             // All properties of order
input.*.name              // 'name' property of all children
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
input.*
// Result: All three product objects

input.*.name
// Result: ["Widget", "Gadget", "Thing"]

input.*.price
// Result: [10, 25, 15]
```

---

## Selector Combinations

### Chaining Selectors

Combine multiple selector operations:

```utlx
input.order.items[price > 100].name
```

**Breakdown:**
1. `$input.order` - Navigate to order
2. `.items` - Get items array
3. `[price > 100]` - Filter by price
4. `.name` - Extract name from each

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
input.order.items[price > 100].name
// Result: ["Gadget", "Gizmo"]
```

### Multiple Predicates

Apply multiple filters:

```utlx
input.products[category == "Electronics"][price < 1000][inStock == true]
```

**Equivalent to:**
```utlx
input.products[
  category == "Electronics" && 
  price < 1000 && 
  inStock == true
]
```

---

## Context in Selectors

### Current Context

Inside a predicate, use current element properties directly:

```utlx
input.items[price > quantity * 10]
```

Here, `price` and `quantity` refer to properties of the current item being tested.

### Parent Context

Access parent elements: **not supported, and not planned.** There is no `..` parent step and
no `parent()` function — the latter was removed in 2026-11-13 because it read a property no
parser writes and could not work without parent pointers in the UDM
([B58](../bugs-fixed/B58-parent-function-never-worked-and-cannot-without-changing-the-udm.md)).

Keep the ancestor in scope instead, which is the idiom the language is built for:

```utlx
let order = $input.order
{ lines: map(order.lines.line, (l) -> { sku: l.sku, orderId: order.@id }) }
```

---

## Selector Functions

### exists()

Check if a property exists:

```utlx
input.items[exists(discount)]      // Items with discount property
input.items[!exists(discount)]     // Items without discount
```

### contains()

String contains check:

```utlx
input.items[contains(description, "premium")]
input.items[contains(tags, "featured")]
```

### startsWith() / endsWith()

```utlx
input.items[startsWith(sku, "ELEC")]
input.items[endsWith(name, "Pro")]
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
input.element.text()
```

**Example:**
```xml
<product>
  <name>Widget</name>
  <description>A useful widget</description>
</product>
```

```utlx
input.product.name.text()              // "Widget"
input.product.description.text()       // "A useful widget"
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
input.orders[*].items[*]
// Gets all items from all orders
```

### Conditional Navigation

```utlx
input.order[status == "active"].items
// Only navigate to items if order is active
```

### Multi-Level Filtering

```utlx
input.categories[*].products[price > 100]
// Filter products within each category
```

---

## Performance Considerations

### Efficient Selectors

**✅ Good - Specific paths:**
```utlx
input.order.items[price > 100]
```

**Not available — recursive search:** `input..items` does not parse (see *Recursive Descent*
above). Name the path; a step already projects across repeated elements.

### Caching Results

**❌ Bad - Repeated computation:**
```utlx
{
  count: count($input.order.items[active == true]),
  total: sum($input.order.items[active == true].*.price),
  names: $input.order.items[active == true].*.name
}
```

**✅ Good - Compute once:**
```utlx
{
  let activeItems = $input.order.items[active == true],
  
  count: count(activeItems),
  total: sum(activeItems.*.price),
  names: activeItems.*.name
}
```

---

## Common Patterns

### Pattern 1: Extract All Values

```utlx
input.orders[*].total
// Get all order totals
```

### Pattern 2: Filter Then Transform

```utlx
input.products[price > 100][*] |> map(p => {
  name: p.name,
  discountPrice: p.price * 0.9
})
```

### Pattern 3: Nested Filtering

```utlx
input.categories[*].products[featured == true]
// Featured products from all categories
```

### Pattern 4: Conditional Access

```utlx
input.customer.premium.benefits || $input.customer.standard.benefits
// Try premium first, fallback to standard
```

### Pattern 5: Values across repeated elements

```utlx
$input.order.lines.line.productCode
// Every productCode, projected across every repeated <line>
```

For flat records linked by a key rather than nested, use `nestBy()`.

---

## Troubleshooting

### Selector Returns null

**Problem:**
```utlx
input.order.customer.name  // Returns null
```

**Causes:**
1. Property doesn't exist
2. Typo in property name
3. Wrong path

**Solutions:**
```utlx
// Add default value
input.order.customer.name || "Unknown"

// Check existence
if ($input.order.customer != null) 
  $input.order.customer.name 
else 
  "No customer"

// Use an explicit path — recursive search is NOT implemented (B49)
$input.order.customer.name
```

### Predicate Not Matching

**Problem:**
```utlx
input.items[price > 100]  // Returns empty array
```

**Causes:**
1. Price is a string, not a number
2. Property name is wrong
3. Logic is inverted

**Solutions:**
```utlx
// Convert string to number
input.items[parseNumber(price) > 100]

// Debug: check what you're comparing
{
  _debug: $input.items[*].price,
  result: $input.items[price > 100]
}

// Check property names
{
  _keys: keys($input.items[0]),
  result: $input.items[Price > 100]  // Capital P?
}
```

### Attribute Not Found (XML)

**Problem:**
```utlx
input.Order.id  // Should be $id
```

**Solution:**
```utlx
input.Order.@id  // Use @ for attributes
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

**Selectors:**
```utlx
// Electronics under $1000 that are in stock
input.products[
  category == "Electronics" && 
  price < 1000 && 
  inStock == true
]

// Just the names
input.products[
  category == "Electronics" && 
  price < 1000 && 
  inStock == true
].*.name

// Count matching products
count($input.products[
  category == "Electronics" && 
  price < 1000 && 
  inStock == true
])
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

**Selectors:**
```utlx
// Pending orders over $100
input.Orders.Order[
  $status == "pending" && 
  parseNumber($total) > 100
]

// Their IDs
input.Orders.Order[
  $status == "pending" && 
  parseNumber($total) > 100
].@id

// Total of pending orders
sum($input.Orders.Order[
  $status == "pending"
] |> map(o => parseNumber(o.@total)))
```

---

## Selector Cheat Sheet

| Selector | Description | Example |
|----------|-------------|---------|
| `.property` | Property access | `$input.name` |
| `.@attribute` | Attribute (XML) | `$input.Order.@id` |
| `[index]` | Array index | `$input.items[0]` |
| `[*]` | All elements | `$input.items[*]` |
| `.*` | All properties | `$input.order.*` |
| `[condition]` | Filter | `$input.items[price > 100]` |
| `.text()` | Text content (XML) | `$input.element.text()` |

---

## Next Steps

- **Learn functions:** [Functions Guide](functions.md)
- **Practice with examples:** [Examples](../examples)
- **Read full spec:** [Language Specification](../reference/language-spec.md)

---

**Questions?** Ask in [Discussions](https://github.com/grauwen/utl-x/discussions)
