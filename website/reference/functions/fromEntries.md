---
title: fromEntries
description: "fromEntries — UTL-X Object function. Build an object from an array of [key, value] pairs. The inverse of"
pageClass: stdlib-page
---

# fromEntries

<p class="stdlib-meta"><code>fromEntries(pairs) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Build an object from an array of `[key, value]` pairs. The inverse of
`entries()`.

- `pairs` (required): array of `[key, value]` arrays

``` bash
echo '{"items": [{"id": "A", "name": "Widget"}, {"id": "B", "name": "Gadget"}]}' \
  | utlx -e 'fromEntries(map($input.items, (i) -> [i.id, i.name]))'
# {"A": "Widget", "B": "Gadget"}
```

``` utlx
{
  lookup: fromEntries(map($input.items, (i) -> [i.id, i.name]))
}
```
