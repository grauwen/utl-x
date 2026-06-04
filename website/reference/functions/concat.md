---
title: concat
description: "concat — UTL-X String function. Concatenate any number of strings."
pageClass: stdlib-page
---

# concat

<p class="stdlib-meta"><code>concat(string1, string2, ...) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Concatenate any number of strings.

- `string1, string2, ...` (variadic): strings to concatenate

``` bash
echo '{"title": "Dr.", "firstName": "Alice", "lastName": "Johnson"}' \
  | utlx -e 'concat($input.title, " ", $input.firstName, " ", $input.lastName)'
# "Dr. Alice Johnson"
```

``` utlx
{
  fullName: concat($input.title, " ", $input.firstName, " ", $input.lastName),
  reference: concat("Order-", toString($input.orderId))
}
```

**Anti-pattern:** building long strings with `reduce` + `concat`. Use
`join(array, separator)` instead.
