---
title: toObject
description: "toObject — UTL-X Type function. Try to convert a value to an object. Arrays of pairs become objects;"
pageClass: stdlib-page
---

# toObject

<p class="stdlib-meta"><code>toObject(value) → object</code> · <a href="/reference/stdlib#type">Type</a></p>

Try to convert a value to an object. Arrays of pairs become objects;
objects pass through.

- `value` (required): value to convert

``` utlx
toObject([["name", "Alice"], ["age", 30]])  // {"name": "Alice", "age": 30}
```
