---
title: variance
description: "variance — UTL-X Math function. Compute the variance of a numeric array."
pageClass: stdlib-page
---

# variance

<p class="stdlib-meta"><code>variance(array) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Compute the variance of a numeric array.

- `array` (required): array of numbers

``` bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'variance($input.scores)'
# ~72.2
```
