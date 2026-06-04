---
title: sumBy
description: "sumBy — UTL-X Math function. Sum values extracted from an array of objects using a key function."
pageClass: stdlib-page
---

# sumBy

<p class="stdlib-meta"><code>sumBy(array, fn) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Sum values extracted from an array of objects using a key function.

- `array` (required): array of objects

- `fn` (required): lambda `(element) -> number`

``` bash
echo '{"items": [{"qty": 2, "price": 25}, {"qty": 5, "price": 10}, {"qty": 1, "price": 100}]}' | utlx -e 'sumBy($input.items, (i) -> i.qty * i.price)'
# 200
```

``` utlx
{
  orderTotal: sumBy($input.items, (i) -> i.qty * i.price)
}
```
