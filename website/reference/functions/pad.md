---
title: pad
description: "pad — UTL-X String function. Pad a string on the left to the given length. Default pad character is"
pageClass: stdlib-page
---

# pad

<p class="stdlib-meta"><code>pad(string, length, char?) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Pad a string on the left to the given length. Default pad character is
space.

- `string` (required): the string to pad

- `length` (required): target length

- `char` (optional): pad character (default `" "`)

``` bash
echo '{"id": "42"}' | utlx -e 'pad($input.id, 5, "0")'
# "00042"
```

``` utlx
{
  invoiceNo: pad(toString($input.seq), 8, "0")
}
```
