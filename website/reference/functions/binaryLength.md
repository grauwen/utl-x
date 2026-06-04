---
title: binaryLength
description: "binaryLength — UTL-X Binary function. Get the length of a binary value in bytes."
pageClass: stdlib-page
---

# binaryLength

<p class="stdlib-meta"><code>binaryLength(binary) → number</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Get the length of a binary value in bytes.

- `binary` (required): binary data

``` utlx
{
  sizeBytes: binaryLength(toBinary($input.content, "UTF-8"))
}
```
