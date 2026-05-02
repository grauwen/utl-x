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

=== avg(array) → number #text(size: 8pt, fill: gray)[(Num)]

Average of numeric values in an array. Returns 0 for empty arrays.

- `array` (required): array of numbers

```utlx
// Given: {"scores": [85, 92, 78, 95, 88]}

avg($input.scores)
// Output: 87.6

avg([])
// Output: 0 (empty array returns 0, not error)
```

*Anti-pattern:* `sum(arr) / count(arr)` — crashes on empty arrays (division by zero). Use `avg()` which handles this safely.

=== avgBy(array, keyFn) → number #text(size: 8pt, fill: gray)[(Num)]

Average of values extracted from an array of objects using a key function.

- `array` (required): array of objects
- `keyFn` (required): lambda `(element) -> number`

```utlx
// Given: {"products": [{"name": "A", "price": 10}, {"name": "B", "price": 30}, {"name": "C", "price": 20}]}

avgBy($input.products, (p) -> p.price)
// Output: 20
```

== B

=== base64Encode(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Encode data to a Base64 string for safe transport (e.g., in URLs or headers).

- `data` (required): string to encode

```utlx
base64Encode("Hello, World!")
// Output: "SGVsbG8sIFdvcmxkIQ=="
```

=== base64Decode(string) → string #text(size: 8pt, fill: gray)[(Sec)]

Decode a Base64-encoded string back to its original value.

- `string` (required): Base64-encoded string to decode

```utlx
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

=== c14n(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize an XML document using W3C C14N (sorted attributes, normalized whitespace). See Chapter 22 for full details.

- `xml` (required): XML UDM value to canonicalize

```utlx
// Given: XML document with unsorted attributes, whitespace variations

c14n($input)
// Output: canonical XML string (sorted attributes, normalized whitespace)
```

Also: `c14nWithComments(xml)`, `excC14n(xml)` (exclusive, for SOAP), `c14n11(xml)` (version 1.1), `c14nFingerprint(xml)` (short hash for logging).

=== c14nHash(xml, algorithm?) → string #text(size: 8pt, fill: gray)[(XML)]

Compute a hash digest of the canonical form of an XML document.

- `xml` (required): XML UDM value to canonicalize and hash
- `algorithm` (optional, default `"SHA-256"`): hash algorithm (e.g., `"SHA-512"`)

```utlx
c14nHash($input)
// Output: "a1b2c3d4e5f6..."  (SHA-256 hex digest of canonical form)

c14nHash($input, "SHA-512")
// Output: "9b71d224bd62..."  (SHA-512 instead)
```

=== c14nEquals(xml1, xml2) → boolean #text(size: 8pt, fill: gray)[(XML)]

Compare two XML documents semantically (ignoring formatting differences) by comparing their canonical forms.

- `xml1` (required): first XML UDM value
- `xml2` (required): second XML UDM value

```utlx
// Compare two XML documents semantically (ignoring formatting differences)
c14nEquals(xmlFromSystemA, xmlFromSystemB)
// Output: true if same content, regardless of attribute order or whitespace
```

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

=== count(array) → number #text(size: 8pt, fill: gray)[(Arr)]

Count all elements in an array.

- `array` (required): the array to count

```utlx
// Given: {"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}

count($input.orders)
// Output: 3
```

=== countBy(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

Count elements in an array that match a predicate.

- `array` (required): the array to count
- `predicate` (required): lambda `(element) -> boolean`

```utlx
// Given: {"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}

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

=== dayOfWeek(date) → number #text(size: 8pt, fill: gray)[(Date)]

Return the day of the week as a number (1=Monday, 7=Sunday).

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
dayOfWeek(d)          // 5 (Thursday — 1=Monday, 7=Sunday)
```

=== dayOfWeekName(date) → string #text(size: 8pt, fill: gray)[(Date)]

Return the day of the week as a name (e.g., "Thursday").

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
dayOfWeekName(d)      // "Thursday"
```

=== dayOfYear(date) → number #text(size: 8pt, fill: gray)[(Date)]

Return the day number within the year (1-365 or 1-366 for leap years).

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
dayOfYear(d)          // 121 (121st day of 2026)
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

=== distinct(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values from an array using value equality.

- `array` (required): the array to deduplicate

```utlx
distinct([1, 2, 2, 3, 3, 3])
// Output: [1, 2, 3]

// Extract unique values from a field:
distinct(map($input.orders, (o) -> o.customerId))
// Output: ["C-42", "C-41"]
```

=== distinctBy(array, keyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values from an array using a key extractor to determine uniqueness. Keeps the first element for each key.

- `array` (required): the array to deduplicate
- `keyFn` (required): lambda `(element) -> key`

```utlx
// Given: {"orders": [
//   {"id": 1, "customerId": "C-42"},
//   {"id": 2, "customerId": "C-42"},
//   {"id": 3, "customerId": "C-41"}
// ]}

distinctBy($input.orders, (o) -> o.customerId)
// Output: [{"id": 1, "customerId": "C-42"}, {"id": 3, "customerId": "C-41"}]
// (first order per customer kept, duplicates removed)
```

=== divideBy #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== dotCase #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== drop(array, n) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove the first N elements from an array, returning the rest.

- `array` (required): the source array
- `n` (required): number of elements to drop

```utlx
// Given: {"items": ["A", "B", "C", "D", "E"]}

drop($input.items, 2)
// Output: ["C", "D", "E"]  (first 2 removed)

// Use case: skip CSV header row (when headers: false)
let dataRows = drop($input, 1)  // skip first row
```

=== take(array, n) → array #text(size: 8pt, fill: gray)[(Arr)]

Keep only the first N elements from an array, discarding the rest.

- `array` (required): the source array
- `n` (required): number of elements to keep

```utlx
// Given: {"items": ["A", "B", "C", "D", "E"]}

take($input.items, 3)
// Output: ["A", "B", "C"]  (only first 3 kept)

// Use case: top 10 results
let top10 = take(sortBy($input.products, (p) -> -p.sales), 10)
```

