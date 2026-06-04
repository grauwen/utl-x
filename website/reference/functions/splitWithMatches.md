---
title: splitWithMatches
description: "splitWithMatches — UTL-X String function. Split a string by a pattern, keeping the matched parts as separate"
pageClass: stdlib-page
---

# splitWithMatches

<p class="stdlib-meta"><code>splitWithMatches(string, pattern) → array</code> · <a href="/reference/stdlib#string">String</a></p>

Split a string by a pattern, keeping the matched parts as separate
elements in the result.

- `string` (required): the string to split

- `pattern` (required): regex pattern to split on

``` utlx
splitWithMatches("hello123world456", "\\d+")
// ["hello", "123", "world", "456"]
```
