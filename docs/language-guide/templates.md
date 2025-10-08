
# Templates

Templates provide XSLT-style declarative transformation through pattern matching.

## Template Basics

### Template Definition

```utlx
template match="Pattern" {
  // transformation body
}
```

**Example:**

```utlx
template match="Order" {
  invoice: {
    id: @id,
    date: @date
  }
}
```

### Template Application

Use `apply()` to apply matching templates:

```utlx
apply(selector)
```

**Example:**

```utlx
template match="Order" {
  invoice: {
    items: apply(Items/Item)
  }
}

template match="Item" {
  lineItem: {
    sku: @sku,
    price: @price
  }
}
```

## Pattern Matching

### Element Patterns

Match specific elements:

```utlx
template match="Order" { ... }       // Match Order elements
template match="Customer" { ... }     // Match Customer elements
template match="Item" { ... }         // Match Item elements
```

### Path Patterns

Match elements at specific paths:

```utlx
template match="Order/Customer" { ... }        // Customer under Order
template match="Order/Items/Item" { ... }      // Item under Order/Items
```

### Wildcard Patterns

Match any element:

```utlx
template match="*" { ... }            // Match any element
```

### Root Pattern

Match document root:

```utlx
template match="/" {
  // Root template
  apply(*)
}
```

### Attribute Patterns (XML)

Match elements with specific attributes:

```utlx
template match="Item[@type='physical']" { ... }   // Items with type attribute
```

## Multiple Templates

Define multiple templates for different patterns:

```utlx
%utlx 1.0
input xml
output json
---

template match="Order" {
  invoice: {
    id: @id,
    customer: apply(Customer),
    items: apply(Items/Item),
    total: sum(Items/Item.(@price * @quantity))
  }
}

template match="Customer" {
  name: Name,
  email: Email,
  phone: Phone
}

template match="Item" {
  sku: @sku,
  description: Description,
  quantity: parseNumber(@quantity),
  price: parseNumber(@price),
  subtotal: parseNumber(@quantity) * parseNumber(@price)
}
```

## Template Priority

When multiple templates match, priority determines which applies:

### Default Priority

Templates have default priority based on specificity:

1. **Specific path patterns** (highest): `Order/Items/Item`
2. **Element patterns**: `Item`
3. **Wildcard patterns** (lowest): `*`

### Explicit Priority

Specify priority explicitly:

```utlx
template match="Item" priority="10" { ... }     // High priority
template match="*" priority="1" { ... }         // Low priority
```

Higher numbers = higher priority.

## Recursive Templates

Templates can be recursive:

```utlx
template match="Category" {
  category: {
    name: @name,
    subcategories: if (Subcategory) 
                     apply(Subcategory) 
                   else 
                     null
  }
}
```

**Example with nested structure:**

Input:
```xml
<Category name="Electronics">
  <Subcategory name="Computers">
    <Subcategory name="Laptops"/>
    <Subcategory name="Desktops"/>
  </Subcategory>
</Category>
```

The template recursively processes all levels.

## Default Template

Provide a catch-all template:

```utlx
template match="/" {
  apply(*)
}

template match="Order" {
  // Process Order
}

template match="*" {
  // Default for unmatched elements
  {
    type: "unknown",
    content: .
  }
}
```

## Template Context

Inside a template, selectors are relative to the matched element:

```utlx
template match="Order" {
  // 'this' context is the Order element
  id: @id,              // Order's id attribute
  date: @date,          // Order's date attribute
  customer: Customer.Name  // Customer/Name under this Order
}
```

### Accessing Root

Use `input` to access document root:

```utlx
template match="Item" {
  sku: @sku,
  taxRate: input.Configuration.TaxRate  // Access root
}
```

## Template Modes (v1.1+)

Process same element differently in different contexts:

```utlx
template match="Product" mode="summary" {
  {
    name: Name,
    price: Price
  }
}

template match="Product" mode="detailed" {
  {
    name: Name,
    price: Price,
    description: Description,
    specs: Specifications
  }
}

// Apply with mode
{
  summary: apply(Products/Product, mode="summary"),
  details: apply(Products/Product, mode="detailed")
}
```

## Template Parameters (v1.1+)

Pass parameters to templates:

```utlx
template match="Item" (discount: Number) {
  {
    sku: @sku,
    price: parseNumber(@price),
    discountedPrice: parseNumber(@price) * (1 - discount)
  }
}

// Apply with parameters
apply(Items/Item, discount=0.10)
```

## Inline Templates

Define templates inline within transformations:

```utlx
{
  orders: input.Orders.Order |> map(order => {
    // Inline template-like transformation
    id: order.@id,
    items: order.Items.Item |> map(item => {
      sku: item.@sku,
      price: parseNumber(item.@price)
    })
  })
}
```

## Template vs Functions

### Use Templates When:
- Transforming structured XML/JSON hierarchies
- Pattern-based transformations
- Recursive tree processing
- XSLT-style declarative transformations

### Use Functions When:
- Calculations and logic
- Reusable operations
- Data manipulation
- Stateless transformations

**Example combining both:**

```utlx
function calculateDiscount(price: Number, type: String): Number {
  if (type == "VIP")
    price * 0.20
  else
    price * 0.10
}

template match="Order" {
  invoice: {
    items: apply(Items/Item),
    discount: calculateDiscount(sum(Items/Item.@price), Customer.@type)
  }
}

template match="Item" {
  {
    sku: @sku,
    price: parseNumber(@price)
  }
}
```

## Complete Example

### Input (XML)

```xml
<Orders>
  <Order id="ORD-001" date="2025-10-01">
    <Customer type="VIP">
      <Name>Alice Johnson</Name>
      <Email>alice@example.com</Email>
    </Customer>
    <Items>
      <Item sku="WIDGET-001" quantity="2" price="75.00">
        <Description>Premium Widget</Description>
      </Item>
      <Item sku="GADGET-002" quantity="1" price="150.00">
        <Description>Deluxe Gadget</Description>
      </Item>
    </Items>
  </Order>
</Orders>
```

### Transformation

```utlx
%utlx 1.0
input xml
output json
---

template match="Orders" {
  invoices: apply(Order)
}

template match="Order" {
  {
    invoiceId: "INV-" + @id,
    orderDate: @date,
    customer: apply(Customer),
    lineItems: apply(Items/Item),
    
    let subtotal = sum(Items/Item.(parseNumber(@quantity) * parseNumber(@price))),
    let discount = if (Customer.@type == "VIP") subtotal * 0.20 else 0,
    let tax = (subtotal - discount) * 0.08,
    
    financial: {
      subtotal: subtotal,
      discount: discount,
      tax: tax,
      total: subtotal - discount + tax
    }
  }
}

template match="Customer" {
  {
    name: Name,
    email: Email,
    tier: @type
  }
}

template match="Item" {
  {
    sku: @sku,
    description: Description,
    quantity: parseNumber(@quantity),
    unitPrice: parseNumber(@price),
    lineTotal: parseNumber(@quantity) * parseNumber(@price)
  }
}
```

### Output

```json
{
  "invoices": [
    {
      "invoiceId": "INV-ORD-001",
      "orderDate": "2025-10-01",
      "customer": {
        "name": "Alice Johnson",
        "email": "alice@example.com",
        "tier": "VIP"
      },
      "lineItems": [
        {
          "sku": "WIDGET-001",
          "description": "Premium Widget",
          "quantity": 2,
          "unitPrice": 75.00,
          "lineTotal": 150.00
        },
        {
          "sku": "GADGET-002",
          "description": "Deluxe Gadget",
          "quantity": 1,
          "unitPrice": 150.00,
          "lineTotal": 150.00
        }
      ],
      "financial": {
        "subtotal": 300.00,
        "discount": 60.00,
        "tax": 19.20,
        "total": 259.20
      }
    }
  ]
}
```

## Best Practices

### 1. One Template Per Pattern

```utlx
// ✅ Good - focused templates
template match="Order" { ... }
template match="Customer" { ... }
template match="Item" { ... }

// ❌ Bad - template tries to handle everything
template match="*" {
  // Complex conditional logic for different types
}
```

### 2. Keep Templates Simple

```utlx
// ✅ Good - simple template
template match="Item" {
  {
    sku: @sku,
    price: parseNumber(@price)
  }
}

// ❌ Bad - too much logic
template match="Item" {
  {
    sku: @sku,
    price: if (@discounted == "true") 
             parseNumber(@price) * 0.90
           else if (@clearance == "true")
             parseNumber(@price) * 0.50
           else
             parseNumber(@price),
    // Many more fields...
  }
}
```

### 3. Use Functions for Complex Logic

```utlx
function calculatePrice(item: Object): Number {
  let basePrice = parseNumber(item.@price),
  let discount = if (item.@discounted == "true") 0.10 else 0
  
  basePrice * (1 - discount)
}

template match="Item" {
  {
    sku: @sku,
    price: calculatePrice(.)
  }
}
```

### 4. Name Templates Clearly

Use descriptive patterns:

```utlx
// ✅ Good - clear patterns
template match="Order" { ... }
template match="Order/Customer" { ... }
template match="Order/Items/Item" { ... }

// ❌ Bad - vague patterns
template match="*" { ... }
template match="A" { ... }
```

### 5. Document Complex Templates

```utlx
/**
 * Transforms Order elements into invoice format.
 * Applies VIP discount if customer type is VIP.
 * Calculates tax at 8%.
 */
template match="Order" {
  // Implementation
}
```

---
