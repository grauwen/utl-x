---
title: padLeft
description: "padLeft — UTL-X String function. Explicit left padding — alias for pad()."
pageClass: stdlib-page
---

# padLeft

<p class="stdlib-meta"><code>padLeft(string, length, char?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Explicit left padding — alias for `pad()`.

- `string` (required): the string to pad

- `length` (required): target length

- `char` (optional): pad character (default `" "`)

``` utlx
padLeft("42", 5, "0")                    // "00042"
```
