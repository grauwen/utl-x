---
title: pathCase
description: "pathCase — UTL-X String function. Convert a string to path/case (words separated by /)."
pageClass: stdlib-page
---

# pathCase

<p class="stdlib-meta"><code>pathCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to path/case (words separated by `/`).

- `string` (required): the string to convert

``` utlx
pathCase("hello world")                 // "hello/world"
pathCase("someVariable")                // "some/variable"
```
