---
title: normalizeSpace
description: "normalizeSpace — UTL-X String function. Collapse all whitespace sequences (spaces, tabs, newlines) to single"
pageClass: stdlib-page
---

# normalizeSpace

<p class="stdlib-meta"><code>normalizeSpace(string) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Collapse all whitespace sequences (spaces, tabs, newlines) to single
spaces. Trim leading/trailing.

- `string` (required): the string to normalize

``` utlx
{
  spaces: normalizeSpace("  hello   world  "),       // "hello world"
  mixed: normalizeSpace("line1\n  line2\t\tline3"),  // "line1 line2 line3"
  empty: normalizeSpace("")                          // ""
}
```
