---
title: take
description: "take — UTL-X Array function. Keep only the first N elements from an array, discarding the rest."
pageClass: stdlib-page
---

# take

<p class="stdlib-meta"><code>take(array, n) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Keep only the first N elements from an array, discarding the rest.

- `array` (required): the source array

- `n` (required): number of elements to keep

``` bash
echo '{"items": ["A", "B", "C", "D", "E"]}' | utlx -e 'take($input.items, 3)'
# ["A", "B", "C"]
```

``` utlx
{
  top10: take(sortBy($input.products, (p) -> -p.sales), 10),
  preview: take($input.items, 5)
}
```

## E
