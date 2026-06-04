---
title: insertBefore
description: "insertBefore — UTL-X Array function. Insert an element before the specified index in an array."
pageClass: stdlib-page
---

# insertBefore

<p class="stdlib-meta"><code>insertBefore(array, index, element) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Insert an element before the specified index in an array.

- `array` (required): source array

- `index` (required): position before which to insert

- `element` (required): element to insert

``` bash
echo '{"items": ["a", "c", "d"]}' | utlx -e 'insertBefore($input.items, 1, "b")'
# ["a", "b", "c", "d"]
```

``` utlx
{
  updated: insertBefore($input.items, 0, "first")
}
```
