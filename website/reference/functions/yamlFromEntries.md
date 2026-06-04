---
title: yamlFromEntries
description: "yamlFromEntries — UTL-X YAML function. Create a YAML object from an array of [key, value] pairs. See Chapter"
pageClass: stdlib-page
---

# yamlFromEntries

<p class="stdlib-meta"><code>yamlFromEntries(entries) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Create a YAML object from an array of `[key, value]` pairs. See Chapter
26.

- `entries` (required): array of `[key, value]` pairs

``` utlx
yamlFromEntries([["host", "localhost"], ["port", 5432]])
// {host: "localhost", port: 5432}
```
