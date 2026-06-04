---
title: decompress
description: "decompress — UTL-X Binary function. Decompress binary data using the specified algorithm."
pageClass: stdlib-page
---

# decompress

<p class="stdlib-meta"><code>decompress(data, algorithm?) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Decompress binary data using the specified algorithm.

- `data` (required): compressed binary data

- `algorithm` (optional, default `"gzip"`): decompression algorithm —
  `"gzip"`, `"deflate"`

``` utlx
{
  content: binaryToString(decompress(base64Decode($input.compressedPayload), "gzip"), "UTF-8")
}
```
