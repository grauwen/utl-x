---
title: isArray
description: "isArray — UTL-X Type function. Returns true if the value is an array."
pageClass: stdlib-page
---

# isArray

<p class="stdlib-meta"><code>isArray(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is an array.

- `value` (required): the value to test

``` utlx
isArray([1, 2, 3])                       // true
isArray("hello")                         // false
isArray($input.items)                    // true if items is an array

// Use case: handle XML single-vs-array ambiguity
let items = if (isArray($input.Item)) $input.Item else [$input.Item]
// Ensures items is always an array, even when XML has a single <Item>
```
