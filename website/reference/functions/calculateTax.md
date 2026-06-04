---
title: calculateTax
description: "calculateTax — UTL-X Financial function. Calculate the tax amount for a given amount and rate (returns only the"
pageClass: stdlib-page
---

# calculateTax

<p class="stdlib-meta"><code>calculateTax(amount, rate) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate the tax amount for a given amount and rate (returns only the
tax portion, not the total).

- `amount` (required): taxable amount

- `rate` (required): tax rate as decimal (e.g., 0.21 for 21%)

``` bash
echo '{"subtotal": 500, "vatRate": 0.21}' | utlx -e 'calculateTax($input.subtotal, $input.vatRate)'
# 105.0
```

``` utlx
{
  subtotal: $input.amount,
  tax: calculateTax($input.amount, 0.21),
  total: addTax($input.amount, 0.21)
}
```
