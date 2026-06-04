---
title: capitalize
description: "capitalize — UTL-X String function. Capitalize the first letter of a string. Only affects the first"
pageClass: stdlib-page
---

# capitalize

<p class="stdlib-meta"><code>capitalize(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Capitalize the first letter of a string. Only affects the first
character.

- `string` (required): the string to capitalize

``` utlx
capitalize("hello world")               // "Hello world"
capitalize("HELLO")                      // "HELLO" (already uppercase — no change)
capitalize("")                           // ""
```
