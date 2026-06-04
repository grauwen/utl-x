---
title: csvSummarize
description: "csvSummarize — UTL-X CSV function. Calculate summary statistics for numeric CSV columns (count, sum, avg,"
pageClass: stdlib-page
---

# csvSummarize

<p class="stdlib-meta"><code>csvSummarize(csv, options?) → object</code> · <a href="/reference/stdlib#csv">CSV</a></p>

Calculate summary statistics for numeric CSV columns (count, sum, avg,
min, max).

- `csv` (required): CSV string

- `options` (optional): summarization options

``` utlx
let csv = "Product,Price,Qty\nA,10,5\nB,20,3\nC,15,8"
{
  stats: csvSummarize(csv)
}
// Output: {stats: {Price: {count: 3, sum: 45, avg: 15, min: 10, max: 20},
//   Qty: {count: 3, sum: 16, ...}}}
```
