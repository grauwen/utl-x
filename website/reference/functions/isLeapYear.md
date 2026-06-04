---
title: isLeapYear
description: "isLeapYear — UTL-X Date & Time function. Returns true if the given year is a leap year."
pageClass: stdlib-page
---

# isLeapYear

<p class="stdlib-meta"><code>isLeapYear(year) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Returns true if the given year is a leap year.

- `year` (required): year number

``` utlx
isLeapYear(2024)                         // true (divisible by 4, not by 100, or by 400)
isLeapYear(2026)                         // false
```
