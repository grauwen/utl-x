---
title: seconds
description: "seconds — UTL-X Date & Time function. Extract the seconds component (0-59) from a datetime."
pageClass: stdlib-page
---

# seconds

<p class="stdlib-meta"><code>seconds(datetime) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Extract the seconds component (0-59) from a datetime.

- `datetime` (required): a datetime value

``` utlx
seconds(now())                           // e.g. 45
```
