---
title: iqr
description: "iqr — UTL-X Math function. Calculate the interquartile range (IQR = Q3 - Q1) of a numeric array."
pageClass: stdlib-page
---

# iqr

<p class="stdlib-meta"><code>iqr(numbers) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Calculate the interquartile range (IQR = Q3 - Q1) of a numeric array.

- `numbers` (required): array of numbers

``` bash
echo '{"data": [1, 3, 5, 7, 9, 11, 13]}' | utlx -e 'iqr($input.data)'
# 8
```

``` utlx
{
  spread: iqr($input.values),
  outlierThreshold: iqr($input.values) * 1.5
}
```
