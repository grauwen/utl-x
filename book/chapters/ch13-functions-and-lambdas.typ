= Functions and Lambdas

== Lambda Expressions
// - Syntax: (x) -> x * 2
// - Multi-parameter: (x, y) -> x + y
// - No parameters: () -> "constant"
// - Implicit return (last expression)

== User-Defined Functions
// - def keyword: def double(x) = x * 2
// - Function definitions in transformation body
// - Scope and visibility
// - Recursive functions

== Higher-Order Functions
// - map: transform each element
// - filter: select elements by condition
// - reduce: accumulate a result
// - find: first matching element
// - sort / sortBy: ordering
// - groupBy: partition into groups

== Function Composition
// - Chaining with pipe: data |> map(fn1) |> filter(fn2)
// - Nested calls: filter(map(data, fn1), fn2)
// - When to use pipe vs nesting

== Closures
// - Lambdas capture surrounding variables
// - let bindings visible inside lambdas
// - Common pattern: let threshold = 100; filter(items, (i) -> i.price > threshold)

== The Standard Library (Overview)
// - 652 functions across 18 categories
// - Naming convention: camelCase (upperCase, toLowerCase, parseDate)
// - Category overview: Core, String, Array, Math, Date, Type, Encoding, XML, JSON, CSV, YAML, etc.
// - Full reference in Chapter 22
