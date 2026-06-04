---
title: getBOMBytes
description: "getBOMBytes — UTL-X XML function. Get the BOM (Byte Order Mark) bytes for a given encoding. See Chapter"
pageClass: stdlib-page
---

# getBOMBytes

<p class="stdlib-meta"><code>getBOMBytes(encoding) → binary</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get the BOM (Byte Order Mark) bytes for a given encoding. See Chapter
22.

- `encoding` (required): encoding name (e.g., "UTF-8", "UTF-16LE")

``` utlx
{
  bom: getBOMBytes("UTF-8")
}
```
