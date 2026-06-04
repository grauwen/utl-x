---
title: sort
description: "sort — UTL-X Array function. Sort an array using natural ordering (numbers ascending, strings"
pageClass: stdlib-page
---

# sort

<p class="stdlib-meta"><code>sort(array) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Sort an array using natural ordering (numbers ascending, strings
alphabetical).

- `array` (required): the array to sort

``` bash
echo '[3, 1, 4, 1, 5]' | utlx -e 'sort(.)'
# [1, 1, 3, 4, 5]
```

``` utlx
sort([3, 1, 4, 1, 5, 9])                // [1, 1, 3, 4, 5, 9]
sort(["banana", "apple", "cherry"])      // ["apple", "banana", "cherry"]
```
