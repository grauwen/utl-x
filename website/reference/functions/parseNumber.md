---
title: parseNumber
description: "parseNumber — UTL-X Math function. Primary function for converting string values to numbers. Handles"
pageClass: stdlib-page
---

# parseNumber

<p class="stdlib-meta"><code>parseNumber(string) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Primary function for converting string values to numbers. Handles
integers and decimals.

- `string` (required): numeric string to parse

``` utlx
parseNumber("123.45")                    // 123.45
parseNumber($input.Order.Quantity)       // XML values are strings — convert for arithmetic
```
