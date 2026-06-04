---
title: log
description: "log — UTL-X Math function. Logarithm with optional base. Without base, defaults to natural"
pageClass: stdlib-page
---

# log

<p class="stdlib-meta"><code>log(number, base?) → number</code> · <a href="/reference/stdlib#math">Math</a></p>

Logarithm with optional base. Without base, defaults to natural
logarithm (base *e*).

- `number` (required): positive number

- `base` (optional): logarithm base (default: *e*)

``` utlx
log(100, 10)                             // 2
log(8, 2)                                // 3
log(e())                                 // 1 (natural log)
```
