---
title: chunkBy
description: "chunkBy — UTL-X Array function. Split a flat sequence into chunks. A new chunk starts whenever the"
pageClass: stdlib-page
---

# chunkBy

<p class="stdlib-meta"><code>chunkBy(array, predicate) → array of arrays</code> · <a href="/reference/stdlib#array">Array</a></p>

Split a flat sequence into chunks. A new chunk starts whenever the
predicate returns true.

- `array` (required): the array to split

- `predicate` (required): lambda `(element) -> boolean` — true starts a
  new chunk

``` bash
echo '{"items": [1, 2, 10, 11, 12, 20, 21]}' \
  | utlx -e 'chunkBy($input.items, (x) -> x >= 10 and x % 10 == 0)'
# [[1, 2], [10, 11, 12], [20, 21]]
```

``` utlx
// See Chapter 20 for data restructuring patterns.
{
  groups: chunkBy($input.records, (r) -> r.isHeader)
}
```
