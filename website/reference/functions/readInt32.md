---
title: readInt32
description: "readInt32 — UTL-X Binary function. Read a 32-bit integer from binary data at the given offset (big endian)."
pageClass: stdlib-page
---

# readInt32

<p class="stdlib-meta"><code>readInt32(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a 32-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data

- `offset` (required): byte offset

``` utlx
readInt32($input.data, 0)                // 32-bit signed integer
```
