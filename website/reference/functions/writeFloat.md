---
title: writeFloat
description: "writeFloat — UTL-X Binary function. Write a 32-bit float to binary (big endian)."
pageClass: stdlib-page
---

# writeFloat

<p class="stdlib-meta"><code>writeFloat(value) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Write a 32-bit float to binary (big endian).

- `value` (required): float value

``` utlx
writeFloat(3.14)                         // 4 bytes of binary data
```
