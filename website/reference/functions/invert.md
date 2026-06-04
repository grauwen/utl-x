---
title: invert
description: "invert — UTL-X Object function. Invert an object by swapping keys and values. Values become keys and"
pageClass: stdlib-page
---

# invert

<p class="stdlib-meta"><code>invert(object) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Invert an object by swapping keys and values. Values become keys and
keys become values.

- `object` (required): object to invert

``` bash
echo '{"US": "United States", "GB": "United Kingdom"}' | utlx -e 'invert($input)'
# {"United States": "US", "United Kingdom": "GB"}
```

``` utlx
{
  countryByName: invert($input.codeToName)
}
```
