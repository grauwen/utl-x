---
title: parseEUNumber
description: "parseEUNumber — UTL-X Math function. Parse a European-formatted number string (dot=thousands, comma=decimal)"
pageClass: stdlib-page
---

# parseEUNumber

<p class="stdlib-meta"><code>parseEUNumber(string) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Parse a European-formatted number string (dot=thousands, comma=decimal)
to a standard number. See Chapter 25.

- `string` (required): the formatted number string

``` utlx
parseEUNumber("1.234,56")               // 1234.56 (European: dot=thousands, comma=decimal)

// Use case: CSV from European source
map($input, (row) -> {
  product: row.Product,
  price: parseEUNumber(row.Price),       // "29,99" → 29.99
  weight: parseEUNumber(row.Weight)      // "1.500,00" → 1500.0
})
```

Also: `renderEUNumber(number)` for the reverse direction,
`parseFrenchNumber(string)`, `parseSwissNumber(string)`.
