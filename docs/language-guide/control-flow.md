
# Control Flow

Control flow structures determine the order of execution in transformations.

## Conditional Expressions

### If-Else

Basic conditional:

```utlx
if (condition) 
  valueIfTrue 
else 
  valueIfFalse
```

**Example:**

```utlx
{
  discount: if ($input.customer.type == "VIP") 
              $input.total * 0.20 
            else 
              0
}
```

### Multi-Line If-Else

Use braces for multiple expressions:

```utlx
if (condition) {
  let x = calculate()
  let y = transform(x)
  y
} else {
  defaultValue
}
```

### Else-If Chains

```utlx
if (score >= 90)
  "A"
else if (score >= 80)
  "B"
else if (score >= 70)
  "C"
else if (score >= 60)
  "D"
else
  "F"
```

**Example:**

```utlx
{
  shipping: if ($input.total > 100)
              0
            else if ($input.customer.type == "Premium")
              5.00
            else if ($input.weight < 5)
              7.50
            else
              10.00
}
```

### Nested Conditionals

```utlx
if ($input.customer.type == "VIP") {
  if ($input.total > 1000)
    $input.total * 0.25
  else
    $input.total * 0.20
} else {
  if ($input.total > 500)
    $input.total * 0.10
  else
    0
}
```

### Ternary Operator

Short form for simple conditions:

```utlx
condition ? valueIfTrue : valueIfFalse
```

**Example:**

```utlx
{
  status: $input.quantity > 0 ? "In Stock" : "Out of Stock",
  eligible: $input.age >= 18 ? true : false
}
```

## Pattern Matching

### Match Expression

Pattern matching for multiple cases:

```utlx
match value {
  pattern1 => result1,
  pattern2 => result2,
  pattern3 => result3,
  _ => defaultResult
}
```

**Example:**

```utlx
{
  shippingMethod: match $input.orderType {
    "express" => "Overnight",
    "priority" => "2-Day",
    "standard" => "Ground",
    _ => "Unknown"
  }
}
```

### Multiple Patterns

Match multiple values:

```utlx
match $input.status {
  "pending" | "processing" => "In Progress",
  "shipped" | "delivered" => "Completed",
  "cancelled" | "returned" => "Closed",
  _ => "Unknown"
}
```

### Pattern with Guards

Add conditions to patterns:

```utlx
match $input.order {
  order if order.total > 1000 => "High Value",
  order if order.total > 500 => "Medium Value",
  order if order.total > 0 => "Low Value",
  _ => "Invalid"
}
```

### Destructuring in Patterns (v1.1+)

```utlx
match $input.customer {
  {type: "VIP", total: t} if t > 1000 => "VIP Platinum",
  {type: "VIP"} => "VIP",
  {type: "Premium"} => "Premium",
  _ => "Standard"
}
```

### Match on Types

```utlx
match getType($input.value) {
  "string" => upper($input.value),
  "number" => $input.value * 2,
  "boolean" => if ($input.value) "Yes" else "No",
  _ => "Unknown type"
}
```

## Iteration

### Map (Transform Each Element)

```utlx
input.items |> map(item => {
  name: item.name,
  price: item.price * 1.10
})
```

**Example:**

```utlx
{
  transformedOrders: $input.orders |> map(order => {
    id: order.id,
    total: sum(order.items.*.price),
    itemCount: count(order.items)
  })
}
```

### Filter (Select Elements)

```utlx
input.items |> filter(item => item.price > 100)
```

**Example:**

```utlx
{
  expensiveItems: $input.items 
    |> filter(item => item.price > 100)
    |> map(item => item.name)
}
```

### Reduce (Aggregate)

```utlx
input.items |> reduce((acc, item) => acc + item.price, 0)
```

**Example:**

```utlx
{
  stats: {
    total: $input.items |> reduce((acc, item) => acc + item.price, 0),
    categories: $input.items |> reduce((acc, item) => {
      acc + (if (contains(acc, item.category)) "" else item.category + ",")
    }, "")
  }
}
```

### Chaining Operations

```utlx
input.items
  |> filter(item => item.inStock)
  |> map(item => {
       name: item.name,
       discounted: item.price * 0.90
     })
  |> sortBy(item => item.discounted)
  |> take(10)
```

## Error Handling

### Try-Catch

Handle errors gracefully:

```utlx
try {
  parseNumber($input.stringValue)
} catch {
  0
}
```

**Example:**

```utlx
{
  price: try {
    parseNumber($input.priceString)
  } catch {
    0.00
  },
  
  date: try {
    parseDate($input.dateString, "yyyy-MM-dd")
  } catch {
    now()
  }
}
```

### Catch with Error Binding

Access error information:

```utlx
try {
  riskyOperation()
} catch (e) {
  {
    error: e.message,
    default: fallbackValue
  }
}
```

**Example:**

```utlx
{
  result: try {
    let value = parseNumber($input.value)
    value * 2
  } catch (e) {
    {
      success: false,
      error: e.message,
      input: $input.value
    }
  }
}
```

### Multiple Try-Catch

```utlx
{
  price: try { parseNumber($input.price) } catch { 0 },
  quantity: try { parseNumber($input.quantity) } catch { 1 },
  date: try { parseDate($input.date, "yyyy-MM-dd") } catch { now() }
}
```

## Safe Navigation

Avoid null pointer errors:

```utlx
input.customer?.address?.city
```

**Example:**

```utlx
{
  city: $input.customer?.address?.city ?? "Unknown",
  phone: $input.customer?.contact?.phone ?? "N/A",
  email: $input.customer?.contact?.email ?? "no-email@example.com"
}
```

## Nullish Coalescing

Provide default values:

```utlx
input.value ?? defaultValue
```

**Example:**

```utlx
{
  name: $input.customer.name ?? "Anonymous",
  quantity: $input.quantity ?? 1,
  price: $input.price ?? 0.00,
  notes: $input.notes ?? ""
}
```

## Short-Circuit Evaluation

### Logical AND (&&)

Returns first falsy value or last value:

```utlx
let result = $input.customer && $input.customer.address && $input.customer.address.city
// Returns city if all exist, otherwise null/undefined
```

### Logical OR (||)

Returns first truthy value:

```utlx
let name = $input.nickname || $input.fullName || "Anonymous"
// Returns first non-null/non-empty value
```

## Early Return (Functions)

Exit function early:

```utlx
function processOrder(order: Object): Object {
  if (order.items == null) {
    return {error: "No items"}
  }
  
  if (count(order.items) == 0) {
    return {error: "Empty order"}
  }
  
  // Process order...
  {
    total: sum(order.items.*.price)
  }
}
```

## Conditional Field Inclusion

Include fields conditionally:

```utlx
{
  name: $input.name,
  email: $input.email,
  ...(if ($input.phone != null) {phone: $input.phone} else {}),
  ...(if ($input.address != null) {address: $input.address} else {})
}
```

Or using the spread operator (v1.1+):

```utlx
{
  name: $input.name,
  email: $input.email,
  ...if ($input.phone) {phone: $input.phone},
  ...if ($input.address) {address: $input.address}
}
```

## Guard Clauses

Validate inputs early:

```utlx
function calculateDiscount(order: Object): Number {
  // Guard clauses
  if (order == null) return 0
  if (order.total == null) return 0
  if (order.total <= 0) return 0
  
  // Main logic
  if (order.customer.type == "VIP")
    order.total * 0.20
  else
    order.total * 0.10
}
```

## Complex Control Flow Example

```utlx
{
  orders: $input.orders |> map(order => {
    // Early validation
    let isValid = order.items != null && count(order.items) > 0,
    
    if (!isValid) {
      {
        id: order.id,
        status: "invalid",
        error: "No items"
      }
    } else {
      // Calculate totals
      let subtotal = sum(order.items.*.price),
      
      // Determine discount based on customer type and total
      let discount = match order.customer.type {
        "VIP" => if (subtotal > 1000) 0.25 else 0.20,
        "Premium" => if (subtotal > 500) 0.15 else 0.10,
        _ => if (subtotal > 1000) 0.05 else 0
      },
      
      // Calculate tax
      let taxRate = try {
        parseNumber($input.config.taxRate)
      } catch {
        0.08
      },
      
      let discountedTotal = subtotal * (1 - discount),
      let tax = discountedTotal * taxRate,
      let grandTotal = discountedTotal + tax,
      
      // Build result
      {
        id: order.id,
        status: "valid",
        customer: {
          name: order.customer?.name ?? "Unknown",
          type: order.customer?.type ?? "Standard"
        },
        items: order.items |> filter(item => item.price > 0),
        financial: {
          subtotal: subtotal,
          discount: subtotal * discount,
          discountPercent: discount * 100,
          taxableAmount: discountedTotal,
          tax: tax,
          total: grandTotal
        },
        shipping: if (grandTotal > 100 || order.customer.type == "VIP")
                    0
                  else
                    10.00
      }
    }
  })
}
```

## Best Practices

### 1. Prefer Match Over If-Else Chains

```utlx
// ✅ Good
match status {
  "pending" => "Awaiting",
  "shipped" => "In Transit",
  "delivered" => "Complete",
  _ => "Unknown"
}

// ❌ Bad
if (status == "pending")
  "Awaiting"
else if (status == "shipped")
  "In Transit"
else if (status == "delivered")
  "Complete"
else
  "Unknown"
```

### 2. Use Safe Navigation

```utlx
// ✅ Good
input.customer?.address?.city ?? "Unknown"

// ❌ Bad
if ($input.customer != null && 
    $input.customer.address != null && 
    $input.customer.address.city != null)
  $input.customer.address.city
else
  "Unknown"
```

### 3. Handle Errors Gracefully

```utlx
// ✅ Good
try {
  parseNumber($input.value)
} catch {
  0
}

// ❌ Bad
parseNumber($input.value)  // Might crash
```

### 4. Keep Conditions Simple

```utlx
// ✅ Good
let isEligible = $input.age >= 18 && $input.country == "US"
if (isEligible) { ... }

// ❌ Bad
if ($input.age >= 18 && $input.country == "US" && $input.verified == true && ...) { ... }
```

### 5. Use Early Returns

```utlx
// ✅ Good
function process(input: Object): Object {
  if (input == null) return {error: "Null input"}
  if (!isValid(input)) return {error: "Invalid"}
  
  // Main logic
  {result: transform(input)}
}

// ❌ Bad
function process(input: Object): Object {
  if (input != null) {
    if (isValid(input)) {
      // Main logic nested deeply
      {result: transform(input)}
    } else {
      {error: "Invalid"}
    }
  } else {
    {error: "Null input"}
  }
}
```
