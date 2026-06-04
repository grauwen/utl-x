---
title: formatCurrency
description: "formatCurrency — UTL-X Financial function. Formats a number as currency with locale-specific formatting."
pageClass: stdlib-page
---

# formatCurrency

<p class="stdlib-meta"><code>formatCurrency(amount, currency?, locale?) → string</code> · <a href="/reference/stdlib#financial">Financial</a></p>

Formats a number as currency with locale-specific formatting.

- `amount` (required): numeric amount

- `currency` (optional): ISO 4217 currency code (e.g., "USD", "EUR")

- `locale` (optional): locale for formatting (e.g., "en-US", "de-DE")

``` bash
echo '{"amount": 1234.5}' | utlx -e 'formatCurrency($input.amount, "USD", "en-US")'
# "$1,234.50"
```

``` utlx
{
  price: formatCurrency($input.total, "EUR", "de-DE"),
  usd: formatCurrency($input.amount, "USD", "en-US")
}
```
