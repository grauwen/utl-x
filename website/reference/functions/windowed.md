---
title: windowed
description: "windowed — UTL-X Array function. Create a sliding window over an array. Returns overlapping sub-arrays of"
pageClass: stdlib-page
---

# windowed

<p class="stdlib-meta"><code>windowed(array, size) → array of arrays</code> · <a href="/reference/stdlib#array">Array</a></p>

Create a sliding window over an array. Returns overlapping sub-arrays of
the given size.

- `array` (required): the source array

- `size` (required): window size

``` utlx
windowed([1, 2, 3, 4, 5], 3)               // [[1, 2, 3], [2, 3, 4], [3, 4, 5]]
windowed([1, 2, 3, 4, 5], 2)               // [[1, 2], [2, 3], [3, 4], [4, 5]]
```

``` utlx
let prices = $input.dailyPrices
{
  movingAvg3day: map(windowed(prices, 3), (w) -> avg(w)),
  consecutiveDups: filter(windowed($input.events, 2), (pair) -> pair[0].type == pair[1].type)
}
```
