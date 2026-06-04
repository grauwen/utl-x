---
title: find
description: "find — UTL-X Array function. Returns the FIRST element matching a predicate, or null if no match."
pageClass: stdlib-page
---

# find

<p class="stdlib-meta"><code>find(array, predicate) → element or null</code> · <a href="/reference/stdlib#array">Array</a></p>

Returns the FIRST element matching a predicate, or `null` if no match.

- `array` (required): the array to search

- `predicate` (required): lambda `(element) -> boolean`

``` bash
echo '{"users": [{"id": 1, "email": "alice@example.com"}, {"id": 2, "email": "bob@example.com"}]}' \
  | utlx -e 'find($input.users, (u) -> u.email == "bob@example.com")'
# {"id": 2, "email": "bob@example.com"}
```

``` utlx
{
  bob: find($input.users, (u) -> u.email == "bob@example.com"),
  unknown: find($input.users, (u) -> u.email == "unknown@example.com")
}
```

**Anti-pattern:** `filter($input.users, ...)[0]` — use `find()`. It's
cleaner and returns `null` instead of an index-out-of-bounds error on
empty results.
