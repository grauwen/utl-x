---
title: binaryToString
description: "binaryToString — UTL-X Binary function. Decode binary data to a string using the specified character encoding."
pageClass: stdlib-page
---

# binaryToString

<p class="stdlib-meta"><code>binaryToString(binary, encoding) → string</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Decode binary data to a string using the specified character encoding.

- `binary` (required): binary data to decode

- `encoding` (required): character encoding — `"UTF-8"`, `"ISO-8859-1"`,
  `"US-ASCII"`, etc.

``` utlx
{
  text: binaryToString(base64Decode($input.payload), "UTF-8")
}
```
