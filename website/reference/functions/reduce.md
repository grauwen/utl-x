---
title: reduce
description: "reduce — UTL-X Array function. Accumulate a single result by processing each element sequentially. The"
pageClass: stdlib-page
---

# reduce

<p class="stdlib-meta"><code>reduce(array, initial, accumulator) → value</code> · <a href="/reference/stdlib#array">Array</a></p>

Accumulate a single result by processing each element sequentially. The
most powerful array function — and the most error-prone.

- `array` (required): the array to reduce

- `initial` (required): the starting value for the accumulator

- `accumulator` (required): lambda
  `(accumulated, element) -> newAccumulated`

``` utlx
// Sum numbers:
reduce([10, 20, 30], 0, (sum, x) -> sum + x)
// Step by step: 0→10→30→60.  Output: 60

// Build a comma-separated string:
reduce(["Alice", "Bob", "Charlie"], "", (acc, name) ->
  if (acc == "") name else concat(acc, ", ", name)
)
// "Alice, Bob, Charlie"

// Build a lookup object from an array:
reduce($input, {}, (acc, item) -> {
  ...acc,
  [item.id]: item.name
})
// {"A": "Widget", "B": "Gadget"}

// Count occurrences:
reduce(["a", "b", "a", "c", "a"], {}, (acc, x) -> {
  ...acc,
  [x]: (acc[x] ?? 0) + 1
})
// {"a": 3, "b": 1, "c": 1}
```

**Anti-pattern:** using `reduce` for operations that have dedicated
functions:

``` utlx
// BAD — use sum() instead:
reduce(arr, 0, (acc, x) -> acc + x)

// BAD — use max() instead:
reduce(arr, 0, (acc, x) -> if (x > acc) x else acc)

// BAD — use join() instead:
reduce(arr, "", (acc, x) -> concat(acc, x, ","))

// GOOD use of reduce — complex accumulation with no dedicated function:
reduce($input.transactions, {balance: 0, count: 0}, (acc, tx) -> {
  balance: acc.balance + tx.amount,
  count: acc.count + 1
})
```
