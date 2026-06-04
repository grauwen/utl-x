---
title: csvRow
description: "csvRow — UTL-X CSV function. Get a specific row by index as an object (keyed by column names)."
pageClass: stdlib-page
---

# csvRow

<p class="stdlib-meta"><code>csvRow(csv, index, options?) → object</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Get a specific row by index as an object (keyed by column names).

- `csv` (required): CSV string

- `index` (required): row index (0-based, excluding header)

- `options` (optional): parsing options

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  firstRow: csvRow(csv, 0)    // {Name: "Alice", Age: "30"}
}
```
