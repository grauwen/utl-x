---
title: hmacBase64
description: "hmacBase64 — UTL-X Security function. Computes HMAC and returns the result as Base64-encoded string. See"
pageClass: stdlib-page
---

# hmacBase64

<p class="stdlib-meta"><code>hmacBase64(data, key, algorithm) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Computes HMAC and returns the result as Base64-encoded string. See
Chapter 38.

- `data` (required): the message to authenticate

- `key` (required): the secret key

- `algorithm` (required): hash algorithm (e.g., "SHA-256")

``` utlx
{
  signature: hmacBase64($input.payload, env("SECRET"), "SHA-256")
}
```
