---
title: toBinary
description: "toBinary — UTL-X Binary function. Create binary data from a string with the specified encoding."
pageClass: stdlib-page
---

# toBinary

<p class="stdlib-meta"><code>toBinary(string, encoding?) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Create binary data from a string with the specified encoding.

- `string` (required): the string to convert

- `encoding` (optional): character encoding (default `"UTF-8"`)

``` utlx
let data = toBinary("Hello World", "UTF-8")
{
  length: binaryLength(data)
}
```
