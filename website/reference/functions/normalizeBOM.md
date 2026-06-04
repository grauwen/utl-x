---
title: normalizeBOM
description: "normalizeBOM — UTL-X XML function. Convert binary data to a target encoding with BOM (Byte Order Mark)"
pageClass: stdlib-page
---

# normalizeBOM

<p class="stdlib-meta"><code>normalizeBOM(data, targetEncoding, addBom) → binary</code> · <a href="/reference/stdlib#xml">XML</a></p>

Convert binary data to a target encoding with BOM (Byte Order Mark)
handling. See Chapter 22.

- `data` (required): binary data to convert

- `targetEncoding` (required): target encoding (e.g., `"UTF-8"`,
  `"UTF-16"`)

- `addBom` (required): boolean – whether to add BOM to the output

``` utlx
{
  output: normalizeBOM($input.xmlBytes, "UTF-8", false)
}
```
