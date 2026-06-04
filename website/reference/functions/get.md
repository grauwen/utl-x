---
title: get
description: "get — UTL-X Object function. Gets a value from an object or array by key or index."
pageClass: stdlib-page
---

# get

<p class="stdlib-meta"><code>get(object, path) → any</code> · <a href="/reference/stdlib#object">Object</a></p>

Gets a value from an object or array by key or index.

- `object` (required): object or array

- `path` (required): key name (string) or index (number)

``` bash
echo '{"items": ["a", "b", "c"]}' | utlx -e 'get($input.items, 1)'
# "b"
```

``` utlx
{
  second: get($input.items, 1),
  name: get($input, "name")
}
```
