---
title: stringOrDefault
description: "stringOrDefault — UTL-X Type function. Safely convert a value to string, returning the default if null or"
pageClass: stdlib-page
---

# stringOrDefault

<p class="stdlib-meta"><code>stringOrDefault(value, default) → string</code> · <a href="/reference/stdlib#type">Type</a></p>

Safely convert a value to string, returning the default if null or
undefined.

- `value` (required): value to convert

- `default` (required): fallback string

``` utlx
stringOrDefault($input.name, "Unknown")  // "Unknown" if name is null
```
