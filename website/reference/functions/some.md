---
title: some
description: "some — UTL-X Array function. Check if any element in the array matches the predicate."
pageClass: stdlib-page
---

# some

<p class="stdlib-meta"><code>some(array, predicate) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Check if any element in the array matches the predicate.

- `array` (required): the array to test

- `predicate` (required): lambda `(element) -> boolean`

``` utlx
some([1, 2, 3, 4], (x) -> x > 3)        // true
some([1, 2, 3], (x) -> x > 5)           // false
```
