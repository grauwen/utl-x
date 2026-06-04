---
title: hasKey
description: "hasKey — UTL-X Object function. Check if an object has a property with the given key name."
pageClass: stdlib-page
---

# hasKey

<p class="stdlib-meta"><code>hasKey(object, key) → boolean</code> · <a href="/reference/stdlib#object">Object</a></p>

Check if an object has a property with the given key name.

- `object` (required): the object to check

- `key` (required): property name as string

``` bash
echo '{"name": "Alice", "email": "alice@example.com"}' | utlx -e 'hasKey($input, "email")'
# true
```

``` utlx
if (hasKey($input, "shippingAddress")) {
  address: $input.shippingAddress
} else {
  address: $input.billingAddress
}
```

Also: `containsValue(object, value)` — check if any property has a
specific value.
