---
title: csvSelectColumns
description: "csvSelectColumns — UTL-X CSV function. Select/project only specific columns from a CSV string."
pageClass: stdlib-page
---

# csvSelectColumns

<p class="stdlib-meta"><code>csvSelectColumns(csv, columns) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Select/project only specific columns from a CSV string.

- `csv` (required): CSV string

- `columns` (required): array of column names to keep

``` utlx
let csv = "Name,Age,Email,Phone\nAlice,30,a@b.com,555-1234"
{
  projected: csvSelectColumns(csv, ["Name", "Email"])
}
// Output: {projected: "Name,Email\nAlice,a@b.com"}
```
