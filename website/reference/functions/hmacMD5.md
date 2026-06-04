---
title: hmacMD5
description: "hmacMD5 — UTL-X Security function. Computes HMAC-MD5 hash. Returns hex-encoded string. See Chapter 38."
pageClass: stdlib-page
---

# hmacMD5

<p class="stdlib-meta"><code>hmacMD5(data, key) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Computes HMAC-MD5 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message

- `key` (required): the secret key

``` utlx
{
  hash: hmacMD5($input.message, $input.key)
}
```
