---
title: addTax
description: "addTax — UTL-X Financial function. Calculate total amount including tax."
pageClass: stdlib-page
---

# addTax

<p class="stdlib-meta"><code>addTax(amount, rate) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate total amount including tax.

- `amount` (required): net amount before tax

- `rate` (required): tax rate as decimal (e.g., 0.21 for 21%)

``` bash
echo '{"price": 100, "vatRate": 0.21}' | utlx -e 'addTax($input.price, $input.vatRate)'
# 121.0
```

``` utlx
{
  netPrice: $input.price,
  totalWithVAT: addTax($input.price, 0.21)
}
```
