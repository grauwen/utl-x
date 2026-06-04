---
title: constantCase
description: "constantCase — UTL-X String function. Convert a string to CONSTANT_CASE (uppercase with underscores)."
pageClass: stdlib-page
---

# constantCase

<p class="stdlib-meta"><code>constantCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to CONSTANT_CASE (uppercase with underscores).

- `string` (required): the string to convert

``` utlx
constantCase("hello world")        // "HELLO_WORLD"
constantCase("camelCase")          // "CAMEL_CASE"
constantCase("some-kebab-case")    // "SOME_KEBAB_CASE"
```
