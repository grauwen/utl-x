---
title: mask
description: "mask — UTL-X Security function. Mask a string, keeping only the first N characters visible. For PII"
pageClass: stdlib-page
---

# mask

<p class="stdlib-meta"><code>mask(string, visibleChars) → string</code> · <a href="/reference/stdlib#security">Security</a></p>

Mask a string, keeping only the first N characters visible. For PII
protection in logs and reports. See Chapter 38.

- `string` (required): the value to mask

- `visibleChars` (required): number of characters to leave visible

``` utlx
{
  name: mask("Alice Johnson", 3),            // "Ali***"
  card: mask("4111111111111111", 4),         // "4111************"
  email: mask("alice@example.com", 5),       // "alice***********"
  vatId: mask("NL123456789B01", 2)           // "NL************"
}
```

``` utlx
// Use case: audit-safe output
{
  customerName: mask($input.name, 3),
  email: sha256($input.email),           // irreversible hash
  orderId: $input.orderId                // non-sensitive, keep as-is
}
```
