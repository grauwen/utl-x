---
title: addDays
description: "addDays — UTL-X Date & Time function. Add (or subtract) a number of days to a date."
pageClass: stdlib-page
---

# addDays

<p class="stdlib-meta"><code>addDays(date, count) → date</code> · <a href="/reference/stdlib#date-time">Date & Time</a></p>

Add (or subtract) a number of days to a date.

- `date` (required): the starting date or datetime

- `count` (required): number of days to add. Negative to subtract.

``` bash
echo '{"orderDate": "2026-05-01", "deliveryDays": 14}' \
  | utlx -e 'formatDate(addDays(parseDate(.orderDate, "yyyy-MM-dd"), .deliveryDays), "yyyy-MM-dd")'
# "2026-05-15"
```

``` utlx
%utlx 1.0
input json
output json
---
let order = parseDate($input.orderDate, "yyyy-MM-dd")
{
  orderDate: $input.orderDate,
  deliveryDate: formatDate(addDays(order, $input.deliveryDays), "yyyy-MM-dd"),
  paymentDue: formatDate(addDays(order, 30), "yyyy-MM-dd")
}
```

``` utlx
addDays(parseDate("2026-05-01", "yyyy-MM-dd"), 14)    // 2026-05-15
addDays(parseDate("2026-05-01", "yyyy-MM-dd"), -7)    // 2026-04-24 (subtract)
addDays(parseDate("2026-02-27", "yyyy-MM-dd"), 2)     // 2026-03-01 (crosses month boundary)
```
