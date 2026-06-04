---
title: every
description: "every — UTL-X Array function. Check if ALL elements in an array satisfy a predicate. Returns true"
pageClass: stdlib-page
---

# every

<p class="stdlib-meta"><code>every(array, predicate) → boolean</code> · <a href="/reference/stdlib#array">Array</a></p>

Check if ALL elements in an array satisfy a predicate. Returns `true`
for empty arrays.

- `array` (required): the array to test

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"scores": [85, 92, 78, 95]}' | utlx -e 'every($input.scores, (s) -> s >= 70)'
# true
```

``` utlx
{
  allPassing: every($input.scores, (s) -> s >= 70),
  allActive: every($input.users, (u) -> u.active),
  allPositive: every($input.amounts, (a) -> a > 0)
}
```
