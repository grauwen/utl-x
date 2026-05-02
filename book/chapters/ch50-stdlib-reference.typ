= Standard Library Reference

This appendix lists the UTL-X standard library functions alphabetically. Each entry shows:
- *Signature:* `functionName(required, required, optional?)` — parameters with `?` are optional
- *Category tag:* Str, Arr, Obj, Num, Date, Fmt, XML, JSON, CSV, YAML, Sec, Bin, Geo, URL, Sys, Type
- *Example:* practical, runnable usage
- *Anti-pattern:* what NOT to do (where applicable)

== A

=== abs(number) → number #text(size: 8pt, fill: gray)[(Num)]

Returns the absolute value of a number.

- `number` (required): the number to take the absolute value of

```bash
echo '{"balance": -1500.50}' | utlx -e 'abs(.balance)'
# 1500.5
```

```utlx
abs(-42)      // 42
abs(42)       // 42 (positive unchanged)
abs(0)        // 0
```

=== acos #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== addBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== addNamespaceDeclarations #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== addQueryParam #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== addTax #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== age #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== analyzeString #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== and #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== asin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== assert #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== assertEqual #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== atan #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== atan2 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== attribute #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== attributes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== availableProcessors #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== Date Format Patterns #text(size: 8pt, fill: gray)[(Date — Reference)]

Before the date functions: UTL-X uses Java's `DateTimeFormatter` pattern tokens. Case matters — `MM` is months, `mm` is minutes:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Token*], [*Meaning*], [*Example*],
  [`yyyy`], [4-digit year], [`2026`],
  [`yy`], [2-digit year], [`26`],
  [`MM`], [Month (01-12)], [`05`],
  [`MMM`], [Month abbreviation], [`May`],
  [`MMMM`], [Month full name], [`May`],
  [`dd`], [Day of month (01-31)], [`01`],
  [`HH`], [Hour 24h (00-23)], [`14`],
  [`hh`], [Hour 12h (01-12)], [`02`],
  [`mm`], [Minute (00-59)], [`30`],
  [`ss`], [Second (00-59)], [`45`],
  [`EEEE`], [Day name], [`Thursday`],
  [`EEE`], [Day abbreviation], [`Thu`],
  [`a`], [AM/PM], [`PM`],
  [`XXX`], [Timezone offset], [`+02:00`],
  [`'...'`], [Literal text], [`'T'` outputs `T`],
)

*Why uppercase `MM` but lowercase `mm`?* To distinguish months from minutes. `yyyy-MM-dd` is year-month-day. `HH:mm:ss` is hour-minute-second. Mixing them up (`yyyy-mm-dd`) parses minutes where months should be — a common bug.

=== addDays(date, count) → date #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) a number of days to a date.

- `date` (required): the starting date or datetime
- `count` (required): number of days to add. Negative to subtract.

```bash
echo '{"orderDate": "2026-05-01", "deliveryDays": 14}' \
  | utlx -e 'formatDate(addDays(parseDate(.orderDate, "yyyy-MM-dd"), .deliveryDays), "yyyy-MM-dd")'
# "2026-05-15"
```

```utlx
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

```utlx
addDays(parseDate("2026-05-01", "yyyy-MM-dd"), 14)    // 2026-05-15
addDays(parseDate("2026-05-01", "yyyy-MM-dd"), -7)    // 2026-04-24 (subtract)
addDays(parseDate("2026-02-27", "yyyy-MM-dd"), 2)     // 2026-03-01 (crosses month boundary)
```

=== addHours(datetime, count) → datetime #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) hours to a datetime.

- `datetime` (required): the starting datetime
- `count` (required): number of hours to add. Negative to subtract.

```utlx
addHours(now(), 3)                       // 3 hours from now
addHours(now(), -24)                     // 24 hours ago (same as yesterday, same time)
```

=== addMinutes(datetime, count) → datetime #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) minutes to a datetime.

- `datetime` (required): the starting datetime
- `count` (required): number of minutes to add. Negative to subtract.

```utlx
addMinutes(now(), 90)                    // 1.5 hours from now
addMinutes(now(), -30)                   // 30 minutes ago
```

=== addMonths(date, count) → date #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) months to a date. If the resulting day exceeds the target month's length, it is clamped to the last day of that month.

- `date` (required): a date or datetime value — NOT a string. If your date is a string (JSON, XML, CSV input), convert it first with `parseDate()`. YAML auto-parses dates, so YAML input values are already dates.
- `count` (required): number of months to add. Negative to subtract.

```utlx
// From JSON/XML/CSV input — date is a string, parseDate needed:
let orderDate = parseDate($input.orderDate, "yyyy-MM-dd")
addMonths(orderDate, 3)                  // 2026-08-01

// From YAML input — date is already a date value, no parseDate needed:
addMonths($input.orderDate, 3)           // works directly

// With string literals in examples, parseDate is always needed:
addMonths(parseDate("2026-05-01", "yyyy-MM-dd"), -2)   // 2026-03-01

// Edge case: end-of-month clamping
addMonths(parseDate("2026-01-31", "yyyy-MM-dd"), 1)    // 2026-02-28 (not Feb 31)
addMonths(parseDate("2026-03-31", "yyyy-MM-dd"), 1)    // 2026-04-30 (not Apr 31)
```

=== addQuarters(date, count) → date #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) quarters (3-month periods) to a date.

- `date` (required): the starting date or datetime
- `count` (required): number of quarters to add. Negative to subtract.

```utlx
addQuarters(parseDate("2026-01-15", "yyyy-MM-dd"), 1)  // 2026-04-15
addQuarters(parseDate("2026-01-15", "yyyy-MM-dd"), 4)  // 2027-01-15 (1 year)
```

=== addSeconds(datetime, count) → datetime #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) seconds to a datetime.

- `datetime` (required): the starting datetime
- `count` (required): number of seconds to add. Negative to subtract.

```utlx
addSeconds(now(), 3600)                  // 1 hour from now (3600 seconds)
addSeconds(now(), -1)                    // 1 second ago
```

=== addWeeks(date, count) → date #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) weeks to a date. Equivalent to `addDays(date, count * 7)`.

- `date` (required): the starting date or datetime
- `count` (required): number of weeks to add. Negative to subtract.

```utlx
addWeeks(parseDate("2026-05-01", "yyyy-MM-dd"), 2)     // 2026-05-15
addWeeks(parseDate("2026-05-01", "yyyy-MM-dd"), -1)    // 2026-04-24
```

=== addYears(date, count) → date #text(size: 8pt, fill: gray)[(Date)]

Add (or subtract) years to a date. Handles leap year edge cases.

- `date` (required): the starting date or datetime
- `count` (required): number of years to add. Negative to subtract.

```utlx
addYears(parseDate("2026-05-01", "yyyy-MM-dd"), 1)     // 2027-05-01
addYears(parseDate("2026-05-01", "yyyy-MM-dd"), -10)   // 2016-05-01

// Edge case: leap year
addYears(parseDate("2024-02-29", "yyyy-MM-dd"), 1)     // 2025-02-28 (2025 is not a leap year)
```

=== all(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Returns true if ALL elements satisfy the predicate. Returns true for empty arrays (vacuously true).

- `array` (required): the array to test
- `predicate` (required): lambda `(element) -> boolean`

```utlx
// Given: {"items": [{"price": 10}, {"price": 25}, {"price": 5}]}

all($input.items, (item) -> item.price > 0)
// Output: true (all prices positive)

all($input.items, (item) -> item.price > 20)
// Output: false (10 and 5 are not > 20)

all([], (x) -> x > 0)
// Output: true (vacuously true — no elements to violate)
```

=== any(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Returns true if at least ONE element satisfies the predicate. Returns false for empty arrays.

- `array` (required): the array to test
- `predicate` (required): lambda `(element) -> boolean`

```utlx
// Given: {"orders": [{"status": "PENDING"}, {"status": "SHIPPED"}, {"status": "PENDING"}]}

any($input.orders, (o) -> o.status == "SHIPPED")
// Output: true (second order is shipped)

any($input.orders, (o) -> o.status == "CANCELLED")
// Output: false (none cancelled)
```

=== avg(array) → number / avgBy(array, keyFn) → number #text(size: 8pt, fill: gray)[(Num)]

Average of numeric values. `avg` takes an array of numbers. `avgBy` takes an array of objects and a key extractor.

- `array` (required): array of numbers (avg) or objects (avgBy)
- `keyFn` (required for avgBy): lambda `(element) -> number`

```utlx
// Given: {"scores": [85, 92, 78, 95, 88]}

avg($input.scores)
// Output: 87.6

// Given: {"products": [{"name": "A", "price": 10}, {"name": "B", "price": 30}, {"name": "C", "price": 20}]}

avgBy($input.products, (p) -> p.price)
// Output: 20

avg([])
// Output: 0 (empty array returns 0, not error)
```

*Anti-pattern:* `sum(arr) / count(arr)` — crashes on empty arrays (division by zero). Use `avg()` which handles this safely.

== B

=== base64Encode(data) → string / base64Decode(string) → string #text(size: 8pt, fill: gray)[(Sec)]

Encode data to Base64 string / decode Base64 string back to original.

- `data` (required): string to encode
- `string` (required): Base64-encoded string to decode

```utlx
// Encode a value for safe transport (e.g., in URL or header)
base64Encode("Hello, World!")
// Output: "SGVsbG8sIFdvcmxkIQ=="

// Decode back
base64Decode("SGVsbG8sIFdvcmxkIQ==")
// Output: "Hello, World!"

// Real-world: decode a Base64-encoded JWT payload
let payload = split($input.token, ".")[1]
let decoded = parseJson(base64Decode(payload))
decoded.sub
// Output: "user@example.com"
```

=== bearing #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== binaryConcat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== binaryEquals #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== binaryLength #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== binarySlice #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== binaryToString #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== bitwiseAnd #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== bitwiseNot #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== bitwiseOr #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== bitwiseXor #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== boundingBox #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== buildQueryString #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== buildURL #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== C

=== c14n(xml) → string / c14nHash(xml, algorithm?) → string / c14nEquals(xml1, xml2) → boolean #text(size: 8pt, fill: gray)[(XML)]

XML canonicalization (W3C C14N). See Chapter 22 for full details.

- `xml` (required): XML UDM value to canonicalize
- `algorithm` (optional, default `"SHA-256"`): hash algorithm for `c14nHash`

```utlx
// Given: XML document with unsorted attributes, whitespace variations

c14n($input)
// Output: canonical XML string (sorted attributes, normalized whitespace)

c14nHash($input)
// Output: "a1b2c3d4e5f6..."  (SHA-256 hex digest of canonical form)

c14nHash($input, "SHA-512")
// Output: "9b71d224bd62..."  (SHA-512 instead)

// Compare two XML documents semantically (ignoring formatting differences)
c14nEquals(xmlFromSystemA, xmlFromSystemB)
// Output: true if same content, regardless of attribute order or whitespace
```

Also: `c14nWithComments(xml)`, `excC14n(xml)` (exclusive, for SOAP), `c14n11(xml)` (version 1.1), `c14nFingerprint(xml)` (short hash for logging).

=== c14n11 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== c14n11WithComments #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== c14nFingerprint #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== c14nPhysical #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== c14nSubset #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== c14nWithComments #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== calculateDiscount #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== calculateTax #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== camelCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== camelize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== canCoerce #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== canonicalizeWithAlgorithm #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== canonicalJSONHash #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== canonicalJSONSize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== capitalize(string) → string #text(size: 8pt, fill: gray)[(Str)]

Capitalize the first letter of a string. Only affects the first character.

- `string` (required): the string to capitalize

```utlx
capitalize("hello world")               // "Hello world"
capitalize("HELLO")                      // "HELLO" (already uppercase — no change)
capitalize("")                           // ""
```

=== ceil(number) → integer #text(size: 8pt, fill: gray)[(Num)]

Round up to the nearest integer (ceiling).

- `number` (required): the number to round up

```utlx
ceil(3.2)       // 4
ceil(3.9)       // 4
ceil(-3.2)      // -3 (towards zero for negatives)
ceil(4.0)       // 4 (already integer)
```

=== charAt #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== charCodeAt #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== childCount #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== childNames #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== chunkBy #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== clearLogs #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== coerce #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== coerceAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compactCSV #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compactJSON #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compactXML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compareDates #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compoundInterest #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== compress #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== constantCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== containsValue #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== convertTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== convertXMLEncoding #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== cos #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== cosh #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== countEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== createCDATA #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== createSOAPEnvelope #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== crossJoin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvAddColumn #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvCell #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvColumn #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvColumns #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvRemoveColumns #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvRow #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvRows #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvSelectColumns #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvSort #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvSummarize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== csvTranspose #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== currentDir #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== floor(number) → integer #text(size: 8pt, fill: gray)[(Num)]

Round down to the nearest integer (floor).

- `number` (required): the number to round down

```utlx
floor(3.8)      // 3
floor(3.1)      // 3
floor(-3.8)     // -4 (away from zero for negatives)
floor(4.0)      // 4 (already integer)
```

=== round(number) → integer #text(size: 8pt, fill: gray)[(Num)]

Round to the nearest integer (half-up rounding).

- `number` (required): the number to round

```utlx
round(3.5)      // 4
round(3.4)      // 3
round(-3.5)     // -3

// Round to 2 decimal places (common pattern for currency):
round(6.2895 * 100) / 100               // 6.29
```

*Anti-pattern:* `round(price)` for currency — loses cents entirely. Use `roundToCents(price)` or `roundToDecimalPlaces(price, 2)` instead.

=== chunk(array, size) → array of arrays #text(size: 8pt, fill: gray)[(Arr)]

Split an array into sub-arrays of the given size. Last chunk may be smaller.

- `array` (required): the array to split
- `size` (required): maximum elements per chunk

```utlx
// Given: {"items": ["A", "B", "C", "D", "E", "F", "G"]}

chunk($input.items, 3)
// Output: [["A", "B", "C"], ["D", "E", "F"], ["G"]]

// Use case: batch processing — send items in groups of 100
map(chunk($input.records, 100), (batch) -> {
  batchSize: count(batch),
  items: batch
})
```

=== coalesce(value1, value2, ...) → value #text(size: 8pt, fill: gray)[(Type)]

Returns the first non-null argument. Accepts any number of arguments.

- `value1, value2, ...` (variadic): values to check in order

```utlx
// Given: {"nickname": null, "displayName": null, "fullName": "Alice Johnson"}

coalesce($input.nickname, $input.displayName, $input.fullName, "Anonymous")
// Output: "Alice Johnson" (first non-null)

coalesce(null, null, null)
// Output: null (all null)
```

*Note:* for two values, the `??` operator is cleaner: `$input.name ?? "Unknown"`.

=== compact(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove null, empty string, and false values from an array.

- `array` (required): the array to compact

```utlx
// Given: {"tags": ["urgent", null, "", "review", null, "important"]}

compact($input.tags)
// Output: ["urgent", "review", "important"]

// Use case: build a list with conditional entries, then compact away the nulls
compact([
  $input.name,
  if ($input.email != null) $input.email else null,
  if ($input.phone != null) $input.phone else null
])
// Output: ["Alice", "alice@example.com"]  (phone was null, removed)
```

=== concat(string1, string2, ...) → string #text(size: 8pt, fill: gray)[(Str)]

Concatenate any number of strings.

- `string1, string2, ...` (variadic): strings to concatenate

```utlx
// Given: {"firstName": "Alice", "lastName": "Johnson", "title": "Dr."}

concat($input.title, " ", $input.firstName, " ", $input.lastName)
// Output: "Dr. Alice Johnson"

// With 2 arguments:
concat("Order-", toString($input.orderId))
// Output: "Order-42"
```

*Anti-pattern:* building a long string in a loop with `reduce` + `concat` creates N intermediate strings. Use `join(array, separator)` instead:

```utlx
// BAD: O(N²) string building
reduce($input.names, "", (acc, name) -> concat(acc, name, ", "))

// GOOD: O(N) string building
join($input.names, ", ")
```

=== contains(haystack, needle) → boolean #text(size: 8pt, fill: gray)[(Str/Arr)]

Check if a string contains a substring, or an array contains a value.

- `haystack` (required): the string or array to search in
- `needle` (required): the value to search for

```utlx
// String variant:
contains("Hello World", "World")         // true
contains("Hello World", "world")         // false (case-sensitive)

// Array variant:
// Given: {"roles": ["admin", "editor", "viewer"]}
contains($input.roles, "admin")          // true
contains($input.roles, "superadmin")     // false

// Use case: filter by membership
filter($input.orders, (o) -> contains(["ACTIVE", "PENDING"], o.status))
```

=== count(array) → number / countBy(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

Count elements. `count` counts all. `countBy` counts those matching a predicate.

- `array` (required): the array to count
- `predicate` (required for countBy): lambda `(element) -> boolean`

```utlx
// Given: {"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}

count($input.orders)
// Output: 3

countBy($input.orders, (o) -> o.status == "ACTIVE")
// Output: 2
```

*Anti-pattern:* calling `count(filter(...))` twice with the same filter:

```utlx
// BAD: filters twice
{
  activeCount: count(filter($input.orders, (o) -> o.status == "ACTIVE")),
  activeNames: map(filter($input.orders, (o) -> o.status == "ACTIVE"), (o) -> o.name)
}

// GOOD: filter once, reuse
let active = filter($input.orders, (o) -> o.status == "ACTIVE")
{
  activeCount: count(active),
  activeNames: map(active, (o) -> o.name)
}
```

=== createQname(localName, namespaceUri, prefix?) → object #text(size: 8pt, fill: gray)[(XML)]

Create a structured QName from its parts. See Chapter 22.

- `localName` (required): the local element name without prefix
- `namespaceUri` (required): the full namespace URI
- `prefix` (optional): the namespace prefix

```utlx
createQname("Invoice", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "cbc")
// Output: {
//   localName: "Invoice",
//   namespaceUri: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
//   prefix: "cbc",
//   qualifiedName: "cbc:Invoice"
// }

// Without prefix:
createQname("Order", "urn:example:orders")
// Output: {localName: "Order", namespaceUri: "urn:example:orders", prefix: "", qualifiedName: "Order"}
```

=== csvFilter(csv, column, operator, value) → string #text(size: 8pt, fill: gray)[(CSV)]

Filter CSV rows by a column condition. Returns a new CSV string. See Chapter 25.

- `csv` (required): CSV string to filter
- `column` (required): column name to test
- `operator` (required): comparison — `"eq"`, `"ne"`, `"contains"`, `"startswith"`, `"endswith"`, `"gt"`, `"lt"`, `"gte"`, `"lte"`
- `value` (required): value to compare against

```utlx
// Given CSV string:
let csv = "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200\nCharlie,ACTIVE,50"

csvFilter(csv, "Status", "eq", "ACTIVE")
// Output: "Name,Status,Amount\nAlice,ACTIVE,100\nCharlie,ACTIVE,50"

csvFilter(csv, "Amount", "gt", "75")
// Output: "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200"
```

Also: `csvSort(csv, column, ascending?)`, `csvColumns(csv)`, `csvRows(csv)`, `csvRow(csv, index)`, `csvCell(csv, row, column)`, `csvColumn(csv, name)`, `csvTranspose(csv)`, `csvAddColumn(csv, name, default)`, `csvRemoveColumns(csv, names)`, `csvSelectColumns(csv, names)`, `csvSummarize(csv)`.

== D

=== day #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== dayOfMonth #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== dayOfWeek(date) → number / dayOfWeekName(date) → string / dayOfYear(date) → number #text(size: 8pt, fill: gray)[(Date)]

Extract date components.

- `date` (required): a date or datetime value

```utlx
// Given: {"orderDate": "2026-05-01"}
let d = parseDate($input.orderDate, "yyyy-MM-dd")

dayOfWeek(d)          // 5 (Thursday — 1=Monday, 7=Sunday)
dayOfWeekName(d)      // "Thursday"
dayOfYear(d)          // 121 (121st day of 2026)
day(d)                // 1 (day of month)
month(d)              // 5
monthName(d)          // "May"
year(d)               // 2026
quarter(d)            // 2 (Q2)
weekOfYear(d)         // 18
```

Also: `daysInMonth(year, month)` → `daysInMonth(2026, 2)` returns `28`. `daysInYear(year)` → `daysInYear(2024)` returns `366` (leap year). `isLeapYear(year)`.

=== daysBetween #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== daysInMonth #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== daysInYear #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== debug #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== debugPrint #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== debugPrintCompact #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== decodeJWS #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== decodeJWT #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== decompress #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== decryptAES #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== decryptAES256 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== deepClone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== deepMerge(obj1, obj2) → object #text(size: 8pt, fill: gray)[(Obj)]

Recursively merge two objects. At each level, properties from `obj2` override `obj1`. Nested objects are merged recursively (not replaced).

- `obj1` (required): base object
- `obj2` (required): override object

```utlx
// Given: base config + environment override
let base = {
  server: {host: "localhost", port: 5432, ssl: false},
  logging: {level: "INFO", format: "json"}
}
let prod = {
  server: {host: "prod-db.example.com", ssl: true},
  logging: {level: "WARN"}
}

deepMerge(base, prod)
// Output: {
//   server: {host: "prod-db.example.com", port: 5432, ssl: true},
//   logging: {level: "WARN", format: "json"}
// }
// Note: port (5432) and format ("json") survived from base — deep merge, not replace
```

*Contrast with spread:* `{...base, ...prod}` would REPLACE the entire `server` object, losing `port`. `deepMerge` preserves nested properties. Also: `deepMergeAll(array)` merges an array of objects sequentially.

=== deepMergeAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== deflate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== destinationPoint #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== detectBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== detectXMLEncoding(xmlString) → string #text(size: 8pt, fill: gray)[(XML)]

Detect the encoding declared in an XML document's declaration.

- `xmlString` (required): XML string or UDM value

```utlx
// Given: XML with encoding declaration
// <?xml version="1.0" encoding="ISO-8859-1"?>
// <Order>...</Order>

detectXMLEncoding($input)
// Output: "ISO-8859-1"

// UTF-8 (default when no declaration):
// <Order>...</Order>
detectXMLEncoding($input)
// Output: "UTF-8"
```

Also: `convertXMLEncoding(xml, targetEncoding)` re-encodes the document.

=== diffDays(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference between two dates in the specified unit. Variants: `diffMonths`, `diffYears`, `diffHours`, `diffMinutes`, `diffSeconds`, `diffWeeks`.

- `date1` (required): start date
- `date2` (required): end date

```utlx
// Given: {"startDate": "2026-05-01", "endDate": "2026-06-15"}
let start = parseDate($input.startDate, "yyyy-MM-dd")
let end = parseDate($input.endDate, "yyyy-MM-dd")

diffDays(start, end)                     // 45
diffWeeks(start, end)                    // 6
diffMonths(start, end)                   // 1

// Use case: calculate invoice overdue days
let dueDate = parseDate($input.dueDate, "yyyy-MM-dd")
let overdueDays = diffDays(dueDate, now())
if (overdueDays > 0) concat("Overdue by ", toString(overdueDays), " days")
else "Not yet due"
```

=== diffHours #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== diffMinutes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== diffMonths #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== diffSeconds #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== diffWeeks #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== diffYears #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== distance #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== distinct(array) → array / distinctBy(array, keyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values. `distinct` uses value equality. `distinctBy` uses a key extractor to determine uniqueness.

- `array` (required): the array to deduplicate
- `keyFn` (required for distinctBy): lambda `(element) -> key`

```utlx
distinct([1, 2, 2, 3, 3, 3])
// Output: [1, 2, 3]

// Given: {"orders": [
//   {"id": 1, "customerId": "C-42"},
//   {"id": 2, "customerId": "C-42"},
//   {"id": 3, "customerId": "C-41"}
// ]}

distinctBy($input.orders, (o) -> o.customerId)
// Output: [{"id": 1, "customerId": "C-42"}, {"id": 3, "customerId": "C-41"}]
// (first order per customer kept, duplicates removed)

// Extract unique values from a field:
distinct(map($input.orders, (o) -> o.customerId))
// Output: ["C-42", "C-41"]
```

=== divideBy #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== dotCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== drop(array, n) → array / take(array, n) → array #text(size: 8pt, fill: gray)[(Arr)]

`drop`: remove the first N elements. `take`: keep only the first N elements.

- `array` (required): the source array
- `n` (required): number of elements

```utlx
// Given: {"items": ["A", "B", "C", "D", "E"]}

drop($input.items, 2)
// Output: ["C", "D", "E"]  (first 2 removed)

take($input.items, 3)
// Output: ["A", "B", "C"]  (only first 3 kept)

// Use case: skip CSV header row (when headers: false)
let dataRows = drop($input, 1)  // skip first row

// Use case: top 10 results
let top10 = take(sortBy($input.products, (p) -> -p.sales), 10)
```

== E

=== e #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== elementPath #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== encryptAES #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== encryptAES256 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endOfDay #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endOfMonth #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endOfQuarter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endOfWeek #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endOfYear #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== endsWith(string, suffix) → boolean / startsWith(string, prefix) → boolean #text(size: 8pt, fill: gray)[(Str)]

Check if a string starts or ends with a given substring.

- `string` (required): the string to test
- `suffix`/`prefix` (required): the substring to check for

```utlx
// Given: {"filename": "invoice-2026.xml", "orderId": "ORD-001"}

endsWith($input.filename, ".xml")        // true
endsWith($input.filename, ".json")       // false
startsWith($input.orderId, "ORD-")       // true

// Use case: filter files by extension
filter($input.files, (f) -> endsWith(f.name, ".utlx"))

// Use case: validate ID format
if (!startsWith($input.id, "ORD-")) error("Invalid order ID format")
```

=== endTimer #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== enforceNamespacePrefixes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== entries(object) → array / fromEntries(pairs) → object #text(size: 8pt, fill: gray)[(Obj)]

Convert between objects and `[key, value]` pair arrays. Essential for dynamic key processing. See Chapter 26.

- `object` (required for entries): the object to decompose
- `pairs` (required for fromEntries): array of `[key, value]` arrays

```utlx
// Given: {"servers": {"prod": {"host": "prod-db"}, "staging": {"host": "stg-db"}}}

entries($input.servers)
// Output: [["prod", {"host": "prod-db"}], ["staging", {"host": "stg-db"}]]

// Iterate over dynamic keys:
entries($input.servers) |> map((entry) -> {
  environment: entry[0],     // the key: "prod", "staging"
  host: entry[1].host        // the value's property
})
// Output: [{"environment": "prod", "host": "prod-db"}, {"environment": "staging", "host": "stg-db"}]

// Build an object with dynamic keys:
fromEntries(map($input.items, (i) -> [i.id, i.name]))
// Input items: [{id: "A", name: "Widget"}, {id: "B", name: "Gadget"}]
// Output: {"A": "Widget", "B": "Gadget"}
```

=== env(name) → string / envOrDefault(name, default) → string #text(size: 8pt, fill: gray)[(Sys)]

Read environment variables from the host system.

- `name` (required): environment variable name
- `default` (required for envOrDefault): fallback value if not set

```utlx
env("HOME")                              // "/Users/alice"
env("UNDEFINED_VAR")                     // null

envOrDefault("LOG_LEVEL", "INFO")        // "INFO" if LOG_LEVEL not set
envOrDefault("DATABASE_URL", "postgres://localhost:5432/mydb")
```

Also: `hasEnv(name)` → boolean, `envAll()` → object with all environment variables.

=== envAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== environment #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== equals #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== error(message) → never #text(size: 8pt, fill: gray)[(Sys)]

Throw a runtime error with a message. Stops the transformation.

- `message` (required): error description string

```utlx
// Validate input before processing:
if ($input.total < 0) error("Total cannot be negative")
if ($input.currency == null) error("Currency is required")

// Use in a validation pipeline:
let amount = toNumber($input.amount)
if (amount > 1000000) error(concat("Amount exceeds limit: ", toString(amount)))

// Combine with try/catch in the caller:
try {
  if ($input.type == "UNKNOWN") error("Unknown order type")
  // ... process order
} catch {
  {error: true, message: "Processing failed"}
}
```

=== escapeXML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== every #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== everyEntry #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== excC14n #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== excC14nWithComments #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== exp #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== extractBetween #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== extractCDATA #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== extractTimestampFromUuidV7 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== F

=== filter(array, predicate) → array #text(size: 8pt, fill: gray)[(Arr)]

Keep elements that match a predicate. Always returns an array (even if 0 or 1 match).

- `array` (required): the array to filter
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '[{"name":"Alice","active":true},{"name":"Bob","active":false}]' \
  | utlx -e 'filter(., (u) -> u.active)'
# [{"name": "Alice", "active": true}]
```

```utlx
// Given: {"products": [
//   {"name": "Widget", "price": 25, "active": true},
//   {"name": "Gadget", "price": 150, "active": true},
//   {"name": "Gizmo", "price": 10, "active": false}
// ]}

filter($input.products, (p) -> p.active)
// Output: [{"name": "Widget", ...}, {"name": "Gadget", ...}]

filter($input.products, (p) -> p.price > 100 && p.active)
// Output: [{"name": "Gadget", "price": 150, "active": true}]

filter($input.products, (p) -> p.price > 1000)
// Output: [] (empty array — no matches, NOT null)
```

*Anti-pattern:* `$input.products[price > 10]` — bracket predicate syntax does NOT work in UTL-X. Always use `filter()`. See Chapter 8.

*Anti-pattern:* `filter()` when you want ONE result — use `find()` instead (returns the element, not an array).

=== filterEntries(object, predicate) → object #text(size: 8pt, fill: gray)[(Obj)]

Filter object properties by key and/or value. Returns a new object with only matching entries.

- `object` (required): the object to filter
- `predicate` (required): lambda `(key, value) -> boolean`

```utlx
// Given: {"name": "Alice", "email": "alice@example.com", "password": "secret", "temp": null}

filterEntries($input, (key, value) -> value != null)
// Output: {"name": "Alice", "email": "alice@example.com", "password": "secret"}

filterEntries($input, (key, value) -> key != "password" && key != "temp")
// Output: {"name": "Alice", "email": "alice@example.com"}
```

Also: `someEntry(obj, pred)` → true if any entry matches, `everyEntry(obj, pred)` → true if all match, `countEntries(obj, pred)` → count of matching entries.

=== find(array, predicate) → element or null / findIndex(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

`find`: returns the FIRST matching element, or `null`. `findIndex`: returns its index, or `-1`.

- `array` (required): the array to search
- `predicate` (required): lambda `(element) -> boolean`

```utlx
// Given: {"users": [
//   {"id": 1, "email": "alice@example.com"},
//   {"id": 2, "email": "bob@example.com"},
//   {"id": 3, "email": "alice@example.com"}
// ]}

find($input.users, (u) -> u.email == "bob@example.com")
// Output: {"id": 2, "email": "bob@example.com"}  (the object, NOT an array)

find($input.users, (u) -> u.email == "unknown@example.com")
// Output: null

findIndex($input.users, (u) -> u.id == 2)
// Output: 1 (zero-based index)

findIndex($input.users, (u) -> u.id == 99)
// Output: -1 (not found)
```

*Anti-pattern:* `filter($input.users, ...)[0]` — use `find()`. It's cleaner and returns `null` instead of an index-out-of-bounds error on empty results.

Also: `findLastIndex(array, predicate)` — searches from the end.

=== findAllMatches #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== findLastIndex #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== first(array) → element or null #text(size: 8pt, fill: gray)[(Arr)]

Returns the first element of an array, or `null` if the array is empty.

- `array` (required): the source array

```utlx
first(["Apple", "Banana", "Cherry"])     // "Apple"
first([42])                              // 42
first([])                                // null

// Use case: get the cheapest product
first(sortBy($input.products, (p) -> p.price))
```

=== formatCurrency #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== formatDateTimeInTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== formatEmptyElements #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== formatNumber #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== formatPlural #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromBase64 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromBytes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromCamelCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromCharCode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromConstantCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromDotCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromHex #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromKebabCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromPascalCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromPathCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromSnakeCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromTitleCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fromUTC #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== fullOuterJoin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== futureValue #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== head(array) → element or null #text(size: 8pt, fill: gray)[(Arr)]

Alias for `first()`. Returns the first element of an array, or `null` if empty.

- `array` (required): the source array

```utlx
head(["Apple", "Banana", "Cherry"])      // "Apple"
head([])                                 // null
```

=== last(array) → element or null #text(size: 8pt, fill: gray)[(Arr)]

Returns the last element of an array, or `null` if the array is empty.

- `array` (required): the source array

```utlx
last(["Apple", "Banana", "Cherry"])      // "Cherry"
last([42])                               // 42
last([])                                 // null

// Use case: get the most recent event
last(sortBy($input.events, (e) -> e.timestamp))
```

=== tail(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Returns everything EXCEPT the first element. Returns an empty array if the input has 0 or 1 elements.

- `array` (required): the source array

```utlx
tail(["Apple", "Banana", "Cherry"])      // ["Banana", "Cherry"]
tail(["Apple"])                          // []
tail([])                                 // []

// Use case: skip the header row in a headerless CSV
let dataRows = tail($input)             // all rows except the first
```

=== flatMap(array, fn) → array #text(size: 8pt, fill: gray)[(Arr)]

Map each element to an array, then flatten one level. Equivalent to `flatten(map(...))`. Use when each element produces multiple results.

- `array` (required): the array to process
- `fn` (required): lambda `(element) -> array`

```bash
echo '{"orders": [{"lines": [1,2]}, {"lines": [3]}]}' \
  | utlx -e 'flatMap(.orders, (o) -> o.lines)'
# [1, 2, 3]
```

```utlx
// Given: orders with nested line items
flatMap($input.orders, (o) -> o.lines)
// All lines from all orders in one flat array

// Equivalent to:
flatten(map($input.orders, (o) -> o.lines))
```

=== flatten(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove one level of array nesting. Each element that is an array is unwrapped; non-array elements are kept as-is.

- `array` (required): the nested array to flatten

```utlx
flatten([[1, 2], [3, 4], [5]])
// Output: [1, 2, 3, 4, 5]

flatten([[1, 2], 3, [4, 5]])
// Output: [1, 2, 3, 4, 5]

flatten([[[1, 2]], [[3]]])
// Output: [[1, 2], [3]]  (only ONE level removed)
```

=== flattenDeep(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove ALL levels of array nesting, recursively. Produces a completely flat array.

- `array` (required): the deeply nested array to flatten

```utlx
flattenDeep([[1, [2, [3, [4]]]]])
// Output: [1, 2, 3, 4]

flattenDeep([[[["deep"]]]])
// Output: ["deep"]

flattenDeep([1, 2, 3])
// Output: [1, 2, 3]  (already flat — no change)
```

=== formatDate(date, pattern) → string #text(size: 8pt, fill: gray)[(Date)]

Format a date or datetime as a string using a pattern.

- `date` (required): date or datetime value
- `pattern` (required): format pattern string

Pattern tokens: `yyyy` (year), `MM` (month 01-12), `dd` (day 01-31), `HH` (hour 00-23), `mm` (minute 00-59), `ss` (second 00-59), `EEEE` (day name), `MMMM` (month name), `EEE` (short day), `MMM` (short month).

```utlx
// Given: {"timestamp": "2026-05-01T14:30:00Z"}
let dt = parseDate($input.timestamp, "yyyy-MM-dd'T'HH:mm:ss'Z'")

formatDate(dt, "yyyy-MM-dd")             // "2026-05-01"
formatDate(dt, "dd/MM/yyyy")             // "01/05/2026"
formatDate(dt, "dd-MM-yyyy HH:mm")       // "01-05-2026 14:30"
formatDate(dt, "EEEE, MMMM d, yyyy")     // "Thursday, May 1, 2026"
formatDate(dt, "yyyy-MM-dd'T'HH:mm:ssXXX")  // ISO 8601 with timezone

// Use case: Peppol invoice date (must be yyyy-MM-dd)
formatDate(now(), "yyyy-MM-dd")
```

== G

=== generateIV #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== generateKey #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== generateUuid() → string / generateUuidV4() → string / generateUuidV7() → string #text(size: 8pt, fill: gray)[(Sys)]

Generate universally unique identifiers. v4 is random, v7 is time-ordered (sortable).

No parameters.

```utlx
generateUuid()        // "550e8400-e29b-41d4-a716-446655440000" (v4 random)
generateUuidV4()      // same as generateUuid()
generateUuidV7()      // "018f6c30-a2b0-7000-8000-000000000001" (time-ordered)

// Use case: generate correlation IDs for messages
{
  messageId: generateUuidV7(),
  timestamp: now(),
  payload: $input
}

// v7 is sortable by creation time — useful for database primary keys
generateUuidV7Batch(5)  // generate 5 sequential v7 UUIDs
```

Also: `isValidUuid(string)`, `getUuidVersion(string)`, `isUuidV7(string)`.

=== generateUuidV7Batch #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== get #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getBaseURL #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getBOMBytes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getCurrencyDecimals #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getFragment #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getHost #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSAlgorithm #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSHeader #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSInfo #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSKeyId #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSPayload #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSSigningInput #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWSTokenType #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWTAudience #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWTClaim #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWTClaims #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWTIssuer #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getJWTSubject #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getLogs #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getNamespaces(element) → object #text(size: 8pt, fill: gray)[(XML)]

Get all namespace declarations from an XML element as a prefix-to-URI map. See Chapter 22.

- `element` (required): XML UDM element

```utlx
// Given: <Invoice xmlns:cbc="urn:oasis:...:CommonBasicComponents-2"
//                  xmlns:cac="urn:oasis:...:CommonAggregateComponents-2">

getNamespaces($input.Invoice)
// Output: {
//   "cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
//   "cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
// }

// Use case: check which namespaces a document uses
let ns = getNamespaces($input)
hasKey(ns, "soap")   // true if SOAP namespace declared
```

=== getPath #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getPort #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getProtocol #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getQuery #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getQueryParams #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getTimezoneName #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getTimezoneOffsetHours #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getTimezoneOffsetSeconds #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getType #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== getUuidVersion #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== goldenRatio #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== groupBy(array, keyFn) → object #text(size: 8pt, fill: gray)[(Arr)]

Group array elements by a computed key. Returns an object where keys are the group values and values are arrays of matching elements.

- `array` (required): the array to group
- `keyFn` (required): lambda `(element) -> groupKey`

```utlx
// Given: {"employees": [
//   {"name": "Alice", "department": "Engineering"},
//   {"name": "Bob", "department": "Sales"},
//   {"name": "Charlie", "department": "Engineering"},
//   {"name": "Diana", "department": "Sales"},
//   {"name": "Eve", "department": "Engineering"}
// ]}

groupBy($input.employees, (e) -> e.department)
// Output: {
//   "Engineering": [{"name": "Alice", ...}, {"name": "Charlie", ...}, {"name": "Eve", ...}],
//   "Sales": [{"name": "Bob", ...}, {"name": "Diana", ...}]
// }

// Use case: aggregate per group
let groups = groupBy($input.orders, (o) -> o.status)
entries(groups) |> map((entry) -> {
  status: entry[0],
  count: count(entry[1]),
  total: sum(map(entry[1], (o) -> o.amount))
})
```

=== gunzip #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== gzip #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== H

=== hasAlpha #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasAttribute #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasContent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasEnv #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasKey(object, key) → boolean / containsKey(object, key) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Check if an object has a property. `containsKey` is an alias.

- `object` (required): the object to check
- `key` (required): property name as string

```utlx
// Given: {"name": "Alice", "email": "alice@example.com"}

hasKey($input, "email")                  // true
hasKey($input, "phone")                  // false

// Use case: conditional processing based on field presence
if (hasKey($input, "shippingAddress")) {
  address: $input.shippingAddress
} else {
  address: $input.billingAddress
}
```

Also: `containsValue(object, value)` — check if any property has a specific value.

=== hash(data, algorithm?) → string / md5(data) → string / sha256(data) → string / sha512(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Cryptographic hash functions. Return hex-encoded digest string.

- `data` (required): string to hash
- `algorithm` (optional for `hash`, default `"SHA-256"`): algorithm name (`"MD5"`, `"SHA-1"`, `"SHA-256"`, `"SHA-384"`, `"SHA-512"`, `"SHA3-256"`, `"SHA3-512"`)

```utlx
sha256("hello")
// Output: "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

md5("hello")
// Output: "5d41402abc4b2a76b9719d911017c592"

sha512("hello")
// Output: "9b71d224bd62f3785d96d46ad3ea3d73..."

// Generic hash with explicit algorithm:
hash("hello", "SHA3-256")
// Output: "3338be694f50c5f338814986cdf0686453a888b84f424d792af4b9202398f392"

// Use case: content-addressed caching
let contentHash = sha256(renderJson($input))
{...$input, hash: contentHash}
```

*Anti-pattern:* `md5()` for security — MD5 is cryptographically broken. Use `sha256()` minimum. MD5 is acceptable only for non-security checksums (file deduplication, cache keys).

Also: `sha1(data)`, `sha224(data)`, `sha384(data)`, `sha3_256(data)`, `sha3_512(data)`.

=== hasNamespace #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasNumeric #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hexDecode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hexEncode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmac(data, key, algorithm) → string / hmacSHA256(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

HMAC (Hash-based Message Authentication Code) for verifying message integrity and authenticity.

- `data` (required): the message to authenticate
- `key` (required): the secret key
- `algorithm` (required for `hmac`): hash algorithm

```utlx
hmacSHA256("message-to-verify", "my-secret-key")
// Output: "4a8f3d..." (HMAC-SHA256 hex string)

hmac("message", "key", "SHA-512")
// Output: "..." (HMAC-SHA512)

// Use case: verify webhook signature
let expectedSig = hmacSHA256($input.body, env("WEBHOOK_SECRET"))
if (expectedSig != $input.headers.signature) error("Invalid signature")
```

Also: `hmacSHA512(data, key)`, `hmacSHA1(data, key)`, `hmacMD5(data, key)`, `hmacBase64(data, key, algorithm)` (returns Base64 instead of hex).

=== hmacBase64 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmacMD5 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmacSHA1 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmacSHA384 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmacSHA512 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== homeDir #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hours #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== I

=== ifThenElse #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== implies #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== includes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== indexOf(haystack, needle) → number #text(size: 8pt, fill: gray)[(Str/Arr)]

Find the position of the FIRST occurrence of a value. Returns -1 if not found. Works for both strings and arrays.

- `haystack` (required): string or array to search in
- `needle` (required): value to find

```utlx
indexOf("hello world", "world")          // 6
indexOf("hello world", "xyz")            // -1 (not found)
indexOf(["Apple", "Banana", "Cherry"], "Banana")  // 1
indexOf(["Apple", "Banana", "Cherry"], "Grape")   // -1
```

=== inflate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== info #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== insertAfter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== insertBefore #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== invert #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== iqr #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isAfter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isAlpha #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isAlphanumeric #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isAscii #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isBefore #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isBetween #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isCanonicalJSON #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isCDATA #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isDebugMode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isEmptyElement #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isGzipped #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isHexadecimal #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isJarFile #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isJWSFormat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isJWTExpired #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isLeapYearFunc #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isLocalDateTime #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isLowerCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isNotEmpty #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isNumeric #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isPlural #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isPointInCircle #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isPointInPolygon #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isPrintable #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isSameDay #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isSingular #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isToday #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isUpperCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isUuidV7 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidAmount #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidCoordinates #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidCurrency #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidURL #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isValidUuid #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isWhitespace #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== isZipArchive #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== lastIndexOf(haystack, needle) → number #text(size: 8pt, fill: gray)[(Str/Arr)]

Find the position of the LAST occurrence of a value. Returns -1 if not found.

- `haystack` (required): string or array to search in
- `needle` (required): value to find

```utlx
lastIndexOf("abcabc", "bc")              // 4 (last occurrence, not first at 1)
lastIndexOf(["A", "B", "A", "C"], "A")   // 2 (last A)
lastIndexOf("hello", "xyz")              // -1
```

Also: `findIndex(array, predicate)` — find by condition instead of value. `findLastIndex(array, predicate)` — last match by condition.

=== isArray(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is an array.

- `value` (required): the value to test

```utlx
isArray([1, 2, 3])                       // true
isArray("hello")                         // false
isArray($input.items)                    // true if items is an array

// Use case: handle XML single-vs-array ambiguity
let items = if (isArray($input.Item)) $input.Item else [$input.Item]
// Ensures items is always an array, even when XML has a single <Item>
```

=== isBoolean(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a boolean (`true` or `false`).

- `value` (required): the value to test

```utlx
isBoolean(true)                          // true
isBoolean(false)                         // true
isBoolean("true")                        // false (string, not boolean)
isBoolean(1)                             // false (number, not boolean)
```

=== isNull(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is null.

- `value` (required): the value to test

```utlx
isNull(null)                             // true
isNull("")                               // false (empty string is not null)
isNull(0)                                // false (zero is not null)
isNull($input.optionalField)             // true if field is missing or null
```

=== isNumber(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a number (integer or decimal).

- `value` (required): the value to test

```utlx
isNumber(42)                             // true
isNumber(3.14)                           // true
isNumber("42")                           // false (string, not number)
isNumber(true)                           // false
```

=== isObject(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is an object (key-value map).

- `value` (required): the value to test

```utlx
isObject({name: "Alice"})                // true
isObject([1, 2, 3])                      // false (array, not object)
isObject("hello")                        // false
isObject($input)                         // true (root is typically an object)
```

=== isString(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a string.

- `value` (required): the value to test

```utlx
isString("hello")                        // true
isString(42)                             // false
isString(null)                           // false
```

=== isDate(value) → boolean / isDateTime(value) → boolean / isTime(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a date, datetime, or time respectively.

- `value` (required): the value to test

```utlx
isDate(parseDate("2026-05-01", "yyyy-MM-dd"))       // true
isDateTime(now())                                     // true
isDate("2026-05-01")                                  // false (string, not date)
```

=== isBlank(value) → boolean / isEmpty(value) → boolean / isDefined(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Additional type predicates.

- `value` (required): the value to test

```utlx
isBlank(null)                            // true
isBlank("")                              // true
isBlank("  ")                            // true (whitespace-only)
isBlank("hello")                         // false

isEmpty(null)                            // true
isEmpty("")                              // true
isEmpty([])                              // true (empty array)
isEmpty("hello")                         // false
isEmpty([1])                             // false

isDefined(null)                          // false
isDefined("")                            // true (empty string IS defined)
isDefined(0)                             // true (zero IS defined)
isDefined($input.name)                   // true if field exists and is not null
```

=== isLeapYear(year) → boolean / isWeekday(date) → boolean / isWeekend(date) → boolean #text(size: 8pt, fill: gray)[(Date)]

Date predicates.

- `year` (required for isLeapYear): year number
- `date` (required for isWeekday/isWeekend): date or datetime

```utlx
isLeapYear(2024)                         // true (divisible by 4, not by 100, or by 400)
isLeapYear(2026)                         // false

isWeekday(parseDate("2026-05-01", "yyyy-MM-dd"))  // true (Thursday)
isWeekend(parseDate("2026-05-03", "yyyy-MM-dd"))  // true (Sunday)

// Use case: calculate business days
let workDays = filter(
  map(range(0, 30), (i) -> addDays(startDate, i)),
  (d) -> isWeekday(d)
)

isToday(parseDate("2026-05-01", "yyyy-MM-dd"))  // true/false
isSameDay(date1, date2)                          // true if same calendar day
```

== J

=== javaVersion #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== jcs(json) → string / canonicalizeJSON(json) → string / jsonEquals(json1, json2) → boolean #text(size: 8pt, fill: gray)[(JSON)]

JSON Canonicalization Scheme (RFC 8785). Produces deterministic JSON — identical output regardless of key order or whitespace. See Chapter 24.

- `json` (required): JSON UDM value to canonicalize
- `json1`, `json2` (required for jsonEquals): two values to compare

```utlx
jcs({z: 3, a: 1, m: 2})
// Output: '{"a":1,"m":2,"z":3}'  (keys sorted, no whitespace)

canonicalizeJSON($input)    // alias for jcs()

jsonEquals({b: 2, a: 1}, {a: 1, b: 2})
// Output: true (same content, different key order)

// Use case: detect changes between two API responses
if (!jsonEquals(previousResponse, currentResponse)) {
  changed: true,
  hash: canonicalJSONHash(currentResponse)
}
```

Also: `canonicalJSONHash(json, algorithm?)` (hash the canonical form), `canonicalJSONSize(json)` (byte size), `isCanonicalJSON(string)`.

=== join(array, separator) → string #text(size: 8pt, fill: gray)[(Str)]

Join array elements into a single string with a separator. This is the *string* join — for data restructuring (nesting children under parents), see `nestBy()` in Chapter 21.

- `array` (required): array of values to join (non-strings are converted automatically)
- `separator` (required): string to insert between elements

```utlx
// Given: {"tags": ["urgent", "billing", "review"]}

join($input.tags, ", ")
// Output: "urgent, billing, review"

join($input.tags, " | ")
// Output: "urgent | billing | review"

// Use case: build a CSV line manually
join([$input.name, toString($input.age), $input.city], ";")
// Output: "Alice;30;Amsterdam"

// Use case: build a path
join(["usr", "local", "bin"], "/")
// Output: "usr/local/bin"
```

*Anti-pattern:* `reduce(arr, "", (acc, x) -> concat(acc, x, ", "))` — creates N intermediate strings. `join()` builds the result in one pass.

=== joinToString #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== joinWith #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== K

=== kebabCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== keys(object) → array / values(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Get all property names (keys) or all property values from an object. Key order is preserved (insertion order).

- `object` (required): the object to inspect

```utlx
// Given: {"name": "Alice", "age": 30, "city": "Amsterdam"}

keys($input)
// Output: ["name", "age", "city"]

values($input)
// Output: ["Alice", 30, "Amsterdam"]

// Use case: iterate over dynamic keys
map(keys($input.servers), (env) -> {
  environment: env,
  host: $input.servers[env].host
})

// Use case: check what fields are present
let fields = keys($input)
contains(fields, "email")   // true/false
```

== L

=== leftJoin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== length(value) → number #text(size: 8pt, fill: gray)[(Str/Arr)]

Length of a string (character count) or array (element count). Alias for `count()` on arrays.

- `value` (required): string or array

```utlx
length("hello")                          // 5
length("日本語")                          // 3 (Unicode characters, not bytes)
length([1, 2, 3])                        // 3
length("")                               // 0
length([])                               // 0
```

=== listJarEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== listZipEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== ln #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== localName(element) → string / namespaceUri(element) → string / namespacePrefix(element) → string / qualifiedName(element) → string #text(size: 8pt, fill: gray)[(XML)]

QName decomposition — extract parts of an XML qualified name. See Chapter 22 for full details and XBRL examples.

- `element` (required): XML UDM element

```utlx
// Given: <cbc:InvoiceTypeCode xmlns:cbc="urn:oasis:...">380</cbc:InvoiceTypeCode>

localName($input)                        // "InvoiceTypeCode"
namespacePrefix($input)                  // "cbc"
qualifiedName($input)                    // "cbc:InvoiceTypeCode"
namespaceUri($input)                     // "urn:oasis:names:specification:ubl:..."

// Use case: filter XML elements by namespace (XBRL taxonomy)
let usgaapFacts = filter($input.*, (elem) ->
  namespaceUri(elem) == "http://fasb.org/us-gaap/2024"
)
```

Also: `resolveQname(string, context)`, `matchesQname(element, pattern)`, `hasNamespace(element)`.

=== log #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== log10 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== log2 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== logCount #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== logPretty #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== logSize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== logType #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== lookupBy #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== lower #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== lowerCase(string) → string / upperCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Full case conversion. Also: `camelCase`, `snakeCase`, `kebabCase`, `pascalCase`, `titleCase`, `dotCase`, `pathCase`, `constantCase`, `slugify`.

- `string` (required): the string to convert

```utlx
lowerCase("Hello World")                 // "hello world"
upperCase("Hello World")                 // "HELLO WORLD"
camelCase("hello world")                 // "helloWorld"
snakeCase("helloWorld")                  // "hello_world"
kebabCase("helloWorld")                  // "hello-world"
pascalCase("hello world")               // "HelloWorld"
titleCase("hello world")                // "Hello World"
constantCase("helloWorld")              // "HELLO_WORLD"
slugify("Hello World! 123")             // "hello-world-123"

// Use case: normalize API field names
mapKeys($input, (key) -> camelCase(key))
// {"first_name": "Alice"} → {"firstName": "Alice"}
```

== M

=== map(array, fn) → array #text(size: 8pt, fill: gray)[(Arr)]

Transform every element of an array. The most-used function in UTL-X. Returns a new array of the same length.

- `array` (required): the array to transform
- `fn` (required): lambda `(element) -> newValue` or `(element, index) -> newValue`

```bash
echo '[1, 2, 3]' | utlx -e 'map(., (x) -> x * 2)'
# [2, 4, 6]
```

```utlx
// Given: {"items": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 50}]}

map($input.items, (item) -> {
  product: item.name,
  priceWithTax: item.price * 1.21
})
// Output: [{"product": "Widget", "priceWithTax": 30.25}, {"product": "Gadget", "priceWithTax": 60.50}]

// With index (second parameter):
map($input.items, (item, index) -> {
  lineNumber: index + 1,
  product: item.name
})
// Output: [{"lineNumber": 1, "product": "Widget"}, {"lineNumber": 2, "product": "Gadget"}]

// Simple transformation:
map([1, 2, 3], (x) -> x * 2)
// Output: [2, 4, 6]
```

*Anti-pattern:* using `map()` to filter — `map(arr, (x) -> if (x.active) x else null)` produces nulls. Use `filter()` to remove, then `map()` to transform.

=== mapEntries(object, fn) → object / mapKeys(object, fn) → object / mapValues(object, fn) → object #text(size: 8pt, fill: gray)[(Obj)]

Transform object keys and/or values. See Chapter 26 (dynamic keys).

- `object` (required): the object to transform
- `fn` (required): lambda — `(key, value) -> {key: newKey, value: newValue}` for mapEntries, `(key) -> newKey` for mapKeys, `(value) -> newValue` for mapValues

```utlx
// Given: {"first_name": "Alice", "last_name": "Johnson", "age": 30}

// Transform keys only (snake_case to camelCase):
mapKeys($input, (key) -> camelCase(key))
// Output: {"firstName": "Alice", "lastName": "Johnson", "age": 30}

// Transform values only (stringify everything):
mapValues($input, (value) -> toString(value))
// Output: {"first_name": "Alice", "last_name": "Johnson", "age": "30"}

// Transform both:
mapEntries($input, (key, value) -> {
  key: upperCase(key),
  value: if (isString(value)) upperCase(value) else value
})
// Output: {"FIRST_NAME": "ALICE", "LAST_NAME": "JOHNSON", "AGE": 30}
```

=== mapGroups #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== mapTree #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== mask(string, visibleChars) → string #text(size: 8pt, fill: gray)[(Sec)]

Mask a string, keeping only the first N characters visible. For PII protection in logs and reports. See Chapter 38.

- `string` (required): the value to mask
- `visibleChars` (required): number of characters to leave visible

```utlx
mask("Alice Johnson", 3)                 // "Ali***"
mask("4111111111111111", 4)              // "4111************"
mask("alice@example.com", 5)             // "alice***********"
mask("NL123456789B01", 2)                // "NL************"

// Use case: audit-safe output
{
  customerName: mask($input.name, 3),
  email: sha256($input.email),           // irreversible hash
  orderId: $input.orderId                // non-sensitive, keep as-is
}
```

=== matches(string, regex) → boolean #text(size: 8pt, fill: gray)[(Str)]

Test if a string matches a regular expression (full match).

- `string` (required): the string to test
- `regex` (required): regular expression pattern

```utlx
matches("ORD-001", "^ORD-[0-9]+$")              // true
matches("INV-001", "^ORD-[0-9]+$")              // false
matches("alice@example.com", "^[^@]+@[^@]+\\.[^@]+$")  // true

// Use case: validate field formats
if (!matches($input.vatId, "^[A-Z]{2}[0-9]{9}B[0-9]{2}$"))
  error("Invalid Dutch VAT ID format")
```

Also: `matchesQname(element, pattern)` for XML QName matching (Chapter 22).

=== matchesWhole #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== max(array) → number / min(array) → number / maxBy(array, fn) → element / minBy(array, fn) → element #text(size: 8pt, fill: gray)[(Num/Arr)]

Find extremes. `max`/`min` work on numeric arrays. `maxBy`/`minBy` take a key extractor and return the entire element (not just the value).

- `array` (required): array to search
- `fn` (required for maxBy/minBy): lambda `(element) -> comparable`

```utlx
max([3, 1, 4, 1, 5, 9])                 // 9
min([3, 1, 4, 1, 5, 9])                 // 1

// Given: {"products": [
//   {"name": "Widget", "price": 25},
//   {"name": "Gadget", "price": 150},
//   {"name": "Gizmo", "price": 10}
// ]}

maxBy($input.products, (p) -> p.price)
// Output: {"name": "Gadget", "price": 150}  (the ENTIRE object, not just 150)

minBy($input.products, (p) -> p.price)
// Output: {"name": "Gizmo", "price": 10}
```

=== measure #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== median(array) → number / mode(array) → number / stdDev(array) → number / variance(array) → number / percentile(array, p) → number #text(size: 8pt, fill: gray)[(Num)]

Statistical functions.

- `array` (required): array of numbers
- `p` (required for percentile): percentile value 0-100

```utlx
// Given: {"scores": [72, 85, 90, 95, 88, 76, 92]}

median($input.scores)                    // 88 (middle value)
mode([1, 2, 2, 3, 3, 3])                // 3 (most frequent)
stdDev($input.scores)                    // ~8.5 (standard deviation)
variance($input.scores)                  // ~72.2
percentile($input.scores, 90)            // ~94.2 (90th percentile)

// Also: iqr(array) — interquartile range, quartiles(array) — [Q1, Q2, Q3]
```

=== memoryInfo #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== merge(obj1, obj2, ...) → object #text(size: 8pt, fill: gray)[(Obj)]

Shallow merge of objects. Later arguments override earlier ones. Same as spread but as a function.

- `obj1, obj2, ...` (variadic): objects to merge

```utlx
merge({a: 1, b: 2}, {b: 3, c: 4})
// Output: {a: 1, b: 3, c: 4}  (b overridden by second)

merge({a: 1}, {b: 2}, {c: 3})
// Output: {a: 1, b: 2, c: 3}
```

*Note:* for deep (recursive) merge, use `deepMerge(obj1, obj2)`.

=== midpoint #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== minutes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== month #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== monthName #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== N

=== nand #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== nestBy #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== nodeType #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== none #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== nor #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== normalizeBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== normalizeXMLEncoding #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== not #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== now() → datetime / currentDate() → date / currentTime() → time #text(size: 8pt, fill: gray)[(Date)]

Current date and time. No parameters.

```utlx
now()                                    // 2026-05-01T14:30:00Z (UTC datetime)
currentDate()                            // 2026-05-01 (date only, no time)
currentTime()                            // 14:30:00 (time only, no date)

// Use case: add timestamp to output
{
  ...$input,
  processedAt: formatDate(now(), "yyyy-MM-dd'T'HH:mm:ss'Z'"),
  processedDate: formatDate(currentDate(), "yyyy-MM-dd")
}
```

=== normalizeSpace(string) → string #text(size: 8pt, fill: gray)[(Str)]

Collapse all whitespace sequences (spaces, tabs, newlines) to single spaces. Trim leading/trailing.

- `string` (required): the string to normalize

```utlx
normalizeSpace("  hello   world  ")      // "hello world"
normalizeSpace("line1\n  line2\t\tline3") // "line1 line2 line3"
normalizeSpace("")                        // ""
```

=== numberOrDefault #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== O

=== omit(object, keys) → object / pick(object, keys) → object #text(size: 8pt, fill: gray)[(Obj)]

`omit`: return object WITHOUT the listed properties. `pick`: return object WITH ONLY the listed properties.

- `object` (required): the source object
- `keys` (required): array of property names to omit or keep

```utlx
// Given: {"name": "Alice", "email": "alice@example.com", "password": "secret", "role": "admin"}

pick($input, ["name", "email"])
// Output: {"name": "Alice", "email": "alice@example.com"}

omit($input, ["password"])
// Output: {"name": "Alice", "email": "alice@example.com", "role": "admin"}

// Use case: strip sensitive fields before logging
let safe = omit($input, ["password", "apiKey", "token", "secret"])
```

=== osArch #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== osVersion #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== M

=== map(array, fn) #text(size: 8pt, fill: gray)[(Arr)]

Transform each element. The most-used function in UTL-X.

```utlx
map([1, 2, 3], (x) -> x * 2)            // [2, 4, 6]
map($input.orders, (order) -> {
  id: order.orderId,
  total: order.amount
})
```

*Anti-pattern:* `map()` to null-ify elements — use `filter()` to remove, not `map()` to null.

=== mapEntries(object, fn) / mapKeys(object, fn) / mapValues(object, fn) #text(size: 8pt, fill: gray)[(Obj)]

Transform object keys and/or values. See Chapter 26.

```utlx
mapKeys({name: "Alice"}, (k) -> toUpperCase(k))     // {NAME: "Alice"}
mapValues({a: 1, b: 2}, (v) -> v * 10)              // {a: 10, b: 20}
```

=== mask(string, visibleChars) #text(size: 8pt, fill: gray)[(Sec)]

Mask sensitive data.

```utlx
mask("Alice Johnson", 3)                // "Ali***"
```

=== matches(string, regex) / matchesQname(element, pattern) #text(size: 8pt, fill: gray)[(Str/XML)]

Regex match / QName match.

```utlx
matches("ORD-001", "^ORD-[0-9]+$")      // true
matchesQname($input.Invoice, "cbc:Invoice")  // true
```

=== max(array) / min(array) / maxBy(array, fn) / minBy(array, fn) #text(size: 8pt, fill: gray)[(Num/Arr)]

Extremes.

```utlx
max([3, 1, 4, 1, 5])                    // 5
maxBy($input.products, (p) -> p.price)   // most expensive product
```

=== median(array) / mode(array) / stdDev(array) / variance(array) / percentile(array, p) #text(size: 8pt, fill: gray)[(Num)]

Statistical functions.

```utlx
median([1, 3, 5, 7, 9])                 // 5
mode([1, 2, 2, 3, 3, 3])                // 3
stdDev([2, 4, 4, 4, 5, 5, 7, 9])        // 2.0
percentile([1, 2, 3, 4, 5], 90)          // 4.6
```

=== merge(obj1, obj2, ...) / deepMerge(obj1, obj2) #text(size: 8pt, fill: gray)[(Obj)]

Merge objects (shallow / deep).

```utlx
merge({a: 1}, {b: 2})                   // {a: 1, b: 2}
deepMerge({nested: {x: 1}}, {nested: {y: 2}})  // {nested: {x: 1, y: 2}}
```

== N

=== now() / currentDate() / currentTime() #text(size: 8pt, fill: gray)[(Date)]

```utlx
now()                                    // current datetime (UTC)
currentDate()                            // date only
currentTime()                            // time only
```

=== normalizeSpace(string) #text(size: 8pt, fill: gray)[(Str)]

Collapse whitespace.

```utlx
normalizeSpace("  hello   world  ")      // "hello world"
```

== O

=== omit(object, keys) / pick(object, keys) #text(size: 8pt, fill: gray)[(Obj)]

Select or exclude properties.

```utlx
pick($input, ["name", "email"])          // keep only name and email
omit($input, ["password", "secret"])     // remove sensitive fields
```

== P

=== pad #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== padLeft #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== padRight #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parse(string, format?) → value / parseJson(string) → value / parseXml(string) → value / parseYaml(string) → value / parseCsv(string, options?) → array #text(size: 8pt, fill: gray)[(Fmt)]

Parse a string embedded within a transformation into a navigable UDM value. Use when a format is a VALUE inside your data (JSON string in a CSV column, XML in a CDATA section).

- `string` (required): the string to parse
- `format` (optional for `parse`, default auto-detect): `"json"`, `"xml"`, `"yaml"`, `"csv"`
- `options` (optional for `parseCsv`): `{headers: false}`, `{delimiter: ";"}`

```utlx
// Parse JSON embedded in another format:
let config = parseJson($input.configJson)
config.database.host                     // "localhost"

// Parse XML from a CDATA section:
let innerXml = parse($input.Payload, "xml")
innerXml.Order.Customer                  // "Acme Corp"

// Parse CSV with options:
let data = parseCsv($input.csvData, {delimiter: ";", headers: true})
data[0].Name                             // first row, Name column

// Auto-detect format:
let parsed = parse($input.rawData)       // auto-detects JSON, XML, or YAML
```

For normal file processing, use `input json`/`input xml` in the header — these functions are for the embedded-format-as-value case.

Also: `render(value, format, pretty?)`, `renderJson(value, pretty?)`, `renderXml(value)`, `renderYaml(value)`, `renderCsv(value)`.

=== parseBoolean #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseCurrency #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseDate(string, pattern) → date #text(size: 8pt, fill: gray)[(Date)]

Parse a date or datetime string using a format pattern. See `formatDate` for pattern tokens.

- `string` (required): the date string to parse
- `pattern` (required): format pattern

```utlx
parseDate("2026-05-01", "yyyy-MM-dd")
// Output: date value (May 1, 2026)

parseDate("01/05/2026", "dd/MM/yyyy")
// Output: date value (May 1, 2026)

parseDate("2026-05-01T14:30:00Z", "yyyy-MM-dd'T'HH:mm:ss'Z'")
// Output: datetime value

parseDate("May 1, 2026", "MMMM d, yyyy")
// Output: date value

// Use case: normalize different date formats to ISO
formatDate(parseDate($input.date, "dd/MM/yyyy"), "yyyy-MM-dd")
// "15/04/2026" → "2026-04-15"
```

*Anti-pattern:* assuming date format — `01/02/2026` is January 2nd (US `MM/dd/yyyy`) or February 1st (EU `dd/MM/yyyy`). Always specify the pattern explicitly.

Also: `parseDateTimeWithTimezone(string, pattern, timezone)`.

=== parseDateTimeWithTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseDouble #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseEUNumber(string) → number / parseUSNumber(string) → number #text(size: 8pt, fill: gray)[(Num)]

Parse regional number formats to standard floating-point. See Chapter 25 (CSV).

- `string` (required): the formatted number string

```utlx
parseEUNumber("1.234,56")               // 1234.56 (European: dot=thousands, comma=decimal)
parseUSNumber("1,234.56")               // 1234.56 (US: comma=thousands, dot=decimal)
parseFrenchNumber("1 234,56")            // 1234.56 (French: space=thousands, comma=decimal)
parseSwissNumber("1'234.56")             // 1234.56 (Swiss: apostrophe=thousands, dot=decimal)

// Use case: CSV from European source
map($input, (row) -> {
  product: row.Product,
  price: parseEUNumber(row.Price),       // "29,99" → 29.99
  weight: parseEUNumber(row.Weight)      // "1.500,00" → 1500.0
})
```

Also: `renderEUNumber(number)`, `renderUSNumber(number)`, `renderFrenchNumber(number)`, `renderSwissNumber(number)` for the reverse direction.

=== parseFloat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseInt #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseNumber #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseQueryString #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseURL #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== partition(array, predicate) → [matching, nonMatching] #text(size: 8pt, fill: gray)[(Arr)]

Split an array into two: elements that match the predicate, and elements that don't. Returns a 2-element array of arrays.

- `array` (required): the array to partition
- `predicate` (required): lambda `(element) -> boolean`

```utlx
// Given: {"orders": [
//   {"id": 1, "amount": 500},
//   {"id": 2, "amount": 1500},
//   {"id": 3, "amount": 200},
//   {"id": 4, "amount": 3000}
// ]}

let result = partition($input.orders, (o) -> o.amount > 1000)
// result[0] = [{"id": 2, ...}, {"id": 4, ...}]  (matching: amount > 1000)
// result[1] = [{"id": 1, ...}, {"id": 3, ...}]  (non-matching)

// Use case: separate valid from invalid
let validated = partition($input.records, (r) -> r.email != null && r.name != null)
{
  valid: validated[0],
  invalid: validated[1],
  validCount: count(validated[0]),
  invalidCount: count(validated[1])
}
```

=== pascalCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== pathCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== percentageChange #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== pi #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== platform #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== pluralize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== pluralizeWithCount #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== pow #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prepareForSignature #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== presentValue #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrint #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrintCSV #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrintFormat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrintJSON #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrintXML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== prettyPrintYAML #text(size: 8pt, fill: gray)[(TODO)]

// TODO


== Q

=== quarter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== quartiles #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== R

=== random #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readByte #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readDouble #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readFloat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readInt16 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readInt32 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readInt64 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readJarEntry #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readJarManifest #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== readZipEntry #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== reduce(array, initial, accumulator) → value #text(size: 8pt, fill: gray)[(Arr)]

Accumulate a single result by processing each element sequentially. The most powerful array function — and the most error-prone.

- `array` (required): the array to reduce
- `initial` (required): the starting value for the accumulator
- `accumulator` (required): lambda `(accumulated, element) -> newAccumulated`

```utlx
// Sum numbers:
reduce([10, 20, 30], 0, (sum, x) -> sum + x)
// Step by step: 0→10→30→60.  Output: 60

// Build a comma-separated string:
reduce(["Alice", "Bob", "Charlie"], "", (acc, name) ->
  if (acc == "") name else concat(acc, ", ", name)
)
// Output: "Alice, Bob, Charlie"

// Build a lookup object from an array:
// Input: [{"id": "A", "name": "Widget"}, {"id": "B", "name": "Gadget"}]
reduce($input, {}, (acc, item) -> {
  ...acc,
  [item.id]: item.name
})
// Output: {"A": "Widget", "B": "Gadget"}

// Count occurrences:
reduce(["a", "b", "a", "c", "a"], {}, (acc, x) -> {
  ...acc,
  [x]: (acc[x] ?? 0) + 1
})
// Output: {"a": 3, "b": 1, "c": 1}
```

*Anti-pattern:* using `reduce` for operations that have dedicated functions:

```utlx
// BAD — use sum() instead:
reduce(arr, 0, (acc, x) -> acc + x)

// BAD — use max() instead:
reduce(arr, 0, (acc, x) -> if (x > acc) x else acc)

// BAD — use join() instead:
reduce(arr, "", (acc, x) -> concat(acc, x, ","))

// GOOD use of reduce — complex accumulation with no dedicated function:
reduce($input.transactions, {balance: 0, count: 0}, (acc, tx) -> {
  balance: acc.balance + tx.amount,
  count: acc.count + 1
})
```

=== reduceEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== regexGroups #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== regexNamedGroups #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== remove #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== removeBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== removeQueryParam #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== removeTax #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== render(value, format, pretty?) → string / renderJson(value, pretty?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Serialize a UDM value to a string in the specified format.

- `value` (required): UDM value to serialize
- `format` (required for `render`): `"json"`, `"xml"`, `"yaml"`, `"csv"`
- `pretty` (optional, default false): pretty-print with indentation

```utlx
renderJson({name: "Alice", age: 30})
// Output: '{"name":"Alice","age":30}'

renderJson({name: "Alice", age: 30}, true)
// Output: '{\n  "name": "Alice",\n  "age": 30\n}'

render({name: "Alice"}, "json", true)    // same as renderJson with pretty
render({Order: {Id: "1"}}, "xml")        // "<Order><Id>1</Id></Order>"

// Use case: embed XML inside a JSON field (CDATA pattern)
{
  messageId: generateUuid(),
  payload: render($input, "xml")         // XML as a string value
}
```

=== renderCsv #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== renderXml #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== renderYaml #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== repeat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== replace(string, search, replacement) → string / replaceRegex(string, regex, replacement) → string #text(size: 8pt, fill: gray)[(Str)]

Replace occurrences in a string. `replace`: literal match. `replaceRegex`: regex match.

- `string` (required): the string to modify
- `search`/`regex` (required): what to find
- `replacement` (required): what to replace with

```utlx
replace("Hello World", "World", "UTL-X")
// Output: "Hello UTL-X"

replace("2026-05-01", "-", "/")
// Output: "2026/05/01"  (replaces ALL occurrences)

replaceRegex("Order #123 on 2026-05-01", "[0-9]+", "X")
// Output: "Order #X on X-X-X"

replaceRegex("  extra   spaces  ", "\\s+", " ")
// Output: " extra spaces "  (collapse whitespace — use normalizeSpace() instead)
```

=== replaceWithFunction #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== resolveQName #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== reverse(array) → array / reverseString(string) → string #text(size: 8pt, fill: gray)[(Arr/Str)]

Reverse the order of elements in an array or characters in a string.

- `array`/`string` (required): the value to reverse

```utlx
reverse([1, 2, 3, 4, 5])                // [5, 4, 3, 2, 1]
reverseString("hello")                   // "olleh"

// Use case: most recent first
reverse(sortBy($input.events, (e) -> e.timestamp))
```

=== rightJoin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== roundToCents(number) → number / roundToDecimalPlaces(number, places) → number #text(size: 8pt, fill: gray)[(Num)]

Financial rounding.

- `number` (required): the value to round
- `places` (required for roundToDecimalPlaces): number of decimal places

```utlx
roundToCents(29.999)                     // 30.0
roundToCents(10.004)                     // 10.0
roundToDecimalPlaces(3.14159, 3)         // 3.142
roundToDecimalPlaces(100.0, 0)           // 100

// Use case: invoice line total with correct rounding
let lineTotal = roundToCents(qty * unitPrice)
```

=== runtimeInfo #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== S

=== scan #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== seconds #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== setConsoleLogging #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== setLogLevel #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== setPath #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sha224 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sha256(data) → string / sha512(data) → string / sha1(data) → string #text(size: 8pt, fill: gray)[(Sec)]

See `hash` above. Individual functions for the most common algorithms. 1 required parameter each.

```utlx
sha256("sensitive data")                 // 64-char hex string
sha512("sensitive data")                 // 128-char hex string
sha1("sensitive data")                   // 40-char hex string (avoid for security)
```

=== sha384 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sha3_256 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sha3_512 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== shiftLeft #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== shiftRight #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== shouldUseCDATA #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== simpleInterest #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== singularize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sinh #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== slice(value, start, end?) → array or string #text(size: 8pt, fill: gray)[(Arr/Str)]

Extract a portion of an array or string by index. Zero-based.

- `value` (required): array or string
- `start` (required): starting index (inclusive)
- `end` (optional): ending index (exclusive). If omitted, goes to end.

```utlx
slice([10, 20, 30, 40, 50], 1, 4)       // [20, 30, 40] (index 1,2,3)
slice([10, 20, 30, 40, 50], 2)           // [30, 40, 50] (from index 2 to end)
slice("Hello World", 6, 11)             // "World"
slice("Hello World", 6)                 // "World"
```

=== slugify #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== smartCoerce #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== snakeCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== some #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== someEntry #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sort(array) → array / sortBy(array, keyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Sort an array. `sort` uses natural ordering. `sortBy` uses a key extractor.

- `array` (required): the array to sort
- `keyFn` (required for sortBy): lambda `(element) -> sortKey`

```bash
echo '[3, 1, 4, 1, 5]' | utlx -e 'sort(.)'
# [1, 1, 3, 4, 5]
```

```utlx
sort([3, 1, 4, 1, 5, 9])                // [1, 1, 3, 4, 5, 9]
sort(["banana", "apple", "cherry"])      // ["apple", "banana", "cherry"]

// Given: {"products": [
//   {"name": "Widget", "price": 25},
//   {"name": "Gadget", "price": 150},
//   {"name": "Gizmo", "price": 10}
// ]}

sortBy($input.products, (p) -> p.price)
// Output: [Gizmo(10), Widget(25), Gadget(150)] — cheapest first

sortBy($input.products, (p) -> -p.price)
// Output: [Gadget(150), Widget(25), Gizmo(10)] — most expensive first (negate)

sortBy($input.products, (p) -> p.name)
// Output: [Gadget, Gizmo, Widget] — alphabetical
```

=== split(string, separator) → array #text(size: 8pt, fill: gray)[(Str)]

Split a string into an array of substrings.

- `string` (required): the string to split
- `separator` (required): delimiter string

```utlx
split("a,b,c", ",")                     // ["a", "b", "c"]
split("Hello World", " ")               // ["Hello", "World"]
split("user@example.com", "@")          // ["user", "example.com"]

// Use case: extract domain from email
let parts = split($input.email, "@")
parts[1]                                 // "example.com"

// Use case: parse a path
split("/usr/local/bin", "/")             // ["", "usr", "local", "bin"]
```

=== splitWithMatches #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sqrt #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startOfDay #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startOfMonth #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startOfQuarter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startOfWeek #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startOfYear #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== startTimer #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== stringOrDefault #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== stripBOM #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== substring(string, start, end?) → string / substringBefore(string, delimiter) → string / substringAfter(string, delimiter) → string #text(size: 8pt, fill: gray)[(Str)]

Extract part of a string. `substring` by index. `substringBefore`/`substringAfter` by delimiter.

- `string` (required): the source string
- `start` (required for substring): starting index (zero-based)
- `end` (optional for substring): ending index (exclusive)
- `delimiter` (required for substringBefore/After): the delimiter to search for

```utlx
substring("Hello World", 6)             // "World"
substring("Hello World", 0, 5)          // "Hello"

substringBefore("user@example.com", "@") // "user"
substringAfter("user@example.com", "@")  // "example.com"

substringBefore("no-delimiter", "@")     // "no-delimiter" (not found — returns all)
substringAfter("no-delimiter", "@")      // "" (not found — returns empty)

// Also:
substringBeforeLast("a.b.c.d", ".")      // "a.b.c"
substringAfterLast("a.b.c.d", ".")       // "d"
```

=== substringAfterLast #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== substringBeforeLast #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sum(array) → number / sumBy(array, fn) → number #text(size: 8pt, fill: gray)[(Num)]

Sum numeric values. `sum` takes an array of numbers. `sumBy` takes objects with a key extractor.

- `array` (required): array of numbers (sum) or objects (sumBy)
- `fn` (required for sumBy): lambda `(element) -> number`

```utlx
sum([10, 20, 30])                        // 60
sum([])                                  // 0 (empty array)

// Given: {"items": [{"qty": 2, "price": 25}, {"qty": 5, "price": 10}, {"qty": 1, "price": 100}]}

sumBy($input.items, (i) -> i.qty * i.price)
// Output: 200 (2*25 + 5*10 + 1*100)

// Equivalent but verbose:
sum(map($input.items, (i) -> i.qty * i.price))
// Output: 200
```

*Anti-pattern:* `reduce($input.items, 0, (acc, i) -> acc + i.price)` — use `sum(map(...))` or `sumBy()`.

=== systemPropertiesAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== systemProperty #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== systemPropertyOrDefault #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== T

=== tan #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== tanh #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== tempDir #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== textContent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerCheck #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerClear #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerList #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerReset #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerStart #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerStats #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timerStop #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== timestamp #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== titleCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toArray #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toBase64 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toBinary #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toBoolean(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Convert a value to boolean.

- `value` (required): string, number, or boolean to convert

```utlx
toBoolean("true")                        // true
toBoolean("false")                       // false
toBoolean(1)                             // true
toBoolean(0)                             // false
toBoolean("yes")                         // true
toBoolean("no")                          // false
```

=== toBytes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toDate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toDegrees #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toHex #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toNumber(value) → number #text(size: 8pt, fill: gray)[(Type)]

Convert a value to a number. Throws an error if the value cannot be parsed.

- `value` (required): string, boolean, or number to convert

```utlx
toNumber("42")                           // 42
toNumber("3.14")                         // 3.14
toNumber(true)                           // 1
toNumber(false)                          // 0
toNumber("not-a-number")                 // ERROR — runtime error

// Use case: XML values are always strings — convert for arithmetic
let quantity = toNumber($input.Order.Quantity)
let price = toNumber($input.Order.Price)
{lineTotal: quantity * price}
```

*Anti-pattern:* `toNumber()` on unvalidated user input without error handling:

```utlx
// BAD — crashes on invalid input:
toNumber($input.userProvidedValue)

// GOOD — safe with fallback:
try { toNumber($input.userProvidedValue) } catch { 0 }
```

=== toObject #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toRadians #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toString(value) → string #text(size: 8pt, fill: gray)[(Type)]

Convert any value to its string representation.

- `value` (required): any value to convert

```utlx
toString(42)                             // "42"
toString(3.14)                           // "3.14"
toString(true)                           // "true"
toString(null)                           // "null"
toString([1, 2])                         // "[1, 2]"
```

Also: `toDate(value)`, `toArray(value)` (wraps non-array in array), `toObject(value)`, `toBinary(string)`.

=== toTitleCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== toUTC #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== trace #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== translate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treeDepth #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treeFilter #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treeFind #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treeFlatten #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treeMap #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== treePaths #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== trim(string) → string #text(size: 8pt, fill: gray)[(Str)]

Remove whitespace from both ends of a string.

- `string` (required): the string to trim

```utlx
trim("   hello   ")                      // "hello"

// Use case: clean up CSV values
map($input, (row) -> mapValues(row, (v) -> if (isString(v)) trim(v) else v))
```

=== leftTrim(string) → string #text(size: 8pt, fill: gray)[(Str)]

Remove whitespace from the LEFT (start) of a string only.

- `string` (required): the string to trim

```utlx
leftTrim("   hello   ")                  // "hello   "
```

=== rightTrim(string) → string #text(size: 8pt, fill: gray)[(Str)]

Remove whitespace from the RIGHT (end) of a string only.

- `string` (required): the string to trim

```utlx
rightTrim("   hello   ")                 // "   hello"
```

Also: `normalizeSpace(string)` — trims AND collapses internal whitespace to single spaces.

=== transpose(array2D) → array2D #text(size: 8pt, fill: gray)[(Arr)]

Transpose a 2D array — rows become columns, columns become rows.

- `array2D` (required): array of arrays (all same length)

```utlx
transpose([[1, 2, 3], [4, 5, 6]])
// Output: [[1, 4], [2, 5], [3, 6]]

// Use case: pivot table data
// Input: [["Name", "Q1", "Q2"], ["Alice", 100, 200], ["Bob", 150, 175]]
transpose($input)
// Output: [["Name", "Alice", "Bob"], ["Q1", 100, 150], ["Q2", 200, 175]]
```

=== truncate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== tryCoerce #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== typeOf #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== typeof #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== U

=== udmToJSON #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== udmToXML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== udmToYAML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== uncamelize #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== unescapeXML #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== union(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Combine two arrays, removing duplicates. Returns all unique values from both.

- `arr1` (required): first array
- `arr2` (required): second array

```utlx
union([1, 2, 3], [3, 4, 5])
// Output: [1, 2, 3, 4, 5]

union(["A", "B"], ["B", "C", "D"])
// Output: ["A", "B", "C", "D"]
```

=== intersect(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Return values present in BOTH arrays.

- `arr1` (required): first array
- `arr2` (required): second array

```utlx
intersect([1, 2, 3], [2, 3, 4])
// Output: [2, 3]

intersect(["A", "B", "C"], ["X", "Y"])
// Output: []  (no common elements)
```

=== difference(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Return values in `arr1` that are NOT in `arr2`. Order matters — `difference(a, b)` is not the same as `difference(b, a)`.

- `arr1` (required): the source array
- `arr2` (required): the array to subtract

```utlx
difference([1, 2, 3], [2, 3, 4])
// Output: [1]  (1 is in arr1 but not in arr2)

difference([2, 3, 4], [1, 2, 3])
// Output: [4]  (4 is in arr2's position but not in arr1)

// Use case: find new and removed items between two snapshots
let previous = map($input.previousOrders, (o) -> o.id)
let current = map($input.currentOrders, (o) -> o.id)
{
  newOrders: difference(current, previous),
  removedOrders: difference(previous, current)
}
```

=== symmetricDifference(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Return values that are in EITHER array but NOT in both. The "exclusive or" of two arrays.

- `arr1` (required): first array
- `arr2` (required): second array

```utlx
symmetricDifference([1, 2, 3], [2, 3, 4])
// Output: [1, 4]  (1 only in arr1, 4 only in arr2)

symmetricDifference([1, 2], [1, 2])
// Output: []  (identical arrays — nothing is exclusive)
```

=== unique(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values. Alias for `distinct()`. Preserves first occurrence.

- `array` (required): the array to deduplicate

```utlx
unique([1, 2, 2, 3, 3, 3])              // [1, 2, 3]
unique(["apple", "banana", "apple"])     // ["apple", "banana"]

// Use case: collect unique values from a field
unique(map($input.orders, (o) -> o.customerId))
// Output: ["C-42", "C-41", "C-43"] (unique customer IDs across all orders)
```

=== unnest #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== unwrapCDATA #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== unzip #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== unzipArchive #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== unzipN #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== updateXMLEncoding #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== upper #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== uptime #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== urlDecodeComponent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== urlEncode(string) → string / urlDecode(string) → string #text(size: 8pt, fill: gray)[(URL)]

URL-encode/decode strings (percent-encoding per RFC 3986).

- `string` (required): the string to encode or decode

```utlx
urlEncode("hello world")                 // "hello%20world"
urlEncode("price=10&currency=EUR")       // "price%3D10%26currency%3DEUR"
urlDecode("hello%20world")               // "hello world"

// Also available:
urlEncodeComponent("a=b&c=d")            // encodes & and = too
urlDecodeComponent("a%3Db%26c%3Dd")      // "a=b&c=d"

// Use case: build a query string
let qs = join(map(entries($input.params), (e) ->
  concat(urlEncode(e[0]), "=", urlEncode(toString(e[1])))
), "&")
// Or use the dedicated function:
buildQueryString($input.params)
```

Also: `buildURL(base, path, params)`, `parseURL(url)`, `getHost(url)`, `getPath(url)`, `getQuery(url)`, `getQueryParams(url)`.

=== urlEncodeComponent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== username #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== V-W

=== validateDate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== validateDigest #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== validateEncoding #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== values(object) → array #text(size: 8pt, fill: gray)[(Obj)]

See `keys` above. Returns all property values as an array.

```utlx
values({name: "Alice", age: 30})         // ["Alice", 30]
```

=== version #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== warn #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== weekOfYear #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== windowed(array, size) → array of arrays #text(size: 8pt, fill: gray)[(Arr)]

Create a sliding window over an array. Returns overlapping sub-arrays of the given size.

- `array` (required): the source array
- `size` (required): window size

```utlx
windowed([1, 2, 3, 4, 5], 3)
// Output: [[1, 2, 3], [2, 3, 4], [3, 4, 5]]

windowed([1, 2, 3, 4, 5], 2)
// Output: [[1, 2], [2, 3], [3, 4], [4, 5]]

// Use case: calculate moving average
let prices = [100, 105, 98, 110, 107]
map(windowed(prices, 3), (window) -> avg(window))
// Output: [101, 104.33, 105]  (3-day moving average)

// Use case: detect consecutive duplicates
filter(windowed($input.events, 2), (pair) -> pair[0].type == pair[1].type)
```

=== wordCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== wrapIfNeeded #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeByte #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeDouble #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeFloat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeInt16 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeInt32 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== writeInt64 #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== X-Z

=== xmlEscape(string) → string / xmlUnescape(string) → string #text(size: 8pt, fill: gray)[(XML)]

Escape/unescape XML special characters.

- `string` (required): the string to escape or unescape

```utlx
xmlEscape("price < 100 & tax > 0")
// Output: "price &lt; 100 &amp; tax &gt; 0"

xmlUnescape("price &lt; 100 &amp; tax &gt; 0")
// Output: "price < 100 & tax > 0"

// Use case: safely embed user input in XML output
{
  Comment: xmlEscape($input.userComment)
}

// Characters escaped: < → &lt;  > → &gt;  & → &amp;  " → &quot;  ' → &apos;
```

=== xnor #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== xor #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlExists #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlFilterByKeyPattern #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlFindByField #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlFindObjectsWithField #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlFromEntries #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlGetDocument #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlHasRequiredFields #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlKeys #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlMerge #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlMergeAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlOmitKeys #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlSelectKeys #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlSort #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlSplitDocuments(yaml) → array / yamlMergeDocuments(docs) → string / yamlPath(yaml, path) → value / yamlSet(yaml, path, value) → value / yamlDelete(yaml, path) → value #text(size: 8pt, fill: gray)[(YAML)]

YAML-specific functions for multi-document handling and path-based access. See Chapter 26.

- `yaml` (required): YAML string or UDM value
- `docs` (required for merge): array of documents
- `path` (required): dot-separated path string
- `value` (required for set): value to set at path

```utlx
// Split multi-document YAML (separated by ---):
let docs = yamlSplitDocuments(multiDocString)
docs[0]                                  // first document
docs[1]                                  // second document

// Get a specific document:
yamlGetDocument(multiDocString, 0)       // first document

// Merge documents back:
yamlMergeDocuments(docs)                 // joined with --- separators

// Path-based access and modification:
yamlPath($input, "database.host")        // "localhost"
yamlSet($input, "database.port", 5433)   // returns new structure with port changed
yamlDelete($input, "database.password")  // returns structure without password

// Check path existence:
yamlExists($input, "features.experimental")  // true/false
```

Also: `yamlDeepMerge(obj1, obj2)`, `yamlKeys(obj)`, `yamlValues(obj)`, `yamlSort(obj)`, `yamlValidate(yaml, rules)`, `yamlFilterByKeyPattern(obj, pattern)`.

=== yamlValidate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlValidateKeyPattern #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlValues #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== zip(arr1, arr2) → array / zipWith(arr1, arr2, fn) → array / zipWithIndex(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Combine two arrays element-by-element. `zip`: pairs. `zipWith`: merged by function. `zipWithIndex`: adds index to each element.

- `arr1`, `arr2` (required): arrays to combine (truncated to shorter length)
- `fn` (required for zipWith): lambda `(elem1, elem2) -> combined`
- `array` (required for zipWithIndex): the array to index

```utlx
zip([1, 2, 3], ["a", "b", "c"])
// Output: [[1, "a"], [2, "b"], [3, "c"]]

zipWith([1, 2, 3], [10, 20, 30], (a, b) -> a + b)
// Output: [11, 22, 33]

zipWithIndex(["Apple", "Banana", "Cherry"])
// Output: [["Apple", 0], ["Banana", 1], ["Cherry", 2]]

// Use case: combine two parallel arrays (e.g., headers and values)
let headers = ["Name", "Age", "City"]
let row = ["Alice", "30", "Amsterdam"]
fromEntries(zip(headers, row))
// Output: {"Name": "Alice", "Age": "30", "City": "Amsterdam"}

// Use case: add line numbers
map(zipWithIndex($input.items), (pair) -> {
  lineNumber: pair[1] + 1,
  ...pair[0]
})
```

Also: `zipAll(arrays)` — zips any number of arrays, `unzip(pairs)` — reverse of zip, `unzipN(arrays)` — reverse of zipAll.

=== zipAll #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== zipArchive #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== Functions Not Listed Individually

The following function groups follow the same patterns as their listed counterparts. Use the MCP server (`get_function_info`) or the IDE's function browser for signatures and examples.

- *Geospatial (12):* `distance`, `bearing`, `midpoint`, `destinationPoint`, `boundingBox`, `inCircle`, `inPolygon`, `isValidCoordinates`, `geoBearing`, `geoDistance`, `geoMidpoint`, `geoBounds`
- *Binary (12):* `binaryConcat`, `binaryEquals`, `binaryLength`, `binarySlice`, `binaryToString`, `readByte`, `readInt16`, `readInt32`, `readInt64`, `writeByte`, `writeInt16`, `writeInt32`
- *URL (16):* `buildURL`, `parseURL`, `getHost`, `getPath`, `getPort`, `getProtocol`, `getQuery`, `getFragment`, `getQueryParams`, `buildQueryString`, `addQueryParam`, `removeQueryParam`, `getBaseURL`, `getURLPath`, `parseQueryString`, `getExtension`
- *JWT/JWS (18):* `createJWT`, `verifyJWT`, `decodeJWT`, `getJWTClaim`, `getJWTClaims`, `getJWTIssuer`, `getJWTSubject`, `getJWTAudience`, `isJWTExpired`, `validateJWTStructure`, `decodeJWS`, `getJWSHeader`, `getJWSPayload`, `getJWSAlgorithm`, `getJWSKeyId`, `isJWSFormat`, `getJWSInfo`, `getJWSSigningInput`
- *Encryption (6):* `encryptAES`, `decryptAES`, `encryptAES256`, `decryptAES256`, `generateKey`, `generateIV`
- *Compression (6):* `compress`, `decompress`, `gzip`, `gunzip`, `deflate`, `inflate`
- *Timer/Debug (14):* `timerStart`, `timerStop`, `timerStats`, `timerCheck`, `timerReset`, `timerClear`, `timerList`, `debug`, `debugPrint`, `trace`, `warn`, `info`, `log`, `measure`
- *Tree (7):* `treeMap`, `treeFilter`, `treeFind`, `treeFlatten`, `treePaths`, `treeDepth`, `mapTree`
- *Case conversion (12):* `camelCase`, `snakeCase`, `kebabCase`, `pascalCase`, `titleCase`, `dotCase`, `pathCase`, `constantCase`, `wordCase`, `slugify`, `fromCamelCase`, `fromSnakeCase`
- *Math (12):* `pow`, `sqrt`, `exp`, `ln`, `log10`, `log2`, `sin`, `cos`, `tan`, `asin`, `acos`, `atan`
- *Bitwise (6):* `bitwiseAnd`, `bitwiseOr`, `bitwiseXor`, `bitwiseNot`, `shiftLeft`, `shiftRight`
- *Financial (6):* `simpleInterest`, `compoundInterest`, `presentValue`, `futureValue`, `calculateTax`, `calculateDiscount`

Total: *692 functions* across 16 categories.
