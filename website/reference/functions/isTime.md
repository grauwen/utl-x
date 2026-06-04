---
title: isTime
description: "isTime — UTL-X Type function. Returns true if the value is a time value."
pageClass: stdlib-page
---

# isTime

<p class="stdlib-meta"><code>isTime(value) → boolean</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns true if the value is a time value.

- `value` (required): the value to test

``` utlx
isTime(parseTime("14:30:00", "HH:mm:ss"))            // true
isTime("14:30:00")                                    // false (string, not time)
```
