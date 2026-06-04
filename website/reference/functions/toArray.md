---
title: toArray
description: "toArray — UTL-X Type function. Convert a value to an array. Wraps non-array values in a single-element"
pageClass: stdlib-page
---

# toArray

<p class="stdlib-meta"><code>toArray(value) → array</code> · <a href="/reference/stdlib#type">Type</a></p>

Convert a value to an array. Wraps non-array values in a single-element
array; arrays pass through.

- `value` (required): value to convert

``` utlx
toArray("hello")                         // ["hello"]
toArray([1, 2, 3])                       // [1, 2, 3] (unchanged)
toArray(null)                            // []
```
