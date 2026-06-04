---
title: validateEncoding
description: "validateEncoding — UTL-X XML function. Check if an encoding name is valid and supported. See Chapter 22."
pageClass: stdlib-page
---

# validateEncoding

<p class="stdlib-meta"><code>validateEncoding(encoding) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if an encoding name is valid and supported. See Chapter 22.

- `encoding` (required): encoding name string

``` utlx
validateEncoding("UTF-8")                // true
validateEncoding("INVALID-ENC")          // false
```
