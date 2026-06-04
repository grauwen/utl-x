---
title: fromBase64
description: "fromBase64 — UTL-X Security function. Create binary data from a Base64-encoded string. See Chapter 38."
pageClass: stdlib-page
---

# fromBase64

<p class="stdlib-meta"><code>fromBase64(encoded) → binary</code> · <a href="/reference/stdlib#security">Security</a></p>

Create binary data from a Base64-encoded string. See Chapter 38.

- `encoded` (required): Base64-encoded string

``` bash
echo '{"data": "SGVsbG8gV29ybGQ="}' | utlx -e 'toString(fromBase64($input.data))'
# "Hello World"
```

``` utlx
{
  decoded: toString(fromBase64($input.encodedPayload))
}
```
