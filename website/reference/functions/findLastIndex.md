---
title: findLastIndex
description: "findLastIndex — UTL-X Array function. Find the index of the LAST element matching a predicate, or -1 if not"
pageClass: stdlib-page
---

# findLastIndex

<p class="stdlib-meta"><code>findLastIndex(array, predicate) → number</code> · <a href="/reference/stdlib#array">Array</a></p>

Find the index of the LAST element matching a predicate, or `-1` if not
found.

- `array` (required): the array to search

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"items": ["A", "B", "A", "C"]}' | utlx -e 'findLastIndex($input.items, (x) -> x == "A")'
# 2
```

``` utlx
{
  lastActive: findLastIndex($input.users, (u) -> u.active)
}
```
