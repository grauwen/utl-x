---
title: toHex
description: "toHex — UTL-X Binary function. Convert binary data to a hexadecimal string."
pageClass: stdlib-page
---

# toHex

<p class="stdlib-meta"><code>toHex(binary) → string</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Convert binary data to a hexadecimal string.

- `binary` (required): binary data

``` utlx
toHex(toBinary("AB", "UTF-8"))           // "4142"
```
