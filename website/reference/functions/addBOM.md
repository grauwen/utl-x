---
title: addBOM
description: "addBOM — UTL-X Binary function. Prepend a Byte Order Mark (BOM) to binary data. Detects encoding from"
pageClass: stdlib-page
---

# addBOM

<p class="stdlib-meta"><code>addBOM(data) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Prepend a Byte Order Mark (BOM) to binary data. Detects encoding from
the data.

- `data` (required): binary data to prepend BOM to

``` utlx
// Programmatic BOM insertion for binary file construction
let fileBytes = toBinary($input.content, "UTF-8")
{
  withBOM: addBOM(fileBytes)
}
```

**Note:** for normal output, prefer the header option
`output csv {bom: true}` (Chapter 25) which handles BOM automatically.
Use `addBOM()` only when constructing raw binary content
programmatically.
