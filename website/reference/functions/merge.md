---
title: merge
description: "merge — UTL-X Object function. Shallow merge of objects. Later arguments override earlier ones. Same as"
pageClass: stdlib-page
---

# merge

<p class="stdlib-meta"><code>merge(obj1, obj2, ...) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Shallow merge of objects. Later arguments override earlier ones. Same as
spread but as a function.

- `obj1, obj2, ...` (variadic): objects to merge

``` utlx
{
  two: merge({a: 1, b: 2}, {b: 3, c: 4}),   // {a: 1, b: 3, c: 4}
  three: merge({a: 1}, {b: 2}, {c: 3})      // {a: 1, b: 2, c: 3}
}
```

**Note:** for deep (recursive) merge, use `deepMerge(obj1, obj2)`.
