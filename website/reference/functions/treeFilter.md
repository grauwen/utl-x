---
title: treeFilter
description: "treeFilter — UTL-X Object function. Filter tree nodes by a predicate, removing branches that don't match."
pageClass: stdlib-page
---

# treeFilter

<p class="stdlib-meta"><code>treeFilter(object, predicate) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Filter tree nodes by a predicate, removing branches that don't match.

- `object` (required): tree structure

- `predicate` (required): lambda `(node) -> boolean`

``` utlx
treeFilter($input.menu, (node) -> node.visible == true)
```
