---
title: decryptAES
description: "decryptAES — UTL-X Security function. Decrypt data using AES-128-CBC."
pageClass: stdlib-page
---

# decryptAES

<p class="stdlib-meta"><code>decryptAES(data, key, iv) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Decrypt data using AES-128-CBC.

- `data` (required): Base64-encoded encrypted data

- `key` (required): 16-byte encryption key (Base64 or hex)

- `iv` (required): 16-byte initialization vector (Base64 or hex)

``` utlx
{
  plaintext: decryptAES($input.encrypted, $input.key, $input.iv)
}
```
