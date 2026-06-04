---
title: parseCurrency
description: "parseCurrency — UTL-X Financial function. Parse a currency-formatted string (with symbol and thousands separators)"
pageClass: stdlib-page
---

# parseCurrency

<p class="stdlib-meta"><code>parseCurrency(string) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Parse a currency-formatted string (with symbol and thousands separators)
into a number.

- `string` (required): currency string like `"$1,234.56"` or
  `"EUR 1.000,00"`

``` bash
echo '{"price": "$1,234.56"}' | utlx -e 'parseCurrency($input.price)'
# 1234.56
```

``` utlx
{
  amount: parseCurrency($input.total)
}
```
