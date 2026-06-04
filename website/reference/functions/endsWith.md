---
title: endsWith
description: "endsWith — UTL-X String function. Check if a string ends with a given substring."
pageClass: stdlib-page
---

# endsWith

<p class="stdlib-meta"><code>endsWith(string, suffix) → boolean</code> · <a href="/reference/stdlib#string">String</a></p>

Check if a string ends with a given substring.

- `string` (required): the string to test

- `suffix` (required): the substring to check for

``` bash
echo '{"filename": "invoice-2026.xml"}' | utlx -e 'endsWith($input.filename, ".xml")'
# true
```

``` utlx
{
  isXml: endsWith($input.filename, ".xml"),
  isJson: endsWith($input.filename, ".json"),
  utlxFiles: filter($input.files, (f) -> endsWith(f.name, ".utlx"))
}
```
