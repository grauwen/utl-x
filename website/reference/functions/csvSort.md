---
title: csvSort
description: "csvSort — UTL-X CSV function. Sort CSV rows by a specified column."
pageClass: stdlib-page
---

# csvSort

<p class="stdlib-meta"><code>csvSort(csv, column, ascending?) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Sort CSV rows by a specified column.

- `csv` (required): CSV string

- `column` (required): column name to sort by

- `ascending` (optional, default `true`): sort direction

``` utlx
let csv = "Name,Age\nCharlie,35\nAlice,30\nBob,25"
{
  sorted: csvSort(csv, "Name", true)
}
// Output: {sorted: "Name,Age\nAlice,30\nBob,25\nCharlie,35"}
```
