---
title: compactCSV
description: "compactCSV — UTL-X CSV function. Compact a CSV string by removing extra whitespace."
pageClass: stdlib-page
---

# compactCSV

<p class="stdlib-meta"><code>compactCSV(csv, options?) → string</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Compact a CSV string by removing extra whitespace.

- `csv` (required): CSV string to compact

- `options` (optional): formatting options

``` utlx
// See Chapter 25 for CSV processing.
{
  compacted: compactCSV($input.csvData)
}
```
