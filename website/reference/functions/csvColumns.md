---
title: csvColumns
description: "csvColumns — UTL-X CSV function. Get all column names (headers) from CSV data."
pageClass: stdlib-page
---

# csvColumns

<p class="stdlib-meta"><code>csvColumns(csv) → array</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Get all column names (headers) from CSV data.

- `csv` (required): CSV string

``` utlx
let csv = "Name,Age,Email\nAlice,30,alice@example.com"
{
  headers: csvColumns(csv)    // ["Name", "Age", "Email"]
}
```
