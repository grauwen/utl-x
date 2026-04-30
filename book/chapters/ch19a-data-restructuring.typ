= Data Restructuring: Flat, Hierarchical, and Back

Real-world integration is rarely a simple field-to-field mapping. The source data is structured one way, the target expects it another way. Orders arrive flat, the target wants them nested. Nested data needs to become CSV. Sequential segments need grouping by header. A customer ID needs to become a customer name.

This chapter covers the complete toolkit for restructuring data in UTL-X: from flat to hierarchical, from hierarchical to flat, and everything in between.

== The Six Restructuring Patterns

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Pattern*], [*Direction*], [*Function*], [*Status*],
  [Parent-child nesting], [Flat → Hierarchical], [nestBy()], [Proposed (F03)],
  [Record enrichment], [Flat → Enriched], [lookupBy()], [Proposed (F04)],
  [Sequential grouping], [Flat → Grouped], [chunkBy()], [Proposed (F05)],
  [Denormalization], [Hierarchical → Flat], [unnest()], [Proposed (F06)],
  [Key-based grouping], [Flat → Map], [groupBy()], [Exists],
  [Array flattening], [Nested → Flat], [flatten()], [Exists],
)

// DIAGRAM: Six patterns as arrows between "Flat", "Hierarchical", "Grouped", "Enriched"
// Source: part2-language.pptx

== Pattern 1: Flat → Hierarchical (join)

The most common integration challenge: you receive flat records with key references, and the target expects nested parent-child structures.

=== The Problem

Orders and order lines arrive as separate flat arrays. Each line references its parent order by key:

```json
{
  "orders":     [{"orderId": "A", "customer": "Acme"}, {"orderId": "B", "customer": "Globex"}],
  "orderLines": [{"orderId": "A", "product": "Widget"}, {"orderId": "A", "product": "Gadget"}, {"orderId": "B", "product": "Gizmo"}]
}
```

Target expects nested:

```json
[{"orderId": "A", "customer": "Acme", "lines": [{"product": "Widget"}, {"product": "Gadget"}]},
 {"orderId": "B", "customer": "Globex", "lines": [{"product": "Gizmo"}]}]
```

=== The Solution: nestBy()

```utlx
nestBy(
  \$input.orders,                    // parent array
  \$input.orderLines,                // child array
  (order) -> order.orderId,          // parent key
  (line) -> line.orderId,            // child key
  "lines"                            // property name for nested children
)
```

The name reads naturally: "nest orderLines by orderId into a property called lines." The 5th parameter `"lines"` is a string that tells `nestBy()` what to name the new property it creates on each parent. After this call, each order has a `.lines` property containing its matched order lines:

```utlx
let enrichedOrders = nestBy(...)

// Now use order.lines like any property:
map(enrichedOrders, (order) -> {
  orderId: order.orderId,
  lineCount: count(order.lines),
  total: sum(map(order.lines, (l) -> l.qty * l.price))
})
```

=== Today's Workaround (without join)

Until `nestBy()` is implemented, use `filter()` per parent:

```utlx
map(\$input.orders, (order) -> {
  orderId: order.orderId,
  customer: order.customer,
  lines: filter(\$input.orderLines, (l) -> l.orderId == order.orderId)
})
```

This works but is O(N times M) — for 500 orders and 5,000 lines, that's 2.5 million comparisons. `nestBy()` does it in 5,500 operations.

== Pattern 2: Record Enrichment (lookup)

You have a record with a reference key and need to add fields from a lookup table — not nest the entire matching record, just extract specific fields.

=== The Problem

An order has `customerId` but needs `customerName`:

```json
{"orderId": "ORD-001", "customerId": "C-42", "total": 299.99}
```

Customer table:

```json
[{"id": "C-42", "name": "Acme Corp", "country": "NL"}]
```

You want: `{orderId: "ORD-001", customerName: "Acme Corp", country: "NL"}` — not a nested customer object.

=== The Solution: lookupBy()

```utlx
let customer = lookupBy(order.customerId, \$input.customers, (c) -> c.id)

{
  orderId: order.orderId,
  total: order.total,
  customerName: customer?.name ?? "Unknown",
  country: customer?.country
}
```

`lookupBy()` finds the first matching record and returns it. You then pick the fields you need with dot notation. Use `?.` for safety in case no match is found.

=== Today's Workaround (without lookup)

Use `find()`:

```utlx
let customer = find(\$input.customers, (c) -> c.id == order.customerId)
```

Identical result. `lookupBy()` adds the potential for internal caching when used repeatedly inside `map()`.

== Pattern 3: Sequential Grouping (chunkBy)

For data where parent-child is determined by position, not by key fields.

=== The Problem

SAP IDoc segments arrive in sequence — headers and lines interleaved:

```
E1EDK01  (header)    ← order 1 starts
E1EDP01  (line)      ← belongs to order 1
E1EDP01  (line)      ← belongs to order 1
E1EDK01  (header)    ← order 2 starts
E1EDP01  (line)      ← belongs to order 2
```

No key connects lines to headers. Position is the only relationship: "everything between one header and the next belongs together."

=== The Solution: chunkBy()

```utlx
let groups = chunkBy(\$input.segments, (seg) -> seg.type == "E1EDK01")

map(groups, (chunk) -> {
  header: chunk[0],
  lines: filter(drop(chunk, 1), (seg) -> seg.type == "E1EDP01")
})
```

`chunkBy()` splits the flat sequence into groups. A new group starts whenever the lambda returns true. Each group is an array where the first element is the header and the rest are its children.

=== Today's Workaround (without chunkBy)

Use `reduce()` with manual state tracking — possible but extremely verbose and error-prone. `chunkBy()` turns 15 lines of reduce logic into a single function call.

== Pattern 4: Denormalization (unnest)

The reverse of `nestBy()` — take nested data and flatten it. Essential for producing CSV, database rows, or flat file output.

=== The Problem

Hierarchical order data:

```json
[{"orderId": "A", "customer": "Acme", "lines": [
    {"product": "Widget", "qty": 10},
    {"product": "Gadget", "qty": 5}
]}]
```

Target is flat CSV:

```csv
orderId,customer,product,qty
A,Acme,Widget,10
A,Acme,Gadget,5
```

Each parent field must be repeated for every child row.

=== The Solution: unnest()

```utlx
%utlx 1.0
input json
output csv
---
unnest(\$input.orders, "lines")
```

One function call. The `"lines"` parameter tells `unnest()` which nested array to expand. Parent fields (orderId, customer) are automatically repeated for each child. The `lines` property itself is removed from the output.

For multi-level flattening, chain:

```utlx
\$input.orders |> unnest("lines") |> unnest("schedules")
```

=== Today's Workaround (without unnest)

```utlx
flatten(map(\$input.orders, (order) ->
  map(order.lines, (line) -> {
    orderId: order.orderId,
    customer: order.customer,
    ...line
  })
))
```

Works but requires manually listing parent fields or using spread (which includes the `lines` array itself, causing unwanted nesting in the output).

== Pattern 5: Key-Based Grouping (groupBy — exists)

Group flat records into a map by key. Already available in UTL-X.

```utlx
groupBy(\$input.employees, (e) -> e.department)
// {"Engineering": [{...}, {...}], "Sales": [{...}], "Marketing": [{...}]}
```

Use `groupBy` when you want a map (key → array) rather than a nested hierarchy. Common for: aggregation per group, partitioning data by category, building lookup indexes.

#block(
  fill: rgb("#FFF3E0"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Known Issue (B15):* `groupBy()` currently cannot be indexed with a UDM value — `groups[order.orderId]` fails with "Index must be a number or string." This bug blocks the efficient groupBy + index pattern. Use `filter()` as a workaround until B15 is fixed.
]

== Pattern 6: Array Flattening (flatten — exists)

Unwrap one level of nested arrays. Already available in UTL-X.

```utlx
flatten([[1, 2], [3, 4], [5, 6]])
// [1, 2, 3, 4, 5, 6]
```

Use `flatten` when `map()` produces arrays and you want a single flat array instead of an array of arrays. Common pattern: `map` + `flatten` = `flatMap`.

== Combining Patterns

Real integrations often combine multiple patterns:

=== IDoc → Enriched Hierarchical JSON

```utlx
// 1. Group sequential segments (chunkBy)
let orderGroups = chunkBy(\$input.segments, (seg) -> seg.type == "E1EDK01")

// 2. Structure into parent-child (map)
let orders = map(orderGroups, (chunk) -> {
  ...chunk[0],
  lines: filter(drop(chunk, 1), (seg) -> seg.type == "E1EDP01")
})

// 3. Enrich with customer data (lookup)
map(orders, (order) -> {
  let customer = lookupBy(order.KUNNR, \$input.customers, (c) -> c.id)

  orderId: order.BELNR,
  customer: customer?.name ?? "Unknown",
  country: customer?.country,
  lines: map(order.lines, (line) -> {
    product: line.MATNR,
    quantity: toNumber(line.MENGE)
  })
})
```

Three patterns combined: `chunkBy` (sequential → grouped), `map` (grouped → hierarchical), `lookup` (enrichment).

=== Hierarchical → Flat CSV Report

```utlx
// 1. Unnest orders → lines
let flat = unnest(\$input.orders, "lines")

// 2. Enrich with product descriptions (lookup)
map(flat, (row) -> {
  let product = lookupBy(row.productCode, \$input.catalog, (p) -> p.code)

  ...row,
  productDescription: product?.description ?? row.productCode,
  weight: product?.weight ?? 0
})

// Output as CSV
```

Two patterns: `unnest` (hierarchical → flat), `lookupBy` (enrichment).

== Choosing the Right Pattern

#table(
  columns: (auto, auto),
  align: (left, left),
  [*I have / I need*], [*Use*],
  [Flat records with keys → nested parent-child], [nestBy()],
  [Record with ID → add fields from reference table], [lookupBy() or find()],
  [Sequential segments → groups by header], [chunkBy()],
  [Nested data → flat rows for CSV/database], [unnest()],
  [Flat records → map of key → array], [groupBy()],
  [Array of arrays → single flat array], [flatten()],
  [Conditional children → nest only matching], [filter() then nestBy()],
  [Many-to-many via bridge table], [Two nestBy() calls through bridge],
  [Composite key matching], [concat() key then nestBy() or lookupBy()],
)

== Naming Convention: Why Some Functions End in "By"

The restructuring functions follow a consistent naming convention. Functions that take a *lambda* for key extraction use the `By` suffix — "do X *by* this key function." Functions that take a plain value (string, property name) do not.

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Function*], [*Takes lambda?*], [*By suffix?*], [*Rationale*],
  [groupBy()], [Yes], [Yes], [Group _by_ key function],
  [sortBy()], [Yes], [Yes], [Sort _by_ key function],
  [nestBy()], [Yes], [Yes], [Nest children _by_ key function],
  [chunkBy()], [Yes], [Yes], [Chunk _by_ predicate function],
  [lookupBy()], [Yes], [Yes], [Look up _by_ key function],
  [unnest()], [No (string)], [No], [Unnest a named property — no key function],
  [flatten()], [No], [No], [Flatten arrays — no key function],
)
