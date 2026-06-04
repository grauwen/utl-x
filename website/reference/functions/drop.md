---
title: drop
description: "drop — UTL-X Array function. Remove the first N elements from an array, returning the rest."
pageClass: stdlib-page
---

# drop

<p class="stdlib-meta"><code>drop(array, n) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Remove the first N elements from an array, returning the rest.

- `array` (required): the source array

- `n` (required): number of elements to drop

``` bash
echo '{"items": ["A", "B", "C", "D", "E"]}' | utlx -e 'drop($input.items, 2)'
# ["C", "D", "E"]
```

``` utlx
// Skip CSV header row (when headers: false)
let dataRows = drop($input, 1)
{
  rows: map(dataRows, (row) -> { name: row[0], value: row[1] })
}
```
