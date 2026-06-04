---
title: toString
description: "toString — UTL-X Type function. Convert any value to its string representation."
pageClass: stdlib-page
---

# toString

<p class="stdlib-meta"><code>toString(value) → string</code> · <a href="/reference/stdlib#type">Type</a></p>

Convert any value to its string representation.

- `value` (required): any value to convert

``` utlx
toString(42)                             // "42"
toString(3.14)                           // "3.14"
toString(true)                           // "true"
toString(null)                           // "null"
toString([1, 2])                         // "[1, 2]"
```

Also: `toDate(value)`, `toArray(value)` (wraps non-array in array),
`toObject(value)`, `toBinary(string)`.
