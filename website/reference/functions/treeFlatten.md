---
title: treeFlatten
description: "treeFlatten — UTL-X Object function. Flatten a tree to an array of leaf nodes."
pageClass: stdlib-page
---

# treeFlatten

<p class="stdlib-meta"><code>treeFlatten(object) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Flatten a tree to an array of leaf nodes.

- `object` (required): tree structure

``` utlx
treeFlatten({a: {b: 1, c: 2}, d: 3})    // [1, 2, 3]
```
