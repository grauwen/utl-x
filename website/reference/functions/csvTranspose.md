---
title: csvTranspose
description: "csvTranspose — UTL-X CSV function. Transpose CSV — swap rows and columns."
pageClass: stdlib-page
---

# csvTranspose

<p class="stdlib-meta"><code>csvTranspose(csv, options?, header?, separator?) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Transpose CSV — swap rows and columns.

- `csv` (required): CSV string

- `options` (optional): formatting options

- `header` (optional): include header row

- `separator` (optional): field separator

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  transposed: csvTranspose(csv)
}
// Output: {transposed: "Name,Alice,Bob\nAge,30,25"}
```
