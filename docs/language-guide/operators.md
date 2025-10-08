# Operators

UTL-X supports various operators for arithmetic, comparison, logical operations, and more.

## Arithmetic Operators

### Addition (+)

```utlx
10 + 5                   // 15
3.14 + 2.86              // 6.0
```

**String Concatenation:**

```utlx
"Hello" + " World"       // "Hello World"
"Price: " + 29.99        // "Price: 29.99" (auto-converts number)
```

### Subtraction (-)

```utlx
10 - 5                   // 5
100 - 25.5               // 74.5
```

### Multiplication (*)

```utlx
10 * 5                   // 50
3.5 * 2                  // 7.0
```

### Division (/)

```utlx
10 / 5                   // 2
10 / 3                   // 3.333...
```

**Division by Zero:**

```utlx
10 / 0                   // Error (use try/catch)
```

### Modulo (%)

Remainder after division:

```utlx
10 % 3                   // 1
17 % 5                   // 2
10 % 2                   // 0
```

### Exponentiation (**)

```utlx
2 ** 3                   // 8
10 ** 2                  // 100
2 ** 0.5                 // 1.414... (square root)
```

### Unary Minus (-)

Negate number:

```utlx
-5                       // -5
-(10 + 5)                // -15
```

### Unary Plus (+)

Convert to number:

```utlx
+"42"                    // 42
+true                    // 1
+false                   // 0
```

## Comparison Operators

### Equality (==)

```utlx
10 == 10                 // true
"hello" == "hello"       // true
10 == "10"               // false (strict equality)
```

### Inequality (!=)

```utlx
10 != 5                  // true
"hello" != "world"       // true
10 != 10                 // false
```

### Greater Than (>)

```utlx
10 > 5                   // true
5 > 10                   // false
10 > 10                  // false
```

### Less Than (<)

```utlx
5 < 10                   // true
10 < 5                   // false
10 < 10                  // false
```

### Greater Than or Equal (>=)

```utlx
10 >= 5                  // true
10 >= 10                 // true
5 >= 10                  // false
```

### Less Than or Equal (<=)

```utlx
5 <= 10                  // true
10 <= 10                 // true
10 <= 5                  // false
```

## Logical Operators

### Logical AND (&&)

Returns first falsy value or last value:

```utlx
true && true             // true
true && false            // false
false && true            // false

// Short-circuit evaluation
let x = input.customer && input.customer.name
// If input.customer is null, returns null without evaluating .name
```

### Logical OR (||)

Returns first truthy value or last value:

```utlx
true || false            // true
false || true            // true
false || false           // false

// Default values
let name = input.nickname || input.fullName || "Anonymous"
```

### Logical NOT (!)

Inverts boolean:

```utlx
!true                    // false
!false                   // true
!!value                  // Converts to boolean
```

## Nullish Coalescing Operator (??)

Returns right side if left side is null:

```utlx
null ?? "default"        // "default"
"value" ?? "default"     // "value"
0 ?? "default"           // 0 (not null, so returns 0)
"" ?? "default"          // "" (not null, so returns "")
```

**Difference from ||:**

```utlx
// || treats 0, "", false as falsy
0 || "default"           // "default"
"" || "default"          // "default"

// ?? only checks for null/undefined
0 ?? "default"           // 0
"" ?? "default"          // ""
```

## Safe Navigation Operator (?.)

Access properties safely:

```utlx
input.customer?.address?.city
// Returns null if customer or address is null
```

**Equivalent to:**

```utlx
if (input.customer != null && input.customer.address != null)
  input.customer.address.city
else
  null
```

## Pipe Operator (|>)

Pass value to function:

```utlx
value |> function()
```

**Example:**

```utlx
[1, 2, 3, 4, 5]
  |> filter(n => n > 2)
  |> map(n => n * 2)
  |> sum()
// 24
```

**Equivalent to:**

```utlx
sum(map(filter([1, 2, 3, 4, 5], n => n > 2), n => n * 2))
```

## Ternary Operator (? :)

Conditional expression:

```utlx
condition ? valueIfTrue : valueIfFalse
```

**Example:**

```utlx
let status = input.quantity > 0 ? "Available" : "Out of Stock"
let discount = input.total > 100 ? 0.10 : 0
```

## Member Access Operator (.)

Access object properties:

```utlx
input.customer.name
input.order.items
```

## Bracket Operator ([])

Array/object access:

```utlx
// Array index
items[0]
items[2]

// Object property
customer["name"]
customer["first-name"]   // For keys with special characters

// Dynamic access
let key = "name"
customer[key]
```

## Spread Operator (...) (v1.1+)

### Array Spread

```utlx
let arr1 = [1, 2, 3]
let arr2 = [4, 5, 6]
let combined = [...arr1, ...arr2]    // [1, 2, 3, 4, 5, 6]
```

### Object Spread

```utlx
let person = {name: "Alice", age: 30}
let updated = {...person, age: 31, city: "Seattle"}
// {name: "Alice", age: 31, city: "Seattle"}
```

## Operator Precedence

From highest to lowest priority:

1. **Grouping**: `()`
2. **Member access**: `.`, `[]`, `?.`
3. **Function call**: `f()`
4. **Unary**: `!`, `-`, `+`
5. **Exponentiation**: `**`
6. **Multiplication, Division, Modulo**: `*`, `/`, `%`
7. **Addition, Subtraction**: `+`, `-`
8. **Comparison**: `<`, `>`, `<=`, `>=`
9. **Equality**: `==`, `!=`
10. **Logical AND**: `&&`
11. **Logical OR**: `||`
12. **Nullish Coalescing**: `??`
13. **Ternary**: `? :`
14. **Pipe**: `|>`

**Examples:**

```utlx
2 + 3 * 4                // 14 (not 20)
(2 + 3) * 4              // 20

true || false && false   // true (AND before OR)
(true || false) && false // false

10 > 5 && 20 > 15        // true
10 > 5 && 20 > 25        // false
```

## Operator Associativity

### Left-to-Right

Most operators:

```utlx
10 - 5 - 2               // (10 - 5) - 2 = 3
100 / 10 / 2             // (100 / 10) / 2 = 5
```

### Right-to-Left

Exponentiation and ternary:

```utlx
2 ** 3 ** 2              // 2 ** (3 ** 2) = 512
a ? b : c ? d : e        // a ? b : (c ? d : e)
```

## Type Coercion

### Implicit Coercion

**String Concatenation:**

```utlx
"Total: " + 42           // "Total: 42" (Number → String)
"Price: " + 29.99        // "Price: 29.99"
```

**Boolean Context:**

```utlx
if (value) { ... }       // Truthy/falsy evaluation
```

**Truthy values:**
- Non-empty strings
- Non-zero numbers
- true
- Non-null objects/arrays

**Falsy values:**
- null
- false
- 0
- "" (empty string)

### Explicit Conversion

```utlx
parseNumber("42")        // String → Number
toString(42)             // Number → String
```

## Operator Overloading

UTL-X does not support operator overloading. Operators have fixed behavior based on operand types.

## Short-Circuit Evaluation

### AND (&&)

Stops at first falsy value:

```utlx
false && expensiveFunction()  // expensiveFunction() not called
```

### OR (||)

Stops at first truthy value:

```utlx
true || expensiveFunction()   // expensiveFunction() not called
```

## Operator Examples

### Calculate Total Price

```utlx
{
  subtotal: sum(input.items.*.price),
  tax: sum(input.items.*.price) * 0.08,
  shipping: input.total > 100 ? 0 : 10,
  total: sum(input.items.*.price) * 1.08 + (input.total > 100 ? 0 : 10)
}
```

### Conditional Discount

```utlx
{
  discount: (input.customer.type == "VIP" && input.total > 1000) ? 0.25 :
            (input.customer.type == "VIP") ? 0.20 :
            (input.total > 500) ? 0.10 :
            0
}
```

### Safe Property Access

```utlx
{
  city: input.customer?.address?.city ?? "Unknown",
  phone: input.customer?.contact?.phone ?? "N/A",
  backup: input.primary || input.secondary || input.default
}
```

### Array Transformation

```utlx
input.items
  |> filter(item => item.price > 50 && item.inStock)
  |> map(item => {
       name: item.name,
       discounted: item.price * 0.90
     })
  |> sortBy(item => item.discounted)
```

## Best Practices

### 1. Use Parentheses for Clarity

```utlx
// ✅ Good - clear intent
let result = (a + b) * (c + d)

// ❌ Bad - hard to read
let result = a + b * c + d
```

### 2. Prefer ?? Over || for Defaults

```utlx
// ✅ Good - only replaces null
let quantity = input.quantity ?? 1

// ❌ Bad - 0 would be replaced
let quantity = input.quantity || 1
```

### 3. Use Safe Navigation

```utlx
// ✅ Good
input.customer?.address?.city

// ❌ Bad - might crash
input.customer.address.city
```

### 4. Chain Operations with Pipe

```utlx
// ✅ Good - readable
data
  |> filter(x => x > 0)
  |> map(x => x * 2)
  |> sum()

// ❌ Bad - nested
sum(map(filter(data, x => x > 0), x => x * 2))
```

### 5. Avoid Complex Expressions

```utlx
// ✅ Good - broken into steps
let subtotal = sum(items.*.price)
let discount = subtotal * 0.10
let tax = (subtotal - discount) * 0.08
let total = subtotal - discount + tax

// ❌ Bad - hard to understand
let total = sum(items.*.price) - (sum(items.*.price) * 0.10) + 
            ((sum(items.*.price) - (sum(items.*.price) * 0.10)) * 0.08)
```

---
