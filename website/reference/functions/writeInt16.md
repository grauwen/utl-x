---
title: writeInt16
description: "writeInt16 — UTL-X Binary function. Write a 16-bit integer to binary (big endian)."
pageClass: stdlib-page
---

# writeInt16

<p class="stdlib-meta"><code>writeInt16(value) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Write a 16-bit integer to binary (big endian).

- `value` (required): integer value

``` utlx
writeInt16(256)                          // 2 bytes: [0x01, 0x00]
```
