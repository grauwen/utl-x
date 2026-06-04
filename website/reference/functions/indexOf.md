---
title: indexOf
description: "indexOf — UTL-X Array function. Find the 0-based position of the FIRST occurrence of a value in an"
pageClass: stdlib-page
---

# indexOf

<p class="stdlib-meta"><code>indexOf(array, value) → number</code> · <a href="/reference/stdlib#array">Array</a></p>

Find the 0-based position of the FIRST occurrence of a value in an
array. Returns -1 if not found.

- `array` (required): array to search in

- `value` (required): value to find

``` bash
echo '{"items": ["Apple", "Banana", "Cherry"]}' \
  | utlx -e 'indexOf($input.items, "Banana")'
# 1 (0-based: Apple=0, Banana=1, Cherry=2)
```

``` utlx
{
  pos: indexOf($input.items, "Banana"),   // 1 (0-based index)
  missing: indexOf($input.items, "Grape") // -1 (not found)
}
```
