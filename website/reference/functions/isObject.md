---
title: isObject
description: "isObject — UTL-X Type function. Returns true if the value is an object (key-value map)."
pageClass: stdlib-page
---

# isObject

<p class="stdlib-meta"><code>isObject(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is an object (key-value map).

- `value` (required): the value to test

``` utlx
isObject({name: "Alice"})                // true
isObject([1, 2, 3])                      // false (array, not object)
isObject("hello")                        // false
isObject($input)                         // true (root is typically an object)
```
