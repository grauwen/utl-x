---
title: toBytes
description: "toBytes — UTL-X Binary function. Convert binary data to an array of byte values (0-255)."
pageClass: stdlib-page
---

# toBytes

<p class="stdlib-meta"><code>toBytes(binary) → array</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Convert binary data to an array of byte values (0-255).

- `binary` (required): binary data

``` utlx
toBytes(toBinary("AB", "UTF-8"))         // [65, 66]
```
