---
title: tail
description: "tail — UTL-X Array function. Returns everything EXCEPT the first element. Returns an empty array if"
pageClass: stdlib-page
---

# tail

<p class="stdlib-meta"><code>tail(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns everything EXCEPT the first element. Returns an empty array if
the input has 0 or 1 elements.

- `array` (required): the source array

``` utlx
tail(["Apple", "Banana", "Cherry"])      // ["Banana", "Cherry"]
tail(["Apple"])                          // []
tail([])                                 // []

// Use case: skip the header row in a headerless CSV
let dataRows = tail($input)             // all rows except the first
{ rows: dataRows }
```
