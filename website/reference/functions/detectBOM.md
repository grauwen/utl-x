---
title: detectBOM
description: "detectBOM — UTL-X XML function. Detect the Byte Order Mark type from binary data. Returns the encoding"
pageClass: stdlib-page
---

# detectBOM

<p class="stdlib-meta"><code>detectBOM(binary) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Detect the Byte Order Mark type from binary data. Returns the encoding
name or null if no BOM found.

- `binary` (required): binary data to inspect

``` utlx
{
  bomType: detectBOM($input.rawData)
}
// Output: {"bomType": "UTF-8"} or {"bomType": null}
```
