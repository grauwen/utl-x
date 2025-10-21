# Syntax Guide

Complete guide to UTL-X syntax and grammar.

---

## Document Structure

Every UTL-X document has four parts:

```utlx
%utlx 1.0                    // 1. Version directive (required)
input xml                    // 2. Configuration directives
output json
---                          // 3. Separator (required)
{                           // 4. Transformation body
  result: input.data
}
```

### 1. Version Directive

**Always the first line:**

```utlx
%utlx 1.0
```

Specifies the UTL-X language version. Currently only `1.0` is supported.

### 2. Configuration Directives

**Input directive:**
```utlx
input <format> [options]
```

**Output directive:**
```utlx
output <format> [options]
```

**Options are optional:**
```utlx
input xml
output json

// Or with options:
input csv {
  headers: true,
  delimiter: ","
}
```

### 3. Separator

**Required:** Three dashes on their own line:

```utlx
---
```

Separates configuration from transformation logic.

### 4. Transformation Body

The actual transformation logic. Must be a single expression (can be a complex expression).

---

## Literals

### String Literals

**Double quotes:**
```utlx
"Hello, World!"
"This is a string"
```

**Single quotes:**
```utlx
'Hello, World!'
'This is also a string'
```

**Escape sequences:**
```utlx
"Line 1\nLine 2"          // Newline
"Column 1\tColumn 2"      // Tab
"He said \"Hello\""       // Escaped quote
'It\'s working'           // Escaped single quote
"Path: C:\\Users\\Alice"  // Escaped backslash
```

**Multi-line strings:**
```utlx
"This is a
multi-line
string"
```

### Number Literals

**Integers:**
```utlx
42
-17
0
1000
```

**Decimals:**
```utlx
3.14
-0.5
123.456
```

**Scientific notation:**
```utlx
1.5e10      // 15,000,000,000
2.5e-3      // 0.0025
-1.23e6     // -1,230,000
```

### Boolean Literals

```utlx
true
false
```

### Null Literal

```utlx
null
```

---

## Identifiers

### Rules

**Valid identifiers:**
- Start with letter or underscore
- Contain letters, digits, underscores
- Case-sensitive

```utlx
name           // ✅ Valid
_private       // ✅ Valid
user123        // ✅ Valid
camelCase      // ✅ Valid
snake_case     // ✅ Valid
```

**Invalid identifiers:**
```utlx
123name        // ❌ Cannot start with digit
user-name      // ❌ Hyphens not allowed
my.variable    // ❌ Dots not allowed
class          // ❌ Reserved keyword
```

### Reserved Keywords

```
and, apply, as, break, case, catch, const, continue,
default, do, else, false, finally, for, function,
if, import, in, input, let, match, module, null,
or, output, return, template, throw, true, try,
typeof, var, when, while
```

---

## Object Expressions

### Basic Object

```utlx
{
  name: "Alice",
  age: 30,
  active: true
}
```

### Trailing Commas

**Allowed:**
```utlx
{
  name: "Alice",
  age: 30,        // Trailing comma OK
}
```

### Nested Objects

```utlx
{
  person: {
    name: "Alice",
    address: {
      street: "123 Main St",
      city: "Springfield"
    }
  }
}
```

### Computed Keys

```utlx
{
  [dynamicKey]: "value",
  ["prefix_" + suffix]: "computed"
}
```

### Shorthand Properties

```utlx
let name = "Alice"
let age = 30

{
  name,      // Shorthand for name: name
  age        // Shorthand for age: age
}
```

### Spread Operator

```utlx
let base = {x: 1, y: 2}

{
  ...base,        // Include all properties from base
  z: 3           // Add new property
}
// Result: {x: 1, y: 2, z: 3}
```

---

## Array Expressions

### Basic Array

```utlx
[1, 2, 3]
["red", "green", "blue"]
[true, false, true]
```

### Mixed Types

```utlx
[1, "two", true, null, {key: "value"}]
```

### Trailing Commas

```utlx
[
  1,
  2,
  3,    // Trailing comma OK
]
```

### Nested Arrays

```utlx
[
  [1, 2, 3],
  [4, 5, 6],
  [7, 8, 9]
]
```

### Spread Operator

```utlx
let first = [1, 2, 3]
let second = [4, 5, 6]

[...first, ...second]
// Result: [1, 2, 3, 4, 5, 6]
```

---

## Selectors

### Simple Path

```utlx
input.order
input.order.customer
input.order.customer.name
```

### Attribute Access

```utlx
input.order.@id           // XML attribute
input.order.@date         // XML attribute
```

### Array Index

```utlx
input.items[0]            // First element
input.items[1]            // Second element
input.items[-1]           // Last element (future)
```

### Array Wildcard

```utlx
input.items[*]            // All elements
input.items.*.price       // All prices
```

### Recursive Descent

```utlx
input..name               // All 'name' properties at any depth
input..@id                // All 'id' attributes anywhere
```

### Predicates

**Simple predicate:**
```utlx
input.items[price > 100]
```

**Complex predicate:**
```utlx
input.items[price > 100 && quantity < 10]
input.orders[status == "pending" || status == "processing"]
```

**Predicate with functions:**
```utlx
input.items[upper(category) == "ELECTRONICS"]
```

---

## Operators

### Arithmetic Operators

```utlx
a + b        // Addition
a - b        // Subtraction
a * b        // Multiplication
a / b        // Division
a % b        // Modulo (remainder)
-a           // Unary minus
```

**Examples:**
```utlx
10 + 5       // 15
10 - 5       // 5
10 * 5       // 50
10 / 5       // 2
10 % 3       // 1
-10          // -10
```

### Comparison Operators

```utlx
a == b       // Equal
a != b       // Not equal
a > b        // Greater than
a >= b       // Greater than or equal
a < b        // Less than
a <= b       // Less than or equal
```

**Examples:**
```utlx
5 == 5       // true
5 != 3       // true
10 > 5       // true
10 >= 10     // true
3 < 5        // true
5 <= 5       // true
```

### Logical Operators

```utlx
a && b       // Logical AND
a || b       // Logical OR
!a           // Logical NOT
```

**Examples:**
```utlx
true && true     // true
true && false    // false
true || false    // true
false || false   // false
!true            // false
!false           // true
```

**Short-circuit evaluation:**
```utlx
false && expensiveFunction()  // expensiveFunction() not called
true || expensiveFunction()   // expensiveFunction() not called
```

### String Concatenation

```utlx
"Hello" + " " + "World"      // "Hello World"
"Value: " + 42               // "Value: 42" (auto-conversion)
```

### Pipeline Operator

```utlx
value |> function
```

**Example:**
```utlx
[1, 2, 3] |> map(x => x * 2) |> sum()
// Equivalent to: sum(map([1, 2, 3], x => x * 2))
```

### Precedence

**From highest to lowest:**

1. `.` (member access), `[]` (indexing)
2. `-` (unary minus), `!` (logical NOT)
3. `*`, `/`, `%`
4. `+`, `-`
5. `<`, `<=`, `>`, `>=`
6. `==`, `!=`
7. `&&`
8. `||`
9. `|>` (pipeline)

**Use parentheses for clarity:**
```utlx
(a + b) * c      // Clear grouping
a + (b * c)      // Explicit order
```

---

## Control Flow

### If-Else Expression

**Basic:**
```utlx
if (condition) trueValue else falseValue
```

**Examples:**
```utlx
if (x > 10) "big" else "small"

if (status == "active") 
  enabledFeatures 
else 
  disabledFeatures
```

**Multi-line:**
```utlx
if (score >= 90) 
  "A"
else if (score >= 80)
  "B"
else if (score >= 70)
  "C"
else
  "F"
```

**In object:**
```utlx
{
  discount: if (total > 100) total * 0.1 else 0,
  shipping: if (total > 50) 0 else 5.99
}
```

### Match Expression

**Basic:**
```utlx
match expression {
  pattern1 => result1,
  pattern2 => result2,
  _ => defaultResult
}
```

**Examples:**
```utlx
match orderType {
  "express" => 15.00,
  "standard" => 5.00,
  "economy" => 2.50,
  _ => 0
}
```

**With objects:**
```utlx
match status {
  "pending" => {
    color: "yellow",
    message: "Processing..."
  },
  "complete" => {
    color: "green",
    message: "Done!"
  },
  _ => {
    color: "gray",
    message: "Unknown"
  }
}
```

---

## Functions

### Function Call

```utlx
functionName(arg1, arg2, arg3)
```

**Examples:**
```utlx
sum([1, 2, 3])
upper("hello")
concat("Hello", " ", "World")
```

### Function Definition

```utlx
function name(param1: Type1, param2: Type2): ReturnType {
  expression
}
```

**Examples:**
```utlx
function double(x: Number): Number {
  x * 2
}

function greet(name: String): String {
  "Hello, " + name
}

function calculateTax(amount: Number, rate: Number): Number {
  amount * rate
}
```

### Lambda Expressions

**Syntax:**
```utlx
param => expression
(param1, param2) => expression
```

**Examples:**
```utlx
x => x * 2
(a, b) => a + b
item => item.price > 100
```

**In higher-order functions:**
```utlx
[1, 2, 3] |> map(x => x * 2)
[1, 2, 3, 4] |> filter(x => x > 2)
[1, 2, 3] |> reduce((acc, x) => acc + x, 0)
```

---

## Let Bindings

### Single Binding

```utlx
let name = value
```

**Example:**
```utlx
{
  let total = 100,
  
  result: total * 1.1
}
```

### Multiple Bindings

```utlx
{
  let x = 10,
  let y = 20,
  let z = 30,
  
  sum: x + y + z
}
```

### Dependent Bindings

```utlx
{
  let subtotal = 100,
  let tax = subtotal * 0.08,
  let total = subtotal + tax,
  
  invoice: {
    subtotal: subtotal,
    tax: tax,
    total: total
  }
}
```

### Scope

Variables are scoped to their containing block:

```utlx
{
  let outer = 10,

  nested: {
    let inner = 20,
    sum: outer + inner    // ✅ Both accessible
  },

  value: outer,           // ✅ Accessible
  wrong: inner            // ❌ Not accessible here
}
```

### Block Expressions vs Object Literals

UTL-X distinguishes between **object literals** (which create objects) and **block expressions** (which execute statements and return values).

#### Object Literals

When let bindings are followed by property definitions, use commas as separators:

```utlx
{
  let tax = subtotal * 0.08,
  let total = subtotal + tax,

  // These are object properties
  subtotal: subtotal,
  tax: tax,
  total: total
}
```

#### Block Expressions

When let bindings are followed by a **return expression** (array, object, or any other expression), **semicolons are required**:

```utlx
// ✅ CORRECT - semicolons required before array return
employees |> map(emp => {
  let salary = parseNumber(emp.Salary);
  let bonus = salary * 0.10;

  [emp.Name, salary + bonus]  // Array return
})

// ✅ CORRECT - semicolons required before object return
items |> map(item => {
  let price = parseNumber(item.Price);
  let tax = price * 0.08;

  {  // Object return
    name: item.Name,
    total: price + tax
  }
})
```

#### Why Semicolons Are Required

Without semicolons, the parser cannot distinguish between:
- A let binding followed by an array: `let x = val; [array]`
- Array indexing: `let x = val[array]`

```utlx
// ❌ INCORRECT - causes parse error
{
  let x = 10
  let y = 20
  [x, y]  // Parser thinks: let y = 20[x, y]
}

// ✅ CORRECT - semicolons make intent clear
{
  let x = 10;
  let y = 20;
  [x, y]
}
```

**Best Practice:** Always use semicolons after let bindings in lambda bodies or block expressions to avoid ambiguity.

---

## Template Matching

### Template Definition

```utlx
template match="pattern" {
  transformation
}
```

**Example:**
```utlx
template match="Order" {
  order: {
    id: @id,
    total: sum(Items/Item/(@price * @quantity))
  }
}
```

### Template Application

```utlx
apply(selector)
```

**Example:**
```utlx
template match="Order" {
  order: {
    customer: apply(Customer),
    items: apply(Items/Item)
  }
}

template match="Customer" {
  name: Name,
  email: Email
}

template match="Item" {
  sku: @sku,
  price: @price
}
```

### Template Priority

More specific patterns have higher priority:

```utlx
template match="Item" {
  // General case
}

template match="Item[@special=true]" {
  // Specific case (higher priority)
}
```

---

## Comments

### Single-Line Comments

```utlx
// This is a comment
let x = 10  // End-of-line comment
```

### Multi-Line Comments

```utlx
/*
 * This is a multi-line comment
 * It can span multiple lines
 */
let x = 10
```

### Documentation Comments

```utlx
/**
 * Calculates the total with tax.
 * @param subtotal - The amount before tax
 * @param taxRate - Tax rate (e.g., 0.08 for 8%)
 * @return Total amount including tax
 */
function calculateTotal(subtotal: Number, taxRate: Number): Number {
  subtotal * (1 + taxRate)
}
```

---

## Type Annotations

### Variable Types

```utlx
let name: String = "Alice"
let age: Number = 30
let active: Boolean = true
let items: Array<Number> = [1, 2, 3]
```

### Function Parameter Types

```utlx
function greet(name: String): String {
  "Hello, " + name
}

function add(a: Number, b: Number): Number {
  a + b
}
```

### Optional Types

```utlx
let value: Number? = null  // Can be Number or null
```

---

## Whitespace and Formatting

### Whitespace Rules

**Ignored:**
```utlx
{name:"Alice",age:30}
// Same as:
{
  name: "Alice",
  age: 30
}
```

**Line breaks optional:**
```utlx
let x = 10 let y = 20
// Same as:
let x = 10
let y = 20
```

### Recommended Style

**Indentation:** 2 or 4 spaces

**Object formatting:**
```utlx
// Good
{
  name: "Alice",
  age: 30,
  active: true
}

// Also acceptable
{ name: "Alice", age: 30 }
```

**Array formatting:**
```utlx
// Good
[
  1,
  2,
  3
]

// Also acceptable
[1, 2, 3]
```

**Pipeline formatting:**
```utlx
// Good
input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()

// Acceptable for short chains
input.items |> map(i => i.price) |> sum()
```

---

## Syntax Errors

### Common Errors

**Missing separator:**
```utlx
%utlx 1.0
input xml
output json
// ❌ ERROR: Missing ---
{
  result: input.data
}
```

**Unclosed braces:**
```utlx
{
  name: "Alice",
  age: 30
// ❌ ERROR: Missing closing }
```

**Missing commas:**
```utlx
{
  name: "Alice"
  age: 30      // ❌ ERROR: Missing comma after "Alice"
}
```

**Invalid identifiers:**
```utlx
let 123name = "test"  // ❌ ERROR: Cannot start with digit
let my-var = 10       // ❌ ERROR: Hyphens not allowed
```

---

## Grammar Summary

**EBNF-style grammar:**

```ebnf
document     = version directives "---" expression
version      = "%utlx" NUMBER
directives   = directive*
directive    = "input" format options?
             | "output" format options?

expression   = literal
             | identifier
             | object
             | array
             | selector
             | functionCall
             | lambda
             | ifExpr
             | matchExpr
             | letExpr
             | templateDef
             | expression operator expression
             | "(" expression ")"

object       = "{" (property ("," property)*)? "}"
property     = identifier ":" expression

array        = "[" (expression ("," expression)*)? "]"

selector     = "input" path*
path         = "." identifier
             | "[" expression "]"
             | ".@" identifier

functionCall = identifier "(" (expression ("," expression)*)? ")"

lambda       = identifier "=>" expression
             | "(" params ")" "=>" expression

ifExpr       = "if" "(" expression ")" expression "else" expression

matchExpr    = "match" expression "{" (pattern "=>" expression ",")* "}"

letExpr      = "let" identifier "=" expression

templateDef  = "template" "match=" STRING "{" expression "}"
```

---

## Best Practices

### 1. Use Meaningful Names

```utlx
// ❌ Bad
let x = input.o.c.n

// ✅ Good
let customerName = input.order.customer.name
```

### 2. Format for Readability

```utlx
// ❌ Bad
{let x=100,let y=x*0.08,total:x+y}

// ✅ Good
{
  let subtotal = 100,
  let tax = subtotal * 0.08,
  
  total: subtotal + tax
}
```

### 3. Use Type Annotations

```utlx
// ❌ Less clear
function calc(x, y) { x + y }

// ✅ More clear
function calc(x: Number, y: Number): Number {
  x + y
}
```

### 4. Comment Complex Logic

```utlx
// Calculate discounted price based on volume
let discount = if (quantity > 100) 0.20      // 20% for bulk
               else if (quantity > 50) 0.10   // 10% for medium
               else 0                         // No discount
```

---

## Next Steps

- **Learn data types:** [Data Types Guide](data-types.md)
- **Master selectors:** [Selectors Guide](selectors.md)
- **Explore functions:** [Functions Reference](functions.md)
- **Try examples:** [Examples](../examples/)

---

**Questions?** Ask in [Discussions](https://github.com/grauwen/utl-x/discussions)
