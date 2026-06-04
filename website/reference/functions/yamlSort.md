---
title: yamlSort
description: "yamlSort — UTL-X YAML function. Sort YAML object keys alphabetically. See Chapter 26."
pageClass: stdlib-page
---

# yamlSort

<p class="stdlib-meta"><code>yamlSort(object, comparator?) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Sort YAML object keys alphabetically. See Chapter 26.

- `object` (required): YAML object to sort

- `comparator` (optional): custom comparator function

``` utlx
yamlSort({z: 1, a: 2, m: 3})            // {a: 2, m: 3, z: 1}
```
