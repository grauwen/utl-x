---
title: substring
description: "substring — UTL-X String function. Extract part of a string by index (zero-based)."
pageClass: stdlib-page
---

# substring

<p class="stdlib-meta"><code>substring(string, start, end?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Extract part of a string by index (zero-based).

- `string` (required): the source string

- `start` (required): starting index (zero-based, inclusive)

- `end` (optional): ending index (exclusive). If omitted, goes to end.

``` utlx
substring("Hello World", 6)             // "World"
substring("Hello World", 0, 5)          // "Hello"
```
