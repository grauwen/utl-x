---
title: isGzipped
description: "isGzipped — UTL-X Binary function. Check if binary data is gzip-compressed (by checking magic bytes)."
pageClass: stdlib-page
---

# isGzipped

<p class="stdlib-meta"><code>isGzipped(data) → boolean</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Check if binary data is gzip-compressed (by checking magic bytes).

- `data` (required): binary data to check

``` utlx
{
  compressed: isGzipped($input.payload),
  content: if (isGzipped($input.data)) gunzip($input.data) else $input.data
}
```
