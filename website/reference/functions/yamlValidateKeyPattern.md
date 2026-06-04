---
title: yamlValidateKeyPattern
description: "yamlValidateKeyPattern — UTL-X YAML function. Validate that all keys in a YAML object match a given pattern. See"
pageClass: stdlib-page
---

# yamlValidateKeyPattern

<p class="stdlib-meta"><code>yamlValidateKeyPattern(object, pattern) → boolean</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Validate that all keys in a YAML object match a given pattern. See
Chapter 26.

- `object` (required): YAML object to validate

- `pattern` (required): regex pattern that keys must match

``` utlx
yamlValidateKeyPattern($input, "^[a-z][a-zA-Z0-9]*$")
// true if all keys are camelCase
```
