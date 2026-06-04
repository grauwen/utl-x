---
title: deepMerge
description: "deepMerge — UTL-X Object function. Recursively merge two objects. At each level, properties from obj2"
pageClass: stdlib-page
---

# deepMerge

<p class="stdlib-meta"><code>deepMerge(obj1, obj2) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Recursively merge two objects. At each level, properties from `obj2`
override `obj1`. Nested objects are merged recursively (not replaced).

- `obj1` (required): base object

- `obj2` (required): override object

``` utlx
let base = {server: {host: "localhost", port: 5432, ssl: false}}
let prod = {server: {host: "prod-db.example.com", ssl: true}}
{
  config: deepMerge(base, prod)
}
// Output: {config: {server: {host: "prod-db.example.com", port: 5432, ssl: true}}}
// port survived from base — deep merge, not replace
```

**Contrast with spread:** `{...base, ...prod}` would REPLACE the entire
`server` object, losing `port`. `deepMerge` preserves nested properties.
