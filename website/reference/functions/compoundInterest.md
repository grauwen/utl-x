---
title: compoundInterest
description: "compoundInterest — UTL-X Financial function. Calculate compound interest (total amount after compounding)."
pageClass: stdlib-page
---

# compoundInterest

<p class="stdlib-meta"><code>compoundInterest(principal, rate, periods) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate compound interest (total amount after compounding).

- `principal` (required): initial amount

- `rate` (required): interest rate per period as decimal

- `periods` (required): number of compounding periods

``` bash
echo '{"principal": 1000, "rate": 0.05, "years": 10}' \
  | utlx -e 'compoundInterest($input.principal, $input.rate, $input.years)'
# 1628.89 (approximately)
```

``` utlx
{
  futureValue: compoundInterest($input.principal, $input.annualRate, $input.years)
}
```
