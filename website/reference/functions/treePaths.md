---
title: treePaths
description: "treePaths — UTL-X Object function. Get all paths in a tree structure as an array of path strings."
pageClass: stdlib-page
---

# treePaths

<p class="stdlib-meta"><code>treePaths(object) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Get all paths in a tree structure as an array of path strings.

- `object` (required): tree structure

``` utlx
treePaths({a: {b: 1}, c: 2})             // ["a.b", "c"]
```
