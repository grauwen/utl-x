---
title: base64Decode
description: "base64Decode — UTL-X Security function. Decode a Base64-encoded string back to its original value."
pageClass: stdlib-page
---

# base64Decode

<p class="stdlib-meta"><code>base64Decode(string) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Decode a Base64-encoded string back to its original value.

- `string` (required): Base64-encoded string to decode

``` utlx
{
  decoded: base64Decode("SGVsbG8sIFdvcmxkIQ==")
}
// Output: {"decoded": "Hello, World!"}

// Real-world: decode a Base64-encoded JWT payload
let payload = split($input.token, ".")[1]
let decoded = parseJson(base64Decode(payload))
{
  subject: decoded.sub
}
// Output: {"subject": "user@example.com"}
```
