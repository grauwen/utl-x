---
title: chunk
description: "chunk — UTL-X Array function. Split an array into sub-arrays of the given size. Last chunk may be"
pageClass: stdlib-page
---

# chunk

<p class="stdlib-meta"><code>chunk(array, size) → array of arrays</code> · <a href="/reference/stdlib#array">Array</a></p>

Split an array into sub-arrays of the given size. Last chunk may be
smaller.

- `array` (required): the array to split

- `size` (required): maximum elements per chunk

``` bash
echo '{"items": ["A", "B", "C", "D", "E", "F", "G"]}' \
  | utlx -e 'chunk($input.items, 3)'
# [["A", "B", "C"], ["D", "E", "F"], ["G"]]
```

``` utlx
// Batch processing — send items in groups of 100
{
  batches: map(chunk($input.records, 100), (batch) -> {
    batchSize: count(batch),
    items: batch
  })
}
```
