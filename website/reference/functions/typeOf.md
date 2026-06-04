---
title: typeOf
description: "typeOf — UTL-X Type function. Return the type of a value as a string ('string', 'number',"
pageClass: stdlib-page
---

# typeOf

<p class="stdlib-meta"><code>typeOf(value) → string</code> · <a href="/reference/stdlib#type">Type</a></p>

Return the type of a value as a string (`"string"`, `"number"`,
`"boolean"`, `"array"`, `"object"`, `"null"`).

- `value` (required): any value

``` utlx
typeOf("hello")                          // "string"
typeOf(42)                               // "number"
typeOf([1, 2])                           // "array"
typeOf({a: 1})                           // "object"
```
