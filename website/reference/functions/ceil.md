---
title: ceil
description: "ceil — UTL-X Math function. Round up to the nearest integer (ceiling)."
pageClass: stdlib-page
---

# ceil

<p class="stdlib-meta"><code>ceil(number) → integer</code> · <a href="/reference/stdlib#math">Math</a></p>

Round up to the nearest integer (ceiling).

- `number` (required): the number to round up

``` utlx
ceil(3.2)       // 4
ceil(3.9)       // 4
ceil(-3.2)      // -3 (towards zero for negatives)
ceil(4.0)       // 4 (already integer)
```
