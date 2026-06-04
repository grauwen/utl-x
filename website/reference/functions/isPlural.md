---
title: isPlural
description: "isPlural — UTL-X String function. Checks if a word is in plural form."
pageClass: stdlib-page
---

# isPlural

<p class="stdlib-meta"><code>isPlural(word) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Checks if a word is in plural form.

- `word` (required): word to check

``` utlx
isPlural("cats")                         // true
isPlural("cat")                          // false
isPlural("children")                     // true
```
