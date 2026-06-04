---
title: csvRows
description: "csvRows — UTL-X CSV function. Get the number of data rows in a CSV string (excluding header)."
pageClass: stdlib-page
---

# csvRows

<p class="stdlib-meta"><code>csvRows(csv) → number</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Get the number of data rows in a CSV string (excluding header).

- `csv` (required): CSV string

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25\nCharlie,35"
{
  rowCount: csvRows(csv)    // 3
}
```
