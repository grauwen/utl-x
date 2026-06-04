---
title: mapEntries
description: "mapEntries — UTL-X Object function. Transform both keys and values of an object."
pageClass: stdlib-page
---

# mapEntries

<p class="stdlib-meta"><code>mapEntries(object, fn) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Transform both keys and values of an object.

- `object` (required): the object to transform

- `fn` (required): lambda
  `(key, value) -> {key: newKey, value: newValue}`

``` bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapEntries($input, (key, value) -> {key: upperCase(key), value: if (isString(value)) upperCase(value) else value})'
# {"FIRST_NAME": "ALICE", "LAST_NAME": "JOHNSON", "AGE": 30}
```
