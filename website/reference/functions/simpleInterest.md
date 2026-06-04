---
title: simpleInterest
description: "simpleInterest — UTL-X Financial function. Calculate simple interest: principal **rate** time."
pageClass: stdlib-page
---

# simpleInterest

<p class="stdlib-meta"><code>simpleInterest(principal, rate, time) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate simple interest: principal **rate** time.

- `principal` (required): the principal amount

- `rate` (required): annual interest rate (e.g. 0.05 for 5%)

- `time` (required): time in years

``` utlx
simpleInterest(1000, 0.05, 3)            // 150.0 (interest on 1000 at 5% for 3 years)
```
