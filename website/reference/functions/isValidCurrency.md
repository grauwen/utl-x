---
title: isValidCurrency
description: "isValidCurrency — UTL-X Financial function. Validates if a currency code is valid according to ISO 4217."
pageClass: stdlib-page
---

# isValidCurrency

<p class="stdlib-meta"><code>isValidCurrency(code) → boolean</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Validates if a currency code is valid according to ISO 4217.

- `code` (required): currency code string (e.g., "USD", "EUR")

``` bash
echo '{"currency": "USD"}' | utlx -e 'isValidCurrency($input.currency)'
# true
```

``` utlx
{
  valid: isValidCurrency($input.currencyCode),
  error: if (!isValidCurrency($input.currency)) "Invalid currency" else null
}
```
