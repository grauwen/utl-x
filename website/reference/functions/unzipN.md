---
title: unzipN
description: "unzipN — UTL-X Binary function. Unzip an array of N-tuples into N separate arrays (generalized unzip)."
pageClass: stdlib-page
---

# unzipN

<p class="stdlib-meta"><code>unzipN(tuples) → array of arrays</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Unzip an array of N-tuples into N separate arrays (generalized `unzip`).

- `tuples` (required): array of N-element arrays

``` utlx
unzipN([[1, "a", true], [2, "b", false]])
// [[1, 2], ["a", "b"], [true, false]]
```
