= Language Fundamentals

== Scalar Types
// - Strings: "hello", 'hello', template literals
// - Numbers: integers (42), decimals (3.14), scientific (1.5e10)
// - Booleans: true, false
// - Null: null
// - Type coercion: when and how UTL-X converts between types

== Objects
// - Object literals: {name: "Alice", age: 30}
// - Property access: obj.name, obj["name"]
// - Nested access: $input.Order.Customer.Name
// - Computed property names: {[dynamicKey]: value}
// - Spread operator: {...obj1, ...obj2}

== Arrays
// - Array literals: [1, 2, 3]
// - Index access: arr[0], arr[-1]
// - Array methods: map, filter, reduce, find, sort, unique
// - Array-friendly functions: flatten, zip, groupBy

== The \$input Variable
// - The parsed input data
// - Always a UDM tree (regardless of input format)
// - $input.path.to.property — dot notation access
// - $input[0] — array index access
// - $input.@attribute — XML attribute access
// - $input..recursiveSearch — recursive descent

== Variable Binding (let)
// - let x = expression
// - Immutable by default
// - Scope: visible in the expression that follows
// - Chaining: let a = 1, let b = a + 1, {result: b}

== Conditional Expressions
// - if/else: if (condition) expr1 else expr2
// - Ternary: condition ? expr1 : expr2
// - Nullish coalescing: value ?? default
// - Safe navigation: obj?.property

== Comments
// - Single line: // comment
// - No multi-line comments (by design — keeps scripts scannable)

== Type Annotations (Optional)
// - let x: String = "hello"
// - Function parameters: (x: Number) -> x * 2
// - Not enforced at runtime (documentation only, future type checking)
