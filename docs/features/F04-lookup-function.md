# F04: lookupBy() Function — Enrich Records from a Reference Table

**Status:** Proposed  
**Priority:** Medium  
**Created:** April 2026  
**Related:** F03 (nestBy function), B15 (groupBy index bug)

---

## The Problem — Explained Simply

Imagine you have an order:

```json
{"orderId": "ORD-001", "customerId": "C-42", "total": 299.99}
```

And a customer list:

```json
[
  {"id": "C-41", "name": "Globex Inc", "country": "US"},
  {"id": "C-42", "name": "Acme Corp", "country": "NL"},
  {"id": "C-43", "name": "Initech BV", "country": "DE"}
]
```

You want to ADD the customer name to the order:

```json
{"orderId": "ORD-001", "customerId": "C-42", "customerName": "Acme Corp", "country": "NL", "total": 299.99}
```

This is **enrichment** — you look up a value from a reference table and add it to your record. It's NOT nesting (you don't want the entire customer record stuffed inside the order as a child array). You want specific fields plucked from the matching record and added alongside the existing fields.

This happens in almost every integration:

- Order has `customerId` → look up customer name and address
- Invoice has `productCode` → look up product description and weight
- Employee has `departmentId` → look up department name and manager
- Transaction has `currencyCode` → look up exchange rate and symbol
- Shipment has `countryCode` → look up country name and tax rate

## Why This Is Different from nestBy()

`nestBy()` (F03) **nests children under a parent**:

```
nestBy(orders, orderLines, ..., "lines")

Result: {orderId: "ORD-001", lines: [{...}, {...}, {...}]}
                                ↑
                                Array of ALL matching children nested as a property
```

`lookupBy()` **finds ONE matching record and extracts fields from it**:

```
lookupBy(order, customers, order.customerId, customer.id)

Result: {id: "C-42", name: "Acme Corp", country: "NL"}
         ↑
         The ONE matching record (not an array, not nested)
```

The difference:

| | join() | lookupBy() |
|---|---|---|
| **Purpose** | Nest children under parent | Find one matching record |
| **Result** | Parent + array of children | Single matching record (or null) |
| **Cardinality** | 1:N (one parent, many children) | 1:1 (one record, one match) |
| **Use case** | Order → order lines | Order → customer name |

## Proposed Function

### Signature

```
lookupBy(sourceValue, referenceArray, matchFunction) → Object or null
```

### Parameters

| Parameter | Type | What it does |
|-----------|------|-------------|
| `sourceValue` | Any | The value to search for (e.g., a customer ID from the order) |
| `referenceArray` | Array | The reference table to search in (e.g., all customers) |
| `matchFunction` | Lambda | How to extract the key from each reference record for comparison |

### Return Value

The **first matching record** from the reference array, or `null` if no match found.

### How It Works — Step by Step

```utlx
lookupBy("C-42", $input.customers, (c) -> c.id)
```

1. Take the value `"C-42"` (this is what we're looking for)
2. Go through each record in `$input.customers`
3. For each customer, call the lambda: `(c) -> c.id` → extracts `"C-41"`, `"C-42"`, `"C-43"`
4. When the lambda result equals `"C-42"` → return that customer record
5. Result: `{"id": "C-42", "name": "Acme Corp", "country": "NL"}`

### Using the Result

After `lookupBy()` gives you the matching record, you use it with dot notation:

```utlx
let customer = lookupBy($input.order.customerId, $input.customers, (c) -> c.id)

{
  orderId: $input.order.orderId,
  total: $input.order.total,
  customerName: customer.name,       // ← "Acme Corp" from the lookup result
  country: customer.country,          // ← "NL" from the lookup result
  shipping: if (customer.country == "NL") "domestic" else "international"
}
```

### Full Real-World Example

Input message with orders and a customer reference table:

```json
{
  "orders": [
    {"orderId": "ORD-001", "customerId": "C-42", "total": 299.99},
    {"orderId": "ORD-002", "customerId": "C-41", "total": 150.00},
    {"orderId": "ORD-003", "customerId": "C-42", "total": 89.50}
  ],
  "customers": [
    {"id": "C-41", "name": "Globex Inc", "country": "US", "vatId": "US-123"},
    {"id": "C-42", "name": "Acme Corp", "country": "NL", "vatId": "NL-456"},
    {"id": "C-43", "name": "Initech BV", "country": "DE", "vatId": "DE-789"}
  ]
}
```

Transformation:

```utlx
map($input.orders, (order) -> {
  let customer = lookupBy(order.customerId, $input.customers, (c) -> c.id)

  orderId: order.orderId,
  total: order.total,
  customerName: customer?.name ?? "Unknown",
  country: customer?.country ?? "XX",
  vatId: customer?.vatId,
  domestic: customer?.country == "NL"
})
```

Output:

```json
[
  {"orderId": "ORD-001", "total": 299.99, "customerName": "Acme Corp",  "country": "NL", "vatId": "NL-456", "domestic": true},
  {"orderId": "ORD-002", "total": 150.00, "customerName": "Globex Inc", "country": "US", "vatId": "US-123", "domestic": false},
  {"orderId": "ORD-003", "total": 89.50,  "customerName": "Acme Corp",  "country": "NL", "vatId": "NL-456", "domestic": true}
]
```

Notice: customer C-42 (Acme Corp) is looked up twice (for ORD-001 and ORD-003). The function finds the same record both times.

## Wait — Can't We Already Do This with find()?

Yes! `find()` already exists in the stdlib:

```utlx
let customer = find($input.customers, (c) -> c.id == order.customerId)
```

This does exactly the same thing. So why propose `lookupBy()`?

### The Problem with find()

`find()` scans the entire array every time. If you have 500 orders and 10,000 customers:

```
find() called 500 times × scans 10,000 customers each time = 5,000,000 comparisons
```

That's O(N × M) — the same performance trap as using `filter()` instead of `join()`.

### What lookupBy() Adds: Internal Indexing

`lookupBy()` could be smart about repeated lookups on the same reference array:

**First call:** builds a hash index internally (O(M) — scan customers once)  
**Subsequent calls:** O(1) lookup per order (hash map get)  
**Total:** O(N + M) instead of O(N × M)

```
lookupBy() with internal cache:
  First call:  build index on $input.customers by (c) -> c.id  → O(10,000)
  500 lookups: each is O(1) hash map get                        → O(500)
  Total: O(10,500) vs O(5,000,000) with find()
```

BUT — this optimization only works if the engine detects that the same reference array and same match function are used across multiple calls. This is a runtime optimization, not a language feature.

### Alternative: Use groupBy + index (manual caching)

```utlx
let customerIndex = groupBy($input.customers, (c) -> c.id)

map($input.orders, (order) -> {
  let customer = first(customerIndex[order.customerId] ?? [])
  ...
})
```

This is the efficient O(N + M) approach — but it's blocked by B15 (groupBy index bug). And even when B15 is fixed, it's verbose: `first(customerIndex[order.customerId] ?? [])` is not readable.

### Verdict: lookupBy() Is Syntactic Sugar + Future Optimization Path

For v1, `lookupBy()` can be implemented as a simple wrapper around `find()` — identical performance, but cleaner API:

```kotlin
// Simple implementation (v1):
"lookupBy" to { args ->
    val searchValue = args[0]
    val refArray = args[1].asArray()
    val keyFn = args[2].asFunction()
    refArray.find { item -> keyFn(listOf(item)) == searchValue }
        ?: RuntimeValue.NullValue
}
```

For v2, the engine can add internal caching — detect repeated lookups against the same reference array and build a hash index automatically. The function signature stays the same; only the internal implementation changes.

## Common Lookup Patterns

### Simple field enrichment
```utlx
let dept = lookupBy(emp.departmentId, $input.departments, (d) -> d.id)
{...emp, departmentName: dept?.name}
```

### Currency conversion
```utlx
let rate = lookupBy($input.currency, $input.exchangeRates, (r) -> r.code)
{amount: $input.amount * (rate?.rate ?? 1), currency: "EUR"}
```

### Code-to-description mapping
```utlx
let country = lookupBy($input.countryCode, $input.countries, (c) -> c.code)
{code: $input.countryCode, name: country?.name ?? $input.countryCode}
```

### With fallback for missing matches
```utlx
let product = lookupBy(line.productCode, $input.catalog, (p) -> p.sku)
{
  code: line.productCode,
  description: product?.description ?? "Unknown product",
  weight: product?.weight ?? 0,
  hazardous: product?.hazardous ?? false
}
```

## lookupBy() vs nestBy() vs find() — When to Use Which

| Situation | Use | Why |
|-----------|-----|-----|
| Nest children under parents (1:N) | `nestBy()` | Creates hierarchy: order.lines[] |
| Enrich with ONE matching record (1:1) | `lookupBy()` | Finds one match: customer.name |
| Find first match (one-time) | `find()` | Simple search, no caching needed |
| Filter matches (keep all) | `filter()` | Returns array of ALL matches |
| Group by key | `groupBy()` | Returns map of key → array |

## Effort Estimate

| Task | Effort |
|------|--------|
| Implement lookupBy() in stdlib (simple find wrapper) | 0.5 day |
| Unit tests | 0.5 day |
| Conformance suite tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **2 days** |

Future optimization (internal caching): additional 1-2 days when performance data justifies it.

## Test Plan

1. Basic lookup: find matching record → return it
2. No match: return null
3. Multiple matches: return first
4. Null search value: handle gracefully
5. Empty reference array: return null
6. Used inside map (repeated lookups): correct results
7. Combined with safe navigation: `lookupBy(...)?.name`
8. Real-world: order enrichment with customer data

---

*Feature document F04. April 2026.*
