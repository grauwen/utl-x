---
title: translate
description: "translate — UTL-X String function. Translate characters in a string: each character in from is replaced"
pageClass: stdlib-page
---

# translate

<p class="stdlib-meta"><code>translate(string, from, to) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Translate characters in a string: each character in `from` is replaced
by the corresponding character in `to`.

- `string` (required): the source string

- `from` (required): characters to replace

- `to` (required): replacement characters (same length)

``` utlx
translate("hello", "elo", "ELO")         // "hELLO"
translate("2026-05-01", "-", "/")        // "2026/05/01"
```
