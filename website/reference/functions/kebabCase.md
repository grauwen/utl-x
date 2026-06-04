---
title: kebabCase
description: "kebabCase — UTL-X String function. Convert a string to kebab-case (lowercase words separated by hyphens)."
pageClass: stdlib-page
---

# kebabCase

<p class="stdlib-meta"><code>kebabCase(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert a string to kebab-case (lowercase words separated by hyphens).

- `string` (required): the string to convert

``` bash
echo '"orderLineItem"' | utlx -e 'kebabCase($input)'
# order-line-item
```

``` utlx
{
  fromCamel: kebabCase("OrderLineItem"),     // "order-line-item"
  fromSnake: kebabCase("some_snake_case")    // "some-snake-case"
}
```
