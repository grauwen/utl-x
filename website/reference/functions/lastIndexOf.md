---
title: lastIndexOf
description: "lastIndexOf — UTL-X String function. Find the position of the LAST occurrence of a value. Returns -1 if not"
pageClass: stdlib-page
---

# lastIndexOf

<p class="stdlib-meta"><code>lastIndexOf(haystack, needle) → number</code> · <a href="/reference/stdlib#string">String</a></p>

Find the position of the LAST occurrence of a value. Returns -1 if not
found.

- `haystack` (required): string or array to search in

- `needle` (required): value to find

``` utlx
lastIndexOf("abcabc", "bc")              // 4 (last occurrence, not first at 1)
lastIndexOf(["A", "B", "A", "C"], "A")   // 2 (last A)
lastIndexOf("hello", "xyz")              // -1
```

Also: `findIndex(array, predicate)` — find by condition instead of
value. `findLastIndex(array, predicate)` — last match by condition.
