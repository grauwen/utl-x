---
title: head
description: "head — UTL-X Array function. Alias for first(). Returns the first element of an array, or null if"
pageClass: stdlib-page
---

# head

<p class="stdlib-meta"><code>head(array) → element or null</code> · <a href="/reference/stdlib#array">Array</a></p>

Alias for `first()`. Returns the first element of an array, or `null` if
empty.

- `array` (required): the source array

``` utlx
head(["Apple", "Banana", "Cherry"])      // "Apple"
head([])                                 // null
```
