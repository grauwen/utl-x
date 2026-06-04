---
title: csvAddColumn
description: "csvAddColumn — UTL-X CSV function. Add a new column with a default value to all rows in a CSV string."
pageClass: stdlib-page
---

# csvAddColumn

<p class="stdlib-meta"><code>csvAddColumn(csv, name, defaultValue) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Add a new column with a default value to all rows in a CSV string.

- `csv` (required): CSV string

- `name` (required): new column name

- `defaultValue` (required): default value for all rows

``` utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  result: csvAddColumn(csv, "Status", "active")
}
// Output: {result: "Name,Age,Status\nAlice,30,active\nBob,25,active"}
```
