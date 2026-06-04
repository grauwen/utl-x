---
title: scan
description: "scan — UTL-X Array function. Like reduce but returns all intermediate results as an array."
pageClass: stdlib-page
---

# scan

<p class="stdlib-meta"><code>scan(array, initial, fn) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Like `reduce` but returns all intermediate results as an array.

- `array` (required): the array to scan

- `initial` (required): starting accumulator value

- `fn` (required): lambda `(acc, element) -> newAcc`

``` utlx
scan([1, 2, 3, 4], 0, (acc, x) -> acc + x)
// [1, 3, 6, 10] (running totals)
```
