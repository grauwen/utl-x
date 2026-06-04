---
title: csvCell
description: "csvCell — UTL-X CSV function. Get a specific cell value by row index and column name."
pageClass: stdlib-page
---

# csvCell

<p class="stdlib-meta"><code>csvCell(csv, row, column) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Get a specific cell value by row index and column name.

- `csv` (required): CSV string

- `row` (required): row index (0-based, excluding header)

- `column` (required): column name

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  name: csvCell(csv, 0, "Name"),    // "Alice"
  age: csvCell(csv, 1, "Age")      // "25"
}
```
