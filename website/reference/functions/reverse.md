---
title: reverse
description: "reverse — UTL-X Array function. Reverse the order of elements in an array."
pageClass: stdlib-page
---

# reverse

<p class="stdlib-meta"><code>reverse(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Reverse the order of elements in an array.

- `array` (required): the array to reverse

``` utlx
reverse([1, 2, 3, 4, 5])                // [5, 4, 3, 2, 1]

// Use case: most recent first
reverse(sortBy($input.events, (e) -> e.timestamp))
```
