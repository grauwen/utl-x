---
title: yamlEntries
description: "yamlEntries — UTL-X YAML function. Get entries (key-value pairs) from a YAML object as an array of"
pageClass: stdlib-page
---

# yamlEntries

<p class="stdlib-meta"><code>yamlEntries(object) → array</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Get entries (key-value pairs) from a YAML object as an array of
`[key, value]` pairs. See Chapter 26.

- `object` (required): YAML object

``` utlx
yamlEntries({host: "localhost", port: 5432})
// [["host", "localhost"], ["port", 5432]]
```
