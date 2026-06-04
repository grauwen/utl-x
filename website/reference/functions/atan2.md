---
title: atan2
description: "atan2 — UTL-X Math function. Two-argument arc tangent. Converts Cartesian coordinates (x, y) to polar"
pageClass: stdlib-page
---

# atan2

<p class="stdlib-meta"><code>atan2(y, x) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Two-argument arc tangent. Converts Cartesian coordinates (x, y) to polar
angle in radians.

- `y` (required): y-coordinate

- `x` (required): x-coordinate

``` utlx
atan2(1, 1)    // 0.7853981633974483 (π/4 — 45°)
atan2(0, -1)   // 3.141592653589793 (π — 180°)
atan2(-1, 0)   // -1.5707963267948966 (-π/2 — -90°)
```
