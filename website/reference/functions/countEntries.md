---
title: countEntries
description: "countEntries — UTL-X Object function. Count entries in an object, optionally filtered by a predicate."
pageClass: stdlib-page
---

# countEntries

<p class="stdlib-meta"><code>countEntries(object, predicate?) → number</code> · <a href="/reference/stdlib#object">Object</a></p>

Count entries in an object, optionally filtered by a predicate.

- `object` (required): the object to count entries of

- `predicate` (optional): lambda `(key, value) -> boolean`

``` utlx
let obj = {a: 1, b: null, c: 3, d: null}
{
  total: countEntries(obj),                            // 4
  nonNull: countEntries(obj, (k, v) -> v != null)     // 2
}
```
