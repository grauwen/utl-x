---
title: uncamelize
description: "uncamelize — UTL-X String function. Convert from camelCase to separate words. Legacy naming — prefer"
pageClass: stdlib-page
---

# uncamelize

<p class="stdlib-meta"><code>uncamelize(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Convert from camelCase to separate words. Legacy naming — prefer
`fromCamelCase`.

- `string` (required): camelCase string

``` utlx
uncamelize("helloWorld")                 // "hello world"
uncamelize("firstName")                  // "first name"
```
