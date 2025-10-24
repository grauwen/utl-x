# Quick Reference: @ vs $ Syntax Migration

## Side-by-Side Comparison

### Single Input

| Current (v1.x) | Proposed (v2.0) | Notes |
|---------------|-----------------|-------|
| `$input.Order` | `$input.Order` | ✅ No change - keyword works |
| `$input.Order` | `$input.Order` | 📝 $ for consistency |
| `$input.Order.@id` | `$input.Order.@id` | ✅ Clear: $ = input, @ = attr |

### Multiple Named Inputs

| Current (v1.x) | Proposed (v2.0) | Improvement |
|---------------|-----------------|-------------|
| `$orders.Order` | `$orders.Order` | ✅ Clearer input reference |
| `$customers.Customer` | `$customers.Customer` | ✅ Distinct from attributes |
| `@products.Product` | `$products.Product` | ✅ Industry standard |

### XML Attributes

| Current (v1.x) | Proposed (v2.0) | Notes |
|---------------|-----------------|-------|
| `Order.@id` | `Order.@id` | ✅ No change - @ for attributes |
| `element.@customerId` | `element.@customerId` | ✅ No change |

### Complex Expressions

#### Before (Confusing)
```utlx
{
  enrichedOrders: $orders |> map(order => {
    let customer = $customers
      |> filter(c => c.@id == order.@customerId)
      |> first()

    {
      orderId: order.@id,
      customerName: customer.name,
      total: order.quantity * $products
        |> filter(p => p.@id == order.@productId)
        |> first()
        |> (\product => product.@price)
    }
  })
}
```

**Issues:**
- 6× `@` symbols with 2 different meanings
- Hard to distinguish inputs from attributes
- Cognitive overhead

#### After (Clear)
```utlx
{
  enrichedOrders: $orders |> map(order => {
    let customer = $customers
      |> filter(c => c.@id == order.@customerId)
      |> first()

    {
      orderId: order.@id,
      customerName: customer.name,
      total: order.quantity * $products
        |> filter(p => p.@id == order.@productId)
        |> first()
        |> (\product => product.@price)
    }
  })
}
```

**Improvements:**
- `$` clearly marks data sources (inputs)
- `@` clearly marks metadata (attributes)
- Easier to scan visually

## Common Patterns

### Pattern 1: Input + Attribute Access

```utlx
// Before
$input.Order.@id

// After
$input.Order.@id
   ↑         ↑
   data      metadata
```

### Pattern 2: Joining Multiple Inputs

```utlx
// Before
$orders |> map(order => {
  let customer = $customers
    |> filter(c => c.@id == order.@customerId)
    |> first()
  ...
})

// After
$orders |> map(order => {
  let customer = $customers
    |> filter(c => c.@id == order.@customerId)
    |> first()
  ...
})
```

### Pattern 3: Default Input

```utlx
// Before (two ways)
input.data.field
$input.data.field

// After (still two ways, but clearer)
input.data.field      // Preferred for single input
$input.data.field     // Consistent with named inputs
```

### Pattern 4: Nested Attributes

```utlx
// Before
element.@id.@type      // RARE - attribute with attribute?

// After (same, but @ clearly means attribute)
element.@id.@type
```

### Pattern 5: Array Indexing with Inputs

```utlx
// Before
$orders.Order[0].@id

// After
$orders.Order[0].@id
 ↑              ↑
 input array    attribute
```

## Migration Checklist

### For Each File:

- [ ] Replace `$inputName` with `$inputName` (NOT after dot!)
- [ ] Keep `element.@attribute` unchanged
- [ ] Test transformation still works
- [ ] Check for edge cases (see below)

### Edge Cases to Watch

#### Case 1: Input Named "input"
```utlx
input: input xml, orders xml

// Before
$input.data    // Ambiguous!

// After
$input.data    // Clear - the named input called "input"
```

#### Case 2: Attribute After Input
```utlx
// Before
$orders.Order.@id
   ↑          ↑
   input      attribute

// After
$orders.Order.@id
 ↑            ↑
 input        attribute
```

#### Case 3: Input in String Interpolation
```utlx
// Before
"Order count: " + count($orders.Order)

// After
"Order count: " + count($orders.Order)
```

#### Case 4: Input in Function Arguments
```utlx
// Before
detectXMLEncoding($input)

// After
detectXMLEncoding($input)
```

#### Case 5: Comments (No Change!)
```utlx
// Before
// Use $orders to access order data

// After
// Use $orders to access order data
```

## Regex Patterns for Migration

### Search Pattern (find all @ inputs)
```regex
(?<!\.)@([a-zA-Z_][a-zA-Z0-9_]*)\b
```

### Replace Pattern
```regex
$\1
```

### Exclusions (DO NOT REPLACE)
```regex
\.@([a-zA-Z_][a-zA-Z0-9_]*)\b    # Keep .@attribute
```

## Visual Distinction

### Memory Aid

```
$orders.Order[0].@id
 │              │
 │              └─ @ = at-tribute (metadata)
 └──────────────── $ = dollar (data/value)
```

### Color Coding (for IDE)

Recommended syntax highlighting:
- `$input` → Blue/Cyan (data source)
- `@attribute` → Green/Orange (metadata)
- Different colors = different purposes

## FAQ

### Q: Can I still use `input` without $ or @?
**A:** Yes! For single input, `input` works fine:
```utlx
input.Order.@id  // ✅ Works
$input.Order.@id // ✅ Also works (consistent style)
```

### Q: What if I have an input named "input"?
**A:** Use `$input` to avoid confusion with the keyword:
```utlx
input: input xml    // Named input called "input"
$input.data         // Reference to that input
```

### Q: Do I need to update function definitions?
**A:** No, only **calls** to inputs:
```utlx
function process(data) {  // ✅ No change
  data.field              // ✅ No change
}

$orders |> map(process)   // ✅ Change input ref
```

### Q: What about let bindings?
**A:** Let bindings don't use $ or @:
```utlx
let orders = $orders.Order    // $ for input on right
let count = length(orders)    // No prefix for binding
```

### Q: Will old code break immediately?
**A:** Not in v1.x! Both `@` and `$` work during transition:
- v1.5: Both work, deprecation warnings
- v2.0: Only `$` works for inputs

### Q: How do I migrate automatically?
**A:** Use the migration tool:
```bash
utlx migrate --at-to-dollar myfile.utlx
```

## Summary Table

| Symbol | Purpose | Example | Since |
|--------|---------|---------|-------|
| `$` | Input reference | `$orders.Order` | v2.0 (proposed) |
| `@` | XML attribute | `Order.@id` | v1.0 (current) |
| (none) | Properties, bindings | `order.product` | v1.0 (current) |

## Before & After Examples

### Example 1: E-commerce Order Processing

**Before:**
```utlx
%utlx 1.0
input: orders xml, customers xml, products xml
output json
---
{
  enrichedOrders: $orders.Order |> map(order => {
    {
      id: order.@id,
      customer: $customers.Customer
        |> filter(c => c.@id == order.@customerId)
        |> first()
        |> (\c => c.name),
      product: $products.Product
        |> filter(p => p.@sku == order.@productSku)
        |> first()
        |> (\p => p.name)
    }
  })
}
```

**After:**
```utlx
%utlx 1.0
input: orders xml, customers xml, products xml
output json
---
{
  enrichedOrders: $orders.Order |> map(order => {
    {
      id: order.@id,
      customer: $customers.Customer
        |> filter(c => c.@id == order.@customerId)
        |> first()
        |> (\c => c.name),
      product: $products.Product
        |> filter(p => p.@sku == order.@productSku)
        |> first()
        |> (\p => p.name)
    }
  })
}
```

### Example 2: SAP Integration

**Before:**
```utlx
let material = $input.Material in
{
  encoding: detectXMLEncoding($input),
  sku: material.MaterialNumber
}
```

**After:**
```utlx
let material = $input.Material in
{
  encoding: detectXMLEncoding($input),
  sku: material.MaterialNumber
}
```

### Example 3: Multi-Source Merge

**Before:**
```utlx
{
  systemA: $systemA.data,
  systemB: $systemB.data,
  metadata: {
    encodingA: detectXMLEncoding($systemA),
    encodingB: detectXMLEncoding($systemB)
  }
}
```

**After:**
```utlx
{
  systemA: $systemA.data,
  systemB: $systemB.data,
  metadata: {
    encodingA: detectXMLEncoding($systemA),
    encodingB: detectXMLEncoding($systemB)
  }
}
```

## Learning Resources

- Full proposal: [dollar-sign-input-prefix-migration.md](./dollar-sign-input-prefix-migration.md)
- XSLT/XPath reference: https://www.w3.org/TR/xpath/
- JSONata reference: https://jsonata.org/
- Migration tool: `utlx migrate --help`
