---
title: yamlMergeAll
description: "yamlMergeAll — UTL-X YAML function. Merge multiple YAML objects in order (last wins for conflicts). See"
pageClass: stdlib-page
---

# yamlMergeAll

<p class="stdlib-meta"><code>yamlMergeAll(objects) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Merge multiple YAML objects in order (last wins for conflicts). See
Chapter 26.

- `objects` (required): array of objects to merge

``` utlx
yamlMergeAll([{a: 1}, {b: 2}, {a: 3}])  // {a: 3, b: 2}
```
