---
title: everyEntry
description: "everyEntry — UTL-X Object function. Check if ALL entries in an object satisfy a predicate. The predicate"
pageClass: stdlib-page
---

# everyEntry

<p class="stdlib-meta"><code>everyEntry(object, predicate) → boolean</code> · <a href="/reference/stdlib#object">Object</a></p>

Check if ALL entries in an object satisfy a predicate. The predicate
receives key and value.

- `object` (required): the object to test

- `predicate` (required): lambda `(key, value) -> boolean`

``` bash
echo '{"a": 1, "b": 2, "c": 3}' | utlx -e 'everyEntry($input, (k, v) -> v > 0)'
# true
```

``` utlx
{
  allPositive: everyEntry($input.metrics, (k, v) -> v > 0),
  allNonNull: everyEntry($input, (k, v) -> v != null)
}
```
