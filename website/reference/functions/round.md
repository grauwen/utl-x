---
title: round
description: "round — UTL-X Math function. Round to the nearest integer (half-up rounding)."
pageClass: stdlib-page
---

# round

<p class="stdlib-meta"><code>round(number) → integer</code> · <a href="/reference/stdlib#math">Math</a></p>

Round to the nearest integer (half-up rounding).

- `number` (required): the number to round

``` utlx
round(3.5)      // 4
round(3.4)      // 3
round(-3.5)     // -3

// Round to 2 decimal places (common pattern for currency):
round(6.2895 * 100) / 100               // 6.29
```

**Anti-pattern:** `round(price)` for currency — loses cents entirely.
Use `roundToCents(price)` or `roundToDecimalPlaces(price, 2)` instead.
