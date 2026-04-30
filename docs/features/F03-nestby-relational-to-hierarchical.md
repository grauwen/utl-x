# F03: nestBy() Function — Relational-to-Hierarchical Restructuring

**Status:** Proposed  
**Priority:** Medium  
**Created:** April 2026  
**Related:** N-to-M mapping architecture document, F02 (let binding consistency)

---

## Summary

Add a `nestBy()` function that restructures flat/relational data into hierarchical data by matching parent and child records on key fields. This is the most common operation when processing SAP IDocs, EDI segments, database exports, and CSV files with foreign key relationships.

## The Use Case

Flat data with parent-child relationships expressed through keys (not nesting):

```
Headers:  [{orderId: "A", currency: "EUR"}, {orderId: "B", currency: "USD"}]
Lines:    [{orderId: "A", product: "Widget"}, {orderId: "A", product: "Gadget"}, {orderId: "B", product: "Gizmo"}]
```

Desired hierarchical output:

```json
[
  {"orderId": "A", "currency": "EUR", "lines": [
    {"orderId": "A", "product": "Widget"},
    {"orderId": "A", "product": "Gadget"}
  ]},
  {"orderId": "B", "currency": "USD", "lines": [
    {"orderId": "B", "product": "Gizmo"}
  ]}
]
```

## Current Approach (without join)

```utlx
let linesByOrder = groupBy($input.lines, (l) -> l.orderId)
map($input.headers, (h) -> {
  ...h,
  lines: linesByOrder[h.orderId] ?? []
})
```

This works but requires:
1. The developer to know `groupBy` is the right pre-indexing strategy
2. Manual key extraction with lambda functions
3. Manual property naming for the nested result
4. Repeating the pattern for each level of nesting
5. Understanding that `groupBy` returns a map keyed by the lambda result

For experienced functional programmers this is fine. For integration developers coming from visual mappers (Tibco BW, SAP CPI, Mercator/WTX), this is a barrier.

## Proposed Function

### Signature

```
nestBy(parents, children, parentKey, childKey, childPropertyName) → Array
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `parents` | Array | The parent records (e.g., order headers) |
| `children` | Array | The child records (e.g., order lines) |
| `parentKey` | Lambda `(parent) → value` | Function extracting the join key from a parent |
| `childKey` | Lambda `(child) → value` | Function extracting the join key from a child |
| `childPropertyName` | String | **The name of the new property** that will be added to each parent object, containing its matched children as an array |

### Return Value

A new Array where each parent object has an **additional property** (named by `childPropertyName`) containing an Array of matching children. The original parent properties are preserved.

### How childPropertyName Works

The 5th parameter is a string that becomes a new property name on each parent object. This is the bridge between the flat structure and the hierarchical output:

```
BEFORE nestBy():
  order = {"orderId": "ORD-001", "customer": "Acme"}

AFTER nestBy(..., "lines"):
  order = {"orderId": "ORD-001", "customer": "Acme", "lines": [{...}, {...}]}
                                                       ^^^^^^
                                                       This property was CREATED
                                                       by nestBy(). Its name comes
                                                       from the "lines" parameter.
```

You choose the name. It can be anything meaningful:
- `"lines"` → `order.lines`
- `"items"` → `order.items`
- `"orderLines"` → `order.orderLines`
- `"children"` → `order.children`

After nestBy(), the new property is accessed with dot notation like any other property.

### Example

```utlx
// Step 1: join creates the hierarchy
let enrichedOrders = nestBy(
  $input.orders,                     // parents
  $input.orderLines,                 // children
  (order) -> order.orderId,          // parent key: match on orderId
  (line) -> line.orderId,            // child key: match on orderId
  "lines"                            // add matched children as "lines" property
)

// Step 2: use the joined result — each order now HAS a .lines property
map(enrichedOrders, (order) -> {
  orderId: order.orderId,
  customer: order.customer,
  lineCount: count(order.lines),                              // ← .lines exists now
  total: sum(map(order.lines, (l) -> l.qty * l.price)),       // ← iterate .lines
  mostExpensive: sortBy(order.lines, (l) -> -l.price) |> first()
})
```

### Design Note: Named Parameters (Future)

The string parameter `"lines"` is not ideal — it's a string that becomes a property name, which feels indirect. With named parameters (not yet in UTL-X), this would read more naturally:

```utlx
// Hypothetical future syntax with named parameter:
nestBy($input.orders, $input.orderLines,
  parentKey: (o) -> o.orderId,
  childKey: (l) -> l.orderId,
  as: "lines"                                // ← "as" makes intent clear
)
```

For v1, the positional string parameter is the implementation. The 5-parameter signature is unambiguous — developers learn it once.

### Multi-Level Nesting

Chain for deeper hierarchies:

```utlx
let withLines = nestBy($input.headers, $input.lines,
  (h) -> h.orderId, (l) -> l.orderId, "lines")

let withSchedules = nestBy(withLines[*].lines, $input.schedules,
  (l) -> l.lineId, (s) -> s.lineId, "schedules")
```

Actually, this doesn't work cleanly — `join` returns a new array of parents with children nested, but the second join needs to operate on the already-nested lines. This reveals a design issue.

### Alternative: Nested join

```utlx
let result = nestBy($input.headers, $input.lines,
  (h) -> h.orderId, (l) -> l.orderId, "lines")

// For multi-level, map over the result and join children of children
map(result, (order) -> {
  ...order,
  lines: nestBy(order.lines, $input.schedules,
    (l) -> l.lineId, (s) -> s.lineId, "schedules")
})
```

This works but the multi-level case is still somewhat verbose. The single-level case (which covers 80% of real-world needs) is clean.

## Where Should nestBy() Live?

This is the key architectural question. There are three options:

### Option A: stdlib Function (like groupBy, map, filter)

**Implementation:** A native function in `stdlib/src/main/kotlin/.../StdlibFunctions.kt`

```kotlin
"join" to { args: List<RuntimeValue> ->
    val parents = args[0].asArray()
    val children = args[1].asArray()
    val parentKeyFn = args[2].asFunction()
    val childKeyFn = args[3].asFunction()
    val propName = args[4].asString()
    
    // Build index: childKey → List<child>
    val index = mutableMapOf<Any?, MutableList<RuntimeValue>>()
    for (child in children) {
        val key = childKeyFn.invoke(listOf(child))
        index.getOrPut(key.toNative()) { mutableListOf() }.add(child)
    }
    
    // Join: add children to each parent
    RuntimeValue.ArrayValue(parents.map { parent ->
        val key = parentKeyFn.invoke(listOf(parent))
        val matchingChildren = index[key.toNative()] ?: emptyList()
        val parentObj = parent.asObject()
        RuntimeValue.ObjectValue(
            parentObj.properties + mapOf(propName to RuntimeValue.ArrayValue(matchingChildren))
        )
    })
}
```

**Pros:**
- Consistent with existing stdlib pattern (groupBy, map, filter are all stdlib)
- No core engine changes
- Available in all three executables (utlx, utlxd, utlxe)
- Discoverable via `utlx functions --search join`

**Cons:**
- Operates on `RuntimeValue`, not `UDM` — the interpreter wraps/unwraps between them
- Lambda invocation per record — function call overhead
- No access to engine-level optimizations (compiled strategy can't optimize stdlib calls)

### Option B: Core Interpreter Function (like `map`, `filter`)

**Implementation:** In `interpreter.kt` as a special-case expression, similar to how `map` and `filter` are handled.

**Pros:**
- Direct access to UDM — no RuntimeValue wrapping overhead
- Can be optimized by the COMPILED strategy (bytecode generation)
- Faster for large datasets (avoids lambda call overhead per record)

**Cons:**
- Changes to the interpreter are higher risk (affects all transformations)
- More complex implementation (AST node, interpreter case, compiler case)
- Breaks the principle that the core is format-agnostic (join is a data operation, not a language operation)

### Option C: stdlib Function with UDM-Level Implementation

**Implementation:** stdlib function (like Option A) but internally operates on UDM types directly instead of going through RuntimeValue.

```kotlin
// In stdlib, but using UDM types for efficiency
"join" to { args: List<RuntimeValue> ->
    // Extract UDM arrays directly
    val parentUdm = (args[0] as RuntimeValue.UDMValue).udm as UDM.Array
    val childUdm = (args[1] as RuntimeValue.UDMValue).udm as UDM.Array
    // ... build index on UDM.Scalar keys, produce UDM.Array result
}
```

**Pros:**
- Lives in stdlib (clean architecture)
- Operates on UDM directly (performance)
- No interpreter changes
- Available via function registry

**Cons:**
- stdlib functions normally operate on RuntimeValue — this breaks the abstraction
- Needs access to UDM types (stdlib depends on core, which it already does)
- Lambda handling still goes through RuntimeValue

### Recommendation: Option A (stdlib), with Option C as optimization

Start with a pure stdlib function (Option A). It's the simplest to implement, test, and maintain. The function call overhead is negligible for typical data sizes (hundreds to thousands of records).

If profiling shows `nestBy()` is a bottleneck for large datasets (10,000+ records), optimize to Option C — same external behavior, faster internal implementation.

Do NOT use Option B. Adding `join` to the interpreter/compiler treats a data operation as a language construct. `join` is semantically similar to `groupBy` + `map` — it's a library function, not a language keyword.

## Complexity Analysis

### The groupBy approach (current)

```
groupBy: O(M) to build index
map:     O(N) to iterate parents
lookup:  O(1) per parent (hash map)
Total:   O(N + M)
```

### The nestBy() function

```
Build index: O(M) — hash children by key
Join:        O(N) — iterate parents, O(1) lookup per parent
Total:       O(N + M) — same as manual groupBy
```

Performance is identical. The benefit is **readability and discoverability**, not performance.

### The naive approach (filter per parent — what beginners write)

```
For each parent: O(M) to scan all children
Total:           O(N × M)
```

With 1,000 parents and 10,000 children: 10,000,000 comparisons vs 11,000. The `nestBy()` function prevents beginners from falling into this O(N × M) trap.

## Edge Cases

| Case | Behavior |
|------|----------|
| Child with no matching parent | Dropped (orphan children ignored) |
| Parent with no matching children | Empty array for the child property |
| Null key in parent | Matches children with null key |
| Null key in child | Grouped under null key |
| Duplicate keys in parents | Each parent gets the same children (1:N from child perspective) |
| Empty parents array | Returns empty array |
| Empty children array | Each parent gets empty children array |

## What About Left/Right/Full/Cross Joins?

The basic `nestBy()` is an **inner-ish join** — all parents are kept, matched children are nested. This covers 95% of integration use cases.

For advanced join types, users can compose with existing functions:

```utlx
// Left join (keep parents without children) — default behavior of nestBy()
// Right join (find orphan children)
let matchedKeys = map(parents, (p) -> parentKey(p))
let orphans = filter(children, (c) -> !contains(matchedKeys, childKey(c)))

// Full outer join — combine join result with orphans
```

Adding `leftJoin()`, `rightJoin()`, `fullJoin()`, `crossJoin()` is premature. The single `nestBy()` function covers the dominant use case. SQL-style join variants can be added later if demand arises.

## Naming: Why nestBy() (Not join())

Originally proposed as `join()`, but renamed to `nestBy()` to avoid confusion with the string `join(array, separator)` function that already exists in UTL-X.

| Name considered | Verdict | Reason |
|------|-----------|---------|
| `join()` | **Rejected** | Conflicts with string join(). Two functions named join() with different meanings is confusing — even with different parameter counts |
| `nestBy()` | **Chosen** | Reads naturally: "nest children by key." Unique, no collision. Clear intent. |
| `groupJoin()` | Alternative | C# LINQ precedent. Good but less intuitive. |
| `nest()` | Too generic | What are you nesting? |
| `hierarchize()` | Too long | WTX-inspired but unfamiliar |

**Decision:** `nestBy()` — reads as "nest orderLines by orderId into lines." No collision with any existing function.

## Effort Estimate

| Task | Effort |
|------|--------|
| Implement `nestBy()` in stdlib | 1 day |
| Unit tests (Kotlin) | 0.5 day |
| Conformance suite tests | 0.5 day |
| Documentation (language guide, book) | 0.5 day |
| **Total** | **2-3 days** |

## Test Plan

1. Basic join: 2 parents, 3 children → correct nesting
2. No matching children: parent gets empty array
3. Orphan children: dropped silently
4. Null keys: handled correctly
5. Large dataset: 1,000 parents, 10,000 children → performance acceptable
6. Multi-level: chained join calls
7. Empty inputs: empty arrays → empty result
8. SAP IDoc example: real-world IDoc XML → hierarchical JSON
9. CSV example: flat CSV with foreign keys → nested JSON

## Real-World Example: Flat Order Message

A common integration pattern: a single message contains a flat sequence of orders followed by a flat sequence of order lines. Each order line references its parent order by key.

### Input (flat JSON message)

```json
{
  "orders": [
    {"orderId": "ORD-001", "customer": "Acme Corp", "currency": "EUR"},
    {"orderId": "ORD-002", "customer": "Globex Inc", "currency": "USD"},
    {"orderId": "ORD-003", "customer": "Initech BV", "currency": "EUR"}
  ],
  "orderLines": [
    {"orderId": "ORD-001", "lineNr": 1, "product": "Widget", "qty": 10, "price": 25.00},
    {"orderId": "ORD-001", "lineNr": 2, "product": "Gadget", "qty": 5, "price": 49.99},
    {"orderId": "ORD-001", "lineNr": 3, "product": "Bolt M8", "qty": 500, "price": 0.12},
    {"orderId": "ORD-002", "lineNr": 1, "product": "Server Rack", "qty": 2, "price": 1200.00},
    {"orderId": "ORD-002", "lineNr": 2, "product": "UPS Battery", "qty": 4, "price": 350.00},
    {"orderId": "ORD-003", "lineNr": 1, "product": "Cable Cat6", "qty": 100, "price": 1.50},
    {"orderId": "ORD-003", "lineNr": 2, "product": "Patch Panel", "qty": 2, "price": 89.00},
    {"orderId": "ORD-003", "lineNr": 3, "product": "Switch 48p", "qty": 1, "price": 650.00},
    {"orderId": "ORD-003", "lineNr": 4, "product": "Fiber SFP", "qty": 8, "price": 35.00}
  ]
}
```

3 orders, 9 order lines. The relationship: `orderLines[n].orderId → orders[m].orderId`.

### Desired output (hierarchical)

```json
[
  {
    "orderId": "ORD-001",
    "customer": "Acme Corp",
    "currency": "EUR",
    "lines": [
      {"lineNr": 1, "product": "Widget", "qty": 10, "price": 25.00, "lineTotal": 250.00},
      {"lineNr": 2, "product": "Gadget", "qty": 5, "price": 49.99, "lineTotal": 249.95},
      {"lineNr": 3, "product": "Bolt M8", "qty": 500, "price": 0.12, "lineTotal": 60.00}
    ],
    "orderTotal": 559.95
  },
  ...
]
```

### Current UTL-X approach (filter — O(N × M), works but slow)

```utlx
map($input.orders, (order) -> {
  orderId: order.orderId,
  customer: order.customer,
  currency: order.currency,
  lines: filter($input.orderLines, (l) -> l.orderId == order.orderId)
})
```

This was tested and works. But for each order, it scans ALL order lines.

### Current UTL-X approach (groupBy — should be O(N + M) but BROKEN)

```utlx
let linesByOrder = groupBy($input.orderLines, (l) -> l.orderId)
map($input.orders, (order) -> {
  ...order,
  lines: linesByOrder[order.orderId] ?? []
})
```

**IMPORTANT FINDING:** This currently produces an error:
```
Error: Index must be a number or string, got UDMValue
```

The `groupBy` function returns a map, but the index lookup `linesByOrder[order.orderId]` fails because `order.orderId` is a UDM value, not a native string. This means **the efficient O(N + M) approach doesn't work today** — users are forced to use the O(N × M) filter approach.

This is a bug (or missing feature) in the groupBy/index interaction. The `nestBy()` function would solve this at the root because it handles the indexing internally.

### With proposed nestBy() function

```utlx
nestBy(
  $input.orders,
  $input.orderLines,
  (order) -> order.orderId,
  (line) -> line.orderId,
  "lines"
)
```

Clean, readable, O(N + M), and no groupBy/index workaround needed.

## Deep Performance and Memory Analysis

### Scenario: Enterprise-scale IDoc batch

A typical SAP IDoc batch message:
- 500 orders (headers)
- 5,000 order lines
- 15,000 schedule lines (delivery schedules per order line)
- Total: 20,500 flat records in one message

### Approach 1: filter() per parent — O(N × M × L)

```
For each order (500):
  Scan all lines (5,000) → 500 × 5,000 = 2,500,000 comparisons
  For each matched line (~10):
    Scan all schedules (15,000) → ~5,000 × 15,000 = 75,000,000 comparisons

Total comparisons: ~77,500,000
```

**Memory:** Original data + output tree. No additional memory (no index). But each comparison involves UDM property access + string comparison → slow.

**Estimated time (8 workers, COMPILED):** ~200-500ms per message. Acceptable for low volume but a bottleneck at 86K msg/s.

### Approach 2: groupBy() + index — O(N + M + L)

```
Build line index (5,000 records, hash by orderId): O(M) = 5,000 operations
Build schedule index (15,000 records, hash by lineId): O(L) = 15,000 operations
Map orders (500), lookup in index: O(N) = 500 operations, each O(1) lookup
Map lines (~10 per order), lookup in index: O(M_matched) = ~5,000 operations, each O(1)

Total operations: ~25,500
```

**Memory:** Original data + two hash maps (line index + schedule index). Each hash map holds references (not copies) to the original UDM objects, so memory overhead is proportional to the number of keys: ~20,500 map entries × ~64 bytes = ~1.3 MB additional.

**Estimated time:** ~5-15ms per message. 100x faster than filter.

**Problem:** As discovered above, `groupBy` index lookup currently fails with UDM values. This approach doesn't work today.

### Approach 3: nestBy() function — O(N + M + L)

Same algorithmic complexity as Approach 2, but:

```
nestBy() internally:
  1. Iterate children array once: extract key for each child → O(M)
  2. Build HashMap<key, List<child>>: O(M) with O(1) amortized insert
  3. Iterate parents: for each parent, lookup children by key → O(N) with O(1) lookup
  4. Construct new parent objects with nested children property → O(N)

Total: O(N + M) — identical to manual groupBy
```

**Memory analysis:**

```
Input message (20,500 records):
  - Parsed UDM: ~20,500 UDM.Object instances
  - Estimated: 20,500 × ~500 bytes = ~10 MB

Index (HashMap inside join):
  - 500 unique order keys, 5,000 unique line keys
  - HashMap overhead: ~5,500 entries × 64 bytes = ~350 KB
  - Values: ArrayList references to existing UDM objects (no copy)
  - Total index memory: ~500 KB

Output (new hierarchical tree):
  - 500 new Order objects (with lines property added)
  - 5,000 new Line objects (with schedules property added)
  - BUT: property values REFERENCE the original UDM scalars (no deep copy)
  - New object shells: ~5,500 × 200 bytes = ~1.1 MB
  - Total output overhead: ~1.5 MB

Peak memory: ~10 MB (input) + ~0.5 MB (index) + ~1.5 MB (output) = ~12 MB
```

This is well within the memory budget for a 2 GB container with 8 workers: 8 × 12 MB = 96 MB peak, leaving >1.5 GB for JVM and other messages.

### Performance comparison summary

| Approach | Operations (500/5K/15K records) | Time estimate | Memory overhead | Works today? |
|----------|-------------------------------|---------------|-----------------|-------------|
| filter() per level | ~77.5 million | 200-500ms | None (but slow) | Yes |
| groupBy() + index | ~25,500 | 5-15ms | ~500 KB (index) | **No (bug)** |
| nestBy() function | ~25,500 | 5-15ms | ~500 KB (index) | Proposed |

### At scale: 86K msg/s throughput target

- With filter(): 200-500ms per message → max 2-5 msg/s per worker → 16-40 msg/s with 8 workers. **Nowhere near 86K.**
- With nestBy(): 5-15ms per message → 66-200 msg/s per worker → 530-1,600 msg/s with 8 workers. Acceptable for IDoc batch processing (IDocs don't arrive at 86K/s — typical is 100-1,000/s).

For simple JSON-to-JSON transformations (no join), 86K msg/s is achievable because there's no join overhead. The nestBy() cost is proportional to the number of records in the flat message.

## Is nestBy() THE Best Solution?

### What nestBy() does well:
- Single-level parent-child: clean, obvious, one function call
- Performance: O(N + M) with hash-based index — optimal
- Memory: references not copies — minimal overhead
- Readability: intent is clear from the function name and parameters
- Discoverability: appears in stdlib function search

### What nestBy() does NOT solve perfectly:

**Multi-level nesting** requires chaining:
```utlx
let withLines = nestBy(orders, lines, ...)
map(withLines, (o) -> {
  ...o,
  lines: nestBy(o.lines, schedules, ...)
})
```
This is workable but not as elegant as a single declarative statement. A `deepJoin()` or recursive join would be cleaner but significantly more complex to design.

**Composite keys** (joining on multiple fields):
```utlx
// Key is orderId + warehouseId — need to concat or create a composite
nestBy(orders, lines,
  (o) -> concat(o.orderId, "|", o.warehouseId),
  (l) -> concat(l.orderId, "|", l.warehouseId),
  "lines")
```
Works but feels hacky. A proper composite key syntax would be:
```utlx
// Hypothetical — NOT proposed
nestBy(orders, lines, ["orderId", "warehouseId"], "lines")
```
But this adds complexity. The concat workaround is acceptable for v1.

**Non-key relationships** (parent-child determined by position, not key):
Some flat formats use sequential position instead of keys — "the next N lines belong to the preceding header." This requires stateful parsing that `nestBy()` can't do. It's a pre-processing step (split the flat stream into keyed records first).

### Alternatives considered:

**1. Fix groupBy + index interaction (bug fix only)**
- Fix the "Index must be a number or string, got UDMValue" error
- Users write groupBy + map manually
- Pro: no new function, fixes existing broken behavior
- Con: still verbose, error-prone, non-discoverable

**2. Add a SQL-like query syntax**
```utlx
select orders.*, lines
from orders
join lines on orders.orderId == lines.orderId
group lines into "lines"
```
- Pro: familiar to SQL developers
- Con: massive parser change, new language subset, scope creep, NOT what UTL-X is

**3. Declarative mapping card (Mercator/WTX style)**
```yaml
join:
  parent: orders
  child: lines
  parentKey: orderId
  childKey: orderId
  as: lines
```
- Pro: clean, declarative, supports multi-level
- Con: new artifact type, new parser, not composable with UTL-X expressions

**4. The nestBy() function (proposed)**
- Pro: uses existing language constructs, composable, discoverable, performant
- Con: multi-level requires chaining, composite keys need workaround

### Verdict

**nestBy() is the best 80/20 solution.** It solves the dominant use case (single-level parent-child by key) with a clean API, optimal performance, and minimal implementation cost. The 20% edge cases (multi-level, composite keys, positional relationships) are handled by existing UTL-X constructs (map, concat, custom pre-processing).

The alternatives are either too simple (just fixing the bug leaves users with verbose code) or too complex (SQL syntax, declarative cards). nestBy() hits the sweet spot.

**However: the groupBy + index bug should ALSO be fixed** regardless of whether nestBy() is implemented. Users should be able to index a groupBy result with a UDM value. That's a separate bug fix, not dependent on F03.

## Updated Priority

Given the finding that groupBy + index lookup is broken, the priority of F03 increases:

- **Without F03:** users are stuck with O(N × M) filter loops for flat-to-hierarchical transformation
- **With F03:** users have O(N + M) join with a clean API
- **Additionally:** the groupBy index bug should be filed as B15 and fixed independently

## Book Impact

- Chapter 9 (UDM): already has the flat data section — add `nestBy()` as the recommended approach
- Chapter 14 (Standard Library): add `nestBy()` to the Array functions category
- Chapter 29 (Enterprise Integration): SAP IDoc case study should use `nestBy()`
- Chapter 48 (stdlib reference): add full documentation
- Chapter 35 (Message Parsing & Memory): add nestBy() memory analysis

---

*Feature document F03. April 2026. Updated with real-world example, performance analysis, groupBy bug discovery, and alternatives evaluation.*

