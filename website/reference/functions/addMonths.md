---
title: addMonths
description: "addMonths — UTL-X Date & Time function. Add (or subtract) months to a date. If the resulting day exceeds the"
pageClass: stdlib-page
---

# addMonths

<p class="stdlib-meta"><code>addMonths(date, count) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) months to a date. If the resulting day exceeds the
target month's length, it is clamped to the last day of that month.

- `date` (required): a date or datetime value — NOT a string. If your
  date is a string (JSON, XML, CSV input), convert it first with
  `parseDate()`. YAML auto-parses dates, so YAML input values are
  already dates.

- `count` (required): number of months to add. Negative to subtract.

``` utlx
// From JSON/XML/CSV input — date is a string, parseDate needed:
let orderDate = parseDate($input.orderDate, "yyyy-MM-dd")
addMonths(orderDate, 3)                  // 2026-08-01

// From YAML input — date is already a date value, no parseDate needed:
addMonths($input.orderDate, 3)           // works directly

// With string literals in examples, parseDate is always needed:
addMonths(parseDate("2026-05-01", "yyyy-MM-dd"), -2)   // 2026-03-01

// Edge case: end-of-month clamping
addMonths(parseDate("2026-01-31", "yyyy-MM-dd"), 1)    // 2026-02-28 (not Feb 31)
addMonths(parseDate("2026-03-31", "yyyy-MM-dd"), 1)    // 2026-04-30 (not Apr 31)
```
