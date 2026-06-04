---
title: filter
description: "filter — UTL-X Array function. Keep elements that match a predicate. Always returns an array (even if 0"
pageClass: stdlib-page
---

# filter

<p class="stdlib-meta"><code>filter(array, predicate) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Keep elements that match a predicate. Always returns an array (even if 0
or 1 match).

- `array` (required): the array to filter

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '[{"name":"Alice","active":true},{"name":"Bob","active":false}]' \
  | utlx -e 'filter(., (u) -> u.active)'
# [{"name": "Alice", "active": true}]
```

``` utlx
{
  activeProducts: filter($input.products, (p) -> p.active),
  premiumActive: filter($input.products, (p) -> p.price > 100 && p.active),
  overBudget: filter($input.products, (p) -> p.price > 1000)
}
```

**Anti-pattern:** `$input.products[price > 10]` — bracket predicate
syntax does NOT work in UTL-X. Always use `filter()`. See Chapter 8.

**Anti-pattern:** `filter()` when you want ONE result — use `find()`
instead (returns the element, not an array).
