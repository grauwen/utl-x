---
title: compress
description: "compress — UTL-X Binary function. Compress binary data using the specified algorithm."
pageClass: stdlib-page
---

# compress

<p class="stdlib-meta"><code>compress(data, algorithm?) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Compress binary data using the specified algorithm.

- `data` (required): binary data to compress

- `algorithm` (optional, default `"gzip"`): compression algorithm —
  `"gzip"`, `"deflate"`

``` utlx
{
  compressed: base64Encode(compress(toBinary($input.payload, "UTF-8"), "gzip"))
}
```
