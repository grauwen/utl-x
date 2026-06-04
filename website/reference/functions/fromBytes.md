---
title: fromBytes
description: "fromBytes — UTL-X Binary function. Create binary data from a byte array (array of integers 0-255)."
pageClass: stdlib-page
---

# fromBytes

<p class="stdlib-meta"><code>fromBytes(byteArray) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Create binary data from a byte array (array of integers 0-255).

- `byteArray` (required): array of byte values

``` utlx
{
  binary: fromBytes([72, 101, 108, 108, 111])
}
```
