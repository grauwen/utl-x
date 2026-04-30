= CSV Transformations

CSV is the oldest and most universal data exchange format. Every spreadsheet, every database export, every log file can produce CSV. It's also the most deceptive format — seemingly simple, but full of edge cases: regional number formats, quoted fields with embedded delimiters, missing headers, BOM markers, and the eternal question of whether the separator is a comma, semicolon, or tab.

UTL-X handles all of this with a comprehensive CSV parser and serializer, including regional number formatting for European, French, and Swiss conventions.

== CSV and UDM

CSV maps to UDM differently depending on whether headers are present:

*With headers (default):* The first row becomes property names. Each subsequent row becomes a UDM Object keyed by those headers. The full CSV becomes a UDM Array of Objects:

```
Name,Age,Active          ← headers become property names
Alice,30,true            ← {Name: "Alice", Age: 30, Active: true}
Bob,25,false             ← {Name: "Bob", Age: 25, Active: false}
```

```utlx
$input[0].Name       // "Alice"
$input[1].Age        // 25
count($input)        // 2
```

*Without headers:* Each row becomes a UDM Array of scalars. The full CSV becomes an Array of Arrays:

```
Alice,30,true            ← ["Alice", 30, true]
Bob,25,false             ← ["Bob", 25, false]
```

```utlx
$input[0][0]         // "Alice"
$input[1][1]         // 25
```

== CSV Options

CSV comes in many dialects. UTL-X supports all common variations through header options.

=== Input Options

```utlx
%utlx 1.0
input csv {delimiter: ";", headers: true}
output json
---
$input
```

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Option*], [*Type*], [*Default*], [*What it does*],
  [`delimiter`], [String], [`","`], [Field separator: `","`, `";"`, `"|"`, `"\t"`],
  [`headers`], [Boolean], [`true`], [First row contains column names],
)

=== Output Options

```utlx
%utlx 1.0
input json
output csv {delimiter: ";", headers: true, regionalFormat: "european", decimals: 2}
---
$input
```

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Option*], [*Type*], [*Default*], [*What it does*],
  [`delimiter`], [String], [`","`], [Field separator],
  [`headers`], [Boolean], [`true`], [Emit header row],
  [`bom`], [Boolean], [`false`], [Prepend UTF-8 BOM (for Excel compatibility)],
  [`regionalFormat`], [String], [`"none"`], [Number format: `"usa"`, `"european"`, `"french"`, `"swiss"`],
  [`decimals`], [Int], [`2`], [Decimal places for numbers],
  [`useThousands`], [Boolean], [`true`], [Include thousands separator],
)

== Regional Number Formats

This is where CSV gets tricky. A number written as `1,234.56` in the US is `1.234,56` in Germany, `1 234,56` in France, and `1'234.56` in Switzerland. The comma that separates _fields_ in US CSV is the _decimal separator_ in European CSV. This is why European CSV files use semicolons as delimiters.

=== Output Formatting

UTL-X supports four regional formats on output:

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Format*], [*Option value*], [*Thousands*], [*Decimal*], [*Example*],
  [USA], [`"usa"` or `"us"`], [`,`], [`.`], [`1,234.56`],
  [European], [`"european"` or `"eu"`], [`.`], [`,`], [`1.234,56`],
  [French], [`"french"` or `"fr"`], [thin space], [`,`], [`1 234,56`],
  [Swiss], [`"swiss"` or `"ch"`], [`'`], [`.`], [`1'234.56`],
  [None], [`"none"` (default)], [none], [`.`], [`1234.56`],
)

```utlx
%utlx 1.0
input json
output csv {delimiter: ";", regionalFormat: "european", decimals: 2}
---
map($input.invoices, (inv) -> {
  number: inv.id,
  customer: inv.customer,
  amount: inv.total,
  vat: inv.total * 0.21
})
```

Output (European CSV — semicolons, comma decimals):

```csv
number;customer;amount;vat
INV-001;Acme Corp;1.234,56;259,26
INV-002;Globex Inc;567,89;119,26
```

=== Input Parsing

The CSV parser does NOT auto-detect regional number formats on input. A European number like `1.234,56` arrives as a string because `toDouble()` fails on the dot-comma format. This is by design — the parser cannot guess whether `1,234` means "one thousand two hundred thirty-four" (US) or "one point two three four" (European).

Use the regional parsing functions in your transformation:

```utlx
%utlx 1.0
input csv {delimiter: ";"}
output json
---
map($input, (row) -> {
  product: row.Product,
  price: parseEUNumber(row.Price),        // "1.234,56" → 1234.56
  weight: parseEUNumber(row.Weight)       // "0,75" → 0.75
})
```

Available parsing functions:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Function*], [*Input*], [*Output*],
  [`parseUSNumber("1,234.56")`], [US format], [`1234.56`],
  [`parseEUNumber("1.234,56")`], [European format], [`1234.56`],
  [`parseFrenchNumber("1 234,56")`], [French format], [`1234.56`],
  [`parseSwissNumber("1'234.56")`], [Swiss format], [`1234.56`],
)

And the corresponding rendering functions for formatting numbers as strings within a transformation body:

```utlx
renderEUNumber(1234.56)       // "1.234,56"
renderUSNumber(1234.56)       // "1,234.56"
renderFrenchNumber(1234.56)   // "1 234,56"
renderSwissNumber(1234.56)    // "1'234.56"
```

== Type Inference

The CSV parser automatically detects types for unquoted values:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*CSV value*], [*UDM type*], [*Notes*],
  [`true` / `false`], [Boolean], [Case-insensitive],
  [`null` / `nil` / `n/a`], [Null], [Case-insensitive],
  [Empty field], [Null], [Treated as null],
  [`42` / `3.14`], [Number], [Standard dot-decimal format only],
  [Anything else], [String], [Including regional numbers like `1.234,56`],
)

Dates are NOT auto-detected. A value like `2026-04-30` arrives as a string. Parse it explicitly:

```utlx
map($input, (row) -> {
  ...row,
  orderDate: parseDate(row.OrderDate, "yyyy-MM-dd"),
  deliveryDate: parseDate(row.DeliveryDate, "dd/MM/yyyy")
})
```

This is intentional — date formats are ambiguous (`01/02/2026` could be January 2nd or February 1st), so UTL-X requires you to declare the format.

== Reading CSV

=== Basic CSV to JSON

The most common pattern — turn a flat CSV into structured JSON:

```utlx
%utlx 1.0
input csv
output json
---
map($input, (row) -> {
  orderId: row.OrderId,
  customer: row.Customer,
  total: row.Amount,
  date: parseDate(row.OrderDate, "yyyy-MM-dd")
})
```

=== BOM Handling

Some tools (especially Excel on Windows) prepend a UTF-8 BOM (Byte Order Mark, U+FEFF) to CSV files. UTL-X strips this automatically — no configuration needed. The BOM is detected and removed before parsing begins.

=== Quoting and Special Characters

The parser follows RFC 4180 quoting rules:

- Fields containing the delimiter, quotes, or newlines must be quoted
- Quotes inside quoted fields are doubled: `"She said ""hello"""` → `She said "hello"`
- Multi-line values inside quotes are supported

```csv
Name,Description,Price
Widget,"A small, useful device",29.99
"Gadget Pro","Includes ""turbo"" mode",49.99
Manual,"Line 1
Line 2
Line 3",9.99
```

All three rows parse correctly — the comma inside "A small, useful device" doesn't split the field, the doubled quotes produce a literal quote, and the multi-line description is preserved as a single string value.

=== CSV Without Headers

When the CSV has no header row:

```utlx
%utlx 1.0
input csv {headers: false}
output json
---
map($input, (row) -> {
  name: row[0],
  age: row[1],
  active: row[2]
})
```

Without headers, each row is an array of values accessed by index. You assign meaningful names in the transformation.

=== Tab-Separated Values (TSV)

```utlx
%utlx 1.0
input csv {delimiter: "\t"}
output json
---
$input
```

=== Semicolon-Separated (European)

```utlx
%utlx 1.0
input csv {delimiter: ";"}
output json
---
map($input, (row) -> {
  product: row.Product,
  price: parseEUNumber(row.Price),
  quantity: toInteger(row.Qty)
})
```

== Writing CSV

=== JSON to CSV

The transformation result must be an array of objects for proper CSV output. The keys of the first object become the header row:

```utlx
%utlx 1.0
input json
output csv
---
map($input.orders, (order) -> {
  orderId: order.id,
  customer: order.customerName,
  total: order.total,
  status: order.status
})
```

Output:

```csv
orderId,customer,total,status
ORD-001,Acme Corp,299.99,CONFIRMED
ORD-002,Globex Inc,150.00,PENDING
```

=== Column Ordering

The order of properties in your output object determines the column order in the CSV. This gives you full control:

```utlx
// Columns appear in this exact order:
{
  date: row.orderDate,        // column 1
  customer: row.customerName, // column 2
  product: row.productName,   // column 3
  amount: row.total           // column 4
}
```

=== Flattening Nested JSON for CSV

CSV is flat — no nesting. To export nested JSON as CSV, you must denormalize. The `unnest()` function (Chapter 20) automates this, but you can also do it manually:

```utlx
%utlx 1.0
input json
output csv
---
flatten(map($input.orders, (order) ->
  map(order.lines, (line) -> {
    orderId: order.id,
    customer: order.customer,
    product: line.product,
    qty: line.qty,
    price: line.price,
    lineTotal: line.qty * line.price
  })
))
```

Each parent field (orderId, customer) is repeated for every child row — that's denormalization.

=== CSV with BOM for Excel

Excel sometimes needs a BOM to correctly detect UTF-8 encoding, especially for non-ASCII characters (accented names, CJK characters):

```utlx
%utlx 1.0
input json
output csv {bom: true}
---
$input
```

=== Suppressing Headers

```utlx
%utlx 1.0
input json
output csv {headers: false}
---
map($input.rows, (row) -> {
  a: row.name,
  b: row.value
})
```

Output has no header row — just data rows. Useful for appending to existing CSV files or producing fixed-format output.

== Common CSV Patterns

=== Bank Statement Processing (European)

European bank exports use semicolons and comma decimals:

```csv
Datum;Omschrijving;Bedrag;Saldo
30-04-2026;Salaris;2.500,00;5.234,56
29-04-2026;Albert Heijn;-45,67;2.734,56
28-04-2026;Hypotheek;-1.200,00;2.780,23
```

```utlx
%utlx 1.0
input csv {delimiter: ";"}
output json
---
map($input, (row) -> {
  date: parseDate(row.Datum, "dd-MM-yyyy"),
  description: row.Omschrijving,
  amount: parseEUNumber(row.Bedrag),
  balance: parseEUNumber(row.Saldo),
  type: if (parseEUNumber(row.Bedrag) >= 0) "credit" else "debit"
})
```

=== Log File Processing (Pipe-Delimited)

```utlx
%utlx 1.0
input csv {delimiter: "|"}
output json
---
$input
  |> filter((entry) -> entry.Level == "ERROR" || entry.Level == "CRITICAL")
  |> sortBy((entry) -> entry.Timestamp)
  |> map((entry) -> {
    timestamp: entry.Timestamp,
    level: entry.Level,
    message: entry.Message,
    source: entry.Source
  })
```

=== CSV Aggregation

Group rows and compute totals:

```utlx
%utlx 1.0
input csv
output json
---
let byCategory = groupBy($input, (row) -> row.Category)

map(keys(byCategory), (cat) -> {
  let rows = byCategory[cat]
  category: cat,
  count: count(rows),
  totalRevenue: sum(map(rows, (r) -> r.Revenue)),
  avgPrice: sum(map(rows, (r) -> r.Price)) / count(rows)
})
```

=== XML to CSV (Invoice Lines)

A common integration: receive an XML invoice, produce a CSV for the accounting system:

```utlx
%utlx 1.0
input xml
output csv {delimiter: ";", regionalFormat: "european", decimals: 2}
---
map($input.Invoice.Lines.Line, (line) -> {
  invoiceNr: $input.Invoice.@number,
  date: $input.Invoice.Date,
  product: line.Description,
  quantity: line.Quantity,
  unitPrice: line.UnitPrice,
  lineTotal: line.Quantity * line.UnitPrice,
  vatRate: line.VATRate,
  vatAmount: line.Quantity * line.UnitPrice * line.VATRate / 100
})
```

=== Multi-Format Pipeline: CSV + JSON Enrichment

Read CSV orders, enrich with customer data from JSON:

```utlx
%utlx 1.0
input csv orders
input json customers
output csv {regionalFormat: "usa", decimals: 2}
---
map($orders, (order) -> {
  let customer = find($customers, (c) -> c.id == order.CustomerId)

  orderId: order.OrderId,
  customerName: customer?.name ?? "Unknown",
  country: customer?.country ?? "XX",
  amount: order.Amount,
  domestic: if (customer?.country == "US") "YES" else "NO"
})
```

== CSV-Specific Functions

=== parseCsv

Parse a CSV string embedded within a transformation:

```utlx
let data = parseCsv(csvString)                           // default: comma, headers
let data = parseCsv(csvString, {headers: false})         // no headers
let data = parseCsv(csvString, {delimiter: ";"})         // semicolons
```

This is for the case where CSV is a _value_ inside your data — a CSV string in a JSON field, or CSV embedded in an XML element. For normal CSV file processing, use `input csv` in the header.

=== Spreadsheet Functions

UTL-X includes convenience functions for working with CSV data:

```utlx
csvRows(csv)                      // all rows as array of objects
csvColumns(csv)                   // header names as array
csvColumn(csv, "Price")           // all values in the Price column
csvRow(csv, 0)                    // first row as object
csvCell(csv, 2, "Name")           // cell at row 2, column "Name"

csvFilter(csv, "Status", "eq", "ACTIVE")   // filter rows
csvSort(csv, "Price", true)                // sort ascending
csvTranspose(csv)                          // swap rows/columns

csvAddColumn(csv, "NewCol", "default")     // add column
csvRemoveColumns(csv, ["Temp", "Debug"])   // drop columns
csvSelectColumns(csv, ["Name", "Price"])   // keep only these columns

csvSummarize(csv)                 // {count, sum, avg, min, max} per numeric column
```

These functions operate on CSV strings and are useful for quick exploration and prototyping.

== Edge Cases

=== Empty CSV

An empty file (or headers-only) produces an empty array:

```utlx
// Input: "Name,Age\n"  (headers only, no data rows)
count($input)   // 0
```

=== Single Row

A CSV with one data row produces a single-element array, not a plain object:

```utlx
// Input: "Name,Age\nAlice,30\n"
$input           // [{Name: "Alice", Age: 30}]  ← array of one
$input[0].Name   // "Alice"
```

=== Whitespace Handling

Unquoted field values are trimmed — leading and trailing spaces are removed. Quoted fields preserve all whitespace:

```csv
Name,Value
  Alice  ,  30
" Bob ",  40
```

Row 1: Name is `"Alice"` (trimmed), Value is `30`.
Row 2: Name is `" Bob "` (quoted, spaces preserved), Value is `40`.
