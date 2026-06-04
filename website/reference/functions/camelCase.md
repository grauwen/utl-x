---
title: camelCase
description: "camelCase — UTL-X String function. Convert a string to camelCase."
pageClass: stdlib-page
---

# camelCase

<p class="stdlib-meta"><code>camelCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to camelCase.

- `string` (required): the string to convert

``` utlx
camelCase("hello world")        // "helloWorld"
camelCase("some-kebab-case")    // "someKebabCase"
camelCase("SCREAMING_SNAKE")    // "screamingSnake"
```
