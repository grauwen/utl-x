# UTL-X Cookbook

Common transformation patterns and solutions.

---

## Data Restructuring

### Rename Fields

**Problem:** Change property names

```utlx
// Input: {oldName: "Alice", oldAge: 30}
// Output: {newName: "Alice", newAge: 30}

{
  newName: $input.oldName,
  newAge: $input.oldAge
}
```

### Flatten Nested Structure

**Problem:** Reduce nesting levels

```utlx
// Input: {order: {customer: {name: "Alice"}}}
// Output: {customerName: "Alice"}

{
  customerName: $input.order.customer.name,
  customerId: $input.order.customer.id
}
```

### Create Nested Structure

**Problem:** Increase nesting from flat data

```utlx
// Input: {customerName: "Alice", customerEmail: "alice@example.com"}
// Output: {customer: {name: "Alice", email: "alice@example.com"}}

{
  customer: {
    name: $input.customerName,
    email: $input.customerEmail
  }
}
```

### Split Object into Multiple

**Problem:** Separate concerns

```utlx
// Input: {name: "Alice", age: 30, street: "Main St", city: "NYC"}

{
  personal: {
    name: $input.name,
    age: $input.age
  },
  address: {
    street: $input.street,
    city: $input.city
  }
}
```

### Merge Multiple Objects

**Problem:** Combine separate objects

```utlx
// Input: {user: {...}, profile: {...}, settings: {...}}

{
  ...$input.user,
  ...$input.profile,
  ...$input.settings
}
```

---

## Array Transformations

### Map Array Elements

**Transform each element:**

```utlx
input.items |> map(item => {
  id: item.id,
  name: upper(item.name),
  price: item.price * 1.1
})
```

### Filter Array

**Keep only matching elements:**

```utlx
// Items over $100
input.items |> filter(item => item.price > 100)

// Active users from last 30 days
input.users |> filter(user => 
  user.active && 
  daysBetween(user.lastLogin, now()) < 30
)
```

### Sort Array

**Order elements:**

```utlx
// Sort by price (ascending)
input.items |> sortBy(item => item.price)

// Sort by price (descending)
input.items |> sortBy(item => -item.price)

// Sort by multiple fields
input.items |> sortBy(item => [item.category, item.name])
```

### Group Array Elements

**Group by property:**

```utlx
input.items |> groupBy(item => item.category)

// With aggregation
input.sales 
  |> groupBy(sale => sale.region)
  |> map((region, sales) => {
      region: region,
      total: sum(sales.*.amount),
      count: count(sales)
    })
```

### Deduplicate Array

**Remove duplicates:**

```utlx
input.items |> distinct()

// By specific property
input.users |> distinctBy(user => user.email)
```

### Take/Drop Elements

**Limit or skip elements:**

```utlx
// First 10 items
input.items |> take(10)

// Skip first 20, take next 10
input.items |> drop(20) |> take(10)

// Last 5 items
input.items |> reverse() |> take(5) |> reverse()
```

### Flatten Nested Arrays

**Flatten array of arrays:**

```utlx
// Input: [[1,2], [3,4], [5,6]]
// Output: [1,2,3,4,5,6]

input.arrays |> flatten()

// Flatten nested property
input.orders |> flatMap(order => order.items)
```

---

## Aggregations

### Sum Values

```utlx
// Sum all prices
sum($input.items.*.price)

// Conditional sum
sum($input.items 
  |> filter(i => i.category == "Electronics")
  |> map(i => i.price))
```

### Count Elements

```utlx
// Count all items
count($input.items)

// Count matching
count($input.items |> filter(i => i.inStock))
```

### Average

```utlx
// Average price
avg($input.items.*.price)

// Average of active users' age
avg($input.users 
  |> filter(u => u.active)
  |> map(u => u.age))
```

### Min/Max

```utlx
// Minimum price
min($input.items.*.price)

// Maximum price
max($input.items.*.price)

// Item with max price
input.items 
  |> sortBy(i => -i.price)
  |> first()
```

### Statistics

**Multiple aggregations:**

```utlx
{
  let prices = $input.items.*.price,
  
  count: count(prices),
  sum: sum(prices),
  avg: avg(prices),
  min: min(prices),
  max: max(prices),
  range: max(prices) - min(prices)
}
```

---

## Conditional Logic

### Simple Conditionals

```utlx
// If-else
{
  discount: if (total > 100) 10 else 0,
  shipping: if (total > 50) 0 else 5.99
}
```

### Multi-way Conditionals

```utlx
{
  grade: if (score >= 90) "A"
         else if (score >= 80) "B"
         else if (score >= 70) "C"
         else if (score >= 60) "D"
         else "F"
}
```

### Pattern Matching

```utlx
{
  shippingCost: match orderType {
    "express" => 15.00,
    "standard" => 5.00,
    "economy" => 2.50,
    _ => 0
  }
}
```

### Conditional Fields

**Include fields conditionally:**

```utlx
{
  name: $input.name,
  email: $input.email,
  
  // Only include premium benefits if user is premium
  ...(if ($input.isPremium) {
    premiumBenefits: $input.benefits
  } else {})
}
```

---

## String Manipulations

### Case Conversion

```utlx
{
  uppercase: upper($input.name),
  lowercase: lower($input.name),
  titlecase: titleCase($input.name)
}
```

### Trimming

```utlx
{
  trimmed: trim($input.text),
  trimStart: trimStart($input.text),
  trimEnd: trimEnd($input.text)
}
```

### Concatenation

```utlx
{
  fullName: $input.firstName + " " + $input.lastName,
  greeting: "Hello, " + $input.name + "!",
  url: "https://" + $input.domain + "/" + $input.path
}
```

### Substring Operations

```utlx
{
  // First 10 characters
  preview: substring($input.description, 0, 10),
  
  // Extract domain from email
  domain: split($input.email, "@")[1]
}
```

### String Splitting/Joining

```utlx
{
  // Split CSV string
  tags: split($input.tagString, ","),
  
  // Join array to string
  tagString: join($input.tags, ", ")
}
```

### Pattern Matching

```utlx
{
  // Check if contains
  hasKeyword: contains($input.text, "important"),
  
  // Starts/ends with
  isHttps: startsWith($input.url, "https://"),
  isPdf: endsWith($input.filename, ".pdf")
}
```

---

## Number Operations

### Arithmetic

```utlx
{
  subtotal: $input.quantity * $input.price,
  tax: subtotal * 0.08,
  total: subtotal + tax,
  discount: subtotal * 0.10,
  final: total - discount
}
```

### Rounding

```utlx
{
  rounded: round($input.value),           // 3.7 → 4
  roundedUp: ceil($input.value),          // 3.1 → 4
  roundedDown: floor($input.value),       // 3.9 → 3
  twoDecimals: round($input.value * 100) / 100
}
```

### Formatting

```utlx
{
  // Format as currency
  price: "$" + (round($input.price * 100) / 100).toFixed(2),
  
  // Format percentage
  discount: (round($input.discount * 100)) + "%"
}
```

### Conversions

```utlx
{
  // String to number
  quantity: parseNumber($input.quantityString),
  
  // Number to string
  quantityString: toString($input.quantity)
}
```

---

## Date/Time Operations

### Current Date/Time

```utlx
{
  now: now(),
  today: today(),
  timestamp: timestamp()
}
```

### Date Parsing

```utlx
{
  // Parse ISO date
  parsedDate: parseDate($input.dateString),
  
  // Parse with format
  customDate: parseDate($input.dateString, "MM/DD/YYYY")
}
```

### Date Formatting

```utlx
{
  // Format as ISO
  isoDate: formatDate($input.date, "YYYY-MM-DD"),
  
  // Custom format
  displayDate: formatDate($input.date, "MMM DD, YYYY")
}
```

### Date Arithmetic

```utlx
{
  tomorrow: addDays(today(), 1),
  nextWeek: addDays(today(), 7),
  lastMonth: addMonths(today(), -1),
  
  daysSince: diffDays(today(), $input.startDate)
}
```

---

## Default Values

### Null Coalescing

```utlx
{
  // Use default if null/undefined
  name: $input.name || "Unknown",
  quantity: $input.quantity || 0,
  active: $input.active || false
}
```

### Nested Defaults

```utlx
{
  email: $input.customer.email 
      || $input.user.email 
      || "no-email@example.com"
}
```

### Conditional Defaults

```utlx
{
  discount: $input.discount || (
    if ($input.total > 1000) 0.20
    else if ($input.total > 500) 0.10
    else 0
  )
}
```

---

## Data Validation

### Required Fields

```utlx
{
  name: $input.name || throw("Name is required"),
  email: $input.email || throw("Email is required")
}
```

### Type Checking

```utlx
{
  isValid: isString($input.name) && 
           isNumber($input.age) && 
           isBoolean($input.active)
}
```

### Range Validation

```utlx
{
  age: if ($input.age >= 0 && $input.age <= 150)
         $input.age
       else
         throw("Invalid age: " + $input.age)
}
```

### Format Validation

```utlx
{
  email: if (contains($input.email, "@"))
           $input.email
         else
           throw("Invalid email format")
}
```

---

## Lookup and Mapping

### Simple Lookup

```utlx
{
  let statusMap = {
    "P": "Pending",
    "A": "Approved",
    "R": "Rejected"
  },
  
  statusText: statusMap[$input.statusCode]
}
```

### Array Lookup

```utlx
{
  let products = [
    {id: "P001", name: "Widget"},
    {id: "P002", name: "Gadget"}
  ],
  
  productName: (products |> find(p => p.id == $input.productId)).name
}
```

### Enrichment from External Data

```utlx
{
  let productCatalog = $input.catalog,
  
  enrichedItems: $input.order.items |> map(item => {
    ...item,
    productName: (productCatalog 
      |> find(p => p.sku == item.sku)).name,
    category: (productCatalog 
      |> find(p => p.sku == item.sku)).category
  })
}
```

---

## Error Handling

### Safe Property Access

```utlx
{
  // Avoid null reference errors
  city: $input.customer.address.city || "N/A",
  
  // Check existence first
  hasAddress: $input.customer.address != null,
  city: if ($input.customer.address != null)
          $input.customer.address.city
        else
          "No address"
}
```

### Try-Catch Pattern (Future)

```utlx
{
  parsed: try {
    parseNumber($input.value)
  } catch (e) {
    0  // Default value on error
  }
}
```

### Validation Before Processing

```utlx
{
  let isValid = $input.items != null && count($input.items) > 0,
  
  result: if (isValid)
            processItems($input.items)
          else
            {error: "No items to process"}
}
```

---

## Performance Patterns

### Avoid Repeated Computation

**❌ Bad:**
```utlx
{
  subtotal: sum($input.items.*.price),
  tax: sum($input.items.*.price) * 0.08,        // Computed again
  total: sum($input.items.*.price) * 1.08       // Computed again
}
```

**✅ Good:**
```utlx
{
  let subtotal = sum($input.items.*.price),
  
  subtotal: subtotal,
  tax: subtotal * 0.08,
  total: subtotal * 1.08
}
```

### Cache Filtered Results

**❌ Bad:**
```utlx
{
  activeCount: count($input.users |> filter(u => u.active)),
  activeNames: $input.users |> filter(u => u.active) |> map(u => u.name),
  activeEmails: $input.users |> filter(u => u.active) |> map(u => u.email)
}
```

**✅ Good:**
```utlx
{
  let activeUsers = $input.users |> filter(u => u.active),
  
  activeCount: count(activeUsers),
  activeNames: activeUsers |> map(u => u.name),
  activeEmails: activeUsers |> map(u => u.email)
}
```

### Use Pipeline for Efficiency

**✅ Good:**
```utlx
input.items
  |> filter(i => i.active)        // Filter first (reduce data)
  |> map(i => i.price)            // Then transform
  |> sum()                        // Then aggregate
```

---

## Complex Examples

### Invoice Generation

```utlx
%utlx 1.0
input json
output json
---
{
  let order = $input.order,
  let items = order.items,
  let customer = order.customer,
  
  let subtotal = sum(items |> map(i => i.price * i.quantity)),
  let taxRate = if (customer.state == "CA") 0.0875 else 0.06,
  let tax = subtotal * taxRate,
  let shipping = if (subtotal > 100) 0 else 10.00,
  let discount = if (customer.vip) subtotal * 0.10 else 0,
  let total = subtotal + tax + shipping - discount,
  
  invoice: {
    invoiceNumber: "INV-" + order.id,
    date: now(),
    
    customer: {
      name: customer.name,
      email: customer.email,
      address: customer.address,
      vipStatus: customer.vip
    },
    
    lineItems: items |> map(item => {
      sku: item.sku,
      description: item.name,
      quantity: item.quantity,
      unitPrice: item.price,
      lineTotal: item.price * item.quantity
    }),
    
    summary: {
      subtotal: subtotal,
      taxRate: taxRate,
      tax: tax,
      shipping: shipping,
      discount: discount,
      total: total
    },
    
    notes: if (customer.vip)
             "Thank you for being a VIP customer!"
           else if (total > 1000)
             "Thank you for your large order!"
           else
             "Thank you for your order!"
  }
}
```

### Data Quality Report

```utlx
%utlx 1.0
input json
output json
---
{
  let records = $input.records,
  
  summary: {
    totalRecords: count(records),
    validRecords: count(records |> filter(r => 
      r.name != null && 
      r.email != null && 
      contains(r.email, "@")
    )),
    invalidRecords: count(records |> filter(r => 
      r.name == null || 
      r.email == null || 
      !contains(r.email, "@")
    ))
  },
  
  issues: {
    missingNames: records 
      |> filter(r => r.name == null)
      |> map(r => r.id),
      
    missingEmails: records 
      |> filter(r => r.email == null)
      |> map(r => r.id),
      
    invalidEmails: records 
      |> filter(r => r.email != null && !contains(r.email, "@"))
      |> map(r => {
          id: r.id,
          email: r.email
        })
  },
  
  recommendations: [
    if (count(records |> filter(r => r.name == null)) > 0)
      "Fix records with missing names"
    else
      null,
      
    if (count(records |> filter(r => r.email == null)) > 0)
      "Fix records with missing emails"
    else
      null,
      
    if (count(records |> filter(r => r.email != null && !contains(r.email, "@"))) > 0)
      "Fix records with invalid email format"
    else
      null
  ] |> filter(r => r != null)
}
```

---

## Quick Reference

| Task | Pattern |
|------|---------|
| Rename | `newName: $input.oldName` |
| Flatten | `field: $input.nested.deep.field` |
| Filter | `$input.array \|> filter(x => condition)` |
| Map | `$input.array \|> map(x => transform)` |
| Sort | `$input.array \|> sortBy(x => x.field)` |
| Group | `$input.array \|> groupBy(x => x.field)` |
| Sum | `sum($input.array.*.field)` |
| Count | `count($input.array)` |
| Default | `$input.field \|\| defaultValue` |
| Conditional | `if (condition) value1 else value2` |

---

## Next Steps

- 📖 [XML to JSON](xml-to-json.md) - Format conversions
- 📖 [JSON to XML](json-to-xml.md) - Reverse conversions
- 🔧 [Functions Reference](../reference/stdlib-reference.md) - All functions
- 💡 [Real-World Examples](real-world-use-cases.md) - Production use cases

---

**Have a pattern to share?** Contribute via [Pull Request](https://github.com/grauwen/utl-x/pulls)!
