---
title: parseJson
description: "parseJson — UTL-X Format function. Parse a JSON string into a navigable UDM value. See Chapter 24."
pageClass: stdlib-page
---

# parseJson

<p class="stdlib-meta"><code>parseJson(string) → value</code> · <a href="/reference/stdlib#format">Format</a></p>

Parse a JSON string into a navigable UDM value. See Chapter 24.

- `string` (required): the JSON string to parse

``` utlx
let config = parseJson($input.configJson)
config.database.host                     // "localhost"
```
