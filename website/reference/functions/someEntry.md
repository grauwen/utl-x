---
title: someEntry
description: "someEntry — UTL-X Object function. Check if any entry in the object matches the predicate."
pageClass: stdlib-page
---

# someEntry

<p class="stdlib-meta"><code>someEntry(object, predicate) → boolean</code> · <a href="/reference/stdlib#object">Object</a></p>

Check if any entry in the object matches the predicate.

- `object` (required): the object to test

- `predicate` (required): lambda `(key, value) -> boolean`

``` utlx
someEntry({a: 1, b: 5, c: 3}, (k, v) -> v > 4)  // true
```
