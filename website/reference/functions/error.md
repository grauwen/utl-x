---
title: error
description: "error — UTL-X System function. Throw a runtime error with a message. Stops the transformation."
pageClass: stdlib-page
---

# error

<p class="stdlib-meta"><code>error(message) → never</code> · <a href="/reference/stdlib#system">System</a></p>

Throw a runtime error with a message. Stops the transformation.

- `message` (required): error description string

``` utlx
// Validate input before processing:
if ($input.total < 0) error("Total cannot be negative")
if ($input.currency == null) error("Currency is required")

// Use in a validation pipeline:
let amount = toNumber($input.amount)
if (amount > 1000000) error(concat("Amount exceeds limit: ", toString(amount)))

// Combine with try/catch in the caller:
try {
  if ($input.type == "UNKNOWN") error("Unknown order type")
  // ... process order
} catch {
  {error: true, message: "Processing failed"}
}
```
