---
title: mapTree
description: "mapTree — UTL-X Object function. Recursively transform all values in a nested structure (objects and"
pageClass: stdlib-page
---

# mapTree

<p class="stdlib-meta"><code>mapTree(data, transformer) → object \| array</code> · <a href="/reference/stdlib#object">Object</a></p>

Recursively transform all values in a nested structure (objects and
arrays) by applying a transformer function. Walks the entire tree
depth-first.

- `data` (required): nested object or array to traverse

- `transformer` (required): lambda `(value, path) -> newValue`

``` utlx
// Input: {a: "hello", b: {c: "world"}}
// Uppercase all string values, no matter how deeply nested
{
  result: mapTree($input, (v, p) ->
    if (typeOf(v) == "string") upperCase(v) else v
  )
  // {a: "HELLO", b: {c: "WORLD"}}
}
```
