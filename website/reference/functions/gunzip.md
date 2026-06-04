---
title: gunzip
description: "gunzip — UTL-X Binary function. Decompress gzip-compressed binary data."
pageClass: stdlib-page
---

# gunzip

<p class="stdlib-meta"><code>gunzip(data) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Decompress gzip-compressed binary data.

- `data` (required): gzip-compressed binary

``` utlx
{
  decompressed: gunzip($input.compressedPayload)
}
```
