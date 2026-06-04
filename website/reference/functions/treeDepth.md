---
title: treeDepth
description: "treeDepth — UTL-X Object function. Get the maximum nesting depth of a tree structure."
pageClass: stdlib-page
---

# treeDepth

<p class="stdlib-meta"><code>treeDepth(object) → number</code> · <a href="/reference/stdlib#object">Object</a></p>

Get the maximum nesting depth of a tree structure.

- `object` (required): nested object/array structure

``` utlx
treeDepth({a: {b: {c: 1}}})              // 3
```
