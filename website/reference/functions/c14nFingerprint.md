---
title: c14nFingerprint
description: "c14nFingerprint — UTL-X XML function. Create a short hash fingerprint of the canonical form of XML. Useful for"
pageClass: stdlib-page
---

# c14nFingerprint

<p class="stdlib-meta"><code>c14nFingerprint(xml) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Create a short hash fingerprint of the canonical form of XML. Useful for
deduplication and logging.

- `xml` (required): XML UDM value

``` utlx
{
  fingerprint: c14nFingerprint($input),
  isDuplicate: c14nFingerprint($input) == $input.lastSeen
}
```
