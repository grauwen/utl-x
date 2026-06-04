---
title: roundToCents
description: "roundToCents — UTL-X Math function. Round a number to 2 decimal places (financial rounding for currency)."
pageClass: stdlib-page
---

# roundToCents

<p class="stdlib-meta"><code>roundToCents(number) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Round a number to 2 decimal places (financial rounding for currency).

- `number` (required): the value to round

``` utlx
roundToCents(29.999)                     // 30.0
roundToCents(10.004)                     // 10.0

// Use case: invoice line total with correct rounding
let lineTotal = roundToCents(qty * unitPrice)
```
