# Working with JSON

This guide covers JSON-specific features in UTL-X.

## Basic JSON Transformation

### Input JSON

```json
{
  "order": {
    "id": "ORD-001",
    "date": "2025-10-09",
    "customer": {
      "name": "Alice Johnson",
      "email": "alice@example.com"
    },
    "items": [
      {"sku": "WIDGET-A", "quantity": 2, "price": 29.99},
      {"sku": "GADGET-B", "quantity": 1, "price": 149.99}
    ]
  }
}
```

### UTL-X Transformation

```utlx
%utlx 1.0
input json
output json
---
{
  orderId: $input.order.id,
  orderDate: $input.order.date,
  customer: {
    name: $input.order.customer.name,
    email: $input.order.customer.email
  },
  items: $input.order.items |> map(item => {
    sku: item.sku,
    total: item.quantity * item.price
  }),
  total: sum($input.order.items.(quantity * price))
}
```

### Output JSON

```json
{
  "orderId": "ORD-001",
  "orderDate": "2025-10-09",
  "customer": {
    "name": "Alice Johnson",
    "email": "alice@example.com"
  },
  "items": [
    {"sku": "WIDGET-A", "total": 59.98},
    {"sku": "GADGET-B", "total": 149.99}
  ],
  "total": 209.97
}
```

## JSON Data Types

### Strings

```json
{
  "name": "Alice",
  "description": "This is a\nmulti-line string"
}
```

```utlx
{
  upper: upper($input.name),
  length: length($input.description)
}
```

### Numbers

```json
{
  "integer": 42,
  "decimal": 3.14,
  "negative": -17,
  "scientific": 1.5e10
}
```

```utlx
{
  doubled: $input.integer * 2,
  rounded: round($input.decimal)
}
```

### Booleans

```json
{
  "active": true,
  "deleted": false
}
```

```utlx
{
  status: if ($input.active) "Active" else "Inactive"
}
```

### Null

```json
{
  "optional": null
}
```

```utlx
{
  value: $input.optional ?? "default"
}
```

### Arrays

```json
{
  "numbers": [1, 2, 3, 4, 5],
  "mixed": [1, "two", true, null]
}
```

```utlx
{
  sum: sum($input.numbers),
  count: count($input.mixed)
}
```

### Objects

```json
{
  "person": {
    "name": "Alice",
    "age": 30,
    "address": {
      "city": "Seattle"
    }
  }
}
```

```utlx
{
  name: $input.person.name,
  city: $input.person.address.city
}
```

## Accessing JSON Data

### Dot Notation

```utlx
input.order.customer.name
input.items[0].price
```

### Bracket Notation

```utlx
input["order"]["customer"]["name"]
input.items[0]["price"]

// Dynamic keys
let field = "name"
input.customer[field]
```

### Safe Navigation

```utlx
input.customer?.address?.city ?? "Unknown"
```

## JSON Arrays

### Iterate Array

```utlx
input.items |> map(item => {
  name: item.name,
  total: item.price * item.quantity
})
```

### Filter Array

```utlx
input.items |> filter(item => item.price > 50)
```

### Array Operations

```utlx
{
  first: first($input.items),
  last: last($input.items),
  count: count($input.items),
  prices: $input.items.*.price
}
```

## JSON to XML

### Simple Mapping

**JSON:**
```json
{
  "person": {
    "name": "Alice",
    "age": 30,
    "email": "alice@example.com"
  }
}
```

**UTL-X:**
```utlx
%utlx 1.0
input json
output xml
---
{
  Person: {
    Name: $input.person.name,
    Age: $input.person.age,
    Email: $input.person.email
  }
}
```

**XML:**
```xml
<?xml version="1.0"?>
<Person>
  <Name>Alice</Name>
  <Age>30</Age>
  <Email>alice@example.com</Email>
</Person>
```

### Arrays to Elements

**JSON:**
```json
{
  "products": [
    {"id": 1, "name": "Widget"},
    {"id": 2, "name": "Gadget"}
  ]
}
```

**UTL-X:**
```utlx
{
  Products: {
    Product: $input.products |> map(p => {
      @id: p.id,
      Name: p.name
    })
  }
}
```

**XML:**
```xml
<Products>
  <Product id="1"><Name>Widget</Name></Product>
  <Product id="2"><Name>Gadget</Name></Product>
</Products>
```

## JSON Configuration Options

```utlx
%utlx 1.0
input json {
  allowComments: true,
  allowTrailingCommas: true
}
output json {
  pretty: true,
  indent: 2,
  sortKeys: false
}
```

### Input Options

- **allowComments**: Allow // and /* */ comments (default: false)
- **allowTrailingCommas**: Allow trailing commas (default: false)
- **strictMode**: Strict JSON parsing (default: true)

### Output Options

- **pretty**: Pretty-print output (default: false)
- **indent**: Indentation spaces (default: 2)
- **sortKeys**: Sort object keys alphabetically (default: false)
- **escapeUnicode**: Escape non-ASCII characters (default: false)

## Common JSON Patterns

### Restructure Nested Object

**Input:**
```json
{
  "user": {
    "profile": {
      "personal": {
        "firstName": "Alice",
        "lastName": "Johnson"
      },
      "contact": {
        "email": "alice@example.com"
      }
    }
  }
}
```

**Transform:**
```utlx
{
  name: $input.user.profile.personal.firstName + " " + 
        $input.user.profile.personal.lastName,
  email: $input.user.profile.contact.email
}
```

**Output:**
```json
{
  "name": "Alice Johnson",
  "email": "alice@example.com"
}
```

### Merge Arrays

```json
{
  "list1": [1, 2, 3],
  "list2": [4, 5, 6]
}
```

```utlx
{
  merged: [...$input.list1, ...$input.list2]
}
```

### Group By Property

```json
{
  "orders": [
    {"customer": "Alice", "total": 100},
    {"customer": "Bob", "total": 200},
    {"customer": "Alice", "total": 150}
  ]
}
```

```utlx
{
  byCustomer: $input.orders 
    |> groupBy(o => o.customer)
    |> entries()
    |> map(([customer, orders]) => {
         customer: customer,
         total: sum(orders.*.total)
       })
}
```

### Flatten Nested Array

```json
{
  "categories": [
    {
      "name": "Electronics",
      "products": [{"id": 1}, {"id": 2}]
    },
    {
      "name": "Tools",
      "products": [{"id": 3}, {"id": 4}]
    }
  ]
}
```

```utlx
{
  allProducts: $input.categories 
    |> flatMap(cat => cat.products)
}
```

## Best Practices

### 1. Handle Missing Fields

```utlx
// ✅ Good
{
  email: $input.customer?.email ?? "no-email@example.com",
  phone: $input.customer?.phone ?? null
}

// ❌ Bad - might crash
{
  email: $input.customer.email
}
```

### 2. Validate Data Types

```utlx
// ✅ Good
if (isNumber($input.quantity) && $input.quantity > 0) {
  processOrder(input)
} else {
  {error: "Invalid quantity"}
}
```

### 3. Use Proper Type Conversion

```utlx
// ✅ Good
age: parseNumber($input.ageString)

// ❌ Bad - might not work as expected
age: $input.ageString
```

### 4. Keep JSON Output Clean

```utlx
// ✅ Good - omit null fields
{
  name: $input.name,
  ...(if ($input.email) {email: $input.email} else {})
}

// ❌ Bad - includes null
{
  name: $input.name,
  email: $input.email  // Might be null
}
```

---
