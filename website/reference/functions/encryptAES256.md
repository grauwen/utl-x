---
title: encryptAES256
description: "encryptAES256 — UTL-X Security function. Encrypts data using AES-256-CBC. Requires a 32-byte key. See Chapter 38."
pageClass: stdlib-page
---

# encryptAES256

<p class="stdlib-meta"><code>encryptAES256(data, key, iv) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Encrypts data using AES-256-CBC. Requires a 32-byte key. See Chapter 38.

- `data` (required): plaintext string to encrypt

- `key` (required): 32-byte encryption key

- `iv` (required): initialization vector

``` utlx
let key = generateKey(256)
let iv = generateIV()
{
  encrypted: encryptAES256($input.payload, key, iv),
  key: key,
  iv: iv
}
```
