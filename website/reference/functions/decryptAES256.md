---
title: decryptAES256
description: "decryptAES256 — UTL-X Security function. Decrypt data using AES-256-CBC (requires 32-byte key)."
pageClass: stdlib-page
---

# decryptAES256

<p class="stdlib-meta"><code>decryptAES256(data, key, iv) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Decrypt data using AES-256-CBC (requires 32-byte key).

- `data` (required): Base64-encoded encrypted data

- `key` (required): 32-byte encryption key (Base64 or hex)

- `iv` (required): 16-byte initialization vector (Base64 or hex)

``` utlx
{
  plaintext: decryptAES256($input.ciphertext, $input.key256, $input.iv)
}
```
