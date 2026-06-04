---
title: yamlMerge
description: "yamlMerge — UTL-X YAML function. Deep merge two YAML objects. Values from obj2 override obj1 for"
pageClass: stdlib-page
---

# yamlMerge

<p class="stdlib-meta"><code>yamlMerge(obj1, obj2) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Deep merge two YAML objects. Values from `obj2` override `obj1` for
matching keys. See Chapter 26.

- `obj1` (required): base object

- `obj2` (required): override object

``` utlx
yamlMerge({a: 1, b: {c: 2}}, {b: {d: 3}})
// {a: 1, b: {c: 2, d: 3}}
```
