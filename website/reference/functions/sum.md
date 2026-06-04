---
title: sum
description: "sum — UTL-X Math function. Sum all numeric values in an array. Returns 0 for empty arrays."
pageClass: stdlib-page
---

# sum

<p class="stdlib-meta"><code>sum(array) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Sum all numeric values in an array. Returns 0 for empty arrays.

- `array` (required): array of numbers

``` utlx
sum([10, 20, 30])                        // 60
sum([])                                  // 0 (empty array)
```

**Anti-pattern:** `reduce($input.items, 0, (acc, i) -> acc + i.price)` —
use `sum(map(...))` or `sumBy()`.
