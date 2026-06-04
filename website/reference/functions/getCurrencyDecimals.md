---
title: getCurrencyDecimals
description: "getCurrencyDecimals — UTL-X Financial function. Gets the number of decimal places for a currency code (ISO 4217)."
pageClass: stdlib-page
---

# getCurrencyDecimals

<p class="stdlib-meta"><code>getCurrencyDecimals(currency) → number</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Gets the number of decimal places for a currency code (ISO 4217).

- `currency` (required): ISO 4217 currency code

``` bash
echo '{"cur": "JPY"}' | utlx -e 'getCurrencyDecimals($input.cur)'
# 0
```

``` utlx
{
  usdDecimals: getCurrencyDecimals("USD"),   // 2
  jpyDecimals: getCurrencyDecimals("JPY")    // 0
}
```
