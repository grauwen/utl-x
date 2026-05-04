= Functions and Lambdas

Functions are the building blocks of UTL-X transformations. Lambdas — anonymous inline functions — are what make `map`, `filter`, `reduce`, and the entire functional programming model work. This chapter covers both, from basic syntax to advanced composition patterns.

== Lambda Expressions

A lambda is an anonymous function — a piece of logic you pass to another function. If you've used arrow functions in JavaScript, closures in Kotlin, or lambdas in Python, you'll recognize the syntax immediately.

=== Basic Syntax

```utlx
(parameter) -> expression
```

The parameter is on the left, the arrow `->` separates it from the body, and the body is a single expression that produces the result. No `return` keyword — the expression IS the return value.

```utlx
// Double a number
(x) -> x * 2

// Extract a name
(user) -> user.name

// Format a greeting
(name) -> concat("Hello, ", name, "!")
```

UTL-X also accepts the fat arrow `=>` as an alternative to `->`. With `=>`, single-parameter lambdas can omit the parentheses:

```utlx
// These are all equivalent:
(x) -> x * 2
(x) => x * 2
x => x * 2          // fat arrow allows omitting parentheses for single parameter
```

This book uses `->` as the standard convention. The `=>` syntax is provided for developers coming from JavaScript/TypeScript.

=== Multiple Parameters

```utlx
(x, y) -> x + y
(item, index) -> {position: index, value: item}
(accumulator, current) -> accumulator + current.price
```

=== No Parameters

```utlx
() -> "constant value"
() -> now()
```

=== Lambdas in Action

Lambdas are rarely written on their own — they're passed to higher-order functions:

```utlx
// map: transform each element
map([1, 2, 3], (x) -> x * 2)
// [2, 4, 6]

// filter: keep elements that match
filter($input.users, (u) -> u.active == true)
// [{name: "Alice", active: true}, ...]

// reduce: accumulate a result
reduce([10, 20, 30], 0, (sum, x) -> sum + x)
// 60

// sortBy: order by a computed value
sortBy($input.products, (p) -> p.price)
// [{price: 9.99, ...}, {price: 29.99, ...}, ...]
```

== User-Defined Functions

For logic you want to name and reuse within a transformation, use `function`:

```utlx
function CalculateTax(amount, rate) {
  amount * rate / 100
}

function FormatCurrency(amount, currency) {
  concat(currency, " ", toString(round(amount * 100) / 100))
}

{
  subtotal: $input.total,
  tax: CalculateTax($input.total, 21),
  formatted: FormatCurrency($input.total * 1.21, "EUR")
}
```

=== The PascalCase Rule

User-defined function names *must* start with an uppercase letter (PascalCase). The parser enforces this:

```utlx
function CalculateTax(amount, rate) { amount * rate / 100 }   // ✓ valid
function calculateTax(amount, rate) { amount * rate / 100 }   // ✗ parser error
```

If you try `function calculateTax(...)`, the parser rejects it with: _"User-defined functions must start with uppercase letter (PascalCase). Got: 'calculateTax'. Try: 'CalculateTax'."_

Why? UTL-X has 652 built-in stdlib functions, all using camelCase: `map`, `filter`, `groupBy`, `parseDate`, `toNumber`. By requiring user-defined functions to start uppercase, there can never be a collision. When you see `CalculateTax(...)` in a transformation, you know immediately it's user-defined. When you see `parseDate(...)`, you know it's stdlib. No ambiguity, no shadowing, no surprises.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Name starts with*], [*What it is*], [*Example*],
  [lowercase], [Built-in stdlib function], [`map`, `filter`, `concat`, `parseDate`],
  [Uppercase], [User-defined function], [`CalculateTax`, `FormatPhone`, `ValidateVAT`],
)

The keyword `def` is accepted as a shorthand alias for `function`, but `function` is the standard keyword used throughout UTL-X.

=== Function Definitions Are Expressions

A `function` definition binds a name to a callable. These are equivalent:

```utlx
// Named function
function Double(x) { x * 2 }

// Lambda bound to a variable (no naming restriction — lambdas are values)
let double = (x) -> x * 2
```

Both can be called as `Double(5)` or `double(5)` → `10`. The `function` form is more readable for named functions and enforces PascalCase. The `let` form binds a lambda to a variable — variable names follow normal rules (no uppercase requirement).

=== Recursive Functions

Functions can call themselves:

```utlx
function Factorial(n) {
  if (n <= 1) 1
  else n * Factorial(n - 1)
}

Factorial(5)   // 120
```

Use recursion sparingly — deep recursion can exhaust the stack. For most data processing, `map`, `filter`, and `reduce` are better choices.

== Higher-Order Functions

A higher-order function is a function that takes another function as an argument. Instead of telling the function _what value_ to use, you tell it _what logic_ to apply — and it applies that logic to each element, each key, or each step.

If you come from XSLT, this concept doesn't exist there — XSLT uses `xsl:for-each` and template matching instead. If you come from SQL, think of `WHERE` as a filter and `SELECT` as a map — but written as functions you can compose. If you come from JavaScript, Python, or Kotlin, you already know this pattern.

The core idea: *data stays in the array, logic is passed in as a lambda.*

```utlx
// Traditional loop thinking: "go through each order, check if active, collect results"
// Higher-order thinking: "filter orders by active status"
filter($input.orders, (o) -> o.status == "ACTIVE")

// Traditional: "go through each item, calculate tax, build new list"
// Higher-order: "map items to items-with-tax"
map($input.items, (item) -> {
  name: item.name
  priceWithTax: item.price * 1.21
})
```

UTL-X's standard library includes these higher-order functions:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Function*], [*Lambda returns*], [*Result*], [*Use case*],
  [`map`], [new value], [transformed array], [Transform each element],
  [`filter`], [boolean], [subset of array], [Keep matching elements],
  [`find`], [boolean], [first match or null], [Find one element],
  [`findIndex`], [boolean], [index or -1], [Find position],
  [`reduce`], [new accumulator], [single value], [Sum, concatenate, aggregate],
  [`sortBy`], [comparable value], [sorted array], [Custom sort order],
  [`groupBy`], [key value], [object of groups], [Group by computed key],
  [`mapGroups`], [new value], [transformed groups], [Process each group],
  [`any`], [boolean], [boolean], [At least one matches?],
  [`all`], [boolean], [boolean], [Every element matches?],
  [`none`], [boolean], [boolean], [No element matches?],
  [`flatMap`], [array], [flattened array], [Map + flatten],
  [`mapKeys`], [new key], [transformed object], [Rename object keys],
  [`mapValues`], [new value], [transformed object], [Transform object values],
  [`mapEntries`], [new entry], [transformed object], [Transform both key and value],
  [`filterEntries`], [boolean], [subset of object], [Filter object properties],
  [`lookupBy`], [key value], [enriched array], [Enrich from reference data],
  [`nestBy`], [key value], [nested array], [Build parent-child hierarchy],
  [`chunkBy`], [group key], [chunked array], [Positional grouping],
)

Any user-defined function can also be higher-order — if it accepts a function parameter and calls it:

```utlx
// User-defined higher-order function
function ApplyToAll(items, transform) {
  map(items, (item) -> transform(item))
}

// Usage:
ApplyToAll($input.prices, (p) -> round(p * 1.21, 2))
```

This is standard functional programming — functions are values that can be passed around, stored in variables, and applied dynamically.

The essential higher-order functions are:

=== map — Transform Each Element

```utlx
map(array, (element) -> newValue)
```

Produces a new array where each element is transformed:

```utlx
map([1, 2, 3], (x) -> x * 2)
// [2, 4, 6]

map($input.employees, (emp) -> {
  fullName: concat(emp.firstName, " ", emp.lastName),
  salary: emp.salary
})
```

=== filter — Select Matching Elements

`filter` is one of the most-used functions — and one of the most misunderstood. It takes an array and a lambda that returns true or false for each element. Only elements where the lambda returns true are kept:

```utlx
filter(array, (element) -> booleanCondition)
```

==== Basic Examples

```utlx
// Keep orders over 100
filter($input.orders, (o) -> o.total > 100)

// Keep active admin users
filter($input.users, (u) -> u.role == "admin" && u.active)

// Keep non-null values
filter($input.items, (item) -> item != null)

// Keep strings containing "error"
filter($input.logLines, (line) -> contains(line, "error"))
```

==== Common Anti-Pattern: Array Predicate Syntax

If you've used XPath, jq, or JSONPath, you might try this:

```utlx
// WRONG — this does NOT work in UTL-X:
$input.items[price > 10]
$input.orders[status == "ACTIVE"]
$input.users[age >= 18]
```

UTL-X does NOT support predicate filtering inside bracket notation. Square brackets are for *index access only* — `$input.items[0]` gets the first element, `$input.items[2]` gets the third.

The correct UTL-X way:

```utlx
// CORRECT — use filter() with a lambda:
filter($input.items, (item) -> item.price > 10)
filter($input.orders, (o) -> o.status == "ACTIVE")
filter($input.users, (u) -> u.age >= 18)
```

Why doesn't UTL-X support `$input.items[price > 10]`? Because bracket notation serves one purpose (indexing), and adding predicate filtering would create ambiguity: is `items[x]` an index access (get element at position x) or a filter (keep elements where x is truthy)? UTL-X avoids this ambiguity by keeping indexing and filtering as separate, explicit operations.

==== Common Anti-Pattern: Filtering Then Counting

```utlx
// INEFFICIENT — filtering twice:
let activeUsers = filter($input.users, (u) -> u.active)
let activeCount = count(filter($input.users, (u) -> u.active))

// BETTER — filter once, count the result:
let activeUsers = filter($input.users, (u) -> u.active)
let activeCount = count(activeUsers)
```

Always bind the filter result to a `let` variable if you use it more than once.

==== Common Anti-Pattern: Filter + Map When You Need Both

```utlx
// COMMON MISTAKE — filtering and mapping separately, losing context:
let expensiveItems = filter($input.items, (i) -> i.price > 100)
let expensiveNames = map(expensiveItems, (i) -> i.name)
// Works, but you've lost the price information

// BETTER — map first (keeping what you need), then filter:
$input.items
  |> map((i) -> {name: i.name, price: i.price, category: i.category})
  |> filter((i) -> i.price > 100)

// OR — filter first, then map (more efficient if most items are filtered out):
$input.items
  |> filter((i) -> i.price > 100)
  |> map((i) -> {name: i.name, price: i.price, category: i.category})
```

The order (filter-then-map vs map-then-filter) depends on your goal:
- Filter first when you want to *reduce the dataset* before transforming (fewer items to map)
- Map first when the *filter condition depends on the transformed data*

==== Multi-Condition Filters

Combine conditions with `&&` (and) and `||` (or):

```utlx
// AND: all conditions must be true
filter($input.products, (p) ->
  p.inStock && p.price < 50 && p.category == "Electronics"
)

// OR: any condition can be true
filter($input.events, (e) ->
  e.severity == "ERROR" || e.severity == "CRITICAL"
)

// Complex: combine AND and OR with parentheses
filter($input.orders, (o) ->
  o.status == "PENDING" && (o.total > 1000 || o.priority == "HIGH")
)
```

==== Negation: Exclude Instead of Include

```utlx
// Keep everything EXCEPT cancelled orders
filter($input.orders, (o) -> o.status != "CANCELLED")

// Remove null/empty values
filter($input.tags, (tag) -> tag != null && tag != "")

// Exclude a list of values
let excluded = ["DRAFT", "CANCELLED", "ARCHIVED"]
filter($input.orders, (o) -> !contains(excluded, o.status))
```

==== Filter with Safe Navigation

When the filter property might not exist on every element:

```utlx
// WRONG — crashes if an element doesn't have "address"
filter($input.customers, (c) -> c.address.country == "NL")

// CORRECT — safe navigation handles missing properties
filter($input.customers, (c) -> c.address?.country == "NL")

// ALSO CORRECT — explicit null check
filter($input.customers, (c) -> c.address != null && c.address.country == "NL")
```

==== Filter Returns an Array (Always)

`filter` always returns an array — even if zero or one elements match:

```utlx
let matches = filter($input.items, (i) -> i.id == "X-001")
// matches is [] if nothing found
// matches is [{id: "X-001", ...}] if one found
// matches is [{...}, {...}] if multiple found (duplicates)

// If you want the FIRST match (not an array), use find():
let match = find($input.items, (i) -> i.id == "X-001")
// match is the object directly, or null if not found
```

This is a common confusion: `filter` returns `[item]` (array of one), `find` returns `item` (the object itself or null). Use `filter` when you expect multiple results, `find` when you expect one.

==== Lambda Return Types: Objects vs Bare Expressions

A subtle but important distinction: lambdas that return *objects* behave differently from lambdas that return *bare expressions*. This matters for `filter`, `find`, `sortBy`, and `any`/`all` — functions that expect a boolean or comparable value from the lambda.

```utlx
// WRONG — returns an object, not a boolean:
filter($input.items, (x) -> {
  let threshold = 100
  result: x.price > threshold
})
// This does NOT filter! The lambda returns {"result": true} — an object.
// Any non-null object is truthy, so ALL items pass the filter.

// CORRECT — return a bare expression:
filter($input.items, (x) -> x.price > 100)

// CORRECT — with let, use let...in for bare expression return:
filter($input.items, (x) -> (let threshold = 100 in x.price > threshold))
```

The rule is simple:
- *`map`* — lambda returns an object → use `{ }` with properties (this is the common case)
- *`filter`*, *`find`*, *`any`*, *`all`* — lambda returns a boolean → use a bare expression, NOT `{ }`
- *`sortBy`* — lambda returns a comparable value → use a bare expression
- *`reduce`* — lambda returns the new accumulator → can be either, depending on what you accumulate

When you need `let` bindings in a `filter` or `sortBy` lambda, use the `let...in` form which returns a bare value:

```utlx
// let...in returns the expression after "in" — not an object
filter($input.orders, (o) -> (
  let minDate = parseDate("2026-01-01", "yyyy-MM-dd")
  in isAfter(parseDate(o.date, "yyyy-MM-dd"), minDate)
))

sortBy($input.items, (item) -> (
  let score = item.rating * item.reviews
  in -score
))
```

=== reduce — Accumulate a Result

```utlx
reduce(array, initialValue, (accumulator, element) -> newAccumulator)
```

Combines all elements into a single value:

```utlx
// Sum
reduce([10, 20, 30], 0, (sum, x) -> sum + x)
// 60

// Build a comma-separated string
reduce(["Alice", "Bob", "Charlie"], "", (acc, name) ->
  if (acc == "") name else concat(acc, ", ", name)
)
// "Alice, Bob, Charlie"

// Find maximum
reduce($input.scores, 0, (max, s) -> if (s > max) s else max)
```

==== MapReduce in UTL-X

If you come from Hadoop, Spark, or streaming architectures, you know the MapReduce pattern: first *map* each element independently (parallelizable), then *reduce* the results into a single aggregate. UTL-X does not have a dedicated `mapReduce()` function — because `map()` piped into `reduce()` already IS mapReduce:

```utlx
// MapReduce: compute total revenue from orders
$input.orders
  |> map((o) -> o.quantity * o.unitPrice)     // map phase: extract line totals
  |> reduce(0, (acc, lineTotal) -> acc + lineTotal)  // reduce phase: sum them
// Output: 4250.00
```

The pipe operator (`|>`) makes the two phases read naturally as a pipeline — map first, then reduce. This is the same data flow as a Hadoop job, a Spark RDD chain, or a Kafka Streams topology, but expressed in a single UTL-X expression.

For common aggregations, UTL-X provides pre-built map+reduce combinations that are shorter and more readable:

```utlx
// These are all mapReduce under the hood:
sumBy($input.orders, (o) -> o.quantity * o.unitPrice)   // sum
avgBy($input.orders, (o) -> o.total)                    // average
maxBy($input.orders, (o) -> o.total)                    // max (returns the object)
countBy($input.orders, (o) -> o.status == "SHIPPED")    // conditional count
```

When the built-in aggregations are not enough — when you need a custom accumulator shape — use the full `map |> reduce` pattern:

```utlx
// Custom aggregation: group totals by currency
$input.orders |> reduce({}, (acc, order) -> {
  ...acc,
  [order.currency]: (acc[order.currency] ?? 0) + order.total
})
// Output: {"EUR": 3200, "USD": 1050, "GBP": 890}
```

=== find — First Match

```utlx
find(array, (element) -> condition)
```

Returns the first element where the lambda returns true, or null if no match:

```utlx
find($input.users, (u) -> u.email == "alice@example.com")
```

=== sortBy — Order by Computed Value

```utlx
sortBy(array, (element) -> sortKey)
```

Sorts by the value returned by the lambda:

```utlx
sortBy($input.employees, (e) -> e.lastName)     // alphabetical
sortBy($input.products, (p) -> p.price)          // cheapest first
sortBy($input.orders, (p) -> -p.total)           // most expensive first (negate)
```

=== groupBy — Group by Key

```utlx
groupBy(array, (element) -> groupKey)
```

Creates a map where keys are the lambda results and values are arrays of matching elements:

```utlx
groupBy($input.employees, (e) -> e.department)
// {"Engineering": [...], "Sales": [...], "Marketing": [...]}
```

See Chapter 10 for how `groupBy` is used for flat-to-hierarchical transformation.

== Function Composition

=== Pipe Chains

The pipe operator (`|>`) chains functions into readable data flows:

```utlx
$input.transactions
  |> filter((t) -> t.amount > 0)
  |> map((t) -> {
    date: t.date,
    amount: t.amount,
    category: t.category ?? "Uncategorized"
  })
  |> sortBy((t) -> t.date)
  |> map((t) -> concat(t.date, ": ", toString(t.amount), " (", t.category, ")"))
```

Read top to bottom: filter positive transactions, map to a clean structure, sort by date, format as strings.

=== Nested Functions

When pipes aren't suitable, functions nest naturally:

```utlx
join(
  sort(unique(map($input.items, (i) -> i.category))),
  ", "
)
// "Electronics, Furniture, Kitchen"
```

Read inside out: map to categories, remove duplicates, sort, join with commas.

Note: this is the _string_ `join()` function (2 parameters: array + separator). For flat-to-hierarchical data restructuring (nesting children under parents by key), a separate function called `nestBy()` is available — see Chapter 10 and Chapter 21 (Data Restructuring). The names are deliberately different to avoid confusion: `join()` joins strings, `nestBy()` nests data.

=== Combining map and filter

A very common pattern — filter first, then transform:

```utlx
$input.employees
  |> filter((e) -> e.active && e.department == "Engineering")
  |> map((e) -> {
    name: concat(e.firstName, " ", e.lastName),
    level: e.seniorityLevel,
    yearsOfService: dateDiff(now(), parseDate(e.startDate, "yyyy-MM-dd"), "years")
  })
```

Or transform first, then filter (when the filter condition depends on the transformation):

```utlx
$input.orders
  |> map((o) -> {
    ...o,
    lineTotal: sum(map(o.lines, (l) -> l.qty * l.price))
  })
  |> filter((o) -> o.lineTotal > 1000)
```

== Closures

Lambdas capture variables from their surrounding scope:

```utlx
let threshold = 100
let currency = $input.settings.currency

map($input.items, (item) -> {
  name: item.name,
  price: item.price,
  currency: currency,                          // captured from outer scope
  expensive: item.price > threshold             // captured from outer scope
})
```

The lambda "closes over" `threshold` and `currency` — it remembers their values even though they were defined outside the lambda. This is standard closure behavior, familiar from JavaScript, Kotlin, Python, and every functional language.

== Practical Patterns

=== Flatten and Restructure

```utlx
// Input: array of objects with nested arrays
// Output: flat array of all nested items with parent context

$input.departments
  |> map((dept) ->
    map(dept.employees, (emp) -> {
      department: dept.name,
      employee: emp.name,
      salary: emp.salary
    })
  )
  |> flatten()
```

=== Aggregate with Multiple Results

```utlx
let items = $input.order.items
{
  count: count(items),
  total: sum(map(items, (i) -> i.price * i.qty)),
  average: sum(map(items, (i) -> i.price)) / count(items),
  cheapest: sortBy(items, (i) -> i.price) |> first(),
  mostExpensive: sortBy(items, (i) -> -i.price) |> first(),
  categories: unique(map(items, (i) -> i.category))
}
```

=== Lookup Table Pattern

```utlx
let countryNames = {
  "NL": "Netherlands",
  "DE": "Germany",
  "FR": "France",
  "BE": "Belgium"
}

map($input.addresses, (addr) -> {
  ...addr,
  countryName: countryNames[addr.countryCode] ?? addr.countryCode
})
```

=== Conditional Transformation

```utlx
map($input.items, (item) ->
  if (item.type == "PRODUCT") {
    kind: "physical",
    name: item.name,
    weight: item.weight ?? 0,
    price: item.price
  } else if (item.type == "SERVICE") {
    kind: "digital",
    name: item.name,
    duration: item.hours ?? 1,
    rate: item.price
  } else {
    kind: "unknown",
    name: item.name,
    raw: item
  }
)
```

== The 652 Standard Library Functions

The functions shown in this chapter — `map`, `filter`, `reduce`, `find`, `sortBy`, `groupBy`, `concat`, `sum`, `count`, `unique`, `flatten` — are just the beginning. UTL-X includes 652 functions across 18 categories.

The next chapter provides an overview organized by category. The complete reference with signatures and examples is in Part VIII (Chapter 50).
