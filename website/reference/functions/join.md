---
title: join
description: "join — UTL-X String function. Join array elements into a single string with a separator. This is the"
pageClass: stdlib-page
---

# join

<p class="stdlib-meta"><code>join(array, separator) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Join array elements into a single string with a separator. This is the
**string** join — for data restructuring (nesting children under
parents), see `nestBy()` in Chapter 20.

- `array` (required): array of values to join (non-strings are converted
  automatically)

- `separator` (required): string to insert between elements

``` bash
echo '{"tags": ["urgent", "billing", "review"]}' | utlx -e 'join($input.tags, ", ")'
# urgent, billing, review
```

``` utlx
{
  label: join($input.tags, " | "),
  csvLine: join([$input.name, toString($input.age), $input.city], ";"),
  path: join(["usr", "local", "bin"], "/")
}
```

**Anti-pattern:** `reduce(arr, "", (acc, x) -> concat(acc, x, ", "))` —
creates N intermediate strings. `join()` builds the result in one pass.
