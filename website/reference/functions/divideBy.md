---
title: divideBy
description: "divideBy — UTL-X Object function. Divide an object into sub-objects each containing at most N key-value"
pageClass: stdlib-page
---

# divideBy

<p class="stdlib-meta"><code>divideBy(object, n) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Divide an object into sub-objects each containing at most N key-value
pairs.

- `object` (required): object to split

- `n` (required): max entries per sub-object

``` bash
echo '{"a": 1, "b": 2, "c": 3, "d": 4, "e": 5}' | utlx -e 'divideBy($input, 2)'
# [{"a": 1, "b": 2}, {"c": 3, "d": 4}, {"e": 5}]
```

``` utlx
{
  batches: divideBy($input.config, 3)
}
```
