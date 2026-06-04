---
title: hmacSHA384
description: "hmacSHA384 — UTL-X Security function. Computes HMAC-SHA384 hash. Returns hex-encoded string. See Chapter 38."
pageClass: stdlib-page
---

# hmacSHA384

<p class="stdlib-meta"><code>hmacSHA384(data, key) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Computes HMAC-SHA384 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message

- `key` (required): the secret key

``` utlx
{
  hash: hmacSHA384($input.message, $input.key)
}
```
