---
title: hmacSHA512
description: "hmacSHA512 — UTL-X Security function. Computes HMAC-SHA512 hash. Returns hex-encoded string. See Chapter 38."
pageClass: stdlib-page
---

# hmacSHA512

<p class="stdlib-meta"><code>hmacSHA512(data, key) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Computes HMAC-SHA512 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message

- `key` (required): the secret key

``` utlx
{
  hash: hmacSHA512($input.message, $input.key)
}
```
