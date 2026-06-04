---
title: writeDouble
description: "writeDouble — UTL-X Binary function. Write a 64-bit double to binary (big endian)."
pageClass: stdlib-page
---

# writeDouble

<p class="stdlib-meta"><code>writeDouble(value) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Write a 64-bit double to binary (big endian).

- `value` (required): double value

``` utlx
writeDouble(3.14159)                     // 8 bytes of binary data
```
