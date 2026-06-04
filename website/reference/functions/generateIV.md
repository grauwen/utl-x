---
title: generateIV
description: "generateIV — UTL-X Security function. Generates a random initialization vector (IV) for use with AES"
pageClass: stdlib-page
---

# generateIV

<p class="stdlib-meta"><code>generateIV() → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Generates a random initialization vector (IV) for use with AES
encryption. See Chapter 38.

``` utlx
let iv = generateIV()
{
  iv: iv,
  encrypted: encryptAES($input.data, $input.key, iv)
}
```
