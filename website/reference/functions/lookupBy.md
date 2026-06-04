---
title: lookupBy
description: "lookupBy — UTL-X Array function. Find one matching record from a reference array by key. Returns the"
pageClass: stdlib-page
---

# lookupBy

<p class="stdlib-meta"><code>lookupBy(searchValue, referenceArray, keyFn) → element \| null</code> · <a href="/reference/stdlib#array">Array</a></p>

Find one matching record from a reference array by key. Returns the
first match or `null`. The go-to function for 1:1 enrichment – adding
data from a lookup table to each record. See Chapter 20.

- `searchValue` (required): the value to search for (e.g., a customer
  ID)

- `referenceArray` (required): the array to search in (e.g., all
  customers)

- `keyFn` (required): lambda extracting the comparison key from each
  reference record

``` utlx
// Enrich order lines with product names from a product catalog
let products = $input.products           // [{sku: "W-01", name: "Widget"}, ...]

{
  orders: map($input.orders, (order) -> {
    ...order,
    productName: lookupBy(order.sku, products, (p) -> p.sku).name
  })
}
```

``` bash
echo '{"id": "C-42", "customers": [{"id": "C-42", "name": "Acme"}]}' | utlx -e 'lookupBy($input.id, $input.customers, (c) -> c.id)'
# {"id": "C-42", "name": "Acme"}
```

**Choosing the right function:** `lookupBy` for 1:1 enrichment,
`groupBy` for O(1) keyed map, `nestBy` for 1:N parent-child nesting.
