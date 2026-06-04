---
title: floor
description: "floor — UTL-X Math function. Round down to the nearest integer (floor)."
pageClass: stdlib-page
---

# floor

<p class="stdlib-meta"><code>floor(number) → integer</code> · <a href="/reference/stdlib#math">Math</a></p>

Round down to the nearest integer (floor).

- `number` (required): the number to round down

``` utlx
floor(3.8)      // 3
floor(3.1)      // 3
floor(-3.8)     // -4 (away from zero for negatives)
floor(4.0)      // 4 (already integer)
```
