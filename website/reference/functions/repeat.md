---
title: repeat
description: "repeat — UTL-X String function. Repeat a string n times."
pageClass: stdlib-page
---

# repeat

<p class="stdlib-meta"><code>repeat(string, n) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Repeat a string n times.

- `string` (required): the string to repeat

- `n` (required): number of repetitions

``` utlx
repeat("ab", 3)                          // "ababab"
repeat("-", 40)                          // "----------------------------------------"
```
