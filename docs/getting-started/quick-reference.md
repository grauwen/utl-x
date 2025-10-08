# UTL-X Quick Reference

A quick cheat sheet for UTL-X syntax.

---

## Basic Structure

```utlx
%utlx 1.0
input <format>
output <format>
---
<transformation>
```

---

## Data Types

| Type | Example |
|------|---------|
| **String** | `"Hello"`, `'World'` |
| **Number** | `42`, `3.14`, `1.5e10` |
| **Boolean** | `true`, `false` |
| **Null** | `null` |
| **Array** | `[1, 2, 3]` |
| **Object** | `{name: "Alice", age: 30}` |

---

## Selectors

```utlx
input.root.child              // Navigate path
input.item.@attribute         // Access attribute
input.items[0]                // Index access
input.items[*]                // All elements
input..recursive              // Recursive search
input.items[price > 100]      // Filter with predicate
```

---

## Operators

### Arithmetic

```utlx
+    // Addition
-    // Subtraction
*    // Multiplication
/    // Division
%    // Modulo
```

### Comparison

```utlx
==   // Equal
!=   // Not equal
>    // Greater than
>=   // Greater or equal
<    // Less than
<=   // Less or equal
```

### Logical

```utlx
&&   // AND
||   // OR
!    // NOT
```

### Pipeline

```utlx
|>   // Chain operations
```

---

## Common Functions

### Aggregation

```utlx
sum(array)              // Sum of values
avg(array)              // Average
min(array)              // Minimum
max(array)              // Maximum
count(array)            // Count elements
```

### String

```utlx
upper(str)              // UPPERCASE
lower(str)              // lowercase
trim(str)               // Remove whitespace
concat(str1, str2)      // Concatenate
split(str, delim)       // Split to array
join(array, delim)      // Join to string
```

### Array

```utlx
map(array, fn)          // Transform each element
filter(array, fn)       // Keep matching elements
reduce(array, fn, init) // Reduce to single value
sort(array)             // Sort array
first(array)            // First element
last(array)             // Last element
```

---

## Control Flow

### If-Else

```utlx
if (condition) value1 else value2
```

### Match

```utlx
match expression {
  pattern1 => result1,
  pattern2 => result2,
  _ => default
}
```

---

## Template Matching

```utlx
template match="pattern" {
  <transformation>
}

apply(selector)         // Apply matching template
```

---

## Variable Binding

```utlx
let name = value
```

**Example:**
```utlx
{
  let x = 10,
  let y = 20,
  sum: x + y
}
```

---

## Functions

```utlx
function name(param: Type): ReturnType {
  <expression>
}
```

**Example:**
```utlx
function add(a: Number, b: Number): Number {
  a + b
}
```

---

## Common Patterns

### Object Construction

```utlx
{
  field1: value1,
  field2: value2
}
```

### Array Construction

```utlx
[item1, item2, item3]
```

### Mapping

```utlx
input.items |> map(item => {
  name: item.name,
  price: item.price
})
```

### Filtering

```utlx
input.items |> filter(item => item.price > 100)
```

### Sorting

```utlx
input.items |> sortBy(item => item.price)
```

---

## Examples

### XML to JSON

```utlx
%utlx 1.0
input xml
output json
---
{
  id: input.Order.@id,
  customer: input.Order.Customer.Name,
  total: sum(input.Order.Items.Item.(@price * @quantity))
}
```

### JSON to XML

```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: input.orderId,
    Customer: {
      Name: input.customer.name
    }
  }
}
```

### CSV to JSON

```utlx
%utlx 1.0
input csv { headers: true }
output json
---
{
  records: input.rows |> map(row => {
    name: row.Name,
    age: parseNumber(row.Age),
    email: row.Email
  })
}
```

---

## Format Options

### XML

```utlx
input xml {
  namespaces: {
    "ns": "http://example.com"
  }
}
```

### CSV

```utlx
input csv {
  headers: true,
  delimiter: ",",
  quote: "\""
}
```

### JSON

```utlx
output json {
  pretty: true,
  indent: 2
}
```

---

## Comments

```utlx
// Single-line comment

/*
 * Multi-line
 * comment
 */
```

---

## Type Annotations

```utlx
let name: String = "Alice"
let age: Number = 30
let active: Boolean = true
```

---

## Error Handling

### Default Values

```utlx
input.customer.name || "Unknown"
input.quantity || 0
```

---

## Pipeline Chaining

```utlx
input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

---

## Grouping

```utlx
input.items |> groupBy(item => item.category)
```

---

## Transforming Arrays

```utlx
// Map
[1, 2, 3] |> map(x => x * 2)  // [2, 4, 6]

// Filter
[1, 2, 3, 4] |> filter(x => x > 2)  // [3, 4]

// Reduce
[1, 2, 3, 4] |> reduce((acc, x) => acc + x, 0)  // 10
```

---

## Common Transformations

### Rename Fields

```utlx
{
  newName: input.oldName,
  newEmail: input.oldEmail
}
```

### Flatten Nested

```utlx
{
  id: input.order.id,
  customerName: input.order.customer.name,
  customerEmail: input.order.customer.email
}
```

### Aggregate

```utlx
{
  total: sum(input.items.*.price),
  count: count(input.items),
  average: avg(input.items.*.price)
}
```

---

## Keyboard Shortcuts (CLI)

```bash
utlx transform input.xml script.utlx           # Transform
utlx transform input.xml script.utlx -o out.json  # With output
utlx validate script.utlx                      # Validate syntax
utlx --help                                    # Help
utlx --version                                 # Version
```

---

## Useful Links

- 📖 [Full Language Guide](../language-guide/)
- 💡 [Examples](../examples/)
- 📚 [Language Specification](../reference/language-spec.md)
- 🔧 [Function Reference](../reference/stdlib-reference.md)

---

**Print this page for quick reference while coding!** 🚀
