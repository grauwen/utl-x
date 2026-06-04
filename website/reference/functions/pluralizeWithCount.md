---
title: pluralizeWithCount
description: "pluralizeWithCount — UTL-X String function. Return a formatted string with count and correctly pluralized word."
pageClass: stdlib-page
---

# pluralizeWithCount

<p class="stdlib-meta"><code>pluralizeWithCount(word, count) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Return a formatted string with count and correctly pluralized word.

- `word` (required): singular noun

- `count` (required): the number

``` bash
echo '{"n": 5}' | utlx -e 'pluralizeWithCount("item", $input.n)'
# "5 items"
```

``` utlx
{
  summary: pluralizeWithCount("error", count($input.errors))
}
```
