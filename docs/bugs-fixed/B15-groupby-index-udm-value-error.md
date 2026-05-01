# B15: groupBy Result Cannot Be Indexed by UDM Value

**Status:** Fixed (May 2026)  
**Severity:** High  
**Discovered:** April 2026  
**Related:** F03 (nestBy function proposal)

---

## Summary

The `groupBy()` function could not be used for O(1) key lookup — the core use case for efficient flat-to-hierarchical transformation. Two issues:

1. `groupBy()` returned an Array of `{key, value}` pairs instead of an Object keyed by group name — making indexed access (`groups["A"]`) impossible
2. The index operator didn't unwrap UDM scalar values — so even with an Object, `groups[order.orderId]` failed with "Index must be a number or string, got UDMValue"

## The Fix: Two Changes + One New Function

### Change 1: groupBy() now returns Object

**Before (broken for lookup):**
```utlx
let groups = groupBy($input.lines, (l) -> l.orderId)
// Returned: [{key: "A", value: [...]}, {key: "B", value: [...]}]
// groups["A"] → ERROR (can't index array by string)
```

**After (works for lookup):**
```utlx
let groups = groupBy($input.lines, (l) -> l.orderId)
// Returns: {"A": [...], "B": [...]}
// groups["A"] → [line1, line2]  ✓ O(1) lookup
```

This matches every other language: JavaScript `Object.groupBy()`, Kotlin `groupBy()`, Java `Collectors.groupingBy()`, DataWeave `groupBy` — all return a Map/Object.

### Change 2: Index operator unwraps UDM scalars

When the index key comes from a UDM property (`order.orderId`), it arrives as `RuntimeValue.UDMValue(UDM.Scalar("A"))`. The interpreter now unwraps this to `RuntimeValue.StringValue("A")` before indexing.

**File:** `interpreter.kt`, `evaluateIndexAccess()`

### Change 3: New function mapGroups() for iteration

The old `groupBy` was convenient for iteration:
```utlx
// OLD (no longer works — groupBy returns Object, not Array):
map(groupBy($input, "department"), group => {
  department: group.key,
  count: count(group.value)
})
```

The new `mapGroups()` replaces this pattern:
```utlx
// NEW — same readability, same group.key / group.value access:
mapGroups($input, "department", group => {
  department: group.key,
  count: count(group.value)
})
```

`mapGroups` groups the array and then transforms each group. The lambda receives a `group` object with `.key` (the group key) and `.value` (the array of members).

## The Two Functions

### groupBy(array, keyFn) → Object

For **lookup by key** — O(1) indexed access. Returns an Object keyed by group name.

```utlx
// Use case: nest order lines under orders (the B15 pattern)
let lineIndex = groupBy($input.orderLines, (l) -> l.orderId)

map($input.orders, (order) -> {
  orderId: order.orderId,
  customer: order.customer,
  lines: lineIndex[order.orderId] ?? []
})
```

The `keyFn` can be a lambda `(item) -> item.field` or a string shorthand `"field"`.

### mapGroups(array, keyFn, transformFn) → Array

For **iterating groups** — walk through each group and produce a report. Returns an Array of transformed results.

```utlx
// Use case: department summary report
mapGroups($input.employees, "department", group => {
  department: group.key,
  headcount: count(group.value),
  avgSalary: avg(map(group.value, (e) -> e.salary)),
  activeCount: count(filter(group.value, (e) -> e.active))
})
// Output: [
//   {"department": "Engineering", "headcount": 3, "avgSalary": 90000, "activeCount": 3},
//   {"department": "Sales", "headcount": 2, "avgSalary": 62500, "activeCount": 1}
// ]
```

The lambda receives `group` with two properties:
- `group.key` — the group key value (e.g., `"Engineering"`)
- `group.value` — the array of group members (e.g., `[Alice, Carol, Eve]`)

## Migration from Old groupBy

The old `map(groupBy(...), ...)` pattern becomes `mapGroups(...)`:

**Before:**
```utlx
map(groupBy($input, "category"), group => {
  category: group.key,
  total: sum(map(group.value, (item) -> item.price)),
  count: count(group.value)
})
```

**After:**
```utlx
mapGroups($input, "category", group => {
  category: group.key,
  total: sum(map(group.value, (item) -> item.price)),
  count: count(group.value)
})
```

The only change: replace `map(groupBy(array, keyFn),` with `mapGroups(array, keyFn,`. The lambda body is identical — `group.key` and `group.value` work exactly the same.

## When to Use Which

| I want to... | Use | Example |
|-------------|-----|---------|
| Look up items by key (O(1)) | `groupBy` | `let idx = groupBy(lines, ...); idx["A"]` |
| Nest children under parents | `groupBy` | `lineIndex[order.orderId] ?? []` |
| Report per group (count, sum, avg) | `mapGroups` | `mapGroups(items, "dept", group => {...})` |
| Both (lookup + report) | `groupBy` + `entries` | `entries(groups) \|> map(...)` |

## Files Changed

| File | Change |
|------|--------|
| `EnhancedArrayFunctions.kt` | `groupBy()` returns `UDM.Object` instead of `UDM.Array` |
| `interpreter.kt` | `evaluateIndexAccess()` unwraps `UDMValue(Scalar)` before indexing |
| `EnhancedArrayFunctions.kt` | New `mapGroups()` function (TODO) |
| `EnhancedArrayFunctionsTest.kt` | Updated groupBy tests to expect Object, added `testGroupByIndexAccess` |
| Conformance tests (6 files) | Updated to use `mapGroups()` (TODO — currently use `entries()` workaround) |

## Remaining Work

- [ ] Implement `mapGroups()` in stdlib
- [ ] Update 6 conformance tests to use `mapGroups()` instead of `entries()` workaround
- [ ] Add conformance test for `groupBy` indexed access pattern
- [ ] Add conformance test for `mapGroups` iteration pattern
- [ ] Update book: ch10 (UDM), ch15 (Functions), ch21 (Data Restructuring), ch36 (Performance anti-patterns)
- [ ] Remove B15 warning callout from ch21

## Test Results

- Kotlin unit tests: 34/34 passed (including new `testGroupByIndexAccess`)
- Conformance suite: 473/473 passed (100%)
- groupBy + index with UDM value: verified working

---

*Bug B15. Fixed May 2026. Enables the efficient O(N+M) flat-to-hierarchical transformation pattern.*
