---
title: exp
description: "exp — UTL-X Math function. Returns e raised to the power of x (e^x)."
pageClass: stdlib-page
---

# exp

<p class="stdlib-meta"><code>exp(x) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Returns e raised to the power of x (e^x).

- `x` (required): the exponent

``` bash
echo '{"rate": 0.05}' | utlx -e 'exp($input.rate)'
# 1.0512710963760241
```

``` utlx
{
  growth: exp($input.rate * $input.years),
  eSquared: exp(2)
}
```
