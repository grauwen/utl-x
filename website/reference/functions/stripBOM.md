---
title: stripBOM
description: "stripBOM — UTL-X XML function. Remove the BOM character (U+FEFF) from the beginning of a string. See"
pageClass: stdlib-page
---

# stripBOM

<p class="stdlib-meta"><code>stripBOM(string) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Remove the BOM character (U+FEFF) from the beginning of a string. See
Chapter 22.

- `string` (required): string that may start with BOM

``` utlx
let clean = stripBOM($input.fileContent)
```
