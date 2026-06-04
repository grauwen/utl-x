---
title: cos
description: "cos — UTL-X Math function. Cosine of an angle in radians."
pageClass: stdlib-page
---

# cos

<p class="stdlib-meta"><code>cos(radians) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Cosine of an angle in radians.

- `radians` (required): angle in radians

``` utlx
cos(0)         // 1.0
cos(pi())      // -1.0
cos(pi() / 2)  // ~0.0 (very small number due to floating point)
```
