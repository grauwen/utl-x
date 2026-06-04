---
title: insertAfter
description: "insertAfter — UTL-X Array function. Insert an element after the specified index in an array."
pageClass: stdlib-page
---

# insertAfter

<p class="stdlib-meta"><code>insertAfter(array, index, element) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Insert an element after the specified index in an array.

- `array` (required): source array

- `index` (required): position after which to insert

- `element` (required): element to insert

``` bash
echo '{"items": ["a", "b", "d"]}' | utlx -e 'insertAfter($input.items, 1, "c")'
# ["a", "b", "c", "d"]
```

``` utlx
{
  updated: insertAfter($input.steps, 2, {name: "validation", order: 3})
}
```
