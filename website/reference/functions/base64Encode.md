---
title: base64Encode
description: "base64Encode — UTL-X Security function. Encode data to a Base64 string for safe transport (e.g., in URLs or"
pageClass: stdlib-page
---

# base64Encode

<p class="stdlib-meta"><code>base64Encode(data) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Encode data to a Base64 string for safe transport (e.g., in URLs or
headers).

- `data` (required): string to encode

``` utlx
{
  encoded: base64Encode("Hello, World!")
}
// Output: {"encoded": "SGVsbG8sIFdvcmxkIQ=="}
```
