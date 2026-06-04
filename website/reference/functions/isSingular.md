---
title: isSingular
description: "isSingular — UTL-X String function. Checks if a word is in singular form."
pageClass: stdlib-page
---

# isSingular

<p class="stdlib-meta"><code>isSingular(word) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Checks if a word is in singular form.

- `word` (required): word to check

``` utlx
isSingular("cat")                        // true
isSingular("cats")                       // false
isSingular("child")                      // true
```
