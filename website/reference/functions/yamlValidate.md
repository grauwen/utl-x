---
title: yamlValidate
description: "yamlValidate — UTL-X YAML function. Validate YAML against a set of rules. Returns validation result with"
pageClass: stdlib-page
---

# yamlValidate

<p class="stdlib-meta"><code>yamlValidate(yaml, rules) → object</code> · <a href="/reference/stdlib#yaml">YAML</a></p>

Validate YAML against a set of rules. Returns validation result with
errors. See Chapter 26.

- `yaml` (required): YAML value to validate

- `rules` (required): validation rules object

``` utlx
yamlValidate($input, {required: ["apiVersion", "kind"]})
// {valid: true, errors: []}
```
