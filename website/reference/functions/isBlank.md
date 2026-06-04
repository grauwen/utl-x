---
title: isBlank
description: "isBlank — UTL-X Type function. Returns true if the value is null, empty string, or whitespace-only."
pageClass: stdlib-page
---

# isBlank

<p class="stdlib-meta"><code>isBlank(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is null, empty string, or whitespace-only.

- `value` (required): the value to test

``` utlx
isBlank(null)                            // true
isBlank("")                              // true
isBlank("  ")                            // true (whitespace-only)
isBlank("hello")                         // false
```
