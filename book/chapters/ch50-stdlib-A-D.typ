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

=== acos(number) → number #text(size: 8pt, fill: gray)[(Num)]

Arc cosine (inverse cosine). Returns angle in radians.

- `number` (required): value between -1 and 1

```utlx
acos(1)        // 0.0
acos(0)        // 1.5707963267948966 (π/2)
acos(-1)       // 3.141592653589793 (π)
```

=== addBOM(data) → binary #text(size: 8pt, fill: gray)[(Bin)]

Prepend a Byte Order Mark (BOM) to binary data. Detects encoding from the data.

- `data` (required): binary data to prepend BOM to

```utlx
// Programmatic BOM insertion for binary file construction
let fileBytes = toBinary($input.content, "UTF-8")
{
  withBOM: addBOM(fileBytes)
}
```

*Note:* for normal output, prefer the header option `output csv {bom: true}` (Chapter 25) which handles BOM automatically. Use `addBOM()` only when constructing raw binary content programmatically.

=== addNamespaceDeclarations(xml, namespaces) → xml #text(size: 8pt, fill: gray)[(XML)]

Add namespace declarations to an XML element.

- `xml` (required): XML UDM value
- `namespaces` (required): object mapping prefix to URI — `{"cbc": "urn:...", "cac": "urn:..."}`

```utlx
// See Chapter 22 for XML namespace handling.
{
  result: addNamespaceDeclarations($input, {
    "cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
  })
}
```

=== addQueryParam(url, name, value) → string #text(size: 8pt, fill: gray)[(URL)]

Add a query parameter to a URL string.

- `url` (required): the base URL
- `name` (required): parameter name
- `value` (required): parameter value

```bash
echo '{"baseUrl": "https://api.example.com/search", "term": "hello world"}' \
  | utlx -e 'addQueryParam($input.baseUrl, "q", $input.term)'
# "https://api.example.com/search?q=hello+world"
```

```utlx
{
  url: addQueryParam(addQueryParam($input.baseUrl, "page", "1"), "limit", "50")
}
```

=== addTax(amount, rate) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate total amount including tax.

- `amount` (required): net amount before tax
- `rate` (required): tax rate as decimal (e.g., 0.21 for 21%)

```bash
echo '{"price": 100, "vatRate": 0.21}' | utlx -e 'addTax($input.price, $input.vatRate)'
# 121.0
```

```utlx
{
  netPrice: $input.price,
  totalWithVAT: addTax($input.price, 0.21)
}
```

=== age(birthdate, referenceDate?) → number #text(size: 8pt, fill: gray)[(Date)]

Calculate age in whole years from a birthdate. Uses today if no reference date provided.

- `birthdate` (required): date of birth
- `referenceDate` (optional): date to calculate age at (defaults to today)

```bash
echo '{"dob": "1990-03-15"}' | utlx -e 'age(parseDate($input.dob, "yyyy-MM-dd"))'
# 36
```

```utlx
{
  age: age(parseDate($input.dateOfBirth, "yyyy-MM-dd")),
  isMinor: age(parseDate($input.dateOfBirth, "yyyy-MM-dd")) < 18
}
```

=== analyzeString(string, pattern) → object #text(size: 8pt, fill: gray)[(Str)]

Analyze a string against a regex pattern, returning match status and captured groups.

- `string` (required): the string to analyze
- `pattern` (required): regex pattern with capture groups

Returns: `{match: boolean, groups: [string...], start: number, end: number}`

```bash
echo '{"email": "user@example.com"}' \
  | utlx -e 'analyzeString($input.email, "(.+)@(.+)\\.(.+)")'
# {"match": true, "groups": ["user", "example", "com"], "start": 0, "end": 16}
```

```utlx
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

=== and(values...) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical AND — returns true only if all arguments are truthy.

- `values` (variadic): boolean values to combine

```bash
echo '{"active": true, "verified": true, "paid": false}' \
  | utlx -e 'and($input.active, $input.verified, $input.paid)'
# false (all must be true, but paid is false)
```

```utlx
// Input: {"active": true, "verified": true, "paid": true}
{
  canShip: and($input.active, $input.verified, $input.paid),  // true
  canRefund: and($input.paid, $input.delivered)               // false if delivered is null
}
```

=== asin(number) → number #text(size: 8pt, fill: gray)[(Num)]

Arc sine (inverse sine). Returns angle in radians.

- `number` (required): value between -1 and 1

```utlx
asin(0)        // 0.0
asin(1)        // 1.5707963267948966 (π/2)
asin(-1)       // -1.5707963267948966 (-π/2)
```

=== assert(condition, message?) → null #text(size: 8pt, fill: gray)[(Sys)]

Assert that a condition is true. Throws an error with the message if condition is false.

- `condition` (required): boolean expression to verify
- `message` (optional): error message if assertion fails

```utlx
assert(count($input.items) > 0, "Input must have at least one item")
assert($input.amount >= 0, "Amount cannot be negative")
```

=== assertEqual(actual, expected) → null #text(size: 8pt, fill: gray)[(Sys)]

Assert two values are equal. Throws an error showing both values if they differ.

- `actual` (required): the value to test
- `expected` (required): the expected value

```utlx
assertEqual(count($input.items), 3)
assertEqual($input.status, "ACTIVE")
```

=== atan(number) → number #text(size: 8pt, fill: gray)[(Num)]

Arc tangent (inverse tangent). Returns angle in radians.

- `number` (required): any numeric value

```utlx
atan(0)        // 0.0
atan(1)        // 0.7853981633974483 (π/4)
atan(-1)       // -0.7853981633974483 (-π/4)
```

=== atan2(y, x) → number #text(size: 8pt, fill: gray)[(Num)]

Two-argument arc tangent. Converts Cartesian coordinates (x, y) to polar angle in radians.

- `y` (required): y-coordinate
- `x` (required): x-coordinate

```utlx
atan2(1, 1)    // 0.7853981633974483 (π/4 — 45°)
atan2(0, -1)   // 3.141592653589793 (π — 180°)
atan2(-1, 0)   // -1.5707963267948966 (-π/2 — -90°)
```

=== attribute(element, name) → string #text(size: 8pt, fill: gray)[(XML)]

Get a specific attribute value from an XML element by name. Useful when the attribute name is dynamic (stored in a variable). For static attribute access, prefer the `@` operator: `$input.@id`.

- `element` (required): XML UDM element
- `name` (required): attribute name (string)

```utlx
// Input: <Order id="ORD-123" status="active">...</Order>
// See Chapter 22 for XML attribute access (@ operator).

// Preferred: use @ for known attribute names
{
  orderId: $input.@id,                  // "ORD-123"
  status: $input.@status                // "active"
}

// Use attribute() when the name is dynamic (e.g. from input data)
{
  value: attribute($input, $input.lookupField)   // attribute name from data
}
```

=== attributes(element) → object #text(size: 8pt, fill: gray)[(XML)]

Get all attributes from an XML element as a key-value object. Useful for iterating or spreading all attributes. For accessing individual attributes, prefer `$input.@name`.

- `element` (required): XML UDM element

```utlx
// Input: <Product id="P-1" sku="ABC123" category="electronics"/>
// See Chapter 22 for XML attribute access.
{
  allAttrs: attributes($input),         // {"id": "P-1", "sku": "ABC123", "category": "electronics"}
  attrCount: count(keys(attributes($input)))  // 3
}
```

=== availableProcessors() → number #text(size: 8pt, fill: gray)[(Sys)]

Get the number of available CPU cores/processors on the current system.

```utlx
{
  cpuCores: availableProcessors()
}
// Output: {"cpuCores": 8}
```

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

```bash
echo '{"items": [{"price": 10}, {"price": 25}, {"price": 5}]}' \
  | utlx -e 'all($input.items, (item) -> item.price > 0)'
# true
```

```utlx
{
  allPositive: all($input.items, (item) -> item.price > 0),
  allExpensive: all($input.items, (item) -> item.price > 20)
}
// Output: {"allPositive": true, "allExpensive": false}
```

=== any(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Returns true if at least ONE element satisfies the predicate. Returns false for empty arrays.

- `array` (required): the array to test
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"orders": [{"status": "PENDING"}, {"status": "SHIPPED"}, {"status": "PENDING"}]}' \
  | utlx -e 'any($input.orders, (o) -> o.status == "SHIPPED")'
# true
```

```utlx
{
  hasShipped: any($input.orders, (o) -> o.status == "SHIPPED"),
  hasCancelled: any($input.orders, (o) -> o.status == "CANCELLED")
}
// Output: {"hasShipped": true, "hasCancelled": false}
```

=== avg(array) → number #text(size: 8pt, fill: gray)[(Num)]

Average of numeric values in an array. Returns 0 for empty arrays.

- `array` (required): array of numbers

```bash
echo '{"scores": [85, 92, 78, 95, 88]}' | utlx -e 'avg($input.scores)'
# 87.6
```

```utlx
{
  averageScore: avg($input.scores),
  averageEmpty: avg([])                  // 0 (safe on empty arrays)
}
```

*Anti-pattern:* `sum(arr) / count(arr)` — crashes on empty arrays (division by zero). Use `avg()` which handles this safely.

=== avgBy(array, keyFn) → number #text(size: 8pt, fill: gray)[(Num)]

Average of values extracted from an array of objects using a key function.

- `array` (required): array of objects
- `keyFn` (required): lambda `(element) -> number`

```bash
echo '{"products": [{"name": "A", "price": 10}, {"name": "B", "price": 30}, {"name": "C", "price": 20}]}' \
  | utlx -e 'avgBy($input.products, (p) -> p.price)'
# 20
```

```utlx
{
  avgPrice: avgBy($input.products, (p) -> p.price),
  avgWeight: avgBy($input.products, (p) -> p.weight)
}
```

== B

=== base64Encode(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Encode data to a Base64 string for safe transport (e.g., in URLs or headers).

- `data` (required): string to encode

```utlx
{
  encoded: base64Encode("Hello, World!")
}
// Output: {"encoded": "SGVsbG8sIFdvcmxkIQ=="}
```

=== base64Decode(string) → string #text(size: 8pt, fill: gray)[(Sec)]

Decode a Base64-encoded string back to its original value.

- `string` (required): Base64-encoded string to decode

```utlx
{
  decoded: base64Decode("SGVsbG8sIFdvcmxkIQ==")
}
// Output: {"decoded": "Hello, World!"}

// Real-world: decode a Base64-encoded JWT payload
let payload = split($input.token, ".")[1]
let decoded = parseJson(base64Decode(payload))
{
  subject: decoded.sub
}
// Output: {"subject": "user@example.com"}
```

=== bearing(lat1, lon1, lat2, lon2) → number #text(size: 8pt, fill: gray)[(Geo)]

Calculate the initial bearing (forward azimuth) from point1 to point2 in degrees (0-360).

- `lat1` (required): latitude of origin point
- `lon1` (required): longitude of origin point
- `lat2` (required): latitude of destination point
- `lon2` (required): longitude of destination point

```bash
echo '{"from": {"lat": 52.37, "lon": 4.90}, "to": {"lat": 48.86, "lon": 2.35}}' \
  | utlx -e 'bearing($input.from.lat, $input.from.lon, $input.to.lat, $input.to.lon)'
# 210.5 (degrees — roughly southwest)
```

```utlx
{
  heading: bearing($input.origin.lat, $input.origin.lon, $input.dest.lat, $input.dest.lon)
}
```

=== binaryConcat(binary1, binary2, ...) → binary #text(size: 8pt, fill: gray)[(Bin)]

Concatenate multiple binary values into a single binary.

- `binary1` (required): first binary segment
- `binary2` (required): second binary segment
- `...` (optional): additional binary segments

```utlx
let header = toBinary("HDR:", "UTF-8")
let payload = toBinary($input.data, "UTF-8")
{
  frame: binaryConcat(header, payload)
}
```

=== binaryEquals(binary1, binary2) → boolean #text(size: 8pt, fill: gray)[(Bin)]

Compare two binary values for byte-level equality.

- `binary1` (required): first binary
- `binary2` (required): second binary

```utlx
{
  match: binaryEquals(toBinary("hello", "UTF-8"), toBinary("hello", "UTF-8")),
  differ: binaryEquals(toBinary("hello", "UTF-8"), toBinary("world", "UTF-8"))
}
// Output: {"match": true, "differ": false}
```

=== binaryLength(binary) → number #text(size: 8pt, fill: gray)[(Bin)]

Get the length of a binary value in bytes.

- `binary` (required): binary data

```utlx
{
  sizeBytes: binaryLength(toBinary($input.content, "UTF-8"))
}
```

=== binarySlice(binary, start, end) → binary #text(size: 8pt, fill: gray)[(Bin)]

Extract a subsequence of bytes from binary data.

- `binary` (required): source binary
- `start` (required): start byte offset (0-based)
- `end` (required): end byte offset (exclusive)

```utlx
let data = toBinary("Hello World", "UTF-8")
{
  first5: binaryToString(binarySlice(data, 0, 5), "UTF-8"),
  rest: binaryToString(binarySlice(data, 6, 11), "UTF-8")
}
// Output: {"first5": "Hello", "rest": "World"}
```

=== binaryToString(binary, encoding) → string #text(size: 8pt, fill: gray)[(Bin)]

Decode binary data to a string using the specified character encoding.

- `binary` (required): binary data to decode
- `encoding` (required): character encoding — `"UTF-8"`, `"ISO-8859-1"`, `"US-ASCII"`, etc.

```utlx
{
  text: binaryToString(base64Decode($input.payload), "UTF-8")
}
```

=== bitwiseAnd(binary1, binary2) → binary #text(size: 8pt, fill: gray)[(Bin)]

Perform bitwise AND on two binary values.

- `binary1` (required): first operand
- `binary2` (required): second operand (same length)

```utlx
let mask = fromHex("FF00FF00")
let data = fromHex("AABBCCDD")
{
  masked: toHex(bitwiseAnd(data, mask))
}
// Output: {"masked": "aa00cc00"}
```

=== bitwiseNot(binary) → binary #text(size: 8pt, fill: gray)[(Bin)]

Perform bitwise NOT (inversion) on a binary value.

- `binary` (required): binary data to invert

```utlx
{
  inverted: toHex(bitwiseNot(fromHex("FF00")))
}
// Output: {"inverted": "00ff"}
```

=== bitwiseOr(binary1, binary2) → binary #text(size: 8pt, fill: gray)[(Bin)]

Perform bitwise OR on two binary values.

- `binary1` (required): first operand
- `binary2` (required): second operand (same length)

```utlx
{
  combined: toHex(bitwiseOr(fromHex("AA00"), fromHex("0055")))
}
// Output: {"combined": "aa55"}
```

=== bitwiseXor(binary1, binary2) → binary #text(size: 8pt, fill: gray)[(Bin)]

Perform bitwise XOR on two binary values.

- `binary1` (required): first operand
- `binary2` (required): second operand (same length)

```utlx
{
  xored: toHex(bitwiseXor(fromHex("AAFF"), fromHex("55FF")))
}
// Output: {"xored": "ff00"}
```

=== boundingBox(coordinates) → object #text(size: 8pt, fill: gray)[(Geo)]

Calculate the bounding box (min/max latitude and longitude) for an array of coordinates.

- `coordinates` (required): array of `{lat, lon}` objects

```bash
echo '{"points": [{"lat": 52.37, "lon": 4.90}, {"lat": 48.86, "lon": 2.35}, {"lat": 51.51, "lon": -0.13}]}' \
  | utlx -e 'boundingBox($input.points)'
# {"minLat": 48.86, "maxLat": 52.37, "minLon": -0.13, "maxLon": 4.90}
```

```utlx
{
  bounds: boundingBox($input.locations)
}
```

=== buildQueryString(params) → string #text(size: 8pt, fill: gray)[(URL)]

Build a URL query string from an object of key-value pairs.

- `params` (required): object with parameter names and values

```bash
echo '{"page": 2, "limit": 50, "sort": "name"}' \
  | utlx -e 'buildQueryString($input)'
# "page=2&limit=50&sort=name"
```

```utlx
{
  queryString: buildQueryString({q: $input.search, page: "1", format: "json"})
}
```

=== buildURL(components) → string #text(size: 8pt, fill: gray)[(URL)]

Build a complete URL from its component parts.

- `components` (required): object with `protocol`, `host`, `port?`, `path?`, `query?`, `fragment?`

```bash
echo '{"host": "api.example.com", "path": "/v2/users"}' \
  | utlx -e 'buildURL({protocol: "https", host: $input.host, path: $input.path, query: {active: "true"}})'
# "https://api.example.com/v2/users?active=true"
```

```utlx
{
  endpoint: buildURL({
    protocol: "https",
    host: $input.apiHost,
    path: concat("/api/v1/", $input.resource),
    query: {format: "json"}
  })
}
```

== C

=== c14n(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize an XML document using W3C C14N (sorted attributes, normalized whitespace). See Chapter 22 for full details.

- `xml` (required): XML UDM value to canonicalize

```bash
echo '<Order id="1" status="new"/>' | utlx -f xml -e 'c14n($input)'
# canonical XML string (sorted attributes, normalized whitespace)
```

```utlx
{
  canonical: c14n($input),
  fingerprint: c14nFingerprint($input)
}
```

Also: `c14nWithComments(xml)`, `excC14n(xml)` (exclusive, for SOAP), `c14n11(xml)` (version 1.1), `c14nFingerprint(xml)` (short hash for logging).

=== c14nHash(xml, algorithm?) → string #text(size: 8pt, fill: gray)[(XML)]

Compute a hash digest of the canonical form of an XML document.

- `xml` (required): XML UDM value to canonicalize and hash
- `algorithm` (optional, default `"SHA-256"`): hash algorithm (e.g., `"SHA-512"`)

```bash
echo '<Invoice id="1"><Total>100</Total></Invoice>' \
  | utlx -f xml -e 'c14nHash($input)'
# "a1b2c3d4e5..."  (SHA-256 hex digest of canonical form)
```

```utlx
{
  digest256: c14nHash($input),
  digest512: c14nHash($input, "SHA-512")
}
```

=== c14nEquals(xml1, xml2) → boolean #text(size: 8pt, fill: gray)[(XML)]

Compare two XML documents semantically (ignoring formatting differences) by comparing their canonical forms. See Chapter 22.

- `xml1` (required): first XML UDM value
- `xml2` (required): second XML UDM value

```utlx
{
  match: c14nEquals($input.expected, $input.actual),
  status: if (c14nEquals($input.expected, $input.actual)) "PASS" else "FAIL"
}
```

=== c14n11(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize XML using Canonical XML 1.1 (without comments). Handles xml:id and other 1.1-specific features.

- `xml` (required): XML UDM value

```utlx
{
  canonical: c14n11($input)
}
```

=== c14n11WithComments(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize XML using Canonical XML 1.1 with comments preserved.

- `xml` (required): XML UDM value

```utlx
{
  canonical: c14n11WithComments($input)
}
```

=== c14nFingerprint(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Create a short hash fingerprint of the canonical form of XML. Useful for deduplication and logging.

- `xml` (required): XML UDM value

```utlx
{
  fingerprint: c14nFingerprint($input),
  isDuplicate: c14nFingerprint($input) == $input.lastSeen
}
```

=== c14nPhysical(xml, options?) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize XML using Physical Canonical XML (preserves physical structure more faithfully).

- `xml` (required): XML UDM value
- `options` (optional): canonicalization options

```utlx
{
  physical: c14nPhysical($input)
}
```

=== c14nSubset(xml, xpath) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize a subset of an XML document selected by XPath expression.

- `xml` (required): XML UDM value
- `xpath` (required): XPath expression selecting the subset to canonicalize

```utlx
// See Chapter 22 for XML canonicalization.
{
  bodyCanonical: c14nSubset($input, "//soap:Body")
}
```

=== c14nWithComments(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize XML using Canonical XML 1.0 with comments preserved.

- `xml` (required): XML UDM value

```utlx
{
  canonical: c14nWithComments($input)
}
```

=== calculateDiscount(price, rate) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate the price after applying a percentage discount.

- `price` (required): original price
- `rate` (required): discount rate as decimal (e.g., 0.10 for 10%)

```bash
echo '{"price": 200, "discount": 0.15}' | utlx -e 'calculateDiscount($input.price, $input.discount)'
# 170.0
```

```utlx
{
  originalPrice: $input.price,
  discountedPrice: calculateDiscount($input.price, 0.10)
}
```

=== calculateTax(amount, rate) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate the tax amount for a given amount and rate (returns only the tax portion, not the total).

- `amount` (required): taxable amount
- `rate` (required): tax rate as decimal (e.g., 0.21 for 21%)

```bash
echo '{"subtotal": 500, "vatRate": 0.21}' | utlx -e 'calculateTax($input.subtotal, $input.vatRate)'
# 105.0
```

```utlx
{
  subtotal: $input.amount,
  tax: calculateTax($input.amount, 0.21),
  total: addTax($input.amount, 0.21)
}
```

=== camelCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to camelCase.

- `string` (required): the string to convert

```utlx
camelCase("hello world")        // "helloWorld"
camelCase("some-kebab-case")    // "someKebabCase"
camelCase("SCREAMING_SNAKE")    // "screamingSnake"
```

=== camelize(string) → string #text(size: 8pt, fill: gray)[(Str)]

Alias for `camelCase()`. Convert a string to camelCase. Prefer `camelCase()`.

- `string` (required): the string to convert

```utlx
camelize("my_field_name")       // "myFieldName"
```

=== canCoerce(value, targetType) → boolean #text(size: 8pt, fill: gray)[(Type)]

Check if a value can be coerced to a target type without error.

- `value` (required): the value to test
- `targetType` (required): target type as string — `"number"`, `"boolean"`, `"date"`, etc.

```utlx
canCoerce("123", "number")      // true
canCoerce("abc", "number")      // false
canCoerce("true", "boolean")    // true
```

=== canonicalizeWithAlgorithm(xml, algorithm) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalize XML using a specified W3C canonicalization algorithm URI.

- `xml` (required): XML UDM value
- `algorithm` (required): algorithm URI (e.g., `"http://www.w3.org/2001/10/xml-exc-c14n#"`)

```utlx
// See Chapter 22 for XML canonicalization.
{
  canonical: canonicalizeWithAlgorithm($input, "http://www.w3.org/2001/10/xml-exc-c14n#")
}
```

=== canonicalJSONHash(json, algorithm?) → string #text(size: 8pt, fill: gray)[(JSON)]

Canonicalize JSON per RFC 8785 and compute a cryptographic hash of the result.

- `json` (required): JSON string or UDM value
- `algorithm` (optional, default `"SHA-256"`): hash algorithm

```utlx
// See Chapter 24 for JSON processing.
{
  digest: canonicalJSONHash(renderJson($input)),
  digest512: canonicalJSONHash(renderJson($input), "SHA-512")
}
```

=== canonicalJSONSize(json) → number #text(size: 8pt, fill: gray)[(JSON)]

Get the size in bytes (UTF-8) of the canonical JSON form.

- `json` (required): JSON string or UDM value

```utlx
// See Chapter 24 for JSON processing.
{
  sizeBytes: canonicalJSONSize(renderJson($input))
}
```

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

=== charAt(string, index) → string #text(size: 8pt, fill: gray)[(Str)]

Get the character at a specific index (0-based).

- `string` (required): the source string
- `index` (required): position (0-based)

```utlx
charAt("Hello", 0)    // "H"
charAt("Hello", 4)    // "o"
```

=== charCodeAt(string, index) → number #text(size: 8pt, fill: gray)[(Str)]

Get the Unicode code point of the character at a specific index.

- `string` (required): the source string
- `index` (required): position (0-based)

```utlx
charCodeAt("A", 0)    // 65
charCodeAt("a", 0)    // 97
charCodeAt("€", 0)    // 8364
```

=== childCount(element) → number #text(size: 8pt, fill: gray)[(XML)]

Count the number of child elements in an XML element.

- `element` (required): XML UDM element

```utlx
// Input: <Order><Item/><Item/><Item/></Order>
// See Chapter 22 for XML processing.
{
  itemCount: childCount($input)    // 3
}
```

=== childNames(element) → array #text(size: 8pt, fill: gray)[(XML)]

Get the names of all child elements.

- `element` (required): XML UDM element

```utlx
// Input: <Order><Id>1</Id><Date>2026-05-01</Date><Total>100</Total></Order>
// See Chapter 22 for XML processing.
{
  fields: childNames($input)    // ["Id", "Date", "Total"]
}
```

=== chunkBy(array, predicate) → array of arrays #text(size: 8pt, fill: gray)[(Arr)]

Split a flat sequence into chunks. A new chunk starts whenever the predicate returns true.

- `array` (required): the array to split
- `predicate` (required): lambda `(element) -> boolean` — true starts a new chunk

```bash
echo '{"items": [1, 2, 10, 11, 12, 20, 21]}' \
  | utlx -e 'chunkBy($input.items, (x) -> x >= 10 and x % 10 == 0)'
# [[1, 2], [10, 11, 12], [20, 21]]
```

```utlx
// See Chapter 20 for data restructuring patterns.
{
  groups: chunkBy($input.records, (r) -> r.isHeader)
}
```

=== clearLogs() → null #text(size: 8pt, fill: gray)[(Sys)]

Clear all accumulated log entries from the log buffer.

```utlx
clearLogs()
// all previous log entries are discarded
```

=== coerce(value, targetType, default?) → value #text(size: 8pt, fill: gray)[(Type)]

Coerce a value to a target type, returning a default if coercion fails.

- `value` (required): value to coerce
- `targetType` (required): target type — `"number"`, `"boolean"`, `"string"`, `"date"`
- `default` (optional): fallback value on failure

```utlx
coerce("123", "number", 0)       // 123
coerce("abc", "number", 0)       // 0 (fallback)
coerce("true", "boolean", false) // true
```

=== coerceAll(array, targetType, default?) → array #text(size: 8pt, fill: gray)[(Type)]

Coerce all values in an array to a target type.

- `array` (required): array of values to coerce
- `targetType` (required): target type — `"number"`, `"boolean"`, `"string"`
- `default` (optional): fallback value for failed coercions

```utlx
coerceAll(["1", "2", "three", "4"], "number", 0)
// [1, 2, 0, 4]
```

=== compactCSV(csv, options?) → string #text(size: 8pt, fill: gray)[(CSV)]

Compact a CSV string by removing extra whitespace.

- `csv` (required): CSV string to compact
- `options` (optional): formatting options

```utlx
// See Chapter 25 for CSV processing.
{
  compacted: compactCSV($input.csvData)
}
```

=== compactJSON(json, options?, indent?) → string #text(size: 8pt, fill: gray)[(JSON)]

Compact a JSON string by removing all unnecessary whitespace.

- `json` (required): JSON string to compact
- `options` (optional): formatting options
- `indent` (optional): indentation level

```bash
echo '{"data": {"name": "Alice", "age": 30}}' | utlx -e 'compactJSON(renderJson($input))'
# {"name":"Alice","age":30}
```

```utlx
// See Chapter 24 for JSON processing.
{
  minified: compactJSON(renderJson($input))
}
```

=== compactXML(xml, options?, indent?) → string #text(size: 8pt, fill: gray)[(XML)]

Compact an XML string by removing unnecessary whitespace between elements.

- `xml` (required): XML string to compact
- `options` (optional): formatting options
- `indent` (optional): indentation level

```utlx
// See Chapter 22 for XML processing.
{
  minified: compactXML(renderXml($input))
}
```

=== compareDates(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Compare two dates. Returns negative if date1 is before date2, zero if equal, positive if after.

- `date1` (required): first date
- `date2` (required): second date

```utlx
let d1 = parseDate("2026-01-01", "yyyy-MM-dd")
let d2 = parseDate("2026-06-01", "yyyy-MM-dd")
{
  before: compareDates(d1, d2),    // negative (d1 is before d2)
  after: compareDates(d2, d1),     // positive (d2 is after d1)
  equal: compareDates(d1, d1)      // 0 (equal)
}
```

=== compoundInterest(principal, rate, periods) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate compound interest (total amount after compounding).

- `principal` (required): initial amount
- `rate` (required): interest rate per period as decimal
- `periods` (required): number of compounding periods

```bash
echo '{"principal": 1000, "rate": 0.05, "years": 10}' \
  | utlx -e 'compoundInterest($input.principal, $input.rate, $input.years)'
# 1628.89 (approximately)
```

```utlx
{
  futureValue: compoundInterest($input.principal, $input.annualRate, $input.years)
}
```

=== compress(data, algorithm?) → binary #text(size: 8pt, fill: gray)[(Bin)]

Compress binary data using the specified algorithm.

- `data` (required): binary data to compress
- `algorithm` (optional, default `"gzip"`): compression algorithm — `"gzip"`, `"deflate"`

```utlx
{
  compressed: base64Encode(compress(toBinary($input.payload, "UTF-8"), "gzip"))
}
```

=== constantCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to CONSTANT_CASE (uppercase with underscores).

- `string` (required): the string to convert

```utlx
constantCase("hello world")        // "HELLO_WORLD"
constantCase("camelCase")          // "CAMEL_CASE"
constantCase("some-kebab-case")    // "SOME_KEBAB_CASE"
```

=== containsValue(object, value) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Check if an object contains a specific value (searches all values).

- `object` (required): object to search
- `value` (required): value to find

```utlx
let config = {host: "localhost", port: 5432, db: "mydb"}
{
  hasLocalhost: containsValue(config, "localhost"),   // true
  hasRedis: containsValue(config, "redis")           // false
}
```

=== convertTimezone(datetime, fromTz, toTz) → datetime #text(size: 8pt, fill: gray)[(Date)]

Convert a datetime from one timezone to another.

- `datetime` (required): the datetime value to convert
- `fromTz` (required): source timezone (e.g., `"America/New_York"`)
- `toTz` (required): target timezone (e.g., `"Europe/Amsterdam"`)

```bash
echo '{"timestamp": "2026-05-01T09:00:00", "fromTz": "America/New_York", "toTz": "Europe/Amsterdam"}' \
  | utlx -e 'formatDate(convertTimezone(parseDate($input.timestamp, "yyyy-MM-dd'\''T'\''HH:mm:ss"), $input.fromTz, $input.toTz), "HH:mm")'
# "15:00"
```

```utlx
{
  localTime: convertTimezone(now(), "UTC", "Europe/Amsterdam")
}
```

=== convertXMLEncoding(xml, targetEncoding) → binary #text(size: 8pt, fill: gray)[(XML)]

Convert an XML document to a different character encoding. See Chapter 22.

- `xml` (required): XML string or UDM value
- `targetEncoding` (required): target encoding — `"UTF-8"`, `"ISO-8859-1"`, `"UTF-16"`, etc.

```utlx
// See Chapter 22 for XML encoding handling.
{
  latin1Xml: convertXMLEncoding($input, "ISO-8859-1")
}
```

=== cos(radians) → number #text(size: 8pt, fill: gray)[(Num)]

Cosine of an angle in radians.

- `radians` (required): angle in radians

```utlx
cos(0)         // 1.0
cos(pi())      // -1.0
cos(pi() / 2)  // ~0.0 (very small number due to floating point)
```

=== cosh(number) → number #text(size: 8pt, fill: gray)[(Num)]

Hyperbolic cosine.

- `number` (required): input value

```utlx
cosh(0)        // 1.0
cosh(1)        // 1.5430806348152437
```

=== countEntries(object, predicate?) → number #text(size: 8pt, fill: gray)[(Obj)]

Count entries in an object, optionally filtered by a predicate.

- `object` (required): the object to count entries of
- `predicate` (optional): lambda `(key, value) -> boolean`

```utlx
let obj = {a: 1, b: null, c: 3, d: null}
{
  total: countEntries(obj),                            // 4
  nonNull: countEntries(obj, (k, v) -> v != null)     // 2
}
```

=== createCDATA(content) → string #text(size: 8pt, fill: gray)[(XML)]

Create a CDATA section wrapping the given content.

- `content` (required): text content to wrap

```utlx
// See Chapter 22 for XML processing.
{
  wrapped: createCDATA("<script>alert('hi')</script>")
}
// Output: {"wrapped": "<![CDATA[<script>alert('hi')</script>]]>"}
```

=== createSOAPEnvelope(body, header?) → xml #text(size: 8pt, fill: gray)[(XML)]

Create a SOAP envelope with proper namespace prefixes.

- `body` (required): XML content for the SOAP Body
- `header` (optional): XML content for the SOAP Header

```utlx
// See Chapter 22 for XML/SOAP processing.
{
  soapMessage: createSOAPEnvelope($input.requestBody, $input.authHeader)
}
```

=== crossJoin(array1, array2) → array #text(size: 8pt, fill: gray)[(Arr)]

Cartesian product of two arrays — every combination of elements from both.

- `array1` (required): first array
- `array2` (required): second array

```bash
echo '{"sizes": ["S", "M", "L"], "colors": ["red", "blue"]}' \
  | utlx -e 'crossJoin($input.sizes, $input.colors)'
# [["S","red"],["S","blue"],["M","red"],["M","blue"],["L","red"],["L","blue"]]
```

```utlx
{
  variants: map(crossJoin($input.sizes, $input.colors), (pair) -> {
    size: pair[0], color: pair[1]
  })
}
```

=== csvAddColumn(csv, name, defaultValue) → string #text(size: 8pt, fill: gray)[(CSV)]

Add a new column with a default value to all rows in a CSV string.

- `csv` (required): CSV string
- `name` (required): new column name
- `defaultValue` (required): default value for all rows

```utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  result: csvAddColumn(csv, "Status", "active")
}
// Output: {result: "Name,Age,Status\nAlice,30,active\nBob,25,active"}
```

=== csvCell(csv, row, column) → string #text(size: 8pt, fill: gray)[(CSV)]

Get a specific cell value by row index and column name.

- `csv` (required): CSV string
- `row` (required): row index (0-based, excluding header)
- `column` (required): column name

```utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  name: csvCell(csv, 0, "Name"),    // "Alice"
  age: csvCell(csv, 1, "Age")      // "25"
}
```

=== csvColumn(csv, name) → array #text(size: 8pt, fill: gray)[(CSV)]

Get all values from a specific column as an array.

- `csv` (required): CSV string
- `name` (required): column name

```utlx
let csv = "Name,Age\nAlice,30\nBob,25\nCharlie,35"
{
  names: csvColumn(csv, "Name")     // ["Alice", "Bob", "Charlie"]
}
```

=== csvColumns(csv) → array #text(size: 8pt, fill: gray)[(CSV)]

Get all column names (headers) from CSV data.

- `csv` (required): CSV string

```utlx
let csv = "Name,Age,Email\nAlice,30,alice@example.com"
{
  headers: csvColumns(csv)    // ["Name", "Age", "Email"]
}
```

=== csvRemoveColumns(csv, columns) → string #text(size: 8pt, fill: gray)[(CSV)]

Remove specified columns from a CSV string.

- `csv` (required): CSV string
- `columns` (required): array of column names to remove

```utlx
let csv = "Name,Age,Email,Phone\nAlice,30,a@b.com,555-1234"
{
  stripped: csvRemoveColumns(csv, ["Phone", "Email"])
}
// Output: {stripped: "Name,Age\nAlice,30"}
```

=== csvRow(csv, index, options?) → object #text(size: 8pt, fill: gray)[(CSV)]

Get a specific row by index as an object (keyed by column names).

- `csv` (required): CSV string
- `index` (required): row index (0-based, excluding header)
- `options` (optional): parsing options

```utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  firstRow: csvRow(csv, 0)    // {Name: "Alice", Age: "30"}
}
```

=== csvRows(csv) → number #text(size: 8pt, fill: gray)[(CSV)]

Get the number of data rows in a CSV string (excluding header).

- `csv` (required): CSV string

```utlx
let csv = "Name,Age\nAlice,30\nBob,25\nCharlie,35"
{
  rowCount: csvRows(csv)    // 3
}
```

=== csvSelectColumns(csv, columns) → string #text(size: 8pt, fill: gray)[(CSV)]

Select/project only specific columns from a CSV string.

- `csv` (required): CSV string
- `columns` (required): array of column names to keep

```utlx
let csv = "Name,Age,Email,Phone\nAlice,30,a@b.com,555-1234"
{
  projected: csvSelectColumns(csv, ["Name", "Email"])
}
// Output: {projected: "Name,Email\nAlice,a@b.com"}
```

=== csvSort(csv, column, ascending?) → string #text(size: 8pt, fill: gray)[(CSV)]

Sort CSV rows by a specified column.

- `csv` (required): CSV string
- `column` (required): column name to sort by
- `ascending` (optional, default `true`): sort direction

```utlx
let csv = "Name,Age\nCharlie,35\nAlice,30\nBob,25"
{
  sorted: csvSort(csv, "Name", true)
}
// Output: {sorted: "Name,Age\nAlice,30\nBob,25\nCharlie,35"}
```

=== csvSummarize(csv, options?) → object #text(size: 8pt, fill: gray)[(CSV)]

Calculate summary statistics for numeric CSV columns (count, sum, avg, min, max).

- `csv` (required): CSV string
- `options` (optional): summarization options

```utlx
let csv = "Product,Price,Qty\nA,10,5\nB,20,3\nC,15,8"
{
  stats: csvSummarize(csv)
}
// Output: {stats: {Price: {count: 3, sum: 45, avg: 15, min: 10, max: 20},
//   Qty: {count: 3, sum: 16, ...}}}
```

=== csvTranspose(csv, options?, header?, separator?) → string #text(size: 8pt, fill: gray)[(CSV)]

Transpose CSV — swap rows and columns.

- `csv` (required): CSV string
- `options` (optional): formatting options
- `header` (optional): include header row
- `separator` (optional): field separator

```utlx
let csv = "Name,Age\nAlice,30\nBob,25"
{
  transposed: csvTranspose(csv)
}
// Output: {transposed: "Name,Alice,Bob\nAge,30,25"}
```

=== currentDir() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the current working directory path.

```utlx
{
  workingDir: currentDir()
}
// Output: {"workingDir": "/home/user/project"}
```

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

```bash
echo '{"items": ["A", "B", "C", "D", "E", "F", "G"]}' \
  | utlx -e 'chunk($input.items, 3)'
# [["A", "B", "C"], ["D", "E", "F"], ["G"]]
```

```utlx
// Batch processing — send items in groups of 100
{
  batches: map(chunk($input.records, 100), (batch) -> {
    batchSize: count(batch),
    items: batch
  })
}
```

=== coalesce(value1, value2, ...) → value #text(size: 8pt, fill: gray)[(Type)]

Returns the first non-null argument. Accepts any number of arguments.

- `value1, value2, ...` (variadic): values to check in order

```bash
echo '{"nickname": null, "displayName": null, "fullName": "Alice Johnson"}' \
  | utlx -e 'coalesce($input.nickname, $input.displayName, $input.fullName, "Anonymous")'
# "Alice Johnson"
```

```utlx
{
  name: coalesce($input.nickname, $input.displayName, $input.fullName, "Anonymous")
}
```

*Note:* for two values, the `??` operator is cleaner: `$input.name ?? "Unknown"` (see Chapter 9).

=== compact(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove null, empty string, and false values from an array.

- `array` (required): the array to compact

```bash
echo '{"tags": ["urgent", null, "", "review", null, "important"]}' \
  | utlx -e 'compact($input.tags)'
# ["urgent", "review", "important"]
```

```utlx
{
  contacts: compact([
    $input.name,
    $input.email,
    $input.phone
  ])
}
// null values are removed from the resulting array
```

=== concat(string1, string2, ...) → string #text(size: 8pt, fill: gray)[(Str)]

Concatenate any number of strings.

- `string1, string2, ...` (variadic): strings to concatenate

```bash
echo '{"title": "Dr.", "firstName": "Alice", "lastName": "Johnson"}' \
  | utlx -e 'concat($input.title, " ", $input.firstName, " ", $input.lastName)'
# "Dr. Alice Johnson"
```

```utlx
{
  fullName: concat($input.title, " ", $input.firstName, " ", $input.lastName),
  reference: concat("Order-", toString($input.orderId))
}
```

*Anti-pattern:* building long strings with `reduce` + `concat`. Use `join(array, separator)` instead.

=== contains(haystack, needle) → boolean #text(size: 8pt, fill: gray)[(Str/Arr)]

Check if a string contains a substring, or an array contains a value.

- `haystack` (required): the string or array to search in
- `needle` (required): the value to search for

```bash
echo '{"roles": ["admin", "editor", "viewer"]}' \
  | utlx -e 'contains($input.roles, "admin")'
# true
```

```utlx
{
  hasWorld: contains("Hello World", "World"),    // true (string variant)
  isAdmin: contains($input.roles, "admin"),     // true (array variant)
  activeOrders: filter($input.orders, (o) -> contains(["ACTIVE", "PENDING"], o.status))
}
```

=== count(array) → number #text(size: 8pt, fill: gray)[(Arr)]

Count all elements in an array.

- `array` (required): the array to count

```bash
echo '{"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}' \
  | utlx -e 'count($input.orders)'
# 3
```

```utlx
{
  totalOrders: count($input.orders),
  totalItems: count($input.items)
}
```

=== countBy(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

Count elements in an array that match a predicate.

- `array` (required): the array to count
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"orders": [{"status": "ACTIVE"}, {"status": "CLOSED"}, {"status": "ACTIVE"}]}' \
  | utlx -e 'countBy($input.orders, (o) -> o.status == "ACTIVE")'
# 2
```

```utlx
let active = filter($input.orders, (o) -> o.status == "ACTIVE")
{
  activeCount: count(active),
  closedCount: countBy($input.orders, (o) -> o.status == "CLOSED"),
  activeNames: map(active, (o) -> o.name)
}
```

=== createQname(localName, namespaceUri, prefix?) → object #text(size: 8pt, fill: gray)[(XML)]

Create a structured QName from its parts. See Chapter 22.

- `localName` (required): the local element name without prefix
- `namespaceUri` (required): the full namespace URI
- `prefix` (optional): the namespace prefix

```utlx
// See Chapter 22 for XML namespaces.
{
  qname: createQname("Invoice",
    "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "cbc")
}
// Output: {qname: {localName: "Invoice",
//   namespaceUri: "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
//   prefix: "cbc", qualifiedName: "cbc:Invoice"}}

// Without prefix:
{
  qname: createQname("Order", "urn:example:orders")
}
// Output: {qname: {localName: "Order",
//   namespaceUri: "urn:example:orders",
//   prefix: "", qualifiedName: "Order"}}
```

=== csvFilter(csv, column, operator, value) → string #text(size: 8pt, fill: gray)[(CSV)]

Filter CSV rows by a column condition. Returns a new CSV string. See Chapter 25.

- `csv` (required): CSV string to filter
- `column` (required): column name to test
- `operator` (required): comparison — `"eq"`, `"ne"`, `"contains"`, `"startswith"`, `"endswith"`, `"gt"`, `"lt"`, `"gte"`, `"lte"`
- `value` (required): value to compare against

```utlx
// See Chapter 25 for CSV processing.
let csv = "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200\nCharlie,ACTIVE,50"
{
  active: csvFilter(csv, "Status", "eq", "ACTIVE"),
  // "Name,Status,Amount\nAlice,ACTIVE,100\nCharlie,ACTIVE,50"
  highValue: csvFilter(csv, "Amount", "gt", "75")
  // "Name,Status,Amount\nAlice,ACTIVE,100\nBob,CLOSED,200"
}
```

Also: `csvSort(csv, column, ascending?)`, `csvColumns(csv)`, `csvRows(csv)`, `csvRow(csv, index)`, `csvCell(csv, row, column)`, `csvColumn(csv, name)`, `csvTranspose(csv)`, `csvAddColumn(csv, name, default)`, `csvRemoveColumns(csv, names)`, `csvSelectColumns(csv, names)`, `csvSummarize(csv)`.

== D

=== day(date) → number #text(size: 8pt, fill: gray)[(Date)]

Extract the day-of-month component (1-31) from a date.

- `date` (required): a date or datetime value

```utlx
{
  dayOfMonth: day(parseDate("2026-05-15", "yyyy-MM-dd")),    // 15
  today: day(now())                                          // current day of month
}
```

=== dayOfMonth(date) → number #text(size: 8pt, fill: gray)[(Date)]

Alias for `day()`. Extract the day-of-month component (1-31) from a date.

- `date` (required): a date or datetime value

```utlx
{
  dom: dayOfMonth(parseDate("2026-05-15", "yyyy-MM-dd"))    // 15
}
```

=== dayOfWeek(date) → number #text(size: 8pt, fill: gray)[(Date)]

Return the day of the week as a number (1=Monday, 7=Sunday).

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  weekday: dayOfWeek(d)          // 5 (Thursday — 1=Monday, 7=Sunday)
}
```

=== dayOfWeekName(date) → string #text(size: 8pt, fill: gray)[(Date)]

Return the day of the week as a name (e.g., "Thursday").

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  weekdayName: dayOfWeekName(d)      // "Thursday"
}
```

=== dayOfYear(date) → number #text(size: 8pt, fill: gray)[(Date)]

Return the day number within the year (1-365 or 1-366 for leap years).

- `date` (required): a date or datetime value

```utlx
let d = parseDate("2026-05-01", "yyyy-MM-dd")
{
  ordinalDay: dayOfYear(d)          // 121 (121st day of 2026)
}
```

Also: `daysInMonth(year, month)` → `daysInMonth(2026, 2)` returns `28`. `daysInYear(year)` → `daysInYear(2024)` returns `366` (leap year). `isLeapYear(year)`.

=== daysBetween(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Alias for `diffDays()`. Calculate the difference in days between two dates.

- `date1` (required): start date
- `date2` (required): end date

```utlx
let start = parseDate("2026-05-01", "yyyy-MM-dd")
let end = parseDate("2026-05-15", "yyyy-MM-dd")
{
  days: daysBetween(start, end)    // 14
}
```

=== daysInMonth(date) → number #text(size: 8pt, fill: gray)[(Date)]

Get the number of days in the month of the given date.

- `date` (required): a date or datetime value

```utlx
{
  feb2026: daysInMonth(parseDate("2026-02-01", "yyyy-MM-dd")),   // 28
  feb2024: daysInMonth(parseDate("2024-02-01", "yyyy-MM-dd")),   // 29 (leap year)
  jan: daysInMonth(parseDate("2026-01-15", "yyyy-MM-dd"))        // 31
}
```

=== daysInYear(date) → number #text(size: 8pt, fill: gray)[(Date)]

Get the number of days in the year of the given date (365 or 366 for leap years).

- `date` (required): a date or datetime value

```utlx
{
  y2026: daysInYear(parseDate("2026-01-01", "yyyy-MM-dd")),    // 365
  y2024: daysInYear(parseDate("2024-01-01", "yyyy-MM-dd"))     // 366 (leap year)
}
```

=== debug(value, message?) → value #text(size: 8pt, fill: gray)[(Sys)]

Log a value at DEBUG level and pass it through (does not consume the value).

- `value` (required): value to log and return
- `message` (optional): label for the log entry

```utlx
{
  result: debug($input.amount, "processing amount")
}
// logs: [DEBUG] processing amount: 150.00
// output: {"result": 150.00}
```

=== debugPrint(value, label?, indent?) → string #text(size: 8pt, fill: gray)[(Sys)]

Create a human-readable debug representation of a UDM value (multi-line, indented).

- `value` (required): value to represent
- `label` (optional): label prefix
- `indent` (optional): indentation level

```utlx
{
  dump: debugPrint($input, "request")
}
// Output: {"dump": "[request] {name: \"Alice\", age: 30, ...}"}
```

=== debugPrintCompact(value, label?) → string #text(size: 8pt, fill: gray)[(Sys)]

Create a compact single-line debug representation of a UDM value.

- `value` (required): value to represent
- `label` (optional): label prefix

```utlx
{
  dump: debugPrintCompact($input, "payload")
}
// Output: {"dump": "[payload] {name:Alice,age:30}"}
```

=== decodeJWS(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Decode a JWS (JSON Web Signature) token WITHOUT verifying the signature. Returns header and payload.

- `token` (required): JWS token string

```utlx
{
  decoded: decodeJWS($input.token),
  algorithm: decodeJWS($input.token).header.alg
}
```

=== decodeJWT(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Decode a JWT token WITHOUT verification. Returns header, payload (claims), and signature.

- `token` (required): JWT token string

```bash
echo '{"token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.sig"}' \
  | utlx -e 'decodeJWT($input.token).payload.sub'
# "user1"
```

```utlx
let jwt = decodeJWT($input.authToken)
{
  subject: jwt.payload.sub,
  issuer: jwt.payload.iss,
  expired: jwt.payload.exp < timestamp()
}
```

=== decompress(data, algorithm?) → binary #text(size: 8pt, fill: gray)[(Bin)]

Decompress binary data using the specified algorithm.

- `data` (required): compressed binary data
- `algorithm` (optional, default `"gzip"`): decompression algorithm — `"gzip"`, `"deflate"`

```utlx
{
  content: binaryToString(decompress(base64Decode($input.compressedPayload), "gzip"), "UTF-8")
}
```

=== decryptAES(data, key, iv) → string #text(size: 8pt, fill: gray)[(Sec)]

Decrypt data using AES-128-CBC.

- `data` (required): Base64-encoded encrypted data
- `key` (required): 16-byte encryption key (Base64 or hex)
- `iv` (required): 16-byte initialization vector (Base64 or hex)

```utlx
{
  plaintext: decryptAES($input.encrypted, $input.key, $input.iv)
}
```

=== decryptAES256(data, key, iv) → string #text(size: 8pt, fill: gray)[(Sec)]

Decrypt data using AES-256-CBC (requires 32-byte key).

- `data` (required): Base64-encoded encrypted data
- `key` (required): 32-byte encryption key (Base64 or hex)
- `iv` (required): 16-byte initialization vector (Base64 or hex)

```utlx
{
  plaintext: decryptAES256($input.ciphertext, $input.key256, $input.iv)
}
```

=== deepClone(object) → object #text(size: 8pt, fill: gray)[(Obj)]

Create a deep (recursive) copy of an object. Modifications to the clone do not affect the original.

- `object` (required): the object to clone

```utlx
let original = {nested: {value: 42}}
let copy = deepClone(original)
// copy is a fully independent copy — no shared references
{
  cloned: copy    // {nested: {value: 42}}
}
```

=== deepMerge(obj1, obj2) → object #text(size: 8pt, fill: gray)[(Obj)]

Recursively merge two objects. At each level, properties from `obj2` override `obj1`. Nested objects are merged recursively (not replaced).

- `obj1` (required): base object
- `obj2` (required): override object

```utlx
let base = {server: {host: "localhost", port: 5432, ssl: false}}
let prod = {server: {host: "prod-db.example.com", ssl: true}}
{
  config: deepMerge(base, prod)
}
// Output: {config: {server: {host: "prod-db.example.com", port: 5432, ssl: true}}}
// port survived from base — deep merge, not replace
```

*Contrast with spread:* `{...base, ...prod}` would REPLACE the entire `server` object, losing `port`. `deepMerge` preserves nested properties.

=== deepMergeAll(objects) → object #text(size: 8pt, fill: gray)[(Obj)]

Deep merge multiple objects in order (later objects override earlier ones at each nesting level).

- `objects` (required): array of objects to merge

```utlx
let configs = [
  {server: {host: "localhost", port: 5432}},
  {server: {host: "staging-db.internal"}},
  {server: {ssl: true}}
]
{
  merged: deepMergeAll(configs)
}
// Output: {merged: {server: {host: "staging-db.internal", port: 5432, ssl: true}}}
```

=== deflate(data) → binary #text(size: 8pt, fill: gray)[(Bin)]

Compress data using raw Deflate algorithm (no gzip header/trailer).

- `data` (required): binary data to compress

```utlx
{
  deflated: base64Encode(deflate(toBinary($input.payload, "UTF-8")))
}
```

=== destinationPoint(lat, lon, bearing, distance) → object #text(size: 8pt, fill: gray)[(Geo)]

Calculate a destination point given a starting point, bearing, and distance.

- `lat` (required): starting latitude
- `lon` (required): starting longitude
- `bearing` (required): bearing in degrees (0-360)
- `distance` (required): distance in kilometers

```utlx
{
  destination: destinationPoint(52.37, 4.90, 180, 100)
}
// Output: {"lat": 51.47, "lon": 4.90} (approximately 100km due south)
```

=== detectBOM(binary) → string #text(size: 8pt, fill: gray)[(XML)]

Detect the Byte Order Mark type from binary data. Returns the encoding name or null if no BOM found.

- `binary` (required): binary data to inspect

```utlx
{
  bomType: detectBOM($input.rawData)
}
// Output: {"bomType": "UTF-8"} or {"bomType": null}
```

=== detectXMLEncoding(xmlString) → string #text(size: 8pt, fill: gray)[(XML)]

Detect the encoding declared in an XML document's declaration.

- `xmlString` (required): XML string or UDM value

```utlx
// Input: <?xml version="1.0" encoding="ISO-8859-1"?><Order>...</Order>
// See Chapter 22 for XML processing.
{
  encoding: detectXMLEncoding($input)
}
// Output: {"encoding": "ISO-8859-1"}

// Input: <Order>...</Order>  (no encoding declaration — defaults to UTF-8)
{
  encoding: detectXMLEncoding($input)
}
// Output: {"encoding": "UTF-8"}
```

Also: `convertXMLEncoding(xml, targetEncoding)` re-encodes the document.

=== diffDays(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference between two dates in days. Variants: `diffMonths`, `diffYears`, `diffHours`, `diffMinutes`, `diffSeconds`, `diffWeeks`.

- `date1` (required): start date
- `date2` (required): end date

```bash
echo '{"start": "2026-05-01", "end": "2026-06-15"}' \
  | utlx -e 'diffDays(parseDate($input.start, "yyyy-MM-dd"), parseDate($input.end, "yyyy-MM-dd"))'
# 45
```

```utlx
let start = parseDate($input.startDate, "yyyy-MM-dd")
let end = parseDate($input.endDate, "yyyy-MM-dd")
{
  daysBetween: diffDays(start, end),
  weeksBetween: diffWeeks(start, end),
  overdue: if (diffDays(end, now()) > 0) "Yes" else "No"
}
```

=== diffHours(datetime1, datetime2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in hours between two datetimes.

- `datetime1` (required): start datetime
- `datetime2` (required): end datetime

```utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T17:30:00", "yyyy-MM-dd'T'HH:mm:ss")
{
  hours: diffHours(start, end)    // 9
}
```

=== diffMinutes(datetime1, datetime2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in minutes between two datetimes.

- `datetime1` (required): start datetime
- `datetime2` (required): end datetime

```utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T08:45:00", "yyyy-MM-dd'T'HH:mm:ss")
{
  minutes: diffMinutes(start, end)    // 45
}
```

=== diffMonths(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in months between two dates (approximate whole months).

- `date1` (required): start date
- `date2` (required): end date

```utlx
let start = parseDate("2026-01-15", "yyyy-MM-dd")
let end = parseDate("2026-05-15", "yyyy-MM-dd")
{
  months: diffMonths(start, end)    // 4
}
```

=== diffSeconds(datetime1, datetime2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in seconds between two datetimes.

- `datetime1` (required): start datetime
- `datetime2` (required): end datetime

```utlx
let start = parseDate("2026-05-01T08:00:00", "yyyy-MM-dd'T'HH:mm:ss")
let end = parseDate("2026-05-01T08:01:30", "yyyy-MM-dd'T'HH:mm:ss")
{
  seconds: diffSeconds(start, end)    // 90
}
```

=== diffWeeks(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in weeks between two dates.

- `date1` (required): start date
- `date2` (required): end date

```utlx
let start = parseDate("2026-05-01", "yyyy-MM-dd")
let end = parseDate("2026-05-22", "yyyy-MM-dd")
{
  weeks: diffWeeks(start, end)    // 3
}
```

=== diffYears(date1, date2) → number #text(size: 8pt, fill: gray)[(Date)]

Difference in years between two dates.

- `date1` (required): start date
- `date2` (required): end date

```utlx
let start = parseDate("2020-01-01", "yyyy-MM-dd")
let end = parseDate("2026-05-01", "yyyy-MM-dd")
{
  years: diffYears(start, end)    // 6
}
```

=== distance(lat1, lon1, lat2, lon2) → number #text(size: 8pt, fill: gray)[(Geo)]

Calculate the distance in kilometers between two geographic coordinates using the Haversine formula.

- `lat1` (required): latitude of point 1
- `lon1` (required): longitude of point 1
- `lat2` (required): latitude of point 2
- `lon2` (required): longitude of point 2

```bash
echo '{"from": {"lat": 52.37, "lon": 4.90}, "to": {"lat": 48.86, "lon": 2.35}}' \
  | utlx -e 'distance($input.from.lat, $input.from.lon, $input.to.lat, $input.to.lon)'
# 430.5 (approximately — Amsterdam to Paris in km)
```

```utlx
{
  distanceKm: distance($input.origin.lat, $input.origin.lon, $input.dest.lat, $input.dest.lon)
}
```

=== distinct(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values from an array using value equality.

- `array` (required): the array to deduplicate

```bash
echo '{"tags": ["a", "b", "a", "c", "b"]}' | utlx -e 'distinct($input.tags)'
# ["a", "b", "c"]
```

```utlx
{
  uniqueCustomers: distinct(map($input.orders, (o) -> o.customerId)),
  uniqueStatuses: distinct(map($input.orders, (o) -> o.status))
}
```

=== distinctBy(array, keyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values from an array using a key extractor to determine uniqueness. Keeps the first element for each key.

- `array` (required): the array to deduplicate
- `keyFn` (required): lambda `(element) -> key`

```bash
echo '{"orders": [{"id": 1, "cust": "C-42"}, {"id": 2, "cust": "C-42"}, {"id": 3, "cust": "C-41"}]}' \
  | utlx -e 'distinctBy($input.orders, (o) -> o.cust)'
# [{"id": 1, "cust": "C-42"}, {"id": 3, "cust": "C-41"}]
```

```utlx
{
  onePerCustomer: distinctBy($input.orders, (o) -> o.customerId)
}
// keeps first order per customer, removes duplicates
```

=== divideBy(object, n) → array #text(size: 8pt, fill: gray)[(Obj)]

Divide an object into sub-objects each containing at most N key-value pairs.

- `object` (required): object to split
- `n` (required): max entries per sub-object

```bash
echo '{"a": 1, "b": 2, "c": 3, "d": 4, "e": 5}' | utlx -e 'divideBy($input, 2)'
# [{"a": 1, "b": 2}, {"c": 3, "d": 4}, {"e": 5}]
```

```utlx
{
  batches: divideBy($input.config, 3)
}
```

=== dotCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to dot.case (lowercase words separated by dots).

- `string` (required): the string to convert

```utlx
dotCase("hello world")        // "hello.world"
dotCase("camelCase")          // "camel.case"
dotCase("SCREAMING_SNAKE")    // "screaming.snake"
```

=== drop(array, n) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove the first N elements from an array, returning the rest.

- `array` (required): the source array
- `n` (required): number of elements to drop

```bash
echo '{"items": ["A", "B", "C", "D", "E"]}' | utlx -e 'drop($input.items, 2)'
# ["C", "D", "E"]
```

```utlx
// Skip CSV header row (when headers: false)
let dataRows = drop($input, 1)
{
  rows: map(dataRows, (row) -> { name: row[0], value: row[1] })
}
```

=== take(array, n) → array #text(size: 8pt, fill: gray)[(Arr)]

Keep only the first N elements from an array, discarding the rest.

- `array` (required): the source array
- `n` (required): number of elements to keep

```bash
echo '{"items": ["A", "B", "C", "D", "E"]}' | utlx -e 'take($input.items, 3)'
# ["A", "B", "C"]
```

```utlx
{
  top10: take(sortBy($input.products, (p) -> -p.sales), 10),
  preview: take($input.items, 5)
}
```

