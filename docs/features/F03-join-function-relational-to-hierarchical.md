# F03: join() Function — Relational-to-Hierarchical Restructuring

**Status:** Proposed  
**Priority:** Medium  
**Created:** April 2026  
**Related:** N-to-M mapping architecture document, F02 (let binding consistency)

---

## Summary

Add a `join()` function that restructures flat/relational data into hierarchical data by matching parent and child records on key fields. This is the most common operation when processing SAP IDocs, EDI segments, database exports, and CSV files with foreign key relationships.

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
join(parents, children, parentKey, childKey, childPropertyName) → Array
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `parents` | Array | The parent records (e.g., order headers) |
| `children` | Array | The child records (e.g., order lines) |
| `parentKey` | Lambda `(parent) → value` | Function extracting the join key from a parent |
| `childKey` | Lambda `(child) → value` | Function extracting the join key from a child |
| `childPropertyName` | String | Property name for the nested children array |

### Return Value

A new Array where each parent object has an additional property (named by `childPropertyName`) containing an Array of matching children.

### Example

```utlx
join(
  $input.headers,                    // parents
  $input.lines,                      // children
  (h) -> h.orderId,                  // parent key extractor
  (l) -> l.orderId,                  // child key extractor
  "lines"                            // nested property name
)
```

### Multi-Level Nesting

Chain for deeper hierarchies:

```utlx
let withLines = join($input.headers, $input.lines,
  (h) -> h.orderId, (l) -> l.orderId, "lines")

let withSchedules = join(withLines[*].lines, $input.schedules,
  (l) -> l.lineId, (s) -> s.lineId, "schedules")
```

Actually, this doesn't work cleanly — `join` returns a new array of parents with children nested, but the second join needs to operate on the already-nested lines. This reveals a design issue.

### Alternative: Nested join

```utlx
let result = join($input.headers, $input.lines,
  (h) -> h.orderId, (l) -> l.orderId, "lines")

// For multi-level, map over the result and join children of children
map(result, (order) -> {
  ...order,
  lines: join(order.lines, $input.schedules,
    (l) -> l.lineId, (s) -> s.lineId, "schedules")
})
```

This works but the multi-level case is still somewhat verbose. The single-level case (which covers 80% of real-world needs) is clean.

## Where Should join() Live?

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

If profiling shows `join()` is a bottleneck for large datasets (10,000+ records), optimize to Option C — same external behavior, faster internal implementation.

Do NOT use Option B. Adding `join` to the interpreter/compiler treats a data operation as a language construct. `join` is semantically similar to `groupBy` + `map` — it's a library function, not a language keyword.

## Complexity Analysis

### The groupBy approach (current)

```
groupBy: O(M) to build index
map:     O(N) to iterate parents
lookup:  O(1) per parent (hash map)
Total:   O(N + M)
```

### The join() function

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

With 1,000 parents and 10,000 children: 10,000,000 comparisons vs 11,000. The `join()` function prevents beginners from falling into this O(N × M) trap.

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

The basic `join()` is an **inner-ish join** — all parents are kept, matched children are nested. This covers 95% of integration use cases.

For advanced join types, users can compose with existing functions:

```utlx
// Left join (keep parents without children) — default behavior of join()
// Right join (find orphan children)
let matchedKeys = map(parents, (p) -> parentKey(p))
let orphans = filter(children, (c) -> !contains(matchedKeys, childKey(c)))

// Full outer join — combine join result with orphans
```

Adding `leftJoin()`, `rightJoin()`, `fullJoin()`, `crossJoin()` is premature. The single `join()` function covers the dominant use case. SQL-style join variants can be added later if demand arises.

## Naming Considerations

| Name | Precedent | Concern |
|------|-----------|---------|
| `join()` | SQL, Spark, pandas | Conflicts with string `join()` — but UTL-X string join is `join(array, separator)` with different arity |
| `nestJoin()` | Unique | Clear intent but unfamiliar |
| `groupJoin()` | C# LINQ | Describes the operation well |
| `hierarchize()` | WTX-inspired | Too long, unfamiliar |
| `nest()` | D3.js | Too generic |

**Recommendation:** `join()` — same name as every other data tool. The 5-parameter signature (parents, children, parentKey, childKey, propName) is unambiguous. String `join()` has 2 parameters (array, separator) — no collision due to different arity.

## Effort Estimate

| Task | Effort |
|------|--------|
| Implement `join()` in stdlib | 1 day |
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

## Book Impact

- Chapter 9 (UDM): already has the flat data section — add `join()` as the recommended approach
- Chapter 14 (Standard Library): add `join()` to the Array functions category
- Chapter 29 (Enterprise Integration): SAP IDoc case study should use `join()`
- Chapter 48 (stdlib reference): add full documentation

---

*Feature document F03. April 2026.*
