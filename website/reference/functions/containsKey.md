---
title: containsKey
description: "containsKey — UTL-X Object function. Alias for hasKey(). Check if an object has a property with the given"
pageClass: stdlib-page
---

# containsKey

<p class="stdlib-meta"><code>containsKey(object, key) → boolean</code> · <a href="/reference/stdlib#object">Object</a></p>

Alias for `hasKey()`. Check if an object has a property with the given
key name.

- `object` (required): the object to check

- `key` (required): property name as string

``` utlx
containsKey($input, "email")             // true
containsKey($input, "phone")             // false
{
  hasEmail: containsKey($input, "email")
}
```
