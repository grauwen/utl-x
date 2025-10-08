# Working with CSV

This guide covers CSV-specific features in UTL-X.

## Basic CSV Transformation

### Input CSV

```csv
order_id,customer_name,email,item_sku,quantity,price
ORD-001,Alice Johnson,alice@example.com,WIDGET-A,2,29.99
ORD-001,Alice Johnson,alice@example.com,GADGET-B,1,149.99
ORD-002,Bob Smith,bob@example.com,TOOL-C,3,15.50
```

### UTL-X Transformation

```utlx
%utlx 1.0
input csv {
  headers: true,
  delimiter: ","
}
output json
---
{
  orders: input.rows 
    |> groupBy(row => row.order_id)
    |> entries()
    |> map(([orderId, rows]) => {
         orderId: orderId,
         customer: {
           name: first(rows).customer_name,
           email: first(rows).email
         },
         items: rows |> map(row => {
           sku: row.item_sku,
           quantity: parseNumber(row.quantity),
           price: parseNumber(row.price)
         }),
         total: sum(rows.(parseNumber(quantity) * parseNumber(price)))
       })
}
```

### Output JSON

```json
{
  "orders": [
    {
      "orderId": "ORD-001",
      "customer": {
        "name": "Alice Johnson",
        "email": "alice@example.com"
      },
      "items": [
        {"sku": "WIDGET-A", "quantity": 2, "price": 29.99},
        {"sku": "GADGET-B", "quantity": 1, "price": 149.99}
      ],
      "total": 209.97
    },
    {
      "orderId": "ORD-002",
      "customer": {
        "name": "Bob Smith",
        "email": "bob@example.com"
      },
      "items": [
        {"sku": "TOOL-C", "quantity": 3, "price": 15.50}
      ],
      "total": 46.50
    }
  ]
}
```

## CSV Configuration

```utlx
%utlx 1.0
input csv {
  headers: true,              // First row contains headers
  delimiter: ",",             // Field delimiter
  quote: "\"",                // Quote character
  escape: "\\",               // Escape character
  skipEmptyLines: true,       // Skip empty lines
  trim: true                  // Trim whitespace from fields
}
```

### Common Delimiters

```utlx
delimiter: ","              // Comma (CSV)
delimiter: "\t"             // Tab (TSV)
delimiter: "|"              // Pipe
delimiter: ";"              // Semicolon
```

## Accessing CSV Data

### With Headers

```csv
name,age,city
Alice,30,Seattle
Bob,25,Portland
```

```utlx
input csv {
  headers: true
}
---
{
  people: input.rows |> map(row => {
    name: row.name,
    age: parseNumber(row.age),
    city: row.city
  })
}
```

### Without Headers

```csv
Alice,30,Seattle
Bob,25,Portland
```

```utlx
input csv {
  headers: false
}
---
{
  people: input.rows |> map(row => {
    name: row[0],
    age: parseNumber(row[1]),
    city: row[2]
  })
}
```

### Custom Headers

```utlx
input csv {
  headers: false,
  columnNames: ["name", "age", "city"]
}
---
{
  people: input.rows |> map(row => {
    name: row.name,
    age: parseNumber(row.age)
  })
}
```

## JSON/XML to CSV

### Simple Conversion

**JSON:**
```json
{
  "customers": [
    {"name": "Alice", "email": "alice@example.com", "total": 100},
    {"name": "Bob", "email": "bob@example.com", "total": 200}
  ]
}
```

**UTL-X:**
```utlx
%utlx 1.0
input json
output csv
---
{
  headers: ["Name", "Email", "Total"],
  rows: input.customers |> map(c => [
    c.name,
    c.email,
    c.total
  ])
}
```

**CSV:**
```csv
Name,Email,Total
Alice,alice@example.com,100
Bob,bob@example.com,200
```

### Nested Data to CSV

**JSON:**
```json
{
  "orders": [
    {
      "id": "ORD-001",
      "customer": {"name": "Alice"},
      "items": [{"sku": "A", "qty": 2}]
    }
  ]
}
```

**Flatten to CSV:**
```utlx
{
  headers: ["OrderID", "Customer", "ItemSKU", "Quantity"],
  rows: input.orders |> flatMap(order =>
    order.items |> map(item => [
      order.id,
      order.customer.name,
      item.sku,
      item.qty
    ])
  )
}
```

## CSV Output Options

```utlx
output csv {
  delimiter: ",",
  quote: "\"",
  quoteAll: false,            // Quote all fields (default: false)
  lineTerminator: "\n",       // Line ending
  includeHeaders: true        // Include header row (default: true)
}
```

## Handling Special Characters

### Quotes in Data

```csv
name,description
Widget,"A ""premium"" product"
```

Fields with quotes are automatically escaped.

### Commas in Data

```csv
name,address
Alice,"123 Main St, Seattle, WA"
```

Fields with commas are automatically quoted.

### Newlines in Data

```csv
name,notes
Alice,"Line 1
Line 2"
```

Multi-line fields are quoted.

## Common CSV Patterns

### Filter Rows

```utlx
{
  headers: input.headers,
  rows: input.rows |> filter(row => parseNumber(row.total) > 100)
}
```

### Add Calculated Column

```utlx
{
  headers: [...input.headers, "Tax"],
  rows: input.rows |> map(row => [
    ...row,
    parseNumber(row.total) * 0.08
  ])
}
```

### Aggregate by Group

```csv
category,product,sales
Electronics,Widget,100
Electronics,Gadget,200
Tools,Hammer,50
```

```utlx
{
  headers: ["Category", "TotalSales"],
  rows: input.rows 
    |> groupBy(row => row.category)
    |> entries()
    |> map(([category, rows]) => [
         category,
         sum(rows.(parseNumber(sales)))
       ])
}
```

### Join Two CSVs (v1.1+)

```utlx
// Requires reading multiple files
let customers = readCSV("customers.csv")
let orders = readCSV("orders.csv")

{
  headers: ["CustomerName", "OrderID", "Total"],
  rows: orders.rows |> map(order => {
    let customer = customers.rows 
      |> filter(c => c.id == order.customer_id)
      |> first()
    
    [customer.name, order.id, order.total]
  })
}
```

## CSV Validation

### Check Required Fields

```utlx
function validateRow(row: Object): Boolean {
  row.name != null && 
  row.email != null && 
  matches(row.email, ".*@.*\\..*")
}

{
  valid: input.rows |> filter(row => validateRow(row)),
  invalid: input.rows |> filter(row => !validateRow(row))
}
```

### Type Conversion with Validation

```utlx
{
  rows: input.rows |> map(row => {
    id: row.id,
    quantity: try { parseNumber(row.quantity) } catch { 0 },
    price: try { parseNumber(row.price) } catch { 0.00 },
    valid: try { 
      parseNumber(row.quantity) > 0 && parseNumber(row.price) > 0 
    } catch { 
      false 
    }
  })
}
```

## Best Practices

### 1. Always Parse Numbers

```utlx
// ✅ Good
quantity: parseNumber(row.quantity)

// ❌ Bad - stays as string
quantity: row.quantity
```

### 2. Handle Missing Values

```utlx
// ✅ Good
{
  name: row.name ?? "Unknown",
  email: row.email ?? "no-email@example.com"
}

// ❌ Bad - null values in output
{
  name: row.name,
  email: row.email
}
```

### 3. Trim Whitespace

```utlx
input csv {
  trim: true
}
```

### 4. Validate Data

```utlx
{
  rows: input.rows |> filter(row =>
    row.name != null &&
    row.email != null &&
    try { parseNumber(row.age) > 0 } catch { false }
  )
}
```

### 5. Use Descriptive Headers

```utlx
// ✅ Good
headers: ["Customer Name", "Order Date", "Total Amount"]

// ❌ Bad
headers: ["col1", "col2", "col3"]
```
