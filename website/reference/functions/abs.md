---
title: abs
description: "abs — UTL-X Math function. Returns the absolute value of a number."
pageClass: stdlib-page
---

# abs

<p class="stdlib-meta"><code>abs(number) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Returns the absolute value of a number.

- `number` (required): the number to take the absolute value of

``` bash
echo '{"balance": -1500.50}' | utlx -e 'abs(.balance)'
# 1500.5
```

``` utlx
abs(-42)      // 42
abs(42)       // 42 (positive unchanged)
abs(0)        // 0
```
