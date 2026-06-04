---
title: coalesce
description: "coalesce — UTL-X Type function. Returns the first non-null argument. Accepts any number of arguments."
pageClass: stdlib-page
---

# coalesce

<p class="stdlib-meta"><code>coalesce(value1, value2, ...) → value</code> · <a href="/reference/stdlib#type">Type</a></p>

Returns the first non-null argument. Accepts any number of arguments.

- `value1, value2, ...` (variadic): values to check in order

``` bash
echo '{"nickname": null, "displayName": null, "fullName": "Alice Johnson"}' \
  | utlx -e 'coalesce($input.nickname, $input.displayName, $input.fullName, "Anonymous")'
# "Alice Johnson"
```

``` utlx
{
  name: coalesce($input.nickname, $input.displayName, $input.fullName, "Anonymous")
}
```

**Note:** for two values, the `??` operator is cleaner:
`$input.name ?? "Unknown"` (see Chapter 9).
