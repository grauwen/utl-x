= Writing Transformations

This chapter teaches enough UTL-X to write production transformations. It is not the complete language reference --- for that, see the companion book _UTL-X: One Language, All Formats_. Here you learn the patterns that cover ninety percent of real-world integration scenarios.

== The .utlx File Structure

Every transformation has two parts: a header that declares the input and output formats, and a body that defines the transformation logic. They are separated by `---`.

```
%utlx 1.0
input json
output json
---
{
  orderId: $input.id,
  customer: $input.buyer.name,
  total: $input.amount
}
```

The header tells UTLXe how to parse the incoming message and how to serialize the output. The body is an expression that produces the output from the input.

== Property Access

Access input fields with dot notation. The special variable `$input` refers to the incoming message.

```
$input.customer.name          // nested property
$input.items[0].price         // array index (0-based)
$input.@id                    // XML attribute
$input.order.item[2].@qty     // attribute on indexed element
```

If a property does not exist, the result is `null` --- no error is thrown.

== Object Construction

Curly braces create new objects. Each line is a key-value pair.

```
{
  orderId: $input.id,
  customer: $input.buyer.name,
  total: $input.quantity * $input.unitPrice
}
```

The spread operator copies all properties from an existing object:

```
{
  ...$input,
  processed: true,
  processedAt: now()
}
```

This produces the original input with two additional fields.

== Let Bindings

Use `let` to name intermediate values. This avoids repetition and makes transformations readable.

```
{
  let subtotal = reduce($input.items, 0, (acc, i) -> acc + i.price)
  let tax = subtotal * 0.21
  let shipping = if (subtotal > 100) 0 else 9.95

  subtotal: subtotal,
  tax: round(tax, 2),
  shipping: shipping,
  total: round(subtotal + tax + shipping, 2)
}
```

== Conditionals

The `if` expression chooses between two values:

```
if ($input.total > 1000) "premium" else "standard"
```

For multiple branches, use `match`:

```
match $input.status {
  "A" -> "Active",
  "I" -> "Inactive",
  "S" -> "Suspended",
  _ -> "Unknown"
}
```

The underscore `_` is the catch-all pattern.

== Array Operations

Arrays are transformed with higher-order functions. The three most common are `map`, `filter`, and `reduce`.

*map* --- transform each element:

```
map($input.items, (item) -> {
  name: item.product,
  total: item.quantity * item.unitPrice
})
```

*filter* --- keep elements matching a condition:

```
filter($input.items, (item) -> item.price > 100)
```

*reduce* --- aggregate to a single value:

```
reduce($input.items, 0, (sum, item) -> sum + item.price)
```

These compose naturally:

```
$input.orders
  | filter(_, (o) -> o.status == "active")
  | map(_, (o) -> {id: o.id, total: o.amount})
  | sortBy(_, (o) -> o.total)
```

The pipe operator `|` passes the result of each step to the next. The underscore `_` is a placeholder for the piped value.

== Common Standard Library Functions

UTL-X has over 650 standard library functions. The ones you will use most often:

=== Strings

#table(
  columns: (auto, 1fr),
  [`concat(a, b, c)`], [Concatenate strings],
  [`upperCase(s)` / `lowerCase(s)`], [Case conversion],
  [`trim(s)`], [Remove leading and trailing whitespace],
  [`replace(s, old, new)`], [Replace substring],
  [`contains(s, sub)`], [Check if string contains substring],
  [`substring(s, start, end)`], [Extract substring],
  [`length(s)`], [String length],
  [`split(s, delimiter)`], [Split string into array],
  [`startsWith(s, prefix)`], [Check prefix],
)

=== Arrays

#table(
  columns: (auto, 1fr),
  [`map(arr, fn)`], [Transform each element],
  [`filter(arr, fn)`], [Keep matching elements],
  [`reduce(arr, init, fn)`], [Aggregate to single value],
  [`find(arr, fn)`], [First matching element],
  [`sortBy(arr, fn)`], [Sort by key],
  [`groupBy(arr, fn)`], [Group by key],
  [`flatMap(arr, fn)`], [Map and flatten],
  [`size(arr)`], [Array length],
  [`first(arr)` / `last(arr)`], [First or last element],
)

=== Math and Type

#table(
  columns: (auto, 1fr),
  [`round(n, decimals)`], [Round to decimal places],
  [`floor(n)` / `ceil(n)`], [Floor or ceiling],
  [`abs(n)` / `min(a, b)` / `max(a, b)`], [Absolute value, min, max],
  [`toString(v)` / `toNumber(v)`], [Type conversion],
  [`isNull(v)`], [Null check],
  [`now()`], [Current timestamp],
  [`formatDate(d, pattern)`], [Format a date],
)

== Multi-Format Transformations

The header declares formats. The body works the same regardless of whether the input is JSON, XML, CSV, or YAML --- UTL-X operates on the Universal Data Model (UDM), which is format-agnostic.

JSON to XML:

```
%utlx 1.0
input json
output xml
---
{
  Invoice: {
    ID: $input.orderId,
    Amount: $input.total,
    Currency: $input.currency
  }
}
```

The XML serializer produces `<Invoice><ID>12345</ID><Amount>250.0</Amount>...</Invoice>` from this object. You write objects --- the format system handles serialization.

XML to JSON:

```
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Invoice.ID,
  total: toNumber($input.Invoice.Amount),
  currency: $input.Invoice.Currency
}
```

CSV to JSON:

```
%utlx 1.0
input csv {header: true}
output json
---
map($input, (row) -> {
  name: row.Name,
  email: lowerCase(row.Email),
  active: row.Status == "A"
})
```

== User-Defined Functions

Define reusable logic with `function`. User-defined function names must start with an uppercase letter --- this distinguishes them from built-in functions like `round`, `concat`, `map`:

```
function Discount(amount, tier) {
  match tier {
    "gold" -> amount * 0.10,
    "silver" -> amount * 0.05,
    _ -> 0
  }
}

{
  let disc = Discount($input.total, $input.customerTier)
  orderId: $input.id,
  subtotal: $input.total,
  discount: round(disc, 2),
  total: round($input.total - disc, 2)
}
```

`Discount` starts with uppercase (user-defined). `round` starts with lowercase (built-in). This convention makes it immediately clear which functions are yours and which come from the standard library.

Functions are defined before the main expression. They can call other functions and use all standard library functions.

== Worked Example: Invoice Transformation

A complete transformation that converts a Dynamics 365 Business Central order into a simplified UBL-like invoice:

```
%utlx 1.0
input json
output xml
---
{
  let order = $input
  let subtotal = reduce(order.lines, 0,
    (sum, line) -> sum + line.quantity * line.unitPrice)
  let tax = subtotal * 0.21

  Invoice: {
    ID: concat("INV-", order.orderNumber),
    IssueDate: formatDate(now(), "yyyy-MM-dd"),
    CustomerName: order.customer.name,
    Currency: order.currency,
    InvoiceLines: {
      InvoiceLine: map(order.lines, (line) -> {
        ID: line.lineNumber,
        Quantity: line.quantity,
        UnitPrice: line.unitPrice,
        LineTotal: round(line.quantity * line.unitPrice, 2)
      })
    },
    Subtotal: round(subtotal, 2),
    Tax: round(tax, 2),
    Total: round(subtotal + tax, 2)
  }
}
```

The XML serializer produces the UBL structure from this object. You write objects with property names that become element names --- the format system handles the rest.

This transformation demonstrates property access, array iteration with `map`, aggregation with `reduce`, string concatenation, date formatting, and arithmetic.

== Where to Learn More

- The complete language specification, including pattern matching, recursive functions, and the full operator table, is in the companion book _UTL-X: One Language, All Formats_.
- The standard library reference (all 650+ functions with examples) is in chapters 16 and 17 of the companion book.
- Example transformations are available in the project repository under `examples/`.
