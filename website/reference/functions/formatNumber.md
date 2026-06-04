---
title: formatNumber
description: "formatNumber — UTL-X Math function. Format a number using a pattern string."
pageClass: stdlib-page
---

# formatNumber

<p class="stdlib-meta"><code>formatNumber(number, pattern?) → string</code> · <a href="/reference/stdlib#math">Math</a></p>

Format a number using a pattern string.

- `number` (required): number to format

- `pattern` (optional): format pattern (e.g., `"#,##0.00"`)

``` bash
echo '{"amount": 1234567.891}' | utlx -e 'formatNumber($input.amount, "#,##0.00")'
# "1,234,567.89"
```

``` utlx
{
  formatted: formatNumber($input.price, "#,##0.00"),
  integer: formatNumber($input.qty, "#,##0"),
  percent: formatNumber($input.rate * 100, "0.0")
}
```
