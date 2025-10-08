# UTL-X Language Specification v1.0

**Status:** Draft  
**Version:** 1.0.0-SNAPSHOT  
**Authors:** Ir. Marcel A. Grauwen  
**Organization:** Glomidco B.V.  
**Last Updated:** January 2026

---

## 1. Introduction

### 1.1 Purpose

UTL-X (Universal Transformation Language Extended) is a format-agnostic functional transformation language designed to convert data between XML, JSON, CSV, YAML, and other formats using a unified syntax.

### 1.2 Design Goals

1. **Format Agnostic:** Single syntax works across all data formats
2. **Functional:** Immutable data structures, pure functions, composability
3. **Declarative:** XSLT-inspired template matching with modern syntax
4. **Performance:** Compile-time optimization, efficient runtime
5. **Type Safe:** Strong type system with type inference
6. **Extensible:** Plugin architecture for custom formats

### 1.3 Influences

- **XSLT** - Template matching and declarative transformations
- **DataWeave** - Format abstraction and functional approach
- **JSONata** - Concise query syntax
- **Kotlin** - Modern, expressive syntax

---

## 2. Basic Structure

### 2.1 Document Structure

Every UTL-X document follows this structure:

```utlx
%utlx <version>
[directive...]
---
<transformation-body>
```

**Example:**
```utlx
%utlx 1.0
input xml
output json
---
{
  result: input.root.value
}
```

### 2.2 Directives

Directives configure how input/output is processed.

#### Version Directive

```utlx
%utlx 1.0
```

**Required:** Yes (must be first line)  
**Purpose:** Specifies UTL-X language version

#### Input Directive

```utlx
input <format> [options]
```

**Formats:**
- `xml` - XML input
- `json` - JSON input
- `csv` - CSV input
- `yaml` - YAML input
- `auto` - Auto-detect format

**Examples:**
```utlx
input xml
input json
input auto

input csv {
  headers: true,
  delimiter: ",",
  quote: "\""
}
```

#### Output Directive

```utlx
output <format> [options]
```

**Examples:**
```utlx
output json
output xml { pretty: true }
output csv { headers: true }
```

---

## 3. Data Types

### 3.1 Scalar Types

#### String

```utlx
"Hello, World!"
'Single quotes also work'
```

**Escape sequences:**
- `\n` - Newline
- `\t` - Tab
- `\"` - Double quote
- `\'` - Single quote
- `\\` - Backslash

#### Number

```utlx
42          // Integer
3.14        // Float
1.5e10      // Scientific notation
```

#### Boolean

```utlx
true
false
```

#### Null

```utlx
null
```

### 3.2 Composite Types

#### Object

```utlx
{
  name: "Alice",
  age: 30,
  email: "alice@example.com"
}
```

#### Array

```utlx
[1, 2, 3, 4, 5]
["red", "green", "blue"]
[{id: 1}, {id: 2}]
```

---

## 4. Selectors

### 4.1 Path Expressions

Navigate input data structure:

```utlx
input.Order.Customer.Name        // Simple path
input.Order.@id                  // Attribute access
input.Order.Items[0]             // Index access
input.Order.Items[*]             // All elements
input..ProductCode               // Recursive descent
```

### 4.2 Predicates

Filter elements:

```utlx
input.items[price > 100]                // Numeric comparison
input.items[category == "Electronics"]  // String comparison
input.orders[total > 1000 && status == "pending"]  // Logical AND
```

### 4.3 Wildcards

```utlx
input.Order.*              // All children
input.Order.Items.*        // All items
input.*.Name               // All Name properties at any level
```

---

## 5. Expressions

### 5.1 Arithmetic Operators

```utlx
a + b        // Addition
a - b        // Subtraction
a * b        // Multiplication
a / b        // Division
a % b        // Modulo
```

### 5.2 Comparison Operators

```utlx
a == b       // Equal
a != b       // Not equal
a > b        // Greater than
a >= b       // Greater than or equal
a < b        // Less than
a <= b       // Less than or equal
```

### 5.3 Logical Operators

```utlx
a && b       // Logical AND
a || b       // Logical OR
!a           // Logical NOT
```

### 5.4 String Concatenation

```utlx
"Hello, " + name
"Order: " + orderId
```

---

## 6. Functions

### 6.1 Higher-Order Functions

#### Map

```utlx
input.items |> map(item => item.price * 1.1)
```

#### Filter

```utlx
input.items |> filter(item => item.price > 100)
```

#### Reduce

```utlx
input.items |> reduce((acc, item) => acc + item.price, 0)
```

### 6.2 Aggregation Functions

```utlx
sum(input.items.*.price)           // Sum of all prices
avg(input.items.*.quantity)        // Average quantity
min(input.items.*.price)           // Minimum price
max(input.items.*.price)           // Maximum price
count(input.items)                 // Count items
```

### 6.3 String Functions

```utlx
upper(str)                         // Convert to uppercase
lower(str)                         // Convert to lowercase
trim(str)                          // Remove whitespace
substring(str, start, end)         // Extract substring
concat(str1, str2, ...)            // Concatenate strings
split(str, delimiter)              // Split string
join(array, delimiter)             // Join array elements
```

### 6.4 Array Functions

```utlx
first(array)                       // First element
last(array)                        // Last element
take(array, n)                     // First n elements
drop(array, n)                     // Skip first n elements
reverse(array)                     // Reverse array
sort(array)                        // Sort array
sortBy(array, fn)                  // Sort by function
distinct(array)                    // Remove duplicates
```

### 6.5 Type Functions

```utlx
typeOf(value)                      // Get type as string
isString(value)                    // Check if string
isNumber(value)                    // Check if number
isBoolean(value)                   // Check if boolean
isArray(value)                     // Check if array
isObject(value)                    // Check if object
isNull(value)                      // Check if null
```

---

## 7. Control Flow

### 7.1 Conditional Expressions

#### If-Else

```utlx
if (condition) expression1 else expression2
```

**Example:**
```utlx
{
  discount: if (total > 1000) total * 0.1 else 0
}
```

#### Multi-way Conditionals

```utlx
if (score >= 90) "A"
else if (score >= 80) "B"
else if (score >= 70) "C"
else "F"
```

### 7.2 Pattern Matching

```utlx
match expression {
  pattern1 => result1,
  pattern2 => result2,
  _ => default_result
}
```

**Example:**
```utlx
match orderType {
  "express" => {
    shipping: 15.00,
    processing: "24 hours"
  },
  "standard" => {
    shipping: 5.00,
    processing: "3-5 days"
  },
  _ => {
    shipping: 0,
    processing: "unknown"
  }
}
```

---

## 8. Template Matching

### 8.1 Template Definition

XSLT-inspired declarative templates:

```utlx
template match="<pattern>" {
  <transformation>
}
```

**Example:**
```utlx
template match="Order" {
  invoice: {
    id: @id,
    total: sum(Items/Item/(@price * @quantity))
  }
}
```

### 8.2 Template Application

```utlx
apply(<selector>)
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
  quantity: @quantity
}
```

### 8.3 Template Priority

When multiple templates match, the most specific wins:

```utlx
template match="Item" {
  // General item handling
}

template match="Item[@special=true]" {
  // Special item handling (higher priority)
}
```

---

## 9. Variable Bindings

### 9.1 Let Expressions

```utlx
let name = expression
```

**Example:**
```utlx
{
  let subtotal = sum(items.*.price),
  let tax = subtotal * 0.08,
  let total = subtotal + tax
  
  invoice: {
    subtotal: subtotal,
    tax: tax,
    total: total
  }
}
```

### 9.2 Scoping

Variables are lexically scoped:

```utlx
{
  let x = 10,
  
  outer: {
    let y = 20,
    sum: x + y        // x is accessible here
  },
  
  value: x            // y is NOT accessible here
}
```

---

## 10. User-Defined Functions

### 10.1 Function Definition

```utlx
function name(param1: Type1, param2: Type2): ReturnType {
  <expression>
}
```

**Example:**
```utlx
function calculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

function formatCurrency(value: Number): String {
  "$" + value.toFixed(2)
}
```

### 10.2 Function Usage

```utlx
{
  subtotal: 100,
  tax: calculateTax(100, 0.08),
  display: formatCurrency(108.00)
}
```

---

## 11. Format-Specific Features

### 11.1 XML Namespaces

```utlx
input xml {
  namespaces: {
    "soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "m": "http://example.com/message"
  }
}
---
{
  body: input.{"soap:Envelope"}.{"soap:Body"}.{"m:Message"}
}
```

### 11.2 CSV Options

```utlx
input csv {
  headers: true,              // First row is headers
  delimiter: ",",             // Column separator
  quote: "\"",                // Quote character
  escape: "\\",               // Escape character
  skipEmptyLines: true        // Skip empty lines
}
```

### 11.3 JSON Schema Validation

```utlx
input json {
  schema: "path/to/schema.json",
  validate: true
}
```

---

## 12. Multiple Outputs

Generate multiple output files:

```utlx
output {
  summary: json,
  details: xml,
  report: csv
}
---

output.summary = {
  count: count(input.orders)
}

output.details = {
  Orders: input.orders |> map(order => {
    Order: {
      @id: order.id,
      Total: order.total
    }
  })
}

output.report = {
  headers: ["Order ID", "Total"],
  rows: input.orders |> map(order => [order.id, order.total])
}
```

---

## 13. Comments

```utlx
// Single-line comment

/*
 * Multi-line comment
 * Can span multiple lines
 */

{
  name: "Alice",  // End-of-line comment
  age: 30
}
```

---

## 14. Type System

### 14.1 Type Inference

UTL-X infers types automatically:

```utlx
let x = 42           // Inferred as Number
let name = "Alice"   // Inferred as String
let items = [1, 2]   // Inferred as Array<Number>
```

### 14.2 Type Annotations

Optional type annotations:

```utlx
let count: Number = 42
let name: String = "Alice"

function process(data: Array<Object>): Object {
  // ...
}
```

### 14.3 Type Checking

Type errors are caught at compile time:

```utlx
let x: Number = "hello"  // ERROR: Type mismatch
```

---

## 15. Error Handling

### 15.1 Try-Catch (Future)

```utlx
try {
  parseNumber(input.value)
} catch (e) {
  0  // Default value
}
```

### 15.2 Default Values

Use `||` operator for defaults:

```utlx
{
  name: input.customer.name || "Unknown",
  quantity: input.quantity || 0
}
```

---

## 16. Pipeline Operator

Chain transformations:

```utlx
input.items
  |> filter(item => item.price > 100)
  |> map(item => item.price * 0.9)
  |> sum()
```

---

## 17. Keywords

Reserved keywords:

```
and, apply, as, break, case, catch, const, continue, default,
do, else, false, finally, for, function, if, import, in,
input, let, match, module, null, or, output, return,
template, throw, true, try, typeof, var, when, while
```

---

## 18. Operators Precedence

From highest to lowest:

1. `.` (member access), `[]` (indexing)
2. `-` (unary minus), `!` (logical NOT)
3. `*`, `/`, `%`
4. `+`, `-`
5. `<`, `<=`, `>`, `>=`
6. `==`, `!=`
7. `&&`
8. `||`
9. `|>` (pipeline)
10. `=` (assignment)

---

## 19. Examples

### 19.1 Simple Transformation

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: input.Order.@id,
  customer: input.Order.Customer.Name,
  total: sum(input.Order.Items.Item.(@price * @quantity))
}
```

### 19.2 Template Matching

```utlx
%utlx 1.0
input xml
output json
---

template match="Order" {
  invoice: {
    id: @id,
    date: @date,
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
  price: @price,
  quantity: @quantity
}
```

### 19.3 Complex Transformation

```utlx
%utlx 1.0
input json
output json
---

{
  let expensiveItems = input.items |> filter(item => item.price > 1000),
  let avgPrice = avg(input.items.*.price),
  
  summary: {
    totalItems: count(input.items),
    averagePrice: avgPrice,
    expensiveItemCount: count(expensiveItems)
  },
  
  expensiveItems: expensiveItems |> map(item => {
    name: item.name,
    price: item.price,
    discount: item.price * 0.10
  }),
  
  categories: input.items 
    |> groupBy(item => item.category)
    |> map((category, items) => {
        name: category,
        count: count(items),
        total: sum(items.*.price)
      })
}
```

---

## 20. Grammar (ANTLR4 notation)

```antlr
// Simplified grammar

document
    : directive* '---' expression
    ;

directive
    : '%utlx' VERSION
    | 'input' FORMAT options?
    | 'output' FORMAT options?
    ;

expression
    : literal
    | identifier
    | selector
    | functionCall
    | ifExpression
    | matchExpression
    | letExpression
    | objectExpression
    | arrayExpression
    | expression binaryOp expression
    | unaryOp expression
    | expression '|>' expression
    ;

selector
    : 'input' ('.' identifier | '[' expression ']')*
    ;

functionCall
    : identifier '(' (expression (',' expression)*)? ')'
    ;

// ... (full grammar in separate file)
```

---

## 21. Conformance

### 21.1 Levels

- **Level 0:** Basic transformations, simple selectors
- **Level 1:** Functions, control flow, type system
- **Level 2:** Template matching, advanced features

### 21.2 Optional Features

- CSV support
- YAML support
- Custom format plugins
- Performance optimizations

---

## Appendices

### Appendix A: Built-in Functions Reference

See [Standard Library Reference](stdlib-reference.md)

### Appendix B: Format Specifications

See [Format Guides](../formats/)

### Appendix C: Migration Guides

See [Migration Guides](../comparison/migration-guides.md)

---

**Status:** This specification is a working draft and subject to change.

**Feedback:** Submit issues or suggestions at https://github.com/grauwen/utl-x/issues

**Last Updated:** January 2026  
**Version:** 1.0.0-SNAPSHOT
