---
title: fullOuterJoin
description: "fullOuterJoin — UTL-X Array function. Full outer join – returns all items from both arrays, with null for"
pageClass: stdlib-page
---

# fullOuterJoin

<p class="stdlib-meta"><code>fullOuterJoin(left, right, leftKey, rightKey) → array</code> · <a href="/reference/stdlib#array">Array</a></p>

Full outer join – returns all items from both arrays, with `null` for
non-matching sides.

- `left` (required): left array

- `right` (required): right array

- `leftKey` (required): lambda to extract join key from left elements

- `rightKey` (required): lambda to extract join key from right elements

``` utlx
let employees = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let salaries = [{empId: 1, amount: 5000}, {empId: 3, amount: 6000}]
{
  joined: fullOuterJoin(employees, salaries, (e) -> e.id, (s) -> s.empId)
}
```
