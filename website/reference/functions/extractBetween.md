---
title: extractBetween
description: "extractBetween — UTL-X String function. Extract the substring between two delimiter strings."
pageClass: stdlib-page
---

# extractBetween

<p class="stdlib-meta"><code>extractBetween(string, start, end) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Extract the substring between two delimiter strings.

- `string` (required): the source string

- `start` (required): the start delimiter

- `end` (required): the end delimiter

``` bash
echo '{"msg": "Hello [world] today"}' | utlx -e 'extractBetween($input.msg, "[", "]")'
# "world"
```

``` utlx
{
  tag: extractBetween($input.xml, "<name>", "</name>"),
  token: extractBetween($input.header, "Bearer ", " ")
}
```
