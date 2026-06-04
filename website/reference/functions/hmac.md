---
title: hmac
description: "hmac — UTL-X Security function. Compute an HMAC (Hash-based Message Authentication Code) with an"
pageClass: stdlib-page
---

# hmac

<p class="stdlib-meta"><code>hmac(data, key, algorithm) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Compute an HMAC (Hash-based Message Authentication Code) with an
explicit algorithm. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message to authenticate

- `key` (required): the secret key

- `algorithm` (required): hash algorithm (e.g., `"SHA-256"`,
  `"SHA-512"`)

``` utlx
hmac("message", "key", "SHA-512")        // "..." (HMAC-SHA512 hex)
{
  signature: hmac($input.body, env("SECRET"), "SHA-256")
}
```

Also: `hmacSHA512(data, key)`, `hmacSHA1(data, key)`,
`hmacMD5(data, key)`, `hmacBase64(data, key, algorithm)` (returns Base64
instead of hex).
