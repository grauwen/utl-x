---
title: deepMergeAll
description: "deepMergeAll — UTL-X Object function. Deep merge multiple objects in order (later objects override earlier"
pageClass: stdlib-page
---

# deepMergeAll

<p class="stdlib-meta"><code>deepMergeAll(objects) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Deep merge multiple objects in order (later objects override earlier
ones at each nesting level).

- `objects` (required): array of objects to merge

``` utlx
let configs = [
  {server: {host: "localhost", port: 5432}},
  {server: {host: "staging-db.internal"}},
  {server: {ssl: true}}
]
{
  merged: deepMergeAll(configs)
}
// Output: {merged: {server: {host: "staging-db.internal", port: 5432, ssl: true}}}
```
