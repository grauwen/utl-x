---
title: generateKey
description: "generateKey — UTL-X Security function. Generates a random encryption key of the specified bit length. See"
pageClass: stdlib-page
---

# generateKey

<p class="stdlib-meta"><code>generateKey(bits?) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Generates a random encryption key of the specified bit length. See
Chapter 38.

- `bits` (optional): key length in bits (128 or 256, default 128)

``` utlx
let key128 = generateKey(128)
let key256 = generateKey(256)
{
  aes128key: key128,
  aes256key: key256
}
```
