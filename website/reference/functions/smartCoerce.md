---
title: smartCoerce
description: "smartCoerce — UTL-X Type function. Smart coercion — infers the target type from context and coerces the"
pageClass: stdlib-page
---

# smartCoerce

<p class="stdlib-meta"><code>smartCoerce(value) → value</code> · <a href="/reference/stdlib#type">Type</a></p>

Smart coercion — infers the target type from context and coerces the
value.

- `value` (required): value to coerce

``` utlx
smartCoerce("42")                        // 42 (number)
smartCoerce("true")                      // true (boolean)
smartCoerce("2026-05-01")                // date value
```
