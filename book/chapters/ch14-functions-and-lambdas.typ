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

For logic you want to name and reuse within a transformation, use `def`:

```utlx
def calculateTax(amount, rate) = amount * rate / 100

def formatCurrency(amount, currency) =
  concat(currency, " ", toString(round(amount * 100) / 100))

{
  subtotal: $input.total,
  tax: calculateTax($input.total, 21),
  formatted: formatCurrency($input.total * 1.21, "EUR")
}
```

=== Function Definitions Are Expressions

A `def` binds a name to a lambda. These are equivalent:

```utlx
// Named function
def double(x) = x * 2

// Lambda bound to a variable
let double = (x) -> x * 2
```

Both can be called as `double(5)` → `10`. The `def` form is more readable for named functions. The `let` form is useful when passing functions as arguments.

=== Recursive Functions

Functions can call themselves:

```utlx
def factorial(n) =
  if (n <= 1) 1
  else n * factorial(n - 1)

factorial(5)   // 120
```

Use recursion sparingly — deep recursion can exhaust the stack. For most data processing, `map`, `filter`, and `reduce` are better choices.

== Higher-Order Functions

Higher-order functions take other functions as arguments. UTL-X's standard library is built on this pattern. Here are the essential ones:

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

See Chapter 9 for how `groupBy` is used for flat-to-hierarchical transformation.

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

Note: this is the _string_ `join()` function (2 parameters: array + separator). For flat-to-hierarchical data restructuring (nesting children under parents by key), a separate function called `nestBy()` is proposed — see Chapter 9 and the F03 design document. The names are deliberately different to avoid confusion: `join()` joins strings, `nestBy()` nests data.

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

The next chapter provides an overview organized by category. The complete reference with signatures and examples is in Part VIII (Chapter 49).
