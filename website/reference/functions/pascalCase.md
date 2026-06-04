---
title: pascalCase
description: "pascalCase — UTL-X String function. Convert a string to PascalCase (UpperCamelCase)."
pageClass: stdlib-page
---

# pascalCase

<p class="stdlib-meta"><code>pascalCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to PascalCase (UpperCamelCase).

- `string` (required): the string to convert

``` utlx
pascalCase("hello world")               // "HelloWorld"
pascalCase("some-kebab-case")           // "SomeKebabCase"
```
