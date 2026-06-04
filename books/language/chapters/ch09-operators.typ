= Operators

Operators are the building blocks of every UTL-X expression. This chapter covers all 32 operators — arithmetic, comparison, logical, null handling, access, construction, and flow — with examples and precedence rules.

== Arithmetic Operators

Standard arithmetic on numbers:

```utlx
10 + 3       // 13       addition
10 - 3       // 7        subtraction
10 * 3       // 30       multiplication
10 / 3       // 3.333... division (always floating point)
10 % 3       // 1        modulo (remainder)
2 ** 10      // 1024     exponentiation
-42          // -42      unary minus (negation)
```

Division always produces a floating-point result — `10 / 3` is `3.333...`, not `3`. There is no integer division operator. Use `floor(10 / 3)` if you need integer results.

Division by zero throws a runtime error. Guard if the divisor might be zero:

```utlx
if (count > 0) total / count else 0
```

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

String comparison is lexicographic (alphabetical) and case-sensitive:

```utlx
"apple" < "banana"    // true
"Alice" == "alice"    // false (case-sensitive)
"10" < "9"            // true (string comparison: "1" < "9")
```

Null comparisons:

```utlx
null == null   // true
null != "abc"  // true
null == 0      // false (null is not zero)
null == ""     // false (null is not empty string)
```

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

== Logical Operators

Combine boolean expressions:

```utlx
true && false    // false    logical AND
true || false    // true     logical OR
!true            // false    logical NOT
```

*Short-circuit evaluation:* `&&` stops at the first `false`, `||` stops at the first `true`. This is useful for guarding expressions:

```utlx
// Safe: count() only evaluates if items is not null
$input.items != null && count($input.items) > 0
```

=== Truthy and Falsy Values

In boolean contexts, UTL-X treats these as _falsy_:

- `false`
- `null`
- `0`
- `""` (empty string)

Everything else is _truthy_, including empty arrays `[]` and empty objects `{}`.

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`&&`], [Logical AND (short-circuit)], [`true && false`], [`false`],
  [`||`], [Logical OR (short-circuit)], [`true || false`], [`true`],
  [`!`], [Logical NOT], [`!true`], [`false`],
)

== Null Handling Operators

Two operators for dealing with null values — the most common source of runtime errors in data transformation:

=== Nullish Coalescing (??)

Provide a default value when an expression is null:

```utlx
$input.nickname ?? $input.name ?? "Anonymous"
// First non-null value wins
```

Evaluates left to right. Essential for handling optional fields:

```utlx
{
  currency: $input.currency ?? "EUR",
  country: $input.address?.country ?? "NL",
  discount: $input.promoCode ?? "NONE"
}
```

=== Safe Navigation (?.)

Access properties on potentially null values without throwing errors:

```utlx
$input.order?.customer?.address?.city
// Returns null if any step is null — no error
```

Without `?.`, a null intermediate value causes a runtime error. With `?.`, null propagates silently.

Combine `?.` with `??` for safe access with defaults:

```utlx
$input.order?.customer?.name ?? "Unknown Customer"
```

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Operator*], [*Name*], [*Example*], [*Result*],
  [`??`], [Nullish coalescing], [`null ?? "default"`], [`"default"`],
  [`?.`], [Safe navigation], [`null?.name`], [`null` (no error)],
)

== Access and Navigation Operators

These operators navigate the UDM tree — accessing properties, attributes, metadata, and children:

```utlx
$input.name                      // dot: property access
$input["content-type"]           // bracket: dynamic key or special characters
$input[0]                        // bracket: array index
$input.*                         // wildcard: all children as array
$input.@id                       // at: XML attribute
$input.@*                        // at-wildcard: all attributes as array
$input.^schemaType               // caret: metadata
```

Each is a different kind of navigation:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Operator*], [*What it accesses*], [*Example*],
  [`.name`], [Named property], [`$input.Order.Customer`],
  [`["key"]`], [Property by string (dynamic or special chars)], [`$input["cbc:ID"]`],
  [`[0]`], [Array element by index], [`$input.items[0]`],
  [`.*`], [All child properties as array], [`$input.IDOC.*`],
  [`.@name`], [XML attribute by name], [`$input.Order.@id`],
  [`.@*`], [All XML attributes as array], [`$input.Order.@*`],
  [`.^key`], [Internal metadata by key], [`$input.^xsdPattern`],
)

The `.` navigates data (what the user sent). The `@` navigates XML attributes (format-specific metadata on elements). The `^` navigates internal metadata (parser-generated, never in output). See Chapter 8 for detailed examples of each.

== Data Construction Operators

=== Spread (`...`)

Copy all properties from one object into another, or concatenate arrays:

```utlx
{...baseObject, status: "CONFIRMED"}     // copy + override
{...obj1, ...obj2}                        // merge (later wins)
[...array1, ...array2]                    // concatenate arrays
```

Spread is shallow — nested objects are referenced, not deep-copied. See Chapter 8 for the full spread operator guide.

=== Computed Property Names (`[expr]:`)

Use an expression result as a property name:

```utlx
let field = "fullName"
{[field]: concat($input.first, " ", $input.last)}
// {"fullName": "Alice Johnson"}
```

The brackets evaluate the expression and use the result as the key. See Chapter 26 (dynamic keys) for advanced patterns.

== Flow Operators

=== Pipe (`|>`)

Pass the result of the left expression as the first argument to the right function. Reads left-to-right instead of nested inside-out:

```utlx
// Without pipe — read from inside out:
sort(unique(map($input.items, (item) -> item.category)))

// With pipe — read left to right:
$input.items
  |> map((item) -> item.category)
  |> unique()
  |> sort()
```

Both produce the same result. The pipe version reads like a recipe: "take items, map to categories, remove duplicates, sort."

=== Lambda Arrows (`->` and `=>`)

Define anonymous functions (lambdas). Both arrows are equivalent:

```utlx
// Thin arrow (standard):
(x) -> x * 2
(x, y) -> x + y

// Fat arrow (JavaScript-style):
(x) => x * 2
x => x * 2          // single param: parentheses optional with =>
```

The `->` is the convention used throughout this book. The `=>` is accepted for developers coming from JavaScript/TypeScript. The `=>` also appears in match expression arms:

```utlx
match ($input.status) {
  "ACTIVE" => "green",
  "PENDING" => "yellow",
  _ => "gray"
}
```

See Chapter 15 for the full lambda and function guide.

=== Binding (`=`)

Used in `let` bindings to assign a value to a name:

```utlx
let total = $input.quantity * $input.price
let taxRate = 0.21
let tax = total * taxRate
```

The `=` is NOT assignment (there are no mutable variables in UTL-X). It is a one-time binding — the name refers to the value for the rest of its scope. See Chapter 8 for let binding scoping rules.

== String Concatenation

UTL-X does NOT use `+` for string concatenation — use the `concat()` function:

```utlx
concat("Hello", " ", "World")     // "Hello World"
```

This is deliberate. `+` is arithmetic only. Mixing `+` for both numbers and strings leads to ambiguity (`"3" + 4` — is it `"34"` or `7`?). UTL-X avoids this by keeping arithmetic and string operations separate.

For joining arrays of strings: `join(array, separator)`.

== Operator Precedence

From highest to lowest precedence. Higher precedence binds tighter:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Precedence*], [*Operators*], [*Associativity*],
  [1 (highest)], [Postfix: `.` `?.` `[]` `()` `@` `^`], [Left],
  [2], [Unary: `!` `-`], [Right],
  [3], [Exponentiation: `**`], [Right],
  [4], [Multiplicative: `*` `/` `%`], [Left],
  [5], [Additive: `+` `-`], [Left],
  [6], [Comparison: `<` `>` `<=` `>=`], [Left],
  [7], [Equality: `==` `!=`], [Left],
  [8], [Logical AND: `&&`], [Left],
  [9], [Logical OR: `||`], [Left],
  [10], [Nullish coalescing: `??`], [Left],
  [11], [Ternary: `? :`], [Right],
  [12], [Pipe: `|>`], [Left],
  [13 (lowest)], [Conditional: `if/else`], [Right],
)

When in doubt, use parentheses. They make intent explicit and prevent precedence surprises:

```utlx
// Ambiguous: does && bind tighter than ||?
a || b && c        // means: a || (b && c) — && is higher

// Clear: parentheses make intent obvious
(a || b) && c      // different meaning, explicit
```

== Reserved Operator

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Operator*], [*Name*], [*Status*],
  [`..`], [Recursive descent], [Token defined, parser not implemented. Reserved for future use.],
)
