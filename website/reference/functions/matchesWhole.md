---
title: matchesWhole
description: "matchesWhole — UTL-X String function. Test if a string matches a pattern completely (entire string must match,"
pageClass: stdlib-page
---

# matchesWhole

<p class="stdlib-meta"><code>matchesWhole(string, pattern) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Test if a string matches a pattern completely (entire string must match,
not just a substring). Equivalent to anchoring with `^...$`.

- `string` (required): the string to test

- `pattern` (required): regular expression pattern

``` utlx
{
  full: matchesWhole("abc123", "[a-z]+[0-9]+"),      // true (whole string matches)
  partial: matchesWhole("abc123xyz", "[a-z]+[0-9]+") // false (trailing "xyz" prevents full match)
}
```
