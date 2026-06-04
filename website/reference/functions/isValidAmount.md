---
title: isValidAmount
description: "isValidAmount — UTL-X Financial function. Validates if an amount is within acceptable range (finite, not NaN)."
pageClass: stdlib-page
---

# isValidAmount

<p class="stdlib-meta"><code>isValidAmount(amount) → boolean</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Validates if an amount is within acceptable range (finite, not NaN).

- `amount` (required): numeric value to validate

``` utlx
{
  valid: isValidAmount($input.total),
  canProcess: isValidAmount($input.payment) && $input.payment > 0
}
```
