---
title: minBy
description: "minBy — UTL-X Math function. Find the element with the minimum value of a key extractor. Returns the"
pageClass: stdlib-page
---

# minBy

<p class="stdlib-meta"><code>minBy(array, fn) → element</code> · <a href="/reference/stdlib#math">Math</a></p>

Find the element with the minimum value of a key extractor. Returns the
entire element, not just the value.

- `array` (required): array to search

- `fn` (required): lambda `(element) -> comparable`

``` bash
echo '{"products": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}, {"name": "Gizmo", "price": 10}]}' | utlx -e 'minBy($input.products, (p) -> p.price)'
# {"name": "Gizmo", "price": 10}
```
