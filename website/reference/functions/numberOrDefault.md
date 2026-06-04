---
title: numberOrDefault
description: "numberOrDefault — UTL-X Type function. Safely convert a value to a number, returning the default if conversion"
pageClass: stdlib-page
---

# numberOrDefault

<p class="stdlib-meta"><code>numberOrDefault(value, default) → number</code> · <a href="/reference/stdlib#type">Type</a></p>

Safely convert a value to a number, returning the default if conversion
fails or the value is null.

- `value` (required): value to convert

- `default` (required): fallback number if conversion fails

``` bash
echo '{"qty": "abc", "price": "19.99"}' | utlx -e '{qty: numberOrDefault($input.qty, 0), price: numberOrDefault($input.price, 0)}'
# {"qty": 0, "price": 19.99}
```

``` utlx
{
  valid: numberOrDefault("42", 0),           // 42
  missing: numberOrDefault(null, -1),        // -1
  invalid: numberOrDefault("not-a-number", 0) // 0
}
```

## O
