---
title: formatDate
description: "formatDate — UTL-X Date & Time function. Format a date or datetime as a string using a pattern. See the Date"
pageClass: stdlib-page
---

# formatDate

<p class="stdlib-meta"><code>formatDate(date, pattern) → string</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Format a date or datetime as a string using a pattern. See the Date
Format Patterns section later in this chapter.

- `date` (required): date or datetime value

- `pattern` (required): format pattern string

Pattern tokens: `yyyy` (year), `MM` (month 01-12), `dd` (day 01-31),
`HH` (hour 00-23), `mm` (minute 00-59), `ss` (second 00-59), `EEEE` (day
name), `MMMM` (month name), `EEE` (short day), `MMM` (short month).

``` bash
echo '{"timestamp": "2026-05-01T14:30:00Z"}' \
  | utlx -e 'formatDate(parseDate($input.timestamp, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "dd/MM/yyyy")'
# "01/05/2026"
```

``` utlx
let dt = parseDate($input.timestamp, "yyyy-MM-dd'T'HH:mm:ss'Z'")
{
  isoDate: formatDate(dt, "yyyy-MM-dd"),
  european: formatDate(dt, "dd/MM/yyyy"),
  withTime: formatDate(dt, "dd-MM-yyyy HH:mm"),
  human: formatDate(dt, "EEEE, MMMM d, yyyy"),
  invoiceDate: formatDate(now(), "yyyy-MM-dd")
}
```

## G
