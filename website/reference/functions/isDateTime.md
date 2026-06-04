---
title: isDateTime
description: "isDateTime — UTL-X Type function. Returns true if the value is a datetime."
pageClass: stdlib-page
---

# isDateTime

<p class="stdlib-meta"><code>isDateTime(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a datetime.

- `value` (required): the value to test

``` utlx
isDateTime(now())                                     // true
isDateTime("2026-05-01T14:30:00Z")                    // false (string, not datetime)
```
