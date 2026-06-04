---
title: diffDays
description: "diffDays — UTL-X Date & Time function. Difference between two dates in days. Variants: diffMonths,"
pageClass: stdlib-page
---

# diffDays

<p class="stdlib-meta"><code>diffDays(date1, date2) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Difference between two dates in days. Variants: `diffMonths`,
`diffYears`, `diffHours`, `diffMinutes`, `diffSeconds`, `diffWeeks`.

- `date1` (required): start date

- `date2` (required): end date

``` bash
echo '{"start": "2026-05-01", "end": "2026-06-15"}' \
  | utlx -e 'diffDays(parseDate($input.start, "yyyy-MM-dd"), parseDate($input.end, "yyyy-MM-dd"))'
# 45
```

``` utlx
let start = parseDate($input.startDate, "yyyy-MM-dd")
let end = parseDate($input.endDate, "yyyy-MM-dd")
{
  daysBetween: diffDays(start, end),
  weeksBetween: diffWeeks(start, end),
  overdue: if (diffDays(end, now()) > 0) "Yes" else "No"
}
```
