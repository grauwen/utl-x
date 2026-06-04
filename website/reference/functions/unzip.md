---
title: unzip
description: "unzip — UTL-X Binary function. Unzip an array of pairs into two separate arrays (inverse of zip)."
pageClass: stdlib-page
---

# unzip

<p class="stdlib-meta"><code>unzip(pairs) → \[array, array\]</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Unzip an array of pairs into two separate arrays (inverse of `zip`).

- `pairs` (required): array of 2-element arrays

``` utlx
unzip([[1, "a"], [2, "b"], [3, "c"]])    // [[1, 2, 3], ["a", "b", "c"]]
```
