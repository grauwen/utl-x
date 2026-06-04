---
title: zipAll
description: "zipAll — UTL-X Binary function. Zip multiple arrays together, padding shorter arrays with null (or a"
pageClass: stdlib-page
---

# zipAll

<p class="stdlib-meta"><code>zipAll(arrays, pad?) → array</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Zip multiple arrays together, padding shorter arrays with null (or a
specified value).

- `arrays` (required): array of arrays to zip

- `pad` (optional): padding value for shorter arrays

``` utlx
zipAll([[1, 2, 3], ["a", "b"]])          // [[1, "a"], [2, "b"], [3, null]]
```
