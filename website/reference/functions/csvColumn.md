---
title: csvColumn
description: "csvColumn — UTL-X CSV function. Get all values from a specific column as an array."
pageClass: stdlib-page
---

# csvColumn

<p class="stdlib-meta"><code>csvColumn(csv, name) → array</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Get all values from a specific column as an array.

- `csv` (required): CSV string

- `name` (required): column name

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25\nCharlie,35"
{
  names: csvColumn(csv, "Name")     // ["Alice", "Bob", "Charlie"]
}
```
