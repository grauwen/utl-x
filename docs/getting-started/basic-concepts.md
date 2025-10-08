# Basic Concepts

Understanding these core concepts will help you master UTL-X transformations.

---

## Table of Contents

- [Format Agnostic](#format-agnostic)
- [Universal Data Model](#universal-data-model)
- [Functional Programming](#functional-programming)
- [Declarative vs Imperative](#declarative-vs-imperative)
- [Selectors](#selectors)
- [Pipeline Operator](#pipeline-operator)
- [Immutability](#immutability)
- [Type System](#type-system)
- [Debugging](#debugging)

---

## Philosophy

UTL-X is built on three principles:

1. **Format Agnostic**: Write once, transform any format
2. **Functional**: Immutable data, pure functions, composable operations
3. **Declarative**: Describe what you want, not how to get it

## Format Agnostic

### What Does "Format Agnostic" Mean?

UTL-X uses the same transformation logic regardless of input/output formats.

**Example - Same Logic, Different Formats:**

```utlx
%utlx 1.0
input auto  // Auto-detect: XML, JSON, CSV, YAML
output json
---
{
  name: input.person.name,
  age: input.person.age
}
```

This works whether `input` is:
- XML: `<person><name>Alice</name><age>30</age></person>`
- JSON: `{"person": {"name": "Alice", "age": 30}}`
- YAML: `person:\n  name: Alice\n  age: 30`

**Key Benefit:** Write once, use with any format! 🎯

---

## Universal Data Model (UDM)

### The Bridge Between Formats

UTL-X converts all input formats into a **Universal Data Model** before transformation.

```
┌─────────┐
│  XML    │───┐
└─────────┘   │
              │    ┌─────────────┐    ┌──────────────┐    ┌─────────┐
┌─────────┐   ├───▶│     UDM     │───▶│ Transformation│───▶│ Output  │
│  JSON   │───┤    │ (Internal)  │    │   Logic       │    │ (JSON)  │
└─────────┘   │    └─────────────┘    └──────────────┘    └─────────┘
              │
┌─────────┐   │
│  CSV    │───┘
└─────────┘
```

### UDM Structure

The UDM represents data as:

**1. Scalars** (primitive values):
```
String: "Hello"
Number: 42
Boolean: true
Null: null
```

**2. Objects** (key-value pairs):
```
{
  name: "Alice",
  age: 30
}
```

**3. Arrays** (ordered lists):
```
[1, 2, 3]
["red", "green", "blue"]
```

**4. Attributes** (metadata):
```
element.@attribute  // XML attributes
object.property     // JSON properties
```

### Why UDM Matters

- ✅ **Consistency:** Same selector syntax for all formats
- ✅ **Simplicity:** Learn once, use everywhere
- ✅ **Flexibility:** Add new formats without changing transformations

---

## Functional Programming

### Pure Functions

UTL-X transformations are **pure functions**:
- Same input → Same output (always)
- No side effects (no global state changes)
- No mutations (original data unchanged)

**Example:**
```utlx
function double(x: Number): Number {
  x * 2  // Returns new value, doesn't modify x
}
```

### Higher-Order Functions

Functions that work with other functions:

```utlx
// map: applies function to each element
[1, 2, 3] |> map(x => x * 2)  // [2, 4, 6]

// filter: keeps elements matching condition
[1, 2, 3, 4] |> filter(x => x > 2)  // [3, 4]

// reduce: combines elements into single value
[1, 2, 3, 4] |> reduce((acc, x) => acc + x, 0)  // 10
```

### Composition

Chain operations together:

```utlx
input.numbers
  |> filter(x => x > 0)      // Keep positive
  |> map(x => x * 2)         // Double them
  |> sort()                  // Sort ascending
  |> take(5)                 // Take first 5
```

---

## Declarative vs Imperative

### Imperative Style (How to do it)

```javascript
// JavaScript - step by step instructions
let total = 0;
for (let i = 0; i < items.length; i++) {
  if (items[i].price > 10) {
    total += items[i].price;
  }
}
```

### Declarative Style (What you want)

```utlx
// UTL-X - describe the result
sum(items |> filter(item => item.price > 10) |> map(item => item.price))
```

**Benefits of Declarative:**
- ✅ More readable
- ✅ Easier to reason about
- ✅ Less error-prone
- ✅ Easier to optimize

---

## Selectors

### Path Navigation

Navigate through data structure:

```utlx
input.order.customer.name        // Deep path
input.order.items[0]             // Array index
input.order.items[*]             // All array elements
input.order.@id                  // Attribute
```

### Recursive Descent

Find elements at any depth:

```utlx
input..productCode  // Find 'productCode' anywhere in structure
```

### Predicates

Filter elements by condition:

```utlx
input.items[price > 100]                      // Price filter
input.orders[status == "pending"]             // Status filter
input.products[inStock == true && price < 50] // Multiple conditions
```

### Wildcards

Match any name:

```utlx
input.order.*           // All direct children
input.*.name            // All 'name' properties
```

---

## Pipeline Operator

### The `|>` Operator

Chains operations from left to right:

```utlx
input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

**Reads as:** 
1. Take `input.items`
2. Filter for active items
3. Extract prices
4. Sum them up

### Without Pipeline (Nested)

```utlx
sum(map(filter(input.items, item => item.active), item => item.price))
```

**With Pipeline (Readable):**

```utlx
input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

### Multiple Operations

```utlx
input.products
  |> filter(p => p.category == "Electronics")  // Step 1: Filter
  |> map(p => {                                // Step 2: Transform
      name: p.name,
      discountPrice: p.price * 0.9
    })
  |> sortBy(p => p.name)                       // Step 3: Sort
  |> take(10)                                  // Step 4: Limit
```

---

## Immutability

### Data Doesn't Change

Once created, data cannot be modified:

```utlx
let x = 10
x = 20  // ❌ ERROR: Cannot reassign

let items = [1, 2, 3]
items[0] = 99  // ❌ ERROR: Cannot mutate array
```

### Creating New Values

Instead of mutating, create new values:

```utlx
let x = 10
let y = x + 5  // y = 15, x still = 10

let items = [1, 2, 3]
let newItems = items |> map(x => x * 2)  // [2, 4, 6]
// items is still [1, 2, 3]
```

### Benefits

- ✅ **Predictable:** Data can't change unexpectedly
- ✅ **Debuggable:** Easy to trace data flow
- ✅ **Parallelizable:** Safe for concurrent operations
- ✅ **Testable:** Functions always return same result

---

## Type System

### Type Inference

UTL-X automatically infers types:

```utlx
let name = "Alice"      // Inferred as String
let age = 30            // Inferred as Number
let active = true       // Inferred as Boolean
let items = [1, 2, 3]   // Inferred as Array<Number>
```

### Type Annotations (Optional)

Explicitly specify types for clarity:

```utlx
let count: Number = 42
let message: String = "Hello"

function add(a: Number, b: Number): Number {
  a + b
}
```

### Type Checking

Types are checked at compile time:

```utlx
let x: Number = "hello"  // ❌ ERROR: Type mismatch
let y: String = 42       // ❌ ERROR: Type mismatch

function double(x: Number): Number {
  x * 2
}

double("five")  // ❌ ERROR: Expected Number, got String
```

### Built-in Types

```
String   - Text: "Hello"
Number   - Numbers: 42, 3.14
Boolean  - true or false
Null     - null value
Array<T> - List of values: [1, 2, 3]
Object   - Key-value pairs: {name: "Alice"}
```

---

## Expression-Based

### Everything Returns a Value

In UTL-X, everything is an expression that produces a value:

```utlx
// If-else is an expression
let discount = if (total > 100) 10 else 0

// Match is an expression
let shipping = match orderType {
  "express" => 15.00,
  "standard" => 5.00,
  _ => 0
}

// Even blocks return values
let result = {
  let x = 10,
  let y = 20,
  x + y  // Last expression is the result
}
```

### No "void" Functions

Every function must return something:

```utlx
function greet(name: String): String {
  "Hello, " + name  // Returns a string
}

// Not allowed:
function doNothing() {
  // ❌ ERROR: Must return a value
}
```

---

## Variables and Let Bindings

### The `let` Keyword

Declare variables within expressions:

```utlx
{
  let subtotal = 100,
  let tax = subtotal * 0.08,
  let total = subtotal + tax,
  
  result: {
    subtotal: subtotal,
    tax: tax,
    total: total
  }
}
```

### Scope

Variables are lexically scoped:

```utlx
{
  let x = 10,
  
  outer: {
    let y = 20,
    sum: x + y      // ✅ x is accessible
  },
  
  value: x,         // ✅ x is accessible
  wrong: y          // ❌ y is NOT accessible here
}
```

### Shadowing

Inner scopes can shadow outer variables:

```utlx
{
  let x = 10,
  
  nested: {
    let x = 20,     // Shadows outer x
    value: x        // 20 (inner x)
  },
  
  value: x          // 10 (outer x)
}
```

---

## Pattern Matching

### Match Expression

Similar to switch/case but more powerful:

```utlx
match orderType {
  "express" => {
    shipping: 15.00,
    delivery: "1-2 days"
  },
  "standard" => {
    shipping: 5.00,
    delivery: "3-5 days"
  },
  "economy" => {
    shipping: 2.50,
    delivery: "7-10 days"
  },
  _ => {
    shipping: 0,
    delivery: "unknown"
  }
}
```

### Destructuring (Future Feature)

```utlx
match value {
  {type: "order", id: orderId} => processOrder(orderId),
  {type: "refund", amount: amt} => processRefund(amt),
  _ => handleUnknown()
}
```

---

## Comments

### Single-Line Comments

```utlx
// This is a single-line comment
let x = 10  // End-of-line comment
```

### Multi-Line Comments

```utlx
/*
 * This is a multi-line comment
 * Useful for longer explanations
 * or temporarily disabling code
 */
let result = calculate()
```

### Documentation Comments

```utlx
/**
 * Calculates the total price including tax.
 * @param subtotal - The pre-tax amount
 * @param taxRate - Tax rate as decimal (e.g., 0.08 for 8%)
 * @return Total amount with tax
 */
function calculateTotal(subtotal: Number, taxRate: Number): Number {
  subtotal * (1 + taxRate)
}
```

---

## Debugging

### Using Console Output

Print intermediate values:

```utlx
{
  let items = input.items,
  
  // Debug: print items
  _debug: items,
  
  result: items |> map(item => item.price)
}
```

### Breaking Down Complex Expressions

Split into steps:

```utlx
// Instead of this (hard to debug):
result: sum(input.items |> filter(i => i.active) |> map(i => i.price))

// Do this (easy to debug):
{
  let allItems = input.items,
  let activeItems = allItems |> filter(i => i.active),
  let prices = activeItems |> map(i => i.price),
  let total = sum(prices),
  
  result: total
}
```

### Common Debugging Techniques

**1. Check data structure:**
```utlx
{
  // See what your input looks like
  _inputStructure: input,
  
  // Your transformation
  result: transform(input)
}
```

**2. Test predicates:**
```utlx
{
  // Check if filter works
  _filteredCount: count(input.items |> filter(i => i.price > 100)),
  
  // Your actual result
  result: input.items |> filter(i => i.price > 100)
}
```

**3. Validate paths:**
```utlx
{
  // Make sure path exists
  _hasCustomer: input.order.customer != null,
  _customerName: input.order.customer.name || "NOT FOUND",
  
  // Your transformation
  result: input.order.customer
}
```

---

## Performance Considerations

### Lazy Evaluation

UTL-X evaluates expressions lazily when possible:

```utlx
// Only processes items until condition is met
first(input.items |> filter(item => item.id == targetId))
```

### Avoid Repeated Computations

Use `let` to compute once:

```utlx
// ❌ Bad: computes sum multiple times
{
  subtotal: sum(input.items.*.price),
  tax: sum(input.items.*.price) * 0.08,
  total: sum(input.items.*.price) * 1.08
}

// ✅ Good: computes sum once
{
  let subtotal = sum(input.items.*.price),
  
  subtotal: subtotal,
  tax: subtotal * 0.08,
  total: subtotal * 1.08
}
```

### Pipeline Optimization

The compiler optimizes pipeline operations:

```utlx
// This:
input.items 
  |> filter(i => i.active)
  |> map(i => i.price)
  |> sum()

// Is optimized to a single pass
// (no intermediate arrays created)
```

---

## Best Practices

### 1. Use Descriptive Names

```utlx
// ❌ Bad
let x = input.o.c.n

// ✅ Good
let customerName = input.order.customer.name
```

### 2. Keep Expressions Simple

```utlx
// ❌ Bad: too complex
result: sum(input.items |> filter(i => i.active && i.price > 10) |> map(i => i.price * i.quantity * (1 - i.discount)))

// ✅ Good: broken into steps
{
  let eligibleItems = input.items |> filter(i => i.active && i.price > 10),
  let itemTotals = eligibleItems |> map(i => i.price * i.quantity * (1 - i.discount)),
  
  result: sum(itemTotals)
}
```

### 3. Provide Default Values

```utlx
// ❌ Bad: may fail if missing
name: input.customer.name

// ✅ Good: handles missing data
name: input.customer.name || "Unknown"
```

### 4. Use Type Annotations for Functions

```utlx
// ❌ Bad: unclear what types are expected
function calculate(x, y) { x + y }

// ✅ Good: clear interface
function calculate(x: Number, y: Number): Number {
  x + y
}
```

---

## Summary

Key concepts you learned:

- ✅ **Format Agnostic:** Same logic works with XML, JSON, CSV, YAML
- ✅ **Universal Data Model:** Internal representation bridging all formats
- ✅ **Functional Programming:** Pure functions, immutability, composition
- ✅ **Declarative Style:** Describe what you want, not how to do it
- ✅ **Selectors:** Navigate data with paths, predicates, wildcards
- ✅ **Pipeline Operator:** Chain operations left-to-right
- ✅ **Immutability:** Data never changes, create new values instead
- ✅ **Type System:** Strong typing with inference
- ✅ **Expression-Based:** Everything returns a value

---

## Next Steps

Now that you understand the core concepts:

1. **Practice:** Try the [examples](../examples/)
2. **Deep dive:** Read the [Language Guide](../language-guide/overview.md)
3. **Reference:** Bookmark the [Quick Reference](quick-reference.md)
4. **Experiment:** Create your own transformations!

---

## Questions?

- 💬 [Ask in Discussions](https://github.com/grauwen/utl-x/discussions)
- 📖 [Read the FAQ](../community/faq.md)
- 📧 [Email us](mailto:community@glomidco.com)
