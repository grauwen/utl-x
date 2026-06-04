---
title: hash
description: "hash — UTL-X Security function. Compute a cryptographic hash with an explicit algorithm. Returns"
pageClass: stdlib-page
---

# hash

<p class="stdlib-meta"><code>hash(data, algorithm?) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Compute a cryptographic hash with an explicit algorithm. Returns
hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash

- `algorithm` (optional, default `"SHA-256"`): algorithm name (`"MD5"`,
  `"SHA-1"`, `"SHA-256"`, `"SHA-384"`, `"SHA-512"`, `"SHA3-256"`,
  `"SHA3-512"`)

``` utlx
hash("hello", "SHA3-256")               // "3338be694f50c5f338..."
hash("hello", "SHA-256")                // "2cf24dba5fb0a30e..."
{
  digest: hash($input.payload, "SHA-256")
}
```

Also: `sha1(data)`, `sha224(data)`, `sha384(data)`, `sha3_256(data)`,
`sha3_512(data)`.
