---
title: compareDates
description: "compareDates — UTL-X Date & Time function. Compare two dates. Returns negative if date1 is before date2, zero if"
pageClass: stdlib-page
---

# compareDates

<p class="stdlib-meta"><code>compareDates(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Compare two dates. Returns negative if date1 is before date2, zero if
equal, positive if after.

- `date1` (required): first date

- `date2` (required): second date

``` utlx
let d1 = parseDate("2026-01-01", "yyyy-MM-dd")
let d2 = parseDate("2026-06-01", "yyyy-MM-dd")
{
  before: compareDates(d1, d2),    // negative (d1 is before d2)
  after: compareDates(d2, d1),     // positive (d2 is after d1)
  equal: compareDates(d1, d1)      // 0 (equal)
}
```
