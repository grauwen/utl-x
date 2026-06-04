---
title: presentValue
description: "presentValue — UTL-X Financial function. Calculate the present value of a future amount given a discount rate and"
pageClass: stdlib-page
---

# presentValue

<p class="stdlib-meta"><code>presentValue(futureAmount, rate, periods) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate the present value of a future amount given a discount rate and
number of periods.

- `futureAmount` (required): the future amount

- `rate` (required): discount rate per period (e.g. 0.05 for 5%)

- `periods` (required): number of periods

``` utlx
presentValue(10000, 0.05, 3)             // ~8638.38 (PV of 10000 in 3 years at 5%)
```
