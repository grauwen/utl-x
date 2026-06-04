---
title: readDouble
description: "readDouble — UTL-X Binary function. Read a 64-bit double from binary data at the given offset (big endian)."
pageClass: stdlib-page
---

# readDouble

<p class="stdlib-meta"><code>readDouble(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a 64-bit double from binary data at the given offset (big endian).

- `binary` (required): binary data

- `offset` (required): byte offset

``` utlx
readDouble($input.data, 0)               // 64-bit floating point value
```
