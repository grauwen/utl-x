---
title: parseInt
description: "parseInt — UTL-X Math function. Parse a string into an integer, optionally with a radix (base)."
pageClass: stdlib-page
---

# parseInt

<p class="stdlib-meta"><code>parseInt(string, radix?) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Parse a string into an integer, optionally with a radix (base).

- `string` (required): numeric string to parse

- `radix` (optional): numeric base (default 10)

``` bash
echo '{"hex": "FF"}' | utlx -e 'parseInt($input.hex, 16)'
# 255
```

``` utlx
parseInt("42")                           // 42
parseInt("1010", 2)                      // 10
```
