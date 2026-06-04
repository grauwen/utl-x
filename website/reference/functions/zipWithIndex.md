---
title: zipWithIndex
description: "zipWithIndex — UTL-X Array function. Pair each element with its zero-based index."
pageClass: stdlib-page
---

# zipWithIndex

<p class="stdlib-meta"><code>zipWithIndex(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Pair each element with its zero-based index.

- `array` (required): the array to index

``` utlx
zipWithIndex(["Apple", "Banana", "Cherry"])  // [["Apple", 0], ["Banana", 1], ["Cherry", 2]]
```

``` utlx
map(zipWithIndex($input.items), (pair) -> {
  lineNumber: pair[1] + 1,
  ...pair[0]
})
```
