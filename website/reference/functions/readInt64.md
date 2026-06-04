---
title: readInt64
description: "readInt64 — UTL-X Binary function. Read a 64-bit integer from binary data at the given offset (big endian)."
pageClass: stdlib-page
---

# readInt64

<p class="stdlib-meta"><code>readInt64(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a 64-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data

- `offset` (required): byte offset

``` utlx
readInt64($input.data, 0)                // 64-bit signed integer
```
