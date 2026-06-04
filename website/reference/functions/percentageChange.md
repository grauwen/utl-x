---
title: percentageChange
description: "percentageChange — UTL-X Math function. Calculate the percentage change between two values."
pageClass: stdlib-page
---

# percentageChange

<p class="stdlib-meta"><code>percentageChange(oldValue, newValue) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Calculate the percentage change between two values.

- `oldValue` (required): the original value

- `newValue` (required): the new value

``` bash
echo '{"before": 100, "after": 125}' | utlx -e 'percentageChange($input.before, $input.after)'
# 25.0
```

``` utlx
{
  growth: percentageChange($input.lastYear, $input.thisYear)
}
```
