---
title: reduceEntries
description: "reduceEntries — UTL-X Object function. Reduce all entries in an object to a single value."
pageClass: stdlib-page
---

# reduceEntries

<p class="stdlib-meta"><code>reduceEntries(object, initial, accumulator) → value</code> · <a href="/reference/stdlib#object">Object</a></p>

Reduce all entries in an object to a single value.

- `object` (required): the source object

- `initial` (required): starting accumulator value

- `accumulator` (required): lambda `(acc, key, value) -> newAcc`

``` utlx
reduceEntries({a: 1, b: 2, c: 3}, 0, (acc, k, v) -> acc + v)  // 6
```
