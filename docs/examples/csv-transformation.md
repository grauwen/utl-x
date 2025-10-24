# CSV Transformation Examples

Common patterns for working with CSV data.

## CSV to JSON

### Input CSV

```csv
id,name,email,age,city
1,Alice Johnson,alice@example.com,30,Seattle
2,Bob Smith,bob@example.com,25,Portland
3,Charlie Brown,charlie@example.com,35,San Francisco
```

### UTL-X

```utlx
%utlx 1.0
input csv {
  headers: true
}
output json
---
{
  users: $input.rows |> map(row => {
    id: parseNumber(row.id),
    name: row.name,
    email: row.email,
    age: parseNumber(row.age),
    city: row.city
  })
}
```

### Output JSON

```json
{
  "users": [
    {"id": 1, "name": "Alice Johnson", "email": "alice@example.com", "age": 30, "city": "Seattle"},
    {"id": 2, "name": "Bob Smith", "email": "bob@example.com", "age": 25, "city": "Portland"},
    {"id": 3, "name": "Charlie Brown", "email": "charlie@example.com", "age": 35, "city": "San Francisco"}
  ]
}
```

## JSON to CSV

### Input JSON

```json
{
  "products": [
    {"sku": "P001", "name": "Widget", "price": 29.99, "inStock": true},
    {"sku": "P002", "name": "Gadget", "price": 149.99, "inStock": false},
    {"sku": "P003", "name": "Tool", "price": 15.50, "inStock": true}
  ]
}
```

### UTL-X

```utlx
%utlx 1.0
input json
output csv
---
{
  headers: ["SKU", "Product Name", "Price", "In Stock"],
  rows: $input.products |> map(p => [
    p.sku,
    p.name,
    p.price,
    if (p.inStock) "Yes" else "No"
  ])
}
```

### Output CSV

```csv
SKU,Product Name,Price,In Stock
P001,Widget,29.99,Yes
P002,Gadget,149.99,No
P003,Tool,15.50,Yes
```

## Filter CSV Rows

### Input CSV

```csv
order_id,customer,total,status
ORD-001,Alice,250.00,completed
ORD-002,Bob,100.00,pending
ORD-003,Charlie,500.00,completed
ORD-004,Alice,75.00,cancelled
```

### UTL-X

```utlx
%utlx 1.0
input csv {headers: true}
output csv
---
{
  headers: $input.headers,
  rows: $input.rows 
    |> filter(row => row.status == "completed")
    |> filter(row => parseNumber(row.total) > 200)
}
```

### Output CSV

```csv
order_id,customer,total,status
ORD-001,Alice,250.00,completed
ORD-003,Charlie,500.00,completed
```

## Add Calculated Column

### Input CSV

```csv
product,quantity,price_per_unit
Widget,5,29.99
Gadget,2,149.99
Tool,10,15.50
```

### UTL-X

```utlx
%utlx 1.0
input csv {headers: true}
output csv
---
{
  headers: [...$input.headers, "total"],
  rows: $input.rows |> map(row => [
    row.product,
    row.quantity,
    row.price_per_unit,
    parseNumber(row.quantity) * parseNumber(row.price_per_unit)
  ])
}
```

### Output CSV

```csv
product,quantity,price_per_unit,total
Widget,5,29.99,149.95
Gadget,2,149.99,299.98
Tool,10,15.50,155.00
```

## Group and Aggregate

### Input CSV

```csv
category,product,sales
Electronics,Widget,100
Electronics,Gadget,200
Tools,Hammer,50
Tools,Wrench,75
Electronics,Display,150
```

### UTL-X

```utlx
%utlx 1.0
input csv {headers: true}
output csv
---
{
  headers: ["Category", "Total Sales", "Product Count"],
  rows: $input.rows 
    |> groupBy(row => row.category)
    |> entries()
    |> map(([category, rows]) => [
         category,
         sum(rows.(parseNumber(sales))),
         count(rows)
       ])
}
```

### Output CSV

```csv
Category,Total Sales,Product Count
Electronics,450,3
Tools,125,2
```

## Pivot Data

### Input CSV

```csv
date,product,sales
2025-01-01,Widget,100
2025-01-01,Gadget,200
2025-01-02,Widget,150
2025-01-02,Gadget,250
```

### UTL-X

```utlx
%utlx 1.0
input csv {headers: true}
output json
---
{
  pivot: $input.rows 
    |> groupBy(row => row.date)
    |> entries()
    |> map(([date, rows]) => {
         date: date,
         products: rows |> reduce((acc, row) => {
           ...acc,
           [row.product]: parseNumber(row.sales)
         }, {})
       })
}
```

### Output JSON

```json
{
  "pivot": [
    {"date": "2025-01-01", "products": {"Widget": 100, "Gadget": 200}},
    {"date": "2025-01-02", "products": {"Widget": 150, "Gadget": 250}}
  ]
}
```

## CSV Validation

### Input CSV

```csv
id,name,email,age
1,Alice,alice@example.com,30
2,Bob,invalid-email,25
3,Charlie,charlie@example.com,-5
4,David,david@example.com,40
```

### UTL-X

```utlx
%utlx 1.0
input csv {headers: true}
output json
---
{
  valid: $input.rows |> filter(row =>
    row.id != null &&
    matches(row.email, ".*@.*\\..*") &&
    try { parseNumber(row.age) > 0 } catch { false }
  ),
  
  invalid: $input.rows |> filter(row =>
    row.id == null ||
    !matches(row.email, ".*@.*\\..*") ||
    try { parseNumber(row.age) <= 0 } catch { true }
  ) |> map(row => {
    ...row,
    errors: [
      if (row.id == null) "Missing ID",
      if (!matches(row.email, ".*@.*\\..*")) "Invalid email",
      if (try { parseNumber(row.age) <= 0 } catch { true }) "Invalid age"
    ] |> filter(e => e != false)
  })
}
```

---
