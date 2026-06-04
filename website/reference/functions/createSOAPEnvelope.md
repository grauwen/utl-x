---
title: createSOAPEnvelope
description: "createSOAPEnvelope — UTL-X XML function. Create a SOAP envelope with proper namespace prefixes."
pageClass: stdlib-page
---

# createSOAPEnvelope

<p class="stdlib-meta"><code>createSOAPEnvelope(body, header?) → xml</code> · <a href="/reference/stdlib#xml">XML</a></p>

Create a SOAP envelope with proper namespace prefixes.

- `body` (required): XML content for the SOAP Body

- `header` (optional): XML content for the SOAP Header

``` utlx
// See Chapter 22 for XML/SOAP processing.
{
  soapMessage: createSOAPEnvelope($input.requestBody, $input.authHeader)
}
```
