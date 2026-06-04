---
title: isLeapYearFunc
description: "isLeapYearFunc — UTL-X Date & Time function. Check if a year is a leap year."
pageClass: stdlib-page
---

# isLeapYearFunc

<p class="stdlib-meta"><code>isLeapYearFunc(year) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Check if a year is a leap year.

- `year` (required): year number

``` utlx
isLeapYearFunc(2024)                     // true
isLeapYearFunc(2026)                     // false
{
  leap: isLeapYearFunc(year(now()))
}
```
