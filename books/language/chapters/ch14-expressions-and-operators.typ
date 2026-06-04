= Expressions and Operators

UTL-X is an expression-based language — everything produces a value. There are no statements, no void functions, no side effects. A transformation body is one expression (possibly with `let` bindings) that evaluates to the output.

This chapter covers all operators, their precedence, and practical patterns. If you've used JavaScript, Kotlin, or any C-family language, most operators will be immediately familiar.

== Arithmetic Operators

Standard arithmetic on numbers:

```utlx
10 + 3       // 13       addition
10 - 3       // 7        subtraction
10 * 3       // 30       multiplication
10 / 3       // 3.333... division (always floating point)
10 % 3       // 1        modulo (remainder)
2 ** 10      // 1024     exponentiation
-42          // -42      unary minus
```

Division always produces a floating-point result — `10 / 3` is `3.333...`, not `3`. There is no integer division operator. Use `floor(10 / 3)` if you need integer results.

Division by zero throws a runtime error. Use a guard if the divisor might be zero:

```utlx
if (count > 0) total / count else 0
```

== Comparison Operators

Compare values and produce booleans:

```utlx
10 == 10     // true     equality
10 != 5      // true     inequality
10 > 5       // true     greater than
10 < 5       // false    less than
10 >= 10     // true     greater or equal
10 <= 5      // false    less or equal
```

String comparison is lexicographic (alphabetical):

```utlx
"apple" < "banana"    // true
"Alice" == "alice"    // false (case-sensitive)
```

Null comparisons:

```utlx
null == null   // true
null != "abc"  // true
null == 0      // false (null is not zero)
```

== Logical Operators

Combine boolean expressions:

```utlx
true && false    // false    logical AND
true || false    // true     logical OR
!true            // false    logical NOT
```

Short-circuit evaluation: `&&` stops at the first `false`, `||` stops at the first `true`. This is useful for guarding expressions:

```utlx
// Safe: second expression only evaluates if items is not null
$input.items != null && count($input.items) > 0
```

=== Truthy and Falsy Values

In boolean contexts, UTL-X treats these as _falsy_:

- `false`
- `null`
- `0`
- `""` (empty string)

Everything else is _truthy_, including empty arrays `[]` and empty objects `{}`.

== String Concatenation

UTL-X does not use `+` for string concatenation — use the `concat()` function:

```utlx
concat("Hello", " ", "World")     // "Hello World"
concat($input.first, " ", $input.last)
```

This is deliberate. `+` is arithmetic only. Mixing `+` for both numbers and strings leads to ambiguity (`"3" + 4` — is it `"34"` or `7`?). UTL-X avoids this by keeping arithmetic and string operations separate.

For complex string building, chain multiple `concat()` calls or use `join()`:

```utlx
join([first, middle, last], " ")   // "Alice M. Johnson"
```

== Object Construction

Objects are created with curly braces. This is both the most common expression and the most powerful:

```utlx
// Simple object
{name: "Alice", age: 30}

// Nested objects
{
  customer: {name: "Alice", city: "Amsterdam"},
  order: {total: 299.99, currency: "EUR"}
}

// Computed keys
let field = "fullName"
{[field]: concat($input.first, " ", $input.last)}
// produces: {"fullName": "Alice Johnson"}

// Spread: merge objects
let defaults = {currency: "EUR", country: "NL"}
{...defaults, name: $input.name, total: $input.total}
// produces: {currency: "EUR", country: "NL", name: "...", total: ...}
```

=== Conditional Properties

Include properties only when a condition is true by spreading a conditional object:

```utlx
{
  name: $input.name,
  ...if ($input.discount != null) {discount: $input.discount} else {}
}
```

Or use the nullish coalescing pattern to provide defaults:

```utlx
{
  name: $input.name,
  status: $input.status ?? "NEW",
  priority: $input.priority ?? "NORMAL"
}
```

== Array Construction

Arrays are created with square brackets and processed with higher-order functions:

```utlx
// Literal arrays
[1, 2, 3]
["Alice", "Bob", "Charlie"]

// Array from transformation
map($input.items, (item) -> item.name)
// produces: ["Widget", "Gadget", "Gizmo"]

// Combining arrays with spread
let a = [1, 2, 3]
let b = [4, 5, 6]
[...a, ...b]
// produces: [1, 2, 3, 4, 5, 6]

// Filtering
filter($input.items, (item) -> item.price > 100)
```

== The Pipe Operator (|>)

The pipe operator passes the result of the left expression as the first argument to the right function. It makes data flow readable — left to right instead of nested inside-out:

```utlx
// Without pipe — read from inside out
sort(unique(map($input.items, (item) -> item.category)))

// With pipe — read left to right
$input.items
  |> map((item) -> item.category)
  |> unique()
  |> sort()
```

Both produce the same result: a sorted list of unique categories. But the pipe version reads like a recipe: "take items, map to categories, remove duplicates, sort."

Pipes chain naturally for multi-step processing:

```utlx
$input.orders
  |> filter((o) -> o.status == "ACTIVE")
  |> map((o) -> {id: o.id, total: o.total})
  |> sortBy((o) -> o.total)
  |> map((o) -> concat(o.id, ": ", toString(o.total)))
```

== Nullish Coalescing (??)

Provide a default value when an expression is null:

```utlx
$input.nickname ?? $input.name ?? "Anonymous"
```

Evaluates left to right — the first non-null value wins. Essential for handling optional fields:

```utlx
{
  currency: $input.currency ?? "EUR",
  country: $input.address?.country ?? "NL",
  discount: $input.promoCode ?? "NONE"
}
```

== Safe Navigation (?.)

Access properties on potentially null values without throwing errors:

```utlx
$input.order?.customer?.address?.city
// Returns null if any step is null — no error
```

Without `?.`, a null intermediate value causes a runtime error. With `?.`, null propagates silently.

Combine with `??` for defaults:

```utlx
$input.order?.customer?.name ?? "Unknown Customer"
```

== Operator Precedence

From highest to lowest precedence:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Precedence*], [*Operators*], [*Associativity*],
  [1 (highest)], [Unary: `!`, `-`], [Right],
  [2], [Exponentiation: `**`], [Right],
  [3], [Multiplicative: `*`, `/`, `%`], [Left],
  [4], [Additive: `+`, `-`], [Left],
  [5], [Comparison: `<`, `>`, `<=`, `>=`], [Left],
  [6], [Equality: `==`, `!=`], [Left],
  [7], [Logical AND: `&&`], [Left],
  [8], [Logical OR: `||`], [Left],
  [9], [Nullish coalescing: `??`], [Left],
  [10], [Ternary: `? :`], [Right],
  [11], [Pipe: `|>`], [Left],
  [12 (lowest)], [Conditional: `if/else`], [Right],
)

When in doubt, use parentheses. They make intent explicit and prevent precedence surprises:

```utlx
// Ambiguous: does && bind tighter than ||?
a || b && c        // means: a || (b && c) — && is higher

// Clear: parentheses make intent obvious
(a || b) && c      // different meaning, explicit
```

== Practical Expression Patterns

=== Price Calculation

```utlx
let qty = toNumber($input.quantity)
let unit = toNumber($input.unitPrice)
let discount = toNumber($input.discount ?? "0") / 100
let net = qty * unit * (1 - discount)
let vat = net * 0.21
{
  net: round(net * 100) / 100,
  vat: round(vat * 100) / 100,
  gross: round((net + vat) * 100) / 100
}
```

=== Conditional Formatting

```utlx
{
  status: if ($input.paid && $input.shipped) "COMPLETE"
          else if ($input.paid) "AWAITING_SHIPMENT"
          else "PENDING_PAYMENT",

  urgency: if ($input.daysOverdue > 30) "CRITICAL"
           else if ($input.daysOverdue > 7) "WARNING"
           else "NORMAL",

  label: concat(
    $input.firstName ?? "",
    if ($input.middleName != null) concat(" ", $input.middleName) else "",
    " ",
    $input.lastName ?? ""
  )
}
```

=== Data Normalization

```utlx
{
  email: lowerCase(trim($input.email ?? "")),
  phone: replace(replace($input.phone ?? "", " ", ""), "-", ""),
  country: upperCase($input.countryCode ?? "NL"),
  amount: abs(toNumber($input.amount ?? "0"))
}
```

These patterns compose naturally — every sub-expression produces a value, and values flow through operators and functions without intermediate variables (unless you want readability via `let`).

== Complete Operator Reference

All UTL-X operators grouped by category:

=== Arithmetic

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`+`], [Addition], [`10 + 3`], [`13`],
  [`-`], [Subtraction], [`10 - 3`], [`7`],
  [`*`], [Multiplication], [`10 * 3`], [`30`],
  [`/`], [Division (always float)], [`10 / 3`], [`3.333...`],
  [`%`], [Modulo (remainder)], [`10 % 3`], [`1`],
  [`**`], [Exponentiation], [`2 ** 10`], [`1024`],
  [`-` (prefix)], [Unary negation], [`-42`], [`-42`],
)

=== Comparison

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`==`], [Equality], [`10 == 10`], [`true`],
  [`!=`], [Inequality], [`10 != 5`], [`true`],
  [`<`], [Less than], [`3 < 5`], [`true`],
  [`<=`], [Less or equal], [`5 <= 5`], [`true`],
  [`>`], [Greater than], [`10 > 5`], [`true`],
  [`>=`], [Greater or equal], [`10 >= 10`], [`true`],
)

=== Logical

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`&&`], [Logical AND (short-circuit)], [`true && false`], [`false`],
  [`\|\|`], [Logical OR (short-circuit)], [`true \|\| false`], [`true`],
  [`!`], [Logical NOT], [`!true`], [`false`],
)

=== Null Handling

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`??`], [Nullish coalescing], [`null ?? "default"`], [`"default"`],
  [`?.`], [Safe navigation], [`null?.name`], [`null`],
)

=== Access and Navigation

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Reference*],
  [`.`], [Property access], [`obj.name`], [Chapter 8],
  [`[]`], [Index / dynamic key access], [`arr[0]`, `obj[key]`], [Chapter 8],
  [`.*`], [Wildcard (all children)], [`obj.*`], [Chapter 8],
  [`.@`], [XML attribute access], [`elem.@id`], [Chapter 8],
  [`.@*`], [All attributes], [`elem.@*`], [Chapter 8],
  [`.^`], [Metadata access], [`elem.^xsdPattern`], [Chapter 8],
)

=== Data Construction

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Reference*],
  [`...`], [Spread (objects and arrays)], [`{...obj}`, `[...arr]`], [Chapter 8],
  [`[expr]:`], [Computed property name], [`{[key]: value}`], [Chapter 8],
  [`:`], [Property definition], [`{name: "Alice"}`], [],
)

=== Function and Flow

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Reference*],
  [`->`], [Lambda arrow (standard)], [`(x) -> x * 2`], [Chapter 15],
  [`=>`], [Lambda arrow (fat arrow)], [`x => x * 2`], [Chapter 15],
  [`\|>`], [Pipe], [`arr \|> filter(...)`], [This chapter],
  [`=`], [Binding (in `let`)], [`let x = 42`], [Chapter 8],
)

=== Reserved (Not Yet Implemented)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Operator*], [*Name*], [*Status*],
  [`..`], [Recursive descent], [Token defined, parser not implemented],
)
