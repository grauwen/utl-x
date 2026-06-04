---
title: prettyPrintCSV
description: "prettyPrintCSV — UTL-X Format function. Format a CSV string with aligned columns for readability."
pageClass: stdlib-page
---

# prettyPrintCSV

<p class="stdlib-meta"><code>prettyPrintCSV(csv, options?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Format a CSV string with aligned columns for readability.

- `csv` (required): the CSV string

- `options` (optional): formatting options

``` utlx
prettyPrintCSV($input.csvData)           // columns aligned with padding
```
