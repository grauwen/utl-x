---
title: binaryEquals
description: "binaryEquals — UTL-X Binary function. Compare two binary values for byte-level equality."
pageClass: stdlib-page
---

# binaryEquals

<p class="stdlib-meta"><code>binaryEquals(binary1, binary2) → boolean</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Compare two binary values for byte-level equality.

- `binary1` (required): first binary

- `binary2` (required): second binary

``` utlx
{
  match: binaryEquals(toBinary("hello", "UTF-8"), toBinary("hello", "UTF-8")),
  differ: binaryEquals(toBinary("hello", "UTF-8"), toBinary("world", "UTF-8"))
}
// Output: {"match": true, "differ": false}
```
