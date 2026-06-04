---
title: futureValue
description: "futureValue — UTL-X Financial function. Calculates the future value of a present amount given compound interest."
pageClass: stdlib-page
---

# futureValue

<p class="stdlib-meta"><code>futureValue(presentValue, rate, periods) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculates the future value of a present amount given compound interest.

- `presentValue` (required): current amount

- `rate` (required): interest rate per period (e.g., 0.05 for 5%)

- `periods` (required): number of compounding periods

``` bash
echo '{"pv": 1000, "rate": 0.05, "years": 10}' | utlx -e 'futureValue($input.pv, $input.rate, $input.years)'
# 1628.89
```

``` utlx
{
  futureAmount: futureValue($input.principal, $input.annualRate, $input.years)
}
```
