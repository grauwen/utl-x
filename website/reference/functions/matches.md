---
title: matches
description: "matches — UTL-X String function. Test if a string matches a regular expression (full match)."
pageClass: stdlib-page
---

# matches

<p class="stdlib-meta"><code>matches(string, regex) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Test if a string matches a regular expression (full match).

- `string` (required): the string to test

- `regex` (required): regular expression pattern

``` utlx
{
  order: matches("ORD-001", "^ORD-[0-9]+$"),         // true
  wrong: matches("INV-001", "^ORD-[0-9]+$"),         // false
  email: matches("alice@example.com",
    "^[^@]+@[^@]+\\.[^@]+$")                         // true
}
```

``` utlx
// Use case: validate field formats
if (!matches($input.vatId, "^[A-Z]{2}[0-9]{9}B[0-9]{2}$"))
  error("Invalid Dutch VAT ID format")
```

Also: `matchesQname(element, pattern)` for XML QName matching (Chapter
22).
