---
title: csvRemoveColumns
description: "csvRemoveColumns — UTL-X CSV function. Remove specified columns from a CSV string."
pageClass: stdlib-page
---

# csvRemoveColumns

<p class="stdlib-meta"><code>csvRemoveColumns(csv, columns) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Remove specified columns from a CSV string.

- `csv` (required): CSV string

- `columns` (required): array of column names to remove

``` utlx
let csv = "Name,Age,Email,Phone\nAlice,30,a@b.com,555-1234"
{
  stripped: csvRemoveColumns(csv, ["Phone", "Email"])
}
// Output: {stripped: "Name,Age\nAlice,30"}
```
