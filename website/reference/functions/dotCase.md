---
title: dotCase
description: "dotCase — UTL-X String function. Convert a string to dot.case (lowercase words separated by dots)."
pageClass: stdlib-page
---

# dotCase

<p class="stdlib-meta"><code>dotCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to dot.case (lowercase words separated by dots).

- `string` (required): the string to convert

``` utlx
dotCase("hello world")        // "hello.world"
dotCase("camelCase")          // "camel.case"
dotCase("SCREAMING_SNAKE")    // "screaming.snake"
```
