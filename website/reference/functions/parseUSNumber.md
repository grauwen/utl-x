---
title: parseUSNumber
description: "parseUSNumber — UTL-X Math function. Parse a US-formatted number string (comma=thousands, dot=decimal) to a"
pageClass: stdlib-page
---

# parseUSNumber

<p class="stdlib-meta"><code>parseUSNumber(string) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Parse a US-formatted number string (comma=thousands, dot=decimal) to a
standard number.

- `string` (required): the formatted number string

``` utlx
parseUSNumber("1,234.56")               // 1234.56 (US: comma=thousands, dot=decimal)
```

Also: `renderUSNumber(number)` for the reverse direction.
