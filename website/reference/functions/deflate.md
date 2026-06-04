---
title: deflate
description: "deflate — UTL-X Binary function. Compress data using raw Deflate algorithm (no gzip header/trailer)."
pageClass: stdlib-page
---

# deflate

<p class="stdlib-meta"><code>deflate(data) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Compress data using raw Deflate algorithm (no gzip header/trailer).

- `data` (required): binary data to compress

``` utlx
{
  deflated: base64Encode(deflate(toBinary($input.payload, "UTF-8")))
}
```
