---
title: writeInt32
description: "writeInt32 — UTL-X Binary function. Write a 32-bit integer to binary (big endian)."
pageClass: stdlib-page
---

# writeInt32

<p class="stdlib-meta"><code>writeInt32(value) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Write a 32-bit integer to binary (big endian).

- `value` (required): integer value

``` utlx
writeInt32(65536)                        // 4 bytes of binary data
```
