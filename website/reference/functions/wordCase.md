---
title: wordCase
description: "wordCase — UTL-X String function. Convert a string to word case (capitalize first letter, rest lowercase)."
pageClass: stdlib-page
---

# wordCase

<p class="stdlib-meta"><code>wordCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to word case (capitalize first letter, rest lowercase).

- `string` (required): the string to convert

``` utlx
wordCase("HELLO WORLD")                  // "Hello world"
wordCase("hello")                        // "Hello"
```
