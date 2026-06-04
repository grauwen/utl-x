---
title: getType
description: "getType — UTL-X Type function. Returns the type of a value as a string ('string', 'number', 'boolean',"
pageClass: stdlib-page
---

# getType

<p class="stdlib-meta"><code>getType(value) → string</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns the type of a value as a string ("string", "number", "boolean",
"array", "object", "null", "date", "datetime").

- `value` (required): the value to inspect

``` bash
echo '{"name": "Alice", "age": 30}' | utlx -e 'getType($input.name)'
# "string"
```

``` utlx
{
  nameType: getType($input.name),         // "string"
  ageType: getType($input.age),           // "number"
  itemsType: getType($input.items)        // "array"
}
```
