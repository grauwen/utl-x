---
title: readFloat
description: "readFloat — UTL-X Binary function. Read a 32-bit float from binary data at the given offset (big endian)."
pageClass: stdlib-page
---

# readFloat

<p class="stdlib-meta"><code>readFloat(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a 32-bit float from binary data at the given offset (big endian).

- `binary` (required): binary data

- `offset` (required): byte offset

``` utlx
readFloat($input.data, 4)               // 32-bit floating point value
```
