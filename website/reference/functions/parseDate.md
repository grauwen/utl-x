---
title: parseDate
description: "parseDate — UTL-X Date & Time function. Parse a date or datetime string using a format pattern. See formatDate"
pageClass: stdlib-page
---

# parseDate

<p class="stdlib-meta"><code>parseDate(string, pattern) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Parse a date or datetime string using a format pattern. See `formatDate`
for pattern tokens.

- `string` (required): the date string to parse

- `pattern` (required): format pattern

``` utlx
let d = parseDate($input.date, "dd/MM/yyyy")
{
  isoDate: formatDate(d, "yyyy-MM-dd"),
  display: formatDate(d, "MMMM d, yyyy")
}
```

``` bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'formatDate(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "dd MMM yyyy")'
# 01 May 2026
```

**Anti-pattern:** assuming date format — `01/02/2026` is January 2nd (US
`MM/dd/yyyy`) or February 1st (EU `dd/MM/yyyy`). Always specify the
pattern explicitly.

Also: `parseDateTimeWithTimezone(string, pattern, timezone)`.
