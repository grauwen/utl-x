---
title: entries
description: "entries — UTL-X Object function. Decompose an object into an array of [key, value] pairs. Essential for"
pageClass: stdlib-page
---

# entries

<p class="stdlib-meta"><code>entries(object) → array</code> · <a href="/reference/stdlib#object">Object</a></p>

Decompose an object into an array of `[key, value]` pairs. Essential for
dynamic key processing.

- `object` (required): the object to decompose

``` bash
echo '{"servers": {"prod": {"host": "prod-db"}, "staging": {"host": "stg-db"}}}' \
  | utlx -e 'entries($input.servers)'
# [["prod", {"host": "prod-db"}], ["staging", {"host": "stg-db"}]]
```

``` utlx
{
  environments: entries($input.servers) |> map((entry) -> {
    environment: entry[0],
    host: entry[1].host
  })
}
```
