---
title: analyzeString
description: "analyzeString — UTL-X String function. Analyze a string against a regex pattern, returning match status and"
pageClass: stdlib-page
---

# analyzeString

<p class="stdlib-meta"><code>analyzeString(string, pattern) → object</code> · <a href="/reference/stdlib#string">String</a></p>

Analyze a string against a regex pattern, returning match status and
captured groups.

- `string` (required): the string to analyze

- `pattern` (required): regex pattern with capture groups

Returns:
`{match: boolean, groups: [string...], start: number, end: number}`

``` bash
echo '{"email": "user@example.com"}' \
  | utlx -e 'analyzeString($input.email, "(.+)@(.+)\\.(.+)")'
# {"match": true, "groups": ["user", "example", "com"], "start": 0, "end": 16}
```

``` utlx
// Input: {"date": "2026-05-01"}
let result = analyzeString($input.date, "^(\\d{4})-(\\d{2})-(\\d{2})$")
// result = {match: true, groups: ["2026", "05", "01"], start: 0, end: 10}
{
  valid: result.match,              // true
  year: if (result.match) result.groups[0] else null,   // "2026"
  month: if (result.match) result.groups[1] else null   // "05"
}
// Output: {"valid": true, "year": "2026", "month": "05"}
```
