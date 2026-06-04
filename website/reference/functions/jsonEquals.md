---
title: jsonEquals
description: "jsonEquals — UTL-X JSON function. Compare two JSON values semantically, ignoring key order and whitespace."
pageClass: stdlib-page
---

# jsonEquals

<p class="stdlib-meta"><code>jsonEquals(json1, json2) → boolean</code> · <a href="/reference/stdlib#json">JSON</a></p>

Compare two JSON values semantically, ignoring key order and whitespace.
See Chapter 24.

- `json1` (required): first value to compare

- `json2` (required): second value to compare

``` utlx
{
  same: jsonEquals({b: 2, a: 1}, {a: 1, b: 2}),  // true (same content, different key order)
  diff: jsonEquals({a: 1}, {a: 2})                // false
}
```

``` utlx
// Use case: detect changes between two API responses. See Chapter 24.
if (!jsonEquals($input.previous, $input.current)) {
  changed: true,
  hash: canonicalJSONHash($input.current)
}
```
