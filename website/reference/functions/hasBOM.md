---
title: hasBOM
description: "hasBOM — UTL-X XML function. Check if binary data starts with a BOM (Byte Order Mark). See Chapter"
pageClass: stdlib-page
---

# hasBOM

<p class="stdlib-meta"><code>hasBOM(data) → boolean</code> · <a href="/reference/stdlib#xml">XML</a></p>

Check if binary data starts with a BOM (Byte Order Mark). See Chapter
22.

- `data` (required): binary data to check

``` utlx
{
  hasBom: hasBOM($input.fileData),
  clean: if (hasBOM($input.fileData)) removeBOM($input.fileData) else $input.fileData
}
```
