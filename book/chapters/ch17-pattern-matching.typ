= Pattern Matching and Control Flow

Most transformation logic is straightforward: map fields, apply functions, construct objects. But real-world integration always has conditional paths — different output structures based on message type, different calculations based on country, different handling for errors.

This chapter covers UTL-X's control flow mechanisms: `match` expressions for multi-way branching, `try/catch` for error recovery, and practical patterns for routing and polymorphic data.

== Match Expressions

`match` is UTL-X's multi-way branch — like `switch` in Java or `when` in Kotlin, but more powerful because it supports value matching, guard clauses, and variable binding.

=== Basic Value Matching

```utlx
match ($input.status) {
  "NEW" -> "Order received",
  "PROCESSING" -> "In progress",
  "SHIPPED" -> "On the way",
  "DELIVERED" -> "Completed",
  _ -> "Unknown status"
}
```

The `_` is the wildcard — it matches anything. Always include it as the last case to handle unexpected values. Without a wildcard, an unmatched value produces null.

=== Matching with Guards

Add conditions after the pattern with `if`:

```utlx
match ($input.amount) {
  n if n > 10000 -> "high-value",
  n if n > 1000 -> "medium-value",
  n if n > 0 -> "standard",
  0 -> "zero",
  _ -> "negative"
}
```

The variable `n` is bound to the matched value — you can use it in the guard condition and in the result expression.

=== Complex Matching

Match can branch on any expression, not just simple values:

```utlx
match (getType($input.data)) {
  "string" -> {type: "text", value: $input.data},
  "number" -> {type: "numeric", value: $input.data},
  "array" -> {type: "list", count: count($input.data)},
  "object" -> {type: "record", keys: keys($input.data)},
  _ -> {type: "unknown"}
}
```

=== Match vs If/Else Chains

For two or three conditions, `if/else` is fine:

```utlx
if ($input.country == "NL") "domestic"
else if ($input.country == "BE" || $input.country == "DE") "neighboring"
else "international"
```

For more than three conditions, `match` is clearer:

```utlx
match ($input.country) {
  "NL" -> "domestic",
  "BE" -> "neighboring",
  "DE" -> "neighboring",
  "FR" -> "EU-west",
  "IT" -> "EU-south",
  "PL" -> "EU-east",
  _ -> "international"
}
```

The rule of thumb: *two branches → if/else, three or more → match*.

== Try/Catch — Error Recovery

Not every input is clean. Dates might be unparseable. Numbers might be text. Fields might be missing. `try/catch` lets you handle errors gracefully instead of failing the entire transformation.

=== Basic Try/Catch

```utlx
try {
  parseDate($input.dateString, "yyyy-MM-dd")
} catch (e) {
  today()    // fallback: use today's date if parsing fails
}
```

The `try` block contains the expression that might fail. If it succeeds, the result is used. If it throws an error, the `catch` block runs instead.

=== Using the Error Variable

The `catch` variable `e` contains the error message:

```utlx
{
  result: try {
    toNumber($input.price) * toNumber($input.quantity)
  } catch (e) {
    0    // fallback to zero
  },
  error: try {
    toNumber($input.price) * toNumber($input.quantity)
    null    // no error
  } catch (e) {
    e    // capture the error message
  }
}
```

=== Try/Catch in Map

A common pattern — transform an array, handling errors per element instead of failing the entire batch:

```utlx
map($input.records, (record) -> {
  id: record.id,
  amount: try { toNumber(record.amount) } catch (e) { 0 },
  date: try { parseDate(record.date, "yyyy-MM-dd") } catch (e) { null },
  valid: try { toNumber(record.amount); true } catch (e) { false }
})
```

Each record is processed independently. A bad date in record 5 doesn't prevent records 1-4 and 6+ from being transformed correctly.

=== When to Use Try/Catch vs Null Coalescing

```utlx
// Use ?? when the value might be null/missing:
$input.discount ?? 0

// Use try/catch when the operation might FAIL:
try { parseDate($input.date, "yyyy-MM-dd") } catch (e) { null }
```

`??` handles absence (null). `try/catch` handles failure (exceptions). Different problems, different tools.

== Practical Control Flow Patterns

=== Message Type Routing

Integration messages often carry a type indicator. Route to different transformation logic based on type:

```utlx
match ($input.messageType) {
  "ORDER" -> {
    type: "order",
    orderId: $input.data.orderId,
    customer: $input.data.customer,
    lines: $input.data.lines
  },
  "INVOICE" -> {
    type: "invoice",
    invoiceId: $input.data.invoiceId,
    total: $input.data.total,
    dueDate: $input.data.dueDate
  },
  "CREDIT_NOTE" -> {
    type: "credit",
    creditId: $input.data.creditId,
    amount: -abs($input.data.amount)
  },
  _ -> error(concat("Unknown message type: ", $input.messageType))
}
```

=== API Version Handling

Different API versions send different field names. Handle both:

```utlx
{
  // v2 uses "fullName", v1 uses separate "firstName" + "lastName"
  name: if ($input.fullName != null) $input.fullName
        else concat($input.firstName ?? "", " ", $input.lastName ?? ""),

  // v2 uses ISO date, v1 uses US format
  date: try { parseDate($input.date, "yyyy-MM-dd") }
        catch (e) { try { parseDate($input.date, "MM/dd/yyyy") }
        catch (e2) { null } },

  // v2 includes "status", v1 doesn't
  status: $input.status ?? "ACTIVE"
}
```

=== Country-Specific Business Rules

```utlx
let vatRate = match ($input.customer.country) {
  "NL" -> 21,
  "DE" -> 19,
  "FR" -> 20,
  "BE" -> 21,
  "LU" -> 17,
  "IE" -> 23,
  _ -> 0    // non-EU: reverse charge, 0% VAT
}

let isReverseCharge = vatRate == 0 && $input.customer.vatId != null

{
  subtotal: $input.total,
  vatRate: vatRate,
  vatAmount: if (isReverseCharge) 0 else $input.total * vatRate / 100,
  reverseCharge: isReverseCharge,
  total: if (isReverseCharge) $input.total else $input.total * (1 + vatRate / 100)
}
```

=== Data Cleansing with Fallbacks

```utlx
{
  email: try {
    let e = lowerCase(trim($input.email))
    if (matches(e, "^[\\w.-]+@[\\w.-]+\\.[a-z]{2,}$")) e else null
  } catch (err) { null },

  phone: try {
    replace(replace(replace($input.phone ?? "", " ", ""), "-", ""), "+", "00")
  } catch (err) { null },

  amount: try {
    abs(toNumber(replace($input.amount ?? "0", ",", ".")))
  } catch (err) { 0 },

  country: upperCase(trim($input.countryCode ?? "")) ?? "XX"
}
```

=== Polymorphic Output (Different Structure Based on Input)

```utlx
if (count($input.items) == 1) {
  // Single-item order: flat structure
  orderId: $input.orderId,
  product: $input.items[0].name,
  quantity: $input.items[0].qty,
  price: $input.items[0].price
} else {
  // Multi-item order: nested structure with summary
  orderId: $input.orderId,
  itemCount: count($input.items),
  items: map($input.items, (i) -> {name: i.name, qty: i.qty, price: i.price}),
  total: sum(map($input.items, (i) -> i.qty * i.price))
}
```

== Control Flow Summary

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Mechanism*], [*Use when*], [*Example*],
  [if/else], [2-3 simple conditions], [if (active) "yes" else "no"],
  [ternary ? :], [Inline condition], [active ? "yes" : "no"],
  [?? (nullish)], [Default for missing value], [name ?? "Unknown"],
  [?. (safe nav)], [Property might not exist], [order?.customer?.name],
  [match], [3+ conditions or complex routing], [match (type) \{ ... \}],
  [try/catch], [Operation might fail], [try \{ parseDate(...) \} catch \{ null \}],
  [error()], [Deliberately fail], [error("Invalid input")],
)

The key insight: in UTL-X, every control flow construct is an _expression_ that produces a value. There are no statements. This means you can use `if/else`, `match`, and `try/catch` anywhere a value is expected — inside objects, as function arguments, in array elements, nested in other expressions.
