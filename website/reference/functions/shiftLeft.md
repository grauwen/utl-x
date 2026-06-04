---
title: shiftLeft
description: "shiftLeft — UTL-X Binary function. Shift bits left by the specified number of positions."
pageClass: stdlib-page
---

# shiftLeft

<p class="stdlib-meta"><code>shiftLeft(binary, positions) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Shift bits left by the specified number of positions.

- `binary` (required): binary data

- `positions` (required): number of positions to shift

``` utlx
shiftLeft(toBinary("A", "UTF-8"), 2)     // bits shifted left by 2
```
