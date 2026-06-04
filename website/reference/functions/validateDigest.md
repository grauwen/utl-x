---
title: validateDigest
description: "validateDigest — UTL-X Security function. Validate that an XML digest matches the expected value. See Chapter 38."
pageClass: stdlib-page
---

# validateDigest

<p class="stdlib-meta"><code>validateDigest(xml, expectedDigest) → boolean</code> · <a href="/reference/stdlib#security">Security</a></p>

Validate that an XML digest matches the expected value. See Chapter 38.

- `xml` (required): XML string to validate

- `expectedDigest` (required): expected hash value

``` utlx
validateDigest($input.signedXml, $input.expectedHash)
// true if digest matches
```
