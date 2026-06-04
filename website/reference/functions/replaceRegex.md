---
title: replaceRegex
description: "replaceRegex — UTL-X String function. Replace all occurrences matching a regular expression."
pageClass: stdlib-page
---

# replaceRegex

<p class="stdlib-meta"><code>replaceRegex(string, regex, replacement) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Replace all occurrences matching a regular expression.

- `string` (required): the string to modify

- `regex` (required): regular expression pattern

- `replacement` (required): what to replace with

``` utlx
replaceRegex("Order #123 on 2026-05-01", "[0-9]+", "X")  // "Order #X on X-X-X"
replaceRegex("  extra   spaces  ", "\\s+", " ")           // " extra spaces "
```
