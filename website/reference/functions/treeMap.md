---
title: treeMap
description: "treeMap — UTL-X Object function. Recursively transform all values in a nested structure."
pageClass: stdlib-page
---

# treeMap

<p class="stdlib-meta"><code>treeMap(object, fn) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Recursively transform all values in a nested structure.

- `object` (required): tree structure

- `fn` (required): lambda `(value) -> newValue`

``` utlx
treeMap($input, (v) -> if (isString(v)) trim(v) else v)
```
