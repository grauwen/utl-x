---
title: avgBy
description: "avgBy — UTL-X Math function. Average of values extracted from an array of objects using a key"
pageClass: stdlib-page
---

# avgBy

<p class="stdlib-meta"><code>avgBy(array, keyFn) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Average of values extracted from an array of objects using a key
function.

- `array` (required): array of objects

- `keyFn` (required): lambda `(element) -> number`

``` bash
echo '{"products": [{"name": "A", "price": 10}, {"name": "B", "price": 30}, {"name": "C", "price": 20}]}' \
  | utlx -e 'avgBy($input.products, (p) -> p.price)'
# 20
```

``` utlx
{
  avgPrice: avgBy($input.products, (p) -> p.price),
  avgWeight: avgBy($input.products, (p) -> p.weight)
}
```

## B
