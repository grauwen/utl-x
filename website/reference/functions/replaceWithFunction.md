---
title: replaceWithFunction
description: "replaceWithFunction — UTL-X String function. Replace all matches of a pattern using a function to compute the"
pageClass: stdlib-page
---

# replaceWithFunction

<p class="stdlib-meta"><code>replaceWithFunction(string, pattern, fn) → string</code> · <a href="/reference/stdlib#string">String</a></p>

Replace all matches of a pattern using a function to compute the
replacement.

- `string` (required): the source string

- `pattern` (required): regex pattern

- `fn` (required): lambda `(match) -> replacement`

``` utlx
replaceWithFunction("hello world", "[a-z]+", (m) -> upper(m))
// "HELLO WORLD"
```
