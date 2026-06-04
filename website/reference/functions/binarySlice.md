---
title: binarySlice
description: "binarySlice — UTL-X Binary function. Extract a subsequence of bytes from binary data."
pageClass: stdlib-page
---

# binarySlice

<p class="stdlib-meta"><code>binarySlice(binary, start, end) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Extract a subsequence of bytes from binary data.

- `binary` (required): source binary

- `start` (required): start byte offset (0-based)

- `end` (required): end byte offset (exclusive)

``` utlx
let data = toBinary("Hello World", "UTF-8")
{
  first5: binaryToString(binarySlice(data, 0, 5), "UTF-8"),
  rest: binaryToString(binarySlice(data, 6, 11), "UTF-8")
}
// Output: {"first5": "Hello", "rest": "World"}
```
