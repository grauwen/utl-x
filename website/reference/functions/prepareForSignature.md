---
title: prepareForSignature
description: "prepareForSignature — UTL-X XML function. Prepare XML for digital signature (XMLDSig) by canonicalizing it. See"
pageClass: stdlib-page
---

# prepareForSignature

<p class="stdlib-meta"><code>prepareForSignature(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Prepare XML for digital signature (XMLDSig) by canonicalizing it. See
Chapter 22.

- `xml` (required): XML string to prepare

``` utlx
let canonical = prepareForSignature($input.xmlPayload)
{
  signatureInput: canonical
}
```
