# UTL-X Quick Reference

**Version**: 0.1.0-SNAPSHOT  
**Format**: JSON ✅ | XML ⏳ | CSV ⏳

---

## Basic Syntax

### Program Structure

```utlx
%utlx 1.0
input <format>
output <format>
---
<transformation expression>
```

### Formats

- `json` - JSON format ✅
- `xml` - XML format (coming soon)
- `csv` - CSV format (coming soon)
- `auto` - Auto-detect format

---

## Data Types

| Type | Example | Notes |
|------|---------|-------|
| String | `"hello"` | Use double quotes |
| Number | `42`, `3.14`, `1e10` | All numbers are doubles |
| Boolean | `true`, `false` | |
| Null | `null` | |
| Array | `[1, 2, 3]` | |
| Object | `{ key: value }` | |

---

## Operators

### Arithmetic

```utlx
a + b        // Addition (also string concatenation)
a - b        // Subtraction
a * b        // Multiplication
a / b        // Division
a % b        // Modulo
```

### Comparison

```utlx
a == b       // Equal
a != b       // Not equal
a < b        // Less than
a <= b       // Less than or equal
a > b        // Greater than
a >= b       // Greater than or equal
```

### Logical

```utlx
a && b       // Logical AND
a || b       // Logical OR
!a           // Logical NOT
```

---

## Navigation

### Member Access

```utlx
input.customer.name           // Object property
input.order.@id               // Attribute (XML)
input.items[0]                // Array index
input.items[*]                // All elements (wildcard)
input..productCode            // Recursive descent
```

---

## Conditionals

```utlx
// Inline if-else
value if condition else otherValue

// Multiple conditions
value1 if condition1
else value2 if condition2
else value3

// Example
discount = 0.20 if customerType == "VIP"
          else 0.10 if total > 1000
          else 0
```

---

## Let Bindings

```utlx
{
  let subtotal = price * quantity,
  let tax = subtotal * 0.08,
  let total = subtotal + tax,
  
  result: {
    subtotal: subtotal,
    tax: tax,
    total: total
  }
}
```

---

## Objects

```utlx
{
  // Simple properties
  name: "Alice",
  age: 30,
  
  // Computed values
  doubled: input.value * 2,
  
  // Nested objects
  address: {
    street: input.street,
    city: input.city
  },
  
  // Arrays
  items: [1, 2, 3],
  
  // From input
  email: input.customer.email
}
```

---

## Built-in Functions

### String Functions

```utlx
upper("hello")              // "HELLO"
lower("HELLO")              // "hello"
trim("  space  ")           // "space"
length("hello")             // 5
substring("hello", 0, 3)    // "hel"
```

### Array Functions

```utlx
sum([1, 2, 3, 4])           // 10
count([1, 2, 3])            // 3
first([1, 2, 3])            // 1
last([1, 2, 3])             // 3

// Note: map/filter coming in next version
```

### Math Functions

```utlx
abs(-5)                     // 5
round(3.14159)              // 3
ceil(3.2)                   // 4
floor(3.8)                  // 3
min(5, 10)                  // 5
max(5, 10)                  // 10
```

---

## Common Patterns

### Transform Object Structure

```utlx
{
  // Rename fields
  customerId: input.customer_id,
  customerName: input.customer_name,
  
  // Nested → Flat
  email: input.customer.contact.email,
  
  // Flat → Nested
  customer: {
    id: input.id,
    name: input.name
  }
}
```

### Calculate Totals

```utlx
{
  let items = input.items,
  let subtotal = sum(items.(price * quantity)),
  let tax = subtotal * 0.08,
  
  total: subtotal + tax
}
```

### Apply Business Rules

```utlx
{
  discount: 0.20 if input.memberLevel == "GOLD"
           else 0.10 if input.memberLevel == "SILVER"
           else 0,
           
  freeShipping: input.total > 100 && input.memberLevel != "BASIC"
}
```

### Filter and Transform

```utlx
{
  // All items (when map/filter available)
  allItems: input.items,
  
  // Count items
  itemCount: count(input.items),
  
  // First/last
  firstItem: first(input.items),
  lastItem: last(input.items)
}
```

---

## Using UTL-X

### Programmatic API

```kotlin
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.formats.json.JSON

// 1. Parse transformation
val source = """
    %utlx 1.0
    input json
    output json
    ---
    { greeting: "Hello, " + input.name }
"""
val tokens = Lexer(source).tokenize()
val program = (Parser(tokens).parse() as ParseResult.Success).program

// 2. Parse input
val inputJSON = """{"name": "Alice"}"""
val inputUDM = JSON.parse(inputJSON)

// 3. Execute
val result = Interpreter().execute(program, inputUDM)

// 4. Serialize output
val outputJSON = JSON.stringify(result)
println(outputJSON)  // {"greeting": "Hello, Alice"}
```

### Command Line (Coming Soon)

```bash
# Transform file
utlx transform input.json -t transform.utlx -o output.json

# Stdin/stdout
cat input.json | utlx transform -t transform.utlx > output.json

# Validate syntax
utlx validate transform.utlx
```

---

## Complete Examples

### E-Commerce Order → Invoice

```utlx
%utlx 1.0
input json
output json
---
{
  let items = input.order.items,
  let subtotal = sum(items.(price * quantity)),
  let discount = subtotal * 0.15,
  let tax = (subtotal - discount) * 0.0875,
  let total = subtotal - discount + tax,
  
  invoice: {
    invoiceNumber: "INV-" + input.order.id,
    invoiceDate: "2025-10-13",
    
    customer: {
      name: input.customer.name,
      email: input.customer.email
    },
    
    lineItems: items,
    
    totals: {
      subtotal: subtotal,
      discount: discount,
      tax: tax,
      total: total
    }
  }
}
```

### Legacy API → Modern REST

```utlx
%utlx 1.0
input json
output json
---
{
  success: input.ResponseCode == "0000",
  message: input.ResponseMessage,
  
  user: {
    id: input.Data.UserID,
    name: {
      first: input.Data.FirstName,
      last: input.Data.LastName
    },
    email: input.Data.EmailAddr,
    phone: input.Data.PhoneNum,
    status: "active" if input.Data.AcctStatus == "A" else "inactive"
  }
}
```

---

## Tips & Best Practices

### Use Let Bindings

**Good**: Clear and reusable
```utlx
{
  let subtotal = price * quantity,
  let tax = subtotal * 0.08,
  total: subtotal + tax
}
```

**Avoid**: Repeated calculations
```utlx
{
  subtotal: price * quantity,
  tax: (price * quantity) * 0.08,       // Calculated twice!
  total: (price * quantity) + (price * quantity) * 0.08
}
```

### Consistent Naming

- Use camelCase for properties
- Use descriptive names
- Match output format conventions

### Handle Nulls Gracefully

```utlx
{
  // Safe access with defaults
  name: input.name,
  age: 0 if input.age == null else input.age
}
```

---

## Error Messages

### Parse Errors

```
Parse error at 5:12 - Expected ':' after property name
```

### Type Errors

```
Type error at 3:8: Cannot add Number and Boolean
  Expected: Number
  Actual: Boolean
```

### Runtime Errors

```
Runtime error at 4:15: Division by zero
Runtime error: Undefined variable: customer
```

---

## Next Steps

1. **Read**: [QUICKSTART.md](QUICKSTART.md) for setup
2. **Explore**: [examples/](examples/) directory
3. **Build**: Try the complete examples
4. **Contribute**: Add new features or formats

---

## Resources

- **Documentation**: [docs/](docs/)
- **Examples**: [examples/](examples/)
- **GitHub**: https://github.com/grauwen/utl-x
- **Issues**: Report bugs and request features

---

**UTL-X** - Universal Transformation Language Extended  
© 2025 Ir. Marcel A. Grauwen | Dual-licensed: AGPL-3.0 / Commercial
