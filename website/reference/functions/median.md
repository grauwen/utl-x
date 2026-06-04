---
title: median
description: "median — UTL-X Math function. Compute the median (middle value) of a numeric array."
pageClass: stdlib-page
---

# median

<p class="stdlib-meta"><code>median(array) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Compute the median (middle value) of a numeric array.

- `array` (required): array of numbers

``` bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'median($input.scores)'
# 88
```
