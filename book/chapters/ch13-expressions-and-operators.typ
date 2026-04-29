= Expressions and Operators

== Arithmetic Operators
// - +, -, *, /, % (modulo)
// - ** (exponentiation)
// - Unary minus: -x
// - Integer vs floating point behavior

== Comparison Operators
// - ==, !=, <, >, <=, >=
// - Type-aware comparison (string vs number)
// - Null comparisons

== Logical Operators
// - && (and), || (or), ! (not)
// - Short-circuit evaluation
// - Truthy/falsy values in UTL-X

== String Operators
// - Concatenation: concat("hello", " ", "world")
// - Template literals (if supported)
// - String interpolation patterns

== Object Construction
// - Literal: {key: value}
// - Computed keys: {[expr]: value}
// - Spread: {...base, override: "new"}
// - Conditional properties: if (condition) {extra: value} else {}

== Array Construction
// - Literal: [1, 2, 3]
// - Range (if supported)
// - Comprehensions via map/filter
// - Spread in arrays: [...arr1, ...arr2]

== Pipe Operator (|>)
// - Chaining operations: data |> map(fn) |> filter(fn) |> sort()
// - Readable data flow (left to right)
// - Equivalent to nested function calls

== Nullish Coalescing and Safe Navigation
// - ?? operator: value ?? "default"
// - ?. operator: obj?.deep?.path
// - Combining with defaults: $input.Order?.discount ?? 0
