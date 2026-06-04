---
title: yamlHasRequiredFields
description: "yamlHasRequiredFields — UTL-X YAML function. Check if a YAML object has all the required fields. See Chapter 26."
pageClass: stdlib-page
---

# yamlHasRequiredFields

<p class="stdlib-meta"><code>yamlHasRequiredFields(object, fields) → boolean</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Check if a YAML object has all the required fields. See Chapter 26.

- `object` (required): YAML object to check

- `fields` (required): array of required field names

``` utlx
yamlHasRequiredFields($input, ["apiVersion", "kind", "metadata"])
// true if all three fields exist
```
