---
title: roundToDecimalPlaces
description: "roundToDecimalPlaces — UTL-X Math function. Round a number to a specified number of decimal places."
pageClass: stdlib-page
---

# roundToDecimalPlaces

<p class="stdlib-meta"><code>roundToDecimalPlaces(number, places) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Round a number to a specified number of decimal places.

- `number` (required): the value to round

- `places` (required): number of decimal places

``` utlx
roundToDecimalPlaces(3.14159, 3)         // 3.142
roundToDecimalPlaces(100.0, 0)           // 100
```
