= Language Fundamentals

This chapter teaches the building blocks of UTL-X: the types you work with, how you access data, how you bind variables, and how you make decisions. Everything that follows — functions, patterns, pipelines — builds on these fundamentals.

If you've written JavaScript, Python, or any functional language, most of this will feel familiar. UTL-X deliberately borrows syntax from widely-known languages so you can be productive immediately.

== Scalar Types

Scalars are the atomic values — the leaves of any data tree.

*Strings:* enclosed in double quotes.

```utlx
"Hello, World"
"Line one\nLine two"
"She said \"hello\""
```

*Numbers:* integers and decimals. UTL-X uses 64-bit floating point internally (like JavaScript), but preserves integer representation when possible.

```utlx
42
3.14
-17
1.5e10
```

*Booleans:*

```utlx
true
false
```

*Null:* represents the absence of a value.

```utlx
null
```

Type coercion happens automatically in some contexts — for example, concatenating a number with a string converts the number to a string. For explicit conversion, use the stdlib functions: `toString()`, `toNumber()`, `toBoolean()`.

== Objects

Objects are collections of named properties — the workhorses of data transformation.

=== Object Literals

Create objects with curly braces and key-value pairs:

```utlx
{
  name: "Alice",
  age: 30,
  active: true
}
```

Keys don't need quotes unless they contain special characters. Values can be any expression — including other objects, arrays, or function calls.

=== Property Access

Access properties with dot notation:

```utlx
$input.customer.name
$input.order.items[0].price
```

Or bracket notation for dynamic keys:

```utlx
$input["customer"]["name"]
let field = "name"
$input.customer[field]
```

=== Nested Access

Dot notation chains naturally for deep structures:

```utlx
$input.Order.Customer.Address.City
```

This works identically whether the data came from XML, JSON, CSV, or YAML — the UDM normalizes all formats into the same property access pattern.

=== XML Attribute Access

XML attributes use the `@` prefix:

```utlx
$input.Order.@id              // the "id" attribute on <Order>
$input.Product.@price          // the "price" attribute on <Product>
```

This is specific to XML input — JSON, CSV, and YAML don't have attributes. See Chapter 20 for XML transformation details and Chapter 21 for the attribute design decisions.

=== Computed Property Names

When the key is dynamic, use square brackets:

```utlx
let key = "fullName"
{ [key]: concat($input.first, " ", $input.last) }
// produces: {"fullName": "Alice Johnson"}
```

=== The Spread Operator

Merge objects with `...`:

```utlx
let base = {name: "Alice", age: 30}
let override = {age: 31, active: true}
{...base, ...override}
// produces: {name: "Alice", age: 31, active: true}
```

Later spreads override earlier ones — `age: 31` wins over `age: 30`.

== Arrays

Arrays are ordered collections — essential for processing lists of items, rows, or repeated elements.

=== Array Literals

```utlx
[1, 2, 3]
["Alice", "Bob", "Charlie"]
[{name: "Alice"}, {name: "Bob"}]
```

=== Index Access

Zero-based indexing:

```utlx
$input.items[0]       // first item
$input.items[2]       // third item
```

Negative indexing is not supported — use `last()` instead:

```utlx
last($input.items)    // last item
```

=== Array Operations

Arrays are processed with higher-order functions (Chapter 14 covers these in depth):

```utlx
// Transform each element
map($input.items, (item) -> item.name)

// Keep only matching elements
filter($input.items, (item) -> item.price > 100)

// Accumulate a result
reduce($input.items, 0, (sum, item) -> sum + item.price)

// Sort
sortBy($input.items, (item) -> item.name)
```

== The \$input Variable

Every UTL-X transformation has access to `\$input` — the parsed input data as a UDM tree.

```utlx
$input                        // the entire input document
$input.Order                  // a child property
$input.Order.Items[0]         // array element
$input.Order.@id              // XML attribute
```

For multi-input transformations, each input has its own named variable:

```utlx
// Header: input: orders xml, customers json
$orders.Order[0].Total        // from the XML input
$customers[0].name            // from the JSON input
```

=== Safe Navigation

What if a property doesn't exist? Without safe navigation, you get a null reference. With `?.`, you get `null` gracefully:

```utlx
$input.order?.discount         // null if "order" or "discount" is missing
$input.customer?.address?.city // null if any step is missing
```

=== Recursive Descent

Search for a property name anywhere in the tree with `..`:

```utlx
$input..ProductCode     // finds "ProductCode" at any depth
```

This returns an array of all matching values — useful when the exact path varies or the structure is deeply nested.

== Variable Binding (let)

Use `let` to bind a value to a name. Variables are immutable — once bound, they cannot be changed.

```utlx
let name = $input.customer.name
let total = sum(map($input.items, (i) -> i.price * i.qty))
{
  customerName: name,
  orderTotal: total,
  averagePrice: total / count($input.items)
}
```

Variables make transformations readable by naming intermediate results. Without `let`, you'd nest everything:

```utlx
// Without let — hard to read
{
  orderTotal: sum(map($input.items, (i) -> i.price * i.qty)),
  averagePrice: sum(map($input.items, (i) -> i.price * i.qty)) / count($input.items)
}

// With let — clear and no duplication
let total = sum(map($input.items, (i) -> i.price * i.qty))
{
  orderTotal: total,
  averagePrice: total / count($input.items)
}
```

Multiple `let` bindings chain naturally — each sees the previous bindings. But the separator between `let` bindings depends on context. This is one of the few places where UTL-X syntax requires attention:

=== Top-Level: No Separator Needed

At the top level of a .utlx body (outside any braces), `let` bindings are separated by newlines:

```utlx
let items = $input.Order.Items.Item
let prices = map(items, (i) -> toNumber(i.@price) * toNumber(i.@qty))
let total = sum(prices)
let tax = total * 0.21
{
  subtotal: total,
  tax: tax,
  grandTotal: total + tax
}
```

No commas, no semicolons — just newlines. The final expression (the object literal) is the return value.

=== Inside Object Literals: Commas Required

When `let` bindings appear _inside_ an object literal (between `{` and `}`), they need commas — just like properties:

```utlx
{
  let subtotal = 100,
  let tax = subtotal * 0.08,
  let total = subtotal + tax,

  subtotal: subtotal,
  tax: tax,
  total: total
}
```

The commas separate `let` bindings from each other and from the property definitions. Think of `let` inside braces as "object members" — they follow the same comma rules as properties.

=== Inside Lambdas Returning Arrays: Semicolons Required

When `let` bindings are followed by an array (not an object), the parser needs semicolons to distinguish "array return" from "array indexing":

```utlx
// Inside a map lambda that returns an array:
items |> map(item => {
  let price = toNumber(item.Price);
  let tax = price * 0.08;

  [item.Name, price + tax]
})
```

Without semicolons, the parser would read `let tax = price * 0.08[item.Name, ...]` — interpreting the array as an index operation on the let value.

=== Summary of Let Separators

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Context*], [*Separator*], [*Example*],
  [Top-level (.utlx body)], [Newline], [let x = 1 (newline) let y = 2],
  [Inside object \{ \}], [Comma], [\{let x = 1, let y = 2, result: x + y\}],
  [Lambda returning array], [Semicolon], [let x = 1; let y = 2; [x, y]],
)

When in doubt, use commas inside braces and semicolons inside lambdas. At the top level, newlines are always sufficient.

#block(
  fill: rgb("#FFF3E0"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Design Note (F02):* The three-separator rule for `let` bindings is a known inconsistency inherited from the parser implementation, not a deliberate language design choice. A future UTL-X version will simplify this to: _newlines are always sufficient_ (the Kotlin model). Commas and semicolons will remain accepted for backward compatibility but will no longer be required. See the F02 design document for details. This section of the book will be updated when the improvement is implemented.
]

== Conditional Expressions

=== if / else

UTL-X's `if` is an _expression_, not a statement — it always produces a value:

```utlx
if ($input.age >= 18) "adult" else "minor"
```

Use parentheses around the condition. Both branches are required — `if` without `else` is not allowed (every expression must produce a value).

Nest for complex logic:

```utlx
if ($input.score >= 90) "A"
else if ($input.score >= 80) "B"
else if ($input.score >= 70) "C"
else "F"
```

=== Ternary Operator

For inline conditions, the ternary operator is more concise:

```utlx
$input.active ? "yes" : "no"
```

=== Nullish Coalescing (??)

Provide a default value when something is null:

```utlx
$input.nickname ?? $input.name ?? "Unknown"
```

This evaluates left to right — the first non-null value wins.

=== Combining Conditionals

Real-world transformations combine these freely:

```utlx
{
  status: if ($input.paid) "PAID" else "PENDING",
  discount: $input.loyaltyLevel ?? "NONE",
  shipping: $input.express ? "OVERNIGHT" : "STANDARD",
  greeting: concat("Dear ", $input.title ?? "Customer")
}
```

== Comments

Single-line comments start with `//`:

```utlx
// Calculate the order total including tax
let total = sum(prices)  // sum all line items
```

There are no multi-line comments in UTL-X. This is deliberate — it keeps transformations scannable. Every line is either code or a comment, never ambiguous.

== Type Annotations (Optional)

UTL-X supports optional type annotations for documentation:

```utlx
let name: String = $input.customer.name
let total: Number = sum(prices)
```

Type annotations are _not enforced at runtime_ in the current version. They serve as documentation — making your intent clear to other developers (and to the IDE, which uses them for better autocompletion).

== Putting It Together

Here's a complete transformation that uses all the fundamentals covered in this chapter:

```utlx
%utlx 1.0
input xml
output json
---
// Extract order data
let order = $input.Order
let items = order.Items.Item
let currency = order.@currency ?? "EUR"

// Calculate financial summary
let lineAmounts = map(items, (item) ->
  toNumber(item.@price) * toNumber(item.@qty)
)
let subtotal = sum(lineAmounts)
let taxRate = if (order.Customer.@country == "NL") 0.21 else 0
let tax = subtotal * taxRate

// Build output
{
  orderId: order.@id,
  customer: order.Customer,
  currency: currency,
  items: map(items, (item) -> {
    sku: item.@sku,
    description: item,
    quantity: toNumber(item.@qty),
    unitPrice: toNumber(item.@price),
    lineTotal: toNumber(item.@price) * toNumber(item.@qty)
  }),
  subtotal: subtotal,
  tax: tax,
  total: subtotal + tax,
  status: order.@status ?? "NEW"
}
```

This transformation demonstrates: property access, attribute access (\@), safe navigation (??), let bindings, map with lambdas, conditional logic (if/else), arithmetic, and object construction. All in ~25 lines of readable, declarative code.

The next chapter explores the Universal Data Model — the internal representation that makes all of this work across formats.
