---
title: calculateDiscount
description: "calculateDiscount — UTL-X Financial function. Calculate the price after applying a percentage discount."
pageClass: stdlib-page
---

# calculateDiscount

<p class="stdlib-meta"><code>calculateDiscount(price, rate) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate the price after applying a percentage discount.

- `price` (required): original price

- `rate` (required): discount rate as decimal (e.g., 0.10 for 10%)

``` bash
echo '{"price": 200, "discount": 0.15}' | utlx -e 'calculateDiscount($input.price, $input.discount)'
# 170.0
```

``` utlx
{
  originalPrice: $input.price,
  discountedPrice: calculateDiscount($input.price, 0.10)
}
```
