---
title: formatPlural
description: "formatPlural — UTL-X String function. Creates a formatted string with count and correctly pluralized word."
pageClass: stdlib-page
---

# formatPlural

<p class="stdlib-meta"><code>formatPlural(count, word) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Creates a formatted string with count and correctly pluralized word.

- `count` (required): numeric count

- `word` (required): singular form of the word

``` bash
echo '{"n": 3}' | utlx -e 'formatPlural($input.n, "item")'
# "3 items"
```

``` utlx
{
  summary: formatPlural(count($input.errors), "error"),
  files: formatPlural($input.fileCount, "file")
}
```
