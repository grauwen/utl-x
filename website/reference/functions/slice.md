---
title: slice
description: "slice — UTL-X Array function. Extract a portion of an array or string by index. Zero-based."
pageClass: stdlib-page
---

# slice

<p class="stdlib-meta"><code>slice(value, start, end?) → array or string</code> · <a href="/reference/stdlib#array">Array</a></p>

Extract a portion of an array or string by index. Zero-based.

- `value` (required): array or string

- `start` (required): starting index (inclusive)

- `end` (optional): ending index (exclusive). If omitted, goes to end.

``` utlx
slice([10, 20, 30, 40, 50], 1, 4)       // [20, 30, 40] (index 1,2,3)
slice([10, 20, 30, 40, 50], 2)           // [30, 40, 50] (from index 2 to end)
slice("Hello World", 6, 11)             // "World"
slice("Hello World", 6)                 // "World"
```
