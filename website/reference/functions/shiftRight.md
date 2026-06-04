---
title: shiftRight
description: "shiftRight — UTL-X Binary function. Shift bits right by the specified number of positions."
pageClass: stdlib-page
---

# shiftRight

<p class="stdlib-meta"><code>shiftRight(binary, positions) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Shift bits right by the specified number of positions.

- `binary` (required): binary data

- `positions` (required): number of positions to shift

``` utlx
shiftRight(toBinary("A", "UTF-8"), 2)    // bits shifted right by 2
```
