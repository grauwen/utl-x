---
title: padRight
description: "padRight — UTL-X String function. Pad a string on the right to the given length."
pageClass: stdlib-page
---

# padRight

<p class="stdlib-meta"><code>padRight(string, length, char?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Pad a string on the right to the given length.

- `string` (required): the string to pad

- `length` (required): target length

- `char` (optional): pad character (default `" "`)

``` utlx
padRight("hello", 10, ".")              // "hello....."
```
