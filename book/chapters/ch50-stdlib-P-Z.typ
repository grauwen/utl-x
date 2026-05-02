== O

=== omit(object, keys) → object #text(size: 8pt, fill: gray)[(Obj)]

Return a new object WITHOUT the listed properties.

- `object` (required): the source object
- `keys` (required): array of property names to exclude

```bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "role": "admin"}' | utlx -e 'omit($input, ["password"])'
# {"name": "Alice", "email": "alice@example.com", "role": "admin"}
```

```utlx
let safe = omit($input, ["password", "apiKey", "token", "secret"])
{
  sanitized: safe
}
```

=== pick(object, keys) → object #text(size: 8pt, fill: gray)[(Obj)]

Return a new object WITH ONLY the listed properties.

- `object` (required): the source object
- `keys` (required): array of property names to keep

```bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "role": "admin"}' | utlx -e 'pick($input, ["name", "email"])'
# {"name": "Alice", "email": "alice@example.com"}
```

=== osArch #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== osVersion #text(size: 8pt, fill: gray)[(TODO)]

// TODO

== P

=== pad #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== padLeft #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== padRight #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parent #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parse(string, format?) → value #text(size: 8pt, fill: gray)[(Fmt)]

Parse a string into a navigable UDM value, auto-detecting format or using the specified format.

- `string` (required): the string to parse
- `format` (optional, default auto-detect): `"json"`, `"xml"`, `"yaml"`, `"csv"`

```utlx
// Parse XML from a CDATA section:
let innerXml = parse($input.Payload, "xml")
innerXml.Order.Customer                  // "Acme Corp"

// Auto-detect format:
let parsed = parse($input.rawData)       // auto-detects JSON, XML, or YAML
```

For normal file processing, use `input json`/`input xml` in the header — these functions are for the embedded-format-as-value case.

=== parseJson(string) → value #text(size: 8pt, fill: gray)[(Fmt)]

Parse a JSON string into a navigable UDM value.

- `string` (required): the JSON string to parse

```utlx
let config = parseJson($input.configJson)
config.database.host                     // "localhost"
```

=== parseXml(string) → value #text(size: 8pt, fill: gray)[(Fmt)]

Parse an XML string into a navigable UDM value.

- `string` (required): the XML string to parse

```utlx
let doc = parseXml($input.xmlPayload)
doc.Order.Customer                       // "Acme Corp"
```

=== parseYaml(string) → value #text(size: 8pt, fill: gray)[(Fmt)]

Parse a YAML string into a navigable UDM value.

- `string` (required): the YAML string to parse

```utlx
let config = parseYaml($input.yamlConfig)
config.database.host                     // "localhost"
```

=== parseCsv(string, options?) → array #text(size: 8pt, fill: gray)[(Fmt)]

Parse a CSV string into an array of rows (objects if headers, arrays if not).

- `string` (required): the CSV string to parse
- `options` (optional): `{headers: false}`, `{delimiter: ";"}`

```utlx
let data = parseCsv($input.csvData, {delimiter: ";", headers: true})
data[0].Name                             // first row, Name column
```

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
let d = parseDate($input.date, "dd/MM/yyyy")
{
  isoDate: formatDate(d, "yyyy-MM-dd"),
  display: formatDate(d, "MMMM d, yyyy")
}
```

```bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'formatDate(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "dd MMM yyyy")'
# 01 May 2026
```

*Anti-pattern:* assuming date format — `01/02/2026` is January 2nd (US `MM/dd/yyyy`) or February 1st (EU `dd/MM/yyyy`). Always specify the pattern explicitly.

Also: `parseDateTimeWithTimezone(string, pattern, timezone)`.

=== parseDateTimeWithTimezone #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseDouble #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== parseEUNumber(string) → number #text(size: 8pt, fill: gray)[(Num)]

Parse a European-formatted number string (dot=thousands, comma=decimal) to a standard number. See Chapter 25 (CSV).

- `string` (required): the formatted number string

```utlx
parseEUNumber("1.234,56")               // 1234.56 (European: dot=thousands, comma=decimal)

// Use case: CSV from European source
map($input, (row) -> {
  product: row.Product,
  price: parseEUNumber(row.Price),       // "29,99" → 29.99
  weight: parseEUNumber(row.Weight)      // "1.500,00" → 1500.0
})
```

Also: `renderEUNumber(number)` for the reverse direction, `parseFrenchNumber(string)`, `parseSwissNumber(string)`.

=== parseUSNumber(string) → number #text(size: 8pt, fill: gray)[(Num)]

Parse a US-formatted number string (comma=thousands, dot=decimal) to a standard number.

- `string` (required): the formatted number string

```utlx
parseUSNumber("1,234.56")               // 1234.56 (US: comma=thousands, dot=decimal)
```

Also: `renderUSNumber(number)` for the reverse direction.

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

```bash
echo '{"orders": [{"id": 1, "amount": 500}, {"id": 2, "amount": 1500}, {"id": 3, "amount": 200}]}' | utlx -e 'partition($input.orders, (o) -> o.amount > 1000)'
# [[{"id": 2, "amount": 1500}], [{"id": 1, "amount": 500}, {"id": 3, "amount": 200}]]
```

```utlx
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
// "Alice, Bob, Charlie"

// Build a lookup object from an array:
reduce($input, {}, (acc, item) -> {
  ...acc,
  [item.id]: item.name
})
// {"A": "Widget", "B": "Gadget"}

// Count occurrences:
reduce(["a", "b", "a", "c", "a"], {}, (acc, x) -> {
  ...acc,
  [x]: (acc[x] ?? 0) + 1
})
// {"a": 3, "b": 1, "c": 1}
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

=== render(value, format, pretty?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Serialize a UDM value to a string in the specified format.

- `value` (required): UDM value to serialize
- `format` (required): `"json"`, `"xml"`, `"yaml"`, `"csv"`
- `pretty` (optional, default false): pretty-print with indentation

```utlx
render({name: "Alice"}, "json", true)    // pretty-printed JSON
render({Order: {Id: "1"}}, "xml")        // "<Order><Id>1</Id></Order>"

// Use case: embed XML inside a JSON field (CDATA pattern)
{
  messageId: generateUuid(),
  payload: render($input, "xml")         // XML as a string value
}
```

=== renderJson(value, pretty?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Serialize a UDM value to a JSON string.

- `value` (required): UDM value to serialize
- `pretty` (optional, default false): pretty-print with indentation

```utlx
renderJson({name: "Alice", age: 30})        // '{"name":"Alice","age":30}'
renderJson({name: "Alice", age: 30}, true)  // pretty-printed with newlines
```

=== renderCsv #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== renderXml #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== renderYaml #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== repeat #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== replace(string, search, replacement) → string #text(size: 8pt, fill: gray)[(Str)]

Replace all literal occurrences of a substring.

- `string` (required): the string to modify
- `search` (required): literal substring to find
- `replacement` (required): what to replace with

```utlx
replace("Hello World", "World", "UTL-X")   // "Hello UTL-X"
replace("2026-05-01", "-", "/")             // "2026/05/01" (replaces ALL occurrences)
```

=== replaceRegex(string, regex, replacement) → string #text(size: 8pt, fill: gray)[(Str)]

Replace all occurrences matching a regular expression.

- `string` (required): the string to modify
- `regex` (required): regular expression pattern
- `replacement` (required): what to replace with

```utlx
replaceRegex("Order #123 on 2026-05-01", "[0-9]+", "X")  // "Order #X on X-X-X"
replaceRegex("  extra   spaces  ", "\\s+", " ")           // " extra spaces "
```

=== replaceWithFunction #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== resolveQName #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== reverse(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Reverse the order of elements in an array.

- `array` (required): the array to reverse

```utlx
reverse([1, 2, 3, 4, 5])                // [5, 4, 3, 2, 1]

// Use case: most recent first
reverse(sortBy($input.events, (e) -> e.timestamp))
```

=== reverseString(string) → string #text(size: 8pt, fill: gray)[(Str)]

Reverse the characters in a string.

- `string` (required): the string to reverse

```utlx
reverseString("hello")                   // "olleh"
```

=== rightJoin #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== roundToCents(number) → number #text(size: 8pt, fill: gray)[(Num)]

Round a number to 2 decimal places (financial rounding for currency).

- `number` (required): the value to round

```utlx
roundToCents(29.999)                     // 30.0
roundToCents(10.004)                     // 10.0

// Use case: invoice line total with correct rounding
let lineTotal = roundToCents(qty * unitPrice)
```

=== roundToDecimalPlaces(number, places) → number #text(size: 8pt, fill: gray)[(Num)]

Round a number to a specified number of decimal places.

- `number` (required): the value to round
- `places` (required): number of decimal places

```utlx
roundToDecimalPlaces(3.14159, 3)         // 3.142
roundToDecimalPlaces(100.0, 0)           // 100
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

=== sha256(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-256 hash. Returns 64-char hex string. See `hash` for generic algorithm selection.

- `data` (required): string to hash

```utlx
sha256("sensitive data")                 // 64-char hex string
```

=== sha512(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-512 hash. Returns 128-char hex string.

- `data` (required): string to hash

```utlx
sha512("sensitive data")                 // 128-char hex string
```

=== sha1(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-1 hash. Returns 40-char hex string. Avoid for security purposes.

- `data` (required): string to hash

```utlx
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

=== sort(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Sort an array using natural ordering (numbers ascending, strings alphabetical).

- `array` (required): the array to sort

```bash
echo '[3, 1, 4, 1, 5]' | utlx -e 'sort(.)'
# [1, 1, 3, 4, 5]
```

```utlx
sort([3, 1, 4, 1, 5, 9])                // [1, 1, 3, 4, 5, 9]
sort(["banana", "apple", "cherry"])      // ["apple", "banana", "cherry"]
```

=== sortBy(array, keyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Sort an array using a key extractor function.

- `array` (required): the array to sort
- `keyFn` (required): lambda `(element) -> sortKey`

```bash
echo '{"products": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}, {"name": "Gizmo", "price": 10}]}' | utlx -e 'sortBy($input.products, (p) -> p.price)'
# [{"name": "Gizmo", "price": 10}, {"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}]
```

```utlx
{
  cheapestFirst: sortBy($input.products, (p) -> p.price),
  expensiveFirst: sortBy($input.products, (p) -> -p.price),
  alphabetical: sortBy($input.products, (p) -> p.name)
}
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

=== substring(string, start, end?) → string #text(size: 8pt, fill: gray)[(Str)]

Extract part of a string by index (zero-based).

- `string` (required): the source string
- `start` (required): starting index (zero-based, inclusive)
- `end` (optional): ending index (exclusive). If omitted, goes to end.

```utlx
substring("Hello World", 6)             // "World"
substring("Hello World", 0, 5)          // "Hello"
```

=== substringBefore(string, delimiter) → string #text(size: 8pt, fill: gray)[(Str)]

Return the part of a string before the first occurrence of a delimiter. Returns the full string if delimiter is not found.

- `string` (required): the source string
- `delimiter` (required): the delimiter to search for

```utlx
substringBefore("user@example.com", "@") // "user"
substringBefore("no-delimiter", "@")     // "no-delimiter" (not found — returns all)
```

Also: `substringBeforeLast("a.b.c.d", ".")` returns `"a.b.c"`.

=== substringAfter(string, delimiter) → string #text(size: 8pt, fill: gray)[(Str)]

Return the part of a string after the first occurrence of a delimiter. Returns empty string if delimiter is not found.

- `string` (required): the source string
- `delimiter` (required): the delimiter to search for

```utlx
substringAfter("user@example.com", "@")  // "example.com"
substringAfter("no-delimiter", "@")      // "" (not found — returns empty)
```

Also: `substringAfterLast("a.b.c.d", ".")` returns `"d"`.

=== substringAfterLast #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== substringBeforeLast #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== sum(array) → number #text(size: 8pt, fill: gray)[(Num)]

Sum all numeric values in an array. Returns 0 for empty arrays.

- `array` (required): array of numbers

```utlx
sum([10, 20, 30])                        // 60
sum([])                                  // 0 (empty array)
```

*Anti-pattern:* `reduce($input.items, 0, (acc, i) -> acc + i.price)` — use `sum(map(...))` or `sumBy()`.

=== sumBy(array, fn) → number #text(size: 8pt, fill: gray)[(Num)]

Sum values extracted from an array of objects using a key function.

- `array` (required): array of objects
- `fn` (required): lambda `(element) -> number`

```bash
echo '{"items": [{"qty": 2, "price": 25}, {"qty": 5, "price": 10}, {"qty": 1, "price": 100}]}' | utlx -e 'sumBy($input.items, (i) -> i.qty * i.price)'
# 200
```

```utlx
{
  orderTotal: sumBy($input.items, (i) -> i.qty * i.price)
}
```

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
transpose([[1, 2, 3], [4, 5, 6]])          // [[1, 4], [2, 5], [3, 6]]
```

```utlx
{
  pivoted: transpose($input.table)
}
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
union([1, 2, 3], [3, 4, 5])                // [1, 2, 3, 4, 5]
union(["A", "B"], ["B", "C", "D"])          // ["A", "B", "C", "D"]
```

=== intersect(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Return values present in BOTH arrays.

- `arr1` (required): first array
- `arr2` (required): second array

```utlx
intersect([1, 2, 3], [2, 3, 4])            // [2, 3]
intersect(["A", "B", "C"], ["X", "Y"])     // [] (no common elements)
```

=== difference(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Return values in `arr1` that are NOT in `arr2`. Order matters — `difference(a, b)` is not the same as `difference(b, a)`.

- `arr1` (required): the source array
- `arr2` (required): the array to subtract

```utlx
difference([1, 2, 3], [2, 3, 4])           // [1]
difference([2, 3, 4], [1, 2, 3])           // [4]
```

```utlx
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
symmetricDifference([1, 2, 3], [2, 3, 4])  // [1, 4]
symmetricDifference([1, 2], [1, 2])         // [] (identical)
```

=== unique(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove duplicate values. Alias for `distinct()`. Preserves first occurrence.

- `array` (required): the array to deduplicate

```utlx
unique([1, 2, 2, 3, 3, 3])              // [1, 2, 3]
unique(["apple", "banana", "apple"])     // ["apple", "banana"]

// Use case: collect unique customer IDs
{
  customers: unique(map($input.orders, (o) -> o.customerId))
}
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

=== urlEncode(string) → string #text(size: 8pt, fill: gray)[(URL)]

URL-encode a string (percent-encoding per RFC 3986).

- `string` (required): the string to encode

```utlx
urlEncode("hello world")                 // "hello%20world"
urlEncode("price=10&currency=EUR")       // "price%3D10%26currency%3DEUR"

// Use case: build a query string
let qs = join(map(entries($input.params), (e) ->
  concat(urlEncode(e[0]), "=", urlEncode(toString(e[1])))
), "&")
// Or use the dedicated function:
buildQueryString($input.params)
```

Also: `urlEncodeComponent(string)`, `buildURL(base, path, params)`, `parseURL(url)`, `getHost(url)`, `getPath(url)`, `getQuery(url)`, `getQueryParams(url)`.

=== urlDecode(string) → string #text(size: 8pt, fill: gray)[(URL)]

Decode a URL-encoded string (percent-encoding per RFC 3986).

- `string` (required): the string to decode

```utlx
urlDecode("hello%20world")               // "hello world"
```

Also: `urlDecodeComponent(string)`.

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
windowed([1, 2, 3, 4, 5], 3)               // [[1, 2, 3], [2, 3, 4], [3, 4, 5]]
windowed([1, 2, 3, 4, 5], 2)               // [[1, 2], [2, 3], [3, 4], [4, 5]]
```

```utlx
let prices = $input.dailyPrices
{
  movingAvg3day: map(windowed(prices, 3), (w) -> avg(w)),
  consecutiveDups: filter(windowed($input.events, 2), (pair) -> pair[0].type == pair[1].type)
}
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

=== xmlEscape(string) → string #text(size: 8pt, fill: gray)[(XML)]

Escape XML special characters (`<`, `>`, `&`, `"`, `'`).

- `string` (required): the string to escape

```utlx
xmlEscape("price < 100 & tax > 0")         // "price &lt; 100 &amp; tax &gt; 0"
```

```utlx
{
  Comment: xmlEscape($input.userComment)
}
```

=== xmlUnescape(string) → string #text(size: 8pt, fill: gray)[(XML)]

Unescape XML entity references back to their original characters.

- `string` (required): the string to unescape

```utlx
xmlUnescape("price &lt; 100 &amp; tax &gt; 0")  // "price < 100 & tax > 0"
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

=== yamlSplitDocuments(yaml) → array #text(size: 8pt, fill: gray)[(YAML)]

Split a multi-document YAML string (separated by `---`) into an array of documents. See Chapter 26.

- `yaml` (required): YAML string containing multiple documents

```utlx
let docs = yamlSplitDocuments(multiDocString)
docs[0]                                  // first document
docs[1]                                  // second document
```

=== yamlMergeDocuments(docs) → string #text(size: 8pt, fill: gray)[(YAML)]

Merge an array of YAML documents back into a single multi-document string joined with `---` separators.

- `docs` (required): array of documents

```utlx
yamlMergeDocuments(docs)                 // joined with --- separators
```

=== yamlPath(yaml, path) → value #text(size: 8pt, fill: gray)[(YAML)]

Access a nested value in a YAML structure using a dot-separated path.

- `yaml` (required): YAML UDM value
- `path` (required): dot-separated path string

```utlx
yamlPath($input, "database.host")        // "localhost"
```

=== yamlSet(yaml, path, value) → value #text(size: 8pt, fill: gray)[(YAML)]

Return a new YAML structure with the value at the given path replaced.

- `yaml` (required): YAML UDM value
- `path` (required): dot-separated path string
- `value` (required): value to set at path

```utlx
yamlSet($input, "database.port", 5433)   // returns new structure with port changed
```

=== yamlDelete(yaml, path) → value #text(size: 8pt, fill: gray)[(YAML)]

Return a new YAML structure with the value at the given path removed.

- `yaml` (required): YAML UDM value
- `path` (required): dot-separated path string

```utlx
yamlDelete($input, "database.password")  // returns structure without password
```

Also: `yamlDeepMerge(obj1, obj2)`, `yamlKeys(obj)`, `yamlValues(obj)`, `yamlSort(obj)`, `yamlValidate(yaml, rules)`, `yamlFilterByKeyPattern(obj, pattern)`.

=== yamlValidate #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlValidateKeyPattern #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== yamlValues #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== zip(arr1, arr2) → array #text(size: 8pt, fill: gray)[(Arr)]

Combine two arrays element-by-element into pairs. Truncated to the shorter array's length.

- `arr1` (required): first array
- `arr2` (required): second array

```utlx
zip([1, 2, 3], ["a", "b", "c"])            // [[1, "a"], [2, "b"], [3, "c"]]
```

```utlx
let headers = ["Name", "Age", "City"]
let row = ["Alice", "30", "Amsterdam"]
{
  record: fromEntries(zip(headers, row))
}
```

Also: `zipAll(arrays)` — zips any number of arrays, `unzip(pairs)` — reverse of zip.

=== zipWith(arr1, arr2, fn) → array #text(size: 8pt, fill: gray)[(Arr)]

Combine two arrays element-by-element using a merge function.

- `arr1` (required): first array
- `arr2` (required): second array
- `fn` (required): lambda `(elem1, elem2) -> combined`

```utlx
zipWith([1, 2, 3], [10, 20, 30], (a, b) -> a + b)  // [11, 22, 33]
```

=== zipWithIndex(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Pair each element with its zero-based index.

- `array` (required): the array to index

```utlx
zipWithIndex(["Apple", "Banana", "Cherry"])  // [["Apple", 0], ["Banana", 1], ["Cherry", 2]]
```

```utlx
map(zipWithIndex($input.items), (pair) -> {
  lineNumber: pair[1] + 1,
  ...pair[0]
})
```

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
