---
title: rightTrim
description: "rightTrim — UTL-X String function. Remove whitespace from the RIGHT (end) of a string only."
pageClass: stdlib-page
---

# rightTrim

<p class="stdlib-meta"><code>rightTrim(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Remove whitespace from the RIGHT (end) of a string only.

- `string` (required): the string to trim

``` utlx
rightTrim("   hello   ")                 // "   hello"
```

Also: `normalizeSpace(string)` — trims AND collapses internal whitespace
to single spaces.
