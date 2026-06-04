---
title: stdDev
description: "stdDev — UTL-X Math function. Compute the standard deviation of a numeric array."
pageClass: stdlib-page
---

# stdDev

<p class="stdlib-meta"><code>stdDev(array) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Compute the standard deviation of a numeric array.

- `array` (required): array of numbers

``` bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'stdDev($input.scores)'
# ~8.5
```
