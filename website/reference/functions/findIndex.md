---
title: findIndex
description: "findIndex — UTL-X Array function. Returns the zero-based index of the FIRST matching element, or -1 if"
pageClass: stdlib-page
---

# findIndex

<p class="stdlib-meta"><code>findIndex(array, predicate) → number</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns the zero-based index of the FIRST matching element, or `-1` if
not found.

- `array` (required): the array to search

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"users": [{"id": 1}, {"id": 2}, {"id": 3}]}' \
  | utlx -e 'findIndex($input.users, (u) -> u.id == 2)'
# 1
```

``` utlx
{
  position: findIndex($input.users, (u) -> u.id == 2),
  missing: findIndex($input.users, (u) -> u.id == 99)
}
```

Also: `findLastIndex(array, predicate)` — searches from the end.
