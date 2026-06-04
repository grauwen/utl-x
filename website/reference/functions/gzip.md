---
title: gzip
description: "gzip — UTL-X Binary function. Compress binary data using gzip."
pageClass: stdlib-page
---

# gzip

<p class="stdlib-meta"><code>gzip(data) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Compress binary data using gzip.

- `data` (required): binary data to compress

``` utlx
{
  compressed: gzip(toBinary($input.largeText)),
  encoded: base64Encode(gzip(toBinary($input.payload)))
}
```

## H
