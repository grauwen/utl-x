---
title: percentile
description: "percentile — UTL-X Math function. Compute the p-th percentile of a numeric array."
pageClass: stdlib-page
---

# percentile

<p class="stdlib-meta"><code>percentile(array, p) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Compute the p-th percentile of a numeric array.

- `array` (required): array of numbers

- `p` (required): percentile value 0-100

``` bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'percentile($input.scores, 90)'
# ~94.2 (90th percentile)
```

Also: `iqr(array)` — interquartile range, `quartiles(array)` — \[Q1, Q2,
Q3\].
