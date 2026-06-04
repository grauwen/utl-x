---
title: readByte
description: "readByte — UTL-X Binary function. Read a single byte from binary data at the given offset."
pageClass: stdlib-page
---

# readByte

<p class="stdlib-meta"><code>readByte(binary, offset) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Read a single byte from binary data at the given offset.

- `binary` (required): binary data

- `offset` (required): byte offset (0-based)

``` utlx
readByte($input.data, 0)                 // first byte value (0-255)
```
