---
title: toBase64
description: "toBase64 — UTL-X Binary function. Convert binary data to a Base64-encoded string."
pageClass: stdlib-page
---

# toBase64

<p class="stdlib-meta"><code>toBase64(binary) → string</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Convert binary data to a Base64-encoded string.

- `binary` (required): binary data to encode

``` utlx
toBase64(toBinary("Hello", "UTF-8"))     // "SGVsbG8="
```
