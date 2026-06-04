---
title: attribute
description: "attribute — UTL-X XML function. Get a specific attribute value from an XML element by name. Useful when"
pageClass: stdlib-page
---

# attribute

<p class="stdlib-meta"><code>attribute(element, name) → string</code> · <a href="/reference/stdlib#xml">XML</a></p>

Get a specific attribute value from an XML element by name. Useful when
the attribute name is dynamic (stored in a variable). For static
attribute access, prefer the `@` operator: `$input.@id`.

- `element` (required): XML UDM element

- `name` (required): attribute name (string)

``` utlx
// Input: <Order id="ORD-123" status="active">...</Order>
// See Chapter 22 for XML attribute access (@ operator).

// Preferred: use @ for known attribute names
{
  orderId: $input.@id,                  // "ORD-123"
  status: $input.@status                // "active"
}

// Use attribute() when the name is dynamic (e.g. from input data)
{
  value: attribute($input, $input.lookupField)   // attribute name from data
}
```
