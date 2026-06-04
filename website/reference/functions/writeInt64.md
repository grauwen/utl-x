---
title: writeInt64
description: "writeInt64 — UTL-X Binary function. Write a 64-bit integer to binary (big endian)."
pageClass: stdlib-page
---

# writeInt64

<p class="stdlib-meta"><code>writeInt64(value) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Write a 64-bit integer to binary (big endian).

- `value` (required): integer value

``` utlx
writeInt64(1000000000)                   // 8 bytes of binary data
```

## X-Z
