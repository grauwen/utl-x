---
title: udmToJSON
description: "udmToJSON — UTL-X Format function. Pretty-print a UDM object as JSON."
pageClass: stdlib-page
---

# udmToJSON

<p class="stdlib-meta"><code>udmToJSON(value, pretty?) → string</code> · <a href="/reference/stdlib#format">Format</a></p>

Pretty-print a UDM object as JSON.

- `value` (required): UDM value to format

- `pretty` (optional): pretty-print with indentation

``` utlx
udmToJSON($input, true)                  // formatted JSON string
```
