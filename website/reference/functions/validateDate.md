---
title: validateDate
description: "validateDate — UTL-X Date & Time function. Validate whether a string is a valid date (ISO 8601 or custom pattern)."
pageClass: stdlib-page
---

# validateDate

<p class="stdlib-meta"><code>validateDate(string, pattern?) → boolean</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Validate whether a string is a valid date (ISO 8601 or custom pattern).

- `string` (required): the date string to validate

- `pattern` (optional): format pattern to validate against

``` utlx
validateDate("2026-05-01")               // true
validateDate("2026-13-01")               // false (month 13 invalid)
validateDate("01/05/2026", "dd/MM/yyyy") // true
```
