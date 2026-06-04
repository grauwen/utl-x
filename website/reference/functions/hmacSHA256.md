---
title: hmacSHA256
description: "hmacSHA256 — UTL-X Security function. Compute an HMAC-SHA256 for verifying message integrity and authenticity."
pageClass: stdlib-page
---

# hmacSHA256

<p class="stdlib-meta"><code>hmacSHA256(data, key) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Compute an HMAC-SHA256 for verifying message integrity and authenticity.
Returns hex-encoded string. See Chapter 38.

- `data` (required): the message to authenticate

- `key` (required): the secret key

``` utlx
hmacSHA256("message-to-verify", "my-secret-key")  // "4a8f3d..."

// Use case: verify webhook signature
let expectedSig = hmacSHA256($input.body, env("WEBHOOK_SECRET"))
if (expectedSig != $input.headers.signature) error("Invalid signature")
{ verified: true, payload: $input.body }
```
