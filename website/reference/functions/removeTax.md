---
title: removeTax
description: "removeTax — UTL-X Financial function. Calculate the original amount from a total that includes tax."
pageClass: stdlib-page
---

# removeTax

<p class="stdlib-meta"><code>removeTax(totalWithTax, rate) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Calculate the original amount from a total that includes tax.

- `totalWithTax` (required): the total including tax

- `rate` (required): tax rate (e.g. 0.21 for 21%)

``` utlx
removeTax(121, 0.21)                     // 100.0 (remove 21% VAT from 121)
```
