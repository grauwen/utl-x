---
title: findAllMatches
description: "findAllMatches — UTL-X String function. Finds all matches of a regex pattern and returns them with positions."
pageClass: stdlib-page
---

# findAllMatches

<p class="stdlib-meta"><code>findAllMatches(string, pattern) → array</code> · <a href="/reference/stdlib#string">String</a></p>

Finds all matches of a regex pattern and returns them with positions.

- `string` (required): the string to search

- `pattern` (required): regex pattern

``` bash
echo '{"text": "Order ORD-001 and ORD-002 shipped"}' | utlx -e 'findAllMatches($input.text, "ORD-\\d+")'
# [{"match": "ORD-001", "start": 6}, {"match": "ORD-002", "start": 18}]
```

``` utlx
{
  orderIds: findAllMatches($input.text, "ORD-\\d+") |> map((m) -> m.match)
}
```
