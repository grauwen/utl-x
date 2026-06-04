---
title: age
description: "age — UTL-X Date & Time function. Calculate age in whole years from a birthdate. Uses today if no"
pageClass: stdlib-page
---

# age

<p class="stdlib-meta"><code>age(birthdate, referenceDate?) → number</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Calculate age in whole years from a birthdate. Uses today if no
reference date provided.

- `birthdate` (required): date of birth

- `referenceDate` (optional): date to calculate age at (defaults to
  today)

``` bash
echo '{"dob": "1990-03-15"}' | utlx -e 'age(parseDate($input.dob, "yyyy-MM-dd"))'
# 36
```

``` utlx
{
  age: age(parseDate($input.dateOfBirth, "yyyy-MM-dd")),
  isMinor: age(parseDate($input.dateOfBirth, "yyyy-MM-dd")) < 18
}
```
