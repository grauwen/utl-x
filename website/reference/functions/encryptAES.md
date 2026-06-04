---
title: encryptAES
description: "encryptAES — UTL-X Security function. Encrypts data using AES-128-CBC. Returns Base64-encoded ciphertext. See"
pageClass: stdlib-page
---

# encryptAES

<p class="stdlib-meta"><code>encryptAES(data, key, iv) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Encrypts data using AES-128-CBC. Returns Base64-encoded ciphertext. See
Chapter 38.

- `data` (required): plaintext string to encrypt

- `key` (required): 16-byte encryption key (hex or Base64)

- `iv` (required): initialization vector

``` utlx
let key = generateKey(128)
let iv = generateIV()
{
  encrypted: encryptAES($input.secret, key, iv),
  key: key,
  iv: iv
}
```
