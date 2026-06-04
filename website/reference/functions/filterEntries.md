---
title: filterEntries
description: "filterEntries — UTL-X Object function. Filter object properties by key and/or value. Returns a new object with"
pageClass: stdlib-page
---

# filterEntries

<p class="stdlib-meta"><code>filterEntries(object, predicate) → object</code> · <a href="/reference/stdlib#object">Object</a></p>

Filter object properties by key and/or value. Returns a new object with
only matching entries.

- `object` (required): the object to filter

- `predicate` (required): lambda `(key, value) -> boolean`

``` bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "temp": null}' \
  | utlx -e 'filterEntries(., (key, value) -> value != null)'
# {"name": "Alice", "email": "alice@example.com", "password": "secret"}
```

``` utlx
{
  nonNull: filterEntries($input, (key, value) -> value != null),
  safe: filterEntries($input, (key, value) -> key != "password" && key != "temp")
}
```

Also: `someEntry(obj, pred)` → true if any entry matches,
`everyEntry(obj, pred)` → true if all match, `countEntries(obj, pred)` →
count of matching entries.
