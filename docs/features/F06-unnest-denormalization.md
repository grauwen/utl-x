# F06: unnest() Function — Hierarchical to Flat (Denormalization)

**Status:** Proposed  
**Priority:** Medium  
**Created:** April 2026  
**Related:** F03 (nestBy — the reverse operation), F04 (lookup), F05 (chunkBy)

---

## The Problem

You have hierarchical data (nested) and need to produce flat data (one row per child with parent fields repeated). This is the **reverse** of `nestBy()`.

### Example

Input (hierarchical — an order with nested lines):

```json
[
  {
    "orderId": "ORD-001",
    "customer": "Acme Corp",
    "currency": "EUR",
    "lines": [
      {"lineNr": 1, "product": "Widget", "qty": 10, "price": 25.00},
      {"lineNr": 2, "product": "Gadget", "qty": 5, "price": 49.99}
    ]
  },
  {
    "orderId": "ORD-002",
    "customer": "Globex Inc",
    "currency": "USD",
    "lines": [
      {"lineNr": 1, "product": "Server", "qty": 2, "price": 1200.00}
    ]
  }
]
```

Desired output (flat — one row per line, parent fields repeated):

```json
[
  {"orderId": "ORD-001", "customer": "Acme Corp", "currency": "EUR", "lineNr": 1, "product": "Widget", "qty": 10, "price": 25.00},
  {"orderId": "ORD-001", "customer": "Acme Corp", "currency": "EUR", "lineNr": 2, "product": "Gadget", "qty": 5, "price": 49.99},
  {"orderId": "ORD-002", "customer": "Globex Inc", "currency": "USD", "lineNr": 1, "product": "Server", "qty": 2, "price": 1200.00}
]
```

Each parent (order) is repeated for each child (line). Parent fields and child fields are merged into one flat record.

### When You Need This

- **CSV output from nested data** — CSV is inherently flat. To export orders with lines to CSV, you must denormalize first.
- **Database inserts** — relational databases expect flat rows, not nested JSON.
- **Reporting / BI tools** — Excel, Power BI, Tableau expect flat tabular data.
- **EDI generation** — EDI segments are flat, generated from hierarchical business objects.
- **Data lake / analytics** — Parquet, BigQuery prefer flat or semi-flat structures.

### Current Approach (verbose)

```utlx
flatten(map($input.orders, (order) ->
  map(order.lines, (line) -> {
    orderId: order.orderId,
    customer: order.customer,
    currency: order.currency,
    lineNr: line.lineNr,
    product: line.product,
    qty: line.qty,
    price: line.price
  })
))
```

This works but requires:
1. Explicitly listing every parent field to carry forward
2. Explicitly listing every child field
3. Nesting `map` inside `map` inside `flatten`
4. If the parent has 20 fields, you list all 20

With the spread operator it's slightly better:

```utlx
flatten(map($input.orders, (order) ->
  let parentFields = {orderId: order.orderId, customer: order.customer, currency: order.currency}
  map(order.lines, (line) -> {...parentFields, ...line})
))
```

Still verbose. And you must manually select which parent fields to include (you don't want to include the `lines` array itself in the flat output).

## Proposed Function

### Signature

```
unnest(array, childPropertyName) → Array
```

### Parameters

| Parameter | Type | What it does |
|-----------|------|-------------|
| `array` | Array | The array of parent objects with nested children |
| `childPropertyName` | String | The name of the property containing the nested child array |

### Return Value

A flat Array where each child becomes a record with all parent fields (except the nested array itself) merged with all child fields.

### How It Works — Step by Step

```utlx
unnest($input.orders, "lines")
```

For each parent in `$input.orders`:
1. Take the parent: `{orderId: "ORD-001", customer: "Acme Corp", currency: "EUR", lines: [...]}`
2. Remove the `lines` property from the parent → `{orderId: "ORD-001", customer: "Acme Corp", currency: "EUR"}`
3. For each child in `lines`:
   - Merge parent fields + child fields → `{orderId: "ORD-001", customer: "Acme Corp", currency: "EUR", lineNr: 1, product: "Widget", qty: 10, price: 25.00}`
4. Collect all merged records into a flat array

The `"lines"` parameter (string) tells `unnest()` which property to expand — same pattern as `nestBy()`. After `unnest()`, the `lines` property is gone — its contents have been flattened into the parent.

### Using the Result

```utlx
// Flatten orders → lines for CSV export
let flat = unnest($input.orders, "lines")

// Now 'flat' is an array of flat records — ready for CSV:
// [
//   {orderId: "ORD-001", customer: "Acme Corp", lineNr: 1, product: "Widget", ...},
//   {orderId: "ORD-001", customer: "Acme Corp", lineNr: 2, product: "Gadget", ...},
//   {orderId: "ORD-002", customer: "Globex Inc", lineNr: 1, product: "Server", ...}
// ]
```

Output to CSV:

```utlx
%utlx 1.0
input json
output csv
---
unnest($input.orders, "lines")
```

Produces:

```csv
orderId,customer,currency,lineNr,product,qty,price
ORD-001,Acme Corp,EUR,1,Widget,10,25.00
ORD-001,Acme Corp,EUR,2,Gadget,5,49.99
ORD-002,Globex Inc,USD,1,Server,2,1200.00
```

One function call. No manual field listing. No nested maps.

### Multi-Level Unnesting

For deeper hierarchies (order → lines → schedules), chain `unnest()`:

```utlx
$input.orders
  |> unnest("lines")        // flatten lines into orders
  |> unnest("schedules")    // flatten schedules into lines (now flat records)
```

Each `unnest()` removes one level of nesting. Two calls flatten a 3-level hierarchy to completely flat records.

## unnest() Is the Reverse of nestBy()

These two functions are inverses:

```
nestBy()  : flat data  → hierarchical data  (group children under parents)
unnest()  : hierarchical data → flat data   (expand children alongside parents)
```

Round-trip:

```utlx
// Start flat
let flat = [{orderId: "A", product: "Widget"}, {orderId: "A", product: "Gadget"}, {orderId: "B", product: "Gizmo"}]

// Make hierarchical
let nested = nestBy(uniqueOrders, flat, (o) -> o.orderId, (l) -> l.orderId, "lines")

// Back to flat
let flatAgain = unnest(nested, "lines")
// flatAgain ≈ flat (with parent fields added)
```

## Field Name Collision

What if a parent field and child field have the same name?

```json
{
  "id": "ORD-001",           // parent has "id"
  "lines": [
    {"id": "LINE-001", ...}  // child also has "id"
  ]
}
```

After `unnest()`, which `id` wins?

**Proposed rule: child fields override parent fields** (same as spread: `{...parent, ...child}`). This matches the behavior of SQL LATERAL JOIN and is the least surprising — the more specific (child) value takes precedence.

If you need both, rename before unnesting:

```utlx
let prepared = map($input.orders, (o) -> {
  ...o,
  lines: map(o.lines, (l) -> {
    ...l,
    lineId: l.id    // rename child's id to lineId
  })
})
unnest(prepared, "lines")
// Now has both orderId (from parent) and lineId (from child, renamed)
```

## Edge Cases

| Case | Behavior |
|------|----------|
| Parent has no children (empty array) | Parent is excluded from output (no rows to produce) |
| Parent has null for child property | Parent is excluded |
| Child property doesn't exist | Parent is excluded |
| Parent has 1 child | 1 flat record (parent + child merged) |
| Child has same field name as parent | Child value wins (overrides parent) |
| Empty input array | Returns empty array |

Note: parents with no children are **excluded** (not included with empty child fields). This matches SQL UNNEST/LATERAL JOIN behavior. If you want to keep childless parents, use a left-unnest pattern:

```utlx
// Keep parents even without children:
flatten(map($input.orders, (order) ->
  if (count(order.lines ?? []) == 0)
    [{...order}]    // parent-only row, no line fields
  else
    map(order.lines, (line) -> {
      let parent = {...order}
      // remove "lines" from parent to avoid nesting in output
      {...parent, ...line}
    })
))
```

## Performance and Memory

```
Input: N parents, total M children across all parents
Processing: iterate parents, iterate their children — O(N + M) total
Memory: M new flat records, each referencing parent and child field values
        No deep copy of values — references to original UDM scalars

For 500 orders × 10 lines each = 5,000 flat records:
  Time: sub-millisecond
  Memory: ~5,000 × 200 bytes (object shells) = ~1 MB
```

## Implementation

```kotlin
"unnest" to { args: List<RuntimeValue> ->
    val array = args[0].asArray()
    val childPropName = args[1].asString()
    
    val result = mutableListOf<RuntimeValue>()
    
    for (parent in array) {
        val parentObj = parent.asObject() ?: continue
        val children = parentObj.properties[childPropName]?.asArray() ?: continue
        
        // Parent fields without the child array property
        val parentFields = parentObj.properties.filterKeys { it != childPropName }
        
        for (child in children) {
            val childObj = child.asObject()
            if (childObj != null) {
                // Merge: parent fields + child fields (child overrides on collision)
                val merged = parentFields + childObj.properties
                result.add(RuntimeValue.ObjectValue(merged))
            }
        }
    }
    
    RuntimeValue.ArrayValue(result)
}
```

~20 lines. Clean and straightforward.

## Effort Estimate

| Task | Effort |
|------|--------|
| Implement in stdlib | 0.5 day |
| Unit tests | 0.5 day |
| Conformance tests (JSON→CSV with unnest) | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **2 days** |

---

*Feature document F06. April 2026.*
