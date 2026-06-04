---
title: csvFilter
description: "csvFilter — UTL-X CSV function. Filter CSV rows by a column condition. Returns a new CSV string. See"
pageClass: stdlib-page
---

# csvFilter

<p class="stdlib-meta"><code>csvFilter(csv, column, operator, value) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Filter CSV rows by a column condition. Returns a new CSV string. See
Chapter 25.

- `csv` (required): CSV string to filter

- `column` (required): column name to test

- `operator` (required): comparison — `"eq"`, `"ne"`, `"contains"`,
  `"startswith"`, `"endswith"`, `"gt"`, `"lt"`, `"gte"`, `"lte"`

- `value` (required): value to compare against

``` utlx
// See Chapter 25 for CSV processing.
let csv = "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200\nCharlie,ACTIVE,50"
{
  active: csvFilter(csv, "Status", "eq", "ACTIVE"),
  // "Name,Status,Amount\nAlice,ACTIVE,100\nCharlie,ACTIVE,50"
  highValue: csvFilter(csv, "Amount", "gt", "75")
  // "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200"
}
```

Also: `csvSort(csv, column, ascending?)`, `csvColumns(csv)`,
`csvRows(csv)`, `csvRow(csv, index)`, `csvCell(csv, row, column)`,
`csvColumn(csv, name)`, `csvTranspose(csv)`,
`csvAddColumn(csv, name, default)`, `csvRemoveColumns(csv, names)`,
`csvSelectColumns(csv, names)`, `csvSummarize(csv)`.

## D
