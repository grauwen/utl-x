---
title: inflate
description: "inflate — UTL-X Binary function. Decompress Deflate-compressed binary data."
pageClass: stdlib-page
---

# inflate

<p class="stdlib-meta"><code>inflate(data) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Decompress Deflate-compressed binary data.

- `data` (required): deflate-compressed binary

``` utlx
{
  decompressed: inflate($input.compressedData)
}
```
