---
title: length
description: "length — UTL-X String function. Length of a string (character count) or array (element count). Alias for"
pageClass: stdlib-page
---

# length

<p class="stdlib-meta"><code>length(value) → number</code> · <a href="/reference/stdlib#string">String</a></p>

Length of a string (character count) or array (element count). Alias for
`count()` on arrays.

- `value` (required): string or array

``` utlx
{
  str: length("hello"),                      // 5
  unicode: length("日本語"),                  // 3 (Unicode characters, not bytes)
  arr: length([1, 2, 3]),                    // 3
  emptyStr: length(""),                      // 0
  emptyArr: length([])                       // 0
}
```
