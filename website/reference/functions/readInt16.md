---
title: readInt16
description: "readInt16 — UTL-X Binary function. Read a 16-bit integer from binary data at the given offset (big endian)."
pageClass: stdlib-page
---

# readInt16

<p class="stdlib-meta"><code>readInt16(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a 16-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data

- `offset` (required): byte offset

``` utlx
readInt16($input.data, 0)                // 16-bit signed integer
```
