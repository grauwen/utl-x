---
title: removeBOM
description: "removeBOM — UTL-X XML function. Remove the Byte Order Mark (BOM) if present at the start of the data."
pageClass: stdlib-page
---

# removeBOM

<p class="stdlib-meta"><code>removeBOM(data) → data</code> · <a href="/reference/stdlib#xml">XML</a></p>

Remove the Byte Order Mark (BOM) if present at the start of the data.
See Chapter 22.

- `data` (required): binary or string data

``` utlx
let clean = removeBOM($input.fileContent)
```
