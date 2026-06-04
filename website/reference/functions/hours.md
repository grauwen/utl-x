---
title: hours
description: "hours — UTL-X Date & Time function. Extract the hours component (0-23) from a datetime value."
pageClass: stdlib-page
---

# hours

<p class="stdlib-meta"><code>hours(datetime) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Extract the hours component (0-23) from a datetime value.

- `datetime` (required): datetime value

``` bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'hours(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"))'
# 14
```

``` utlx
{
  hour: hours(now()),
  isBusinessHours: hours(now()) >= 9 && hours(now()) < 17
}
```

## I
