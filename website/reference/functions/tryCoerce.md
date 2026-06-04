---
title: tryCoerce
description: "tryCoerce — UTL-X Type function. Try to coerce a value to the target type, returning null on failure"
pageClass: stdlib-page
---

# tryCoerce

<p class="stdlib-meta"><code>tryCoerce(value, type) → value</code> · <a href="/reference/stdlib#type">Type</a></p>

Try to coerce a value to the target type, returning null on failure
(instead of throwing).

- `value` (required): value to coerce

- `type` (required): target type string (e.g. `"number"`, `"boolean"`)

``` utlx
tryCoerce("42", "number")                // 42
tryCoerce("not-a-number", "number")      // null (no error)
```
