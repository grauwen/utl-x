---
title: binaryConcat
description: "binaryConcat — UTL-X Binary function. Concatenate multiple binary values into a single binary."
pageClass: stdlib-page
---

# binaryConcat

<p class="stdlib-meta"><code>binaryConcat(binary1, binary2, ...) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Concatenate multiple binary values into a single binary.

- `binary1` (required): first binary segment

- `binary2` (required): second binary segment

- `...` (optional): additional binary segments

``` utlx
let header = toBinary("HDR:", "UTF-8")
let payload = toBinary($input.data, "UTF-8")
{
  frame: binaryConcat(header, payload)
}
```
