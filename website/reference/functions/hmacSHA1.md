---
title: hmacSHA1
description: "hmacSHA1 — UTL-X Security function. Computes HMAC-SHA1 hash. Returns hex-encoded string. See Chapter 38."
pageClass: stdlib-page
---

# hmacSHA1

<p class="stdlib-meta"><code>hmacSHA1(data, key) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Computes HMAC-SHA1 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message

- `key` (required): the secret key

``` utlx
{
  hash: hmacSHA1($input.message, $input.key)
}
```
