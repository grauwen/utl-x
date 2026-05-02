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

=== osArch() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the operating system architecture (e.g. `"amd64"`, `"aarch64"`).

```utlx
{
  arch: osArch()
}
```

=== osVersion() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the operating system version string.

```utlx
{
  osVersion: osVersion()
}
```

== P

=== pad(string, length, char?) → string #text(size: 8pt, fill: gray)[(Str)]

Pad a string on the left to the given length. Default pad character is space.

- `string` (required): the string to pad
- `length` (required): target length
- `char` (optional): pad character (default `" "`)

```bash
echo '{"id": "42"}' | utlx -e 'pad($input.id, 5, "0")'
# "00042"
```

```utlx
{
  invoiceNo: pad(toString($input.seq), 8, "0")
}
```

=== padLeft(string, length, char?) → string #text(size: 8pt, fill: gray)[(Str)]

Explicit left padding — alias for `pad()`.

- `string` (required): the string to pad
- `length` (required): target length
- `char` (optional): pad character (default `" "`)

```utlx
padLeft("42", 5, "0")                    // "00042"
```

=== padRight(string, length, char?) → string #text(size: 8pt, fill: gray)[(Str)]

Pad a string on the right to the given length.

- `string` (required): the string to pad
- `length` (required): target length
- `char` (optional): pad character (default `" "`)

```utlx
padRight("hello", 10, ".")              // "hello....."
```

=== parent(element) → element #text(size: 8pt, fill: gray)[(XML)]

Get the parent element of an XML node (if metadata is available).

- `element` (required): an XML element

```utlx
let p = parent($input.Order.LineItem)
p.OrderId                                // access sibling of parent
```

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

=== parseBoolean(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Parse a string or number into a boolean. Accepts `"true"`, `"false"`, `"yes"`, `"no"`, `"1"`, `"0"`, etc.

- `value` (required): string or number to parse

```bash
echo '{"active": "yes"}' | utlx -e 'parseBoolean($input.active)'
# true
```

```utlx
{
  enabled: parseBoolean($input.flags.enabled)
}
```

=== parseCurrency(string) → number #text(size: 8pt, fill: gray)[(Fin)]

Parse a currency-formatted string (with symbol and thousands separators) into a number.

- `string` (required): currency string like `"$1,234.56"` or `"EUR 1.000,00"`

```bash
echo '{"price": "$1,234.56"}' | utlx -e 'parseCurrency($input.price)'
# 1234.56
```

```utlx
{
  amount: parseCurrency($input.total)
}
```

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

=== parseDateTimeWithTimezone(string, pattern, timezone) → datetime #text(size: 8pt, fill: gray)[(Date)]

Parse a datetime string using a format pattern and explicit timezone.

- `string` (required): the datetime string to parse
- `pattern` (required): format pattern
- `timezone` (required): timezone ID (e.g. `"Europe/Amsterdam"`)

```utlx
let dt = parseDateTimeWithTimezone($input.ts, "yyyy-MM-dd HH:mm:ss", "Europe/Amsterdam")
{
  utc: toUTC(dt)
}
```

=== parseDouble(string) → number #text(size: 8pt, fill: gray)[(Num)]

Parse a string into a double-precision floating-point number. Alias for `parseNumber`.

- `string` (required): numeric string to parse

```utlx
parseDouble("3.14159")                   // 3.14159
```

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

=== parseFloat(string) → number #text(size: 8pt, fill: gray)[(Num)]

Parse a string into a floating-point number. Alias for `parseNumber`.

- `string` (required): numeric string to parse

```utlx
parseFloat("2.718")                      // 2.718
```

=== parseInt(string, radix?) → number #text(size: 8pt, fill: gray)[(Num)]

Parse a string into an integer, optionally with a radix (base).

- `string` (required): numeric string to parse
- `radix` (optional): numeric base (default 10)

```bash
echo '{"hex": "FF"}' | utlx -e 'parseInt($input.hex, 16)'
# 255
```

```utlx
parseInt("42")                           // 42
parseInt("1010", 2)                      // 10
```

=== parseNumber(string) → number #text(size: 8pt, fill: gray)[(Num)]

Primary function for converting string values to numbers. Handles integers and decimals.

- `string` (required): numeric string to parse

```utlx
parseNumber("123.45")                    // 123.45
parseNumber($input.Order.Quantity)       // XML values are strings — convert for arithmetic
```

=== parseQueryString(string) → object #text(size: 8pt, fill: gray)[(URL)]

Parse a query string into an object of key-value pairs.

- `string` (required): query string (with or without leading `?`)

```bash
echo '{"qs": "name=Alice&age=30&city=Amsterdam"}' | utlx -e 'parseQueryString($input.qs)'
# {"name": "Alice", "age": "30", "city": "Amsterdam"}
```

```utlx
let params = parseQueryString($input.queryString)
{
  userName: params.name
}
```

=== parseURL(url) → object #text(size: 8pt, fill: gray)[(URL)]

Parse a URL string into its components (protocol, host, port, path, query, fragment).

- `url` (required): the URL string to parse

```bash
echo '{"url": "https://example.com:8080/api/v1?key=abc#section"}' | utlx -e 'parseURL($input.url)'
# {"protocol": "https", "host": "example.com", "port": 8080, "path": "/api/v1", "query": "key=abc", "fragment": "section"}
```

```utlx
let parts = parseURL($input.endpoint)
{
  host: parts.host,
  isSecure: parts.protocol == "https"
}
```

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

=== pascalCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to PascalCase (UpperCamelCase).

- `string` (required): the string to convert

```utlx
pascalCase("hello world")               // "HelloWorld"
pascalCase("some-kebab-case")           // "SomeKebabCase"
```

=== pathCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to path/case (words separated by `/`).

- `string` (required): the string to convert

```utlx
pathCase("hello world")                 // "hello/world"
pathCase("someVariable")                // "some/variable"
```

=== percentageChange(oldValue, newValue) → number #text(size: 8pt, fill: gray)[(Num)]

Calculate the percentage change between two values.

- `oldValue` (required): the original value
- `newValue` (required): the new value

```bash
echo '{"before": 100, "after": 125}' | utlx -e 'percentageChange($input.before, $input.after)'
# 25.0
```

```utlx
{
  growth: percentageChange($input.lastYear, $input.thisYear)
}
```

=== pi() → number #text(size: 8pt, fill: gray)[(Num)]

Returns the mathematical constant pi (approximately 3.14159265358979323846).

```utlx
let circumference = 2 * pi() * $input.radius
{
  circumference: circumference
}
```

=== platform() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the platform/operating system name (e.g. `"Linux"`, `"Mac OS X"`, `"Windows 10"`).

```utlx
{
  platform: platform()
}
```

=== pluralize(word) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a singular noun to its plural form.

- `word` (required): singular noun

```utlx
pluralize("child")                       // "children"
pluralize("item")                        // "items"
pluralize("category")                    // "categories"
```

=== pluralizeWithCount(word, count) → string #text(size: 8pt, fill: gray)[(Str)]

Return a formatted string with count and correctly pluralized word.

- `word` (required): singular noun
- `count` (required): the number

```bash
echo '{"n": 5}' | utlx -e 'pluralizeWithCount("item", $input.n)'
# "5 items"
```

```utlx
{
  summary: pluralizeWithCount("error", count($input.errors))
}
```

=== pow(base, exponent) → number #text(size: 8pt, fill: gray)[(Num)]

Raise a number to a power.

- `base` (required): the base number
- `exponent` (required): the exponent

```utlx
pow(2, 10)                               // 1024
pow(10, 3)                               // 1000
```

=== prepareForSignature(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Prepare XML for digital signature (XMLDSig) by canonicalizing it.

- `xml` (required): XML string to prepare

```utlx
let canonical = prepareForSignature($input.xmlPayload)
{
  signatureInput: canonical
}
```

=== presentValue(futureAmount, rate, periods) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate the present value of a future amount given a discount rate and number of periods.

- `futureAmount` (required): the future amount
- `rate` (required): discount rate per period (e.g. 0.05 for 5%)
- `periods` (required): number of periods

```utlx
presentValue(10000, 0.05, 3)             // ~8638.38 (PV of 10000 in 3 years at 5%)
```

=== prettyPrint(string, indent?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Auto-detect format and pretty-print a string (JSON, XML, or YAML).

- `string` (required): the string to format
- `indent` (optional): indentation size

```utlx
prettyPrint($input.compactJson)          // auto-detects JSON and formats
```

=== prettyPrintCSV(csv, options?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Format a CSV string with aligned columns for readability.

- `csv` (required): the CSV string
- `options` (optional): formatting options

```utlx
prettyPrintCSV($input.csvData)           // columns aligned with padding
```

=== prettyPrintFormat(value, format) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a UDM object in the specified format.

- `value` (required): UDM value to format
- `format` (required): `"json"`, `"xml"`, or `"yaml"`

```utlx
prettyPrintFormat($input, "json")        // indented JSON output
```

=== prettyPrintJSON(json, indent?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a JSON string with optional indentation.

- `json` (required): JSON string to format
- `indent` (optional): indentation size (default 2)

```utlx
prettyPrintJSON($input.compactJson)      // formatted with 2-space indent
```

=== prettyPrintXML(xml, options?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print an XML string with optional formatting options.

- `xml` (required): XML string to format
- `options` (optional): formatting options

```utlx
prettyPrintXML($input.xmlPayload)        // indented XML
```

=== prettyPrintYAML(yaml, options?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a YAML string with optional formatting options.

- `yaml` (required): YAML string to format
- `options` (optional): formatting options

```utlx
prettyPrintYAML($input.yamlData)         // formatted YAML
```


== Q

=== quarter(date) → number #text(size: 8pt, fill: gray)[(Date)]

Get the quarter (1-4) from a date.

- `date` (required): a date or datetime value

```utlx
quarter(parseDate("2026-05-01", "yyyy-MM-dd"))  // 2
```

=== quartiles(array) → object #text(size: 8pt, fill: gray)[(Num)]

Calculate quartiles (Q1, Q2/median, Q3) from a numeric array.

- `array` (required): array of numbers

```utlx
quartiles([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
// {q1: 3, q2: 5.5, q3: 8}
```

== R

=== random() → number #text(size: 8pt, fill: gray)[(Num)]

Generate a random number between 0.0 (inclusive) and 1.0 (exclusive).

```utlx
{
  sampleId: floor(random() * 1000)
}
```

=== readByte(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a single byte from binary data at the given offset.

- `binary` (required): binary data
- `offset` (required): byte offset (0-based)

```utlx
readByte($input.data, 0)                 // first byte value (0-255)
```

=== readDouble(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a 64-bit double from binary data at the given offset (big endian).

- `binary` (required): binary data
- `offset` (required): byte offset

```utlx
readDouble($input.data, 0)               // 64-bit floating point value
```

=== readFloat(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a 32-bit float from binary data at the given offset (big endian).

- `binary` (required): binary data
- `offset` (required): byte offset

```utlx
readFloat($input.data, 4)               // 32-bit floating point value
```

=== readInt16(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a 16-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data
- `offset` (required): byte offset

```utlx
readInt16($input.data, 0)                // 16-bit signed integer
```

=== readInt32(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a 32-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data
- `offset` (required): byte offset

```utlx
readInt32($input.data, 0)                // 32-bit signed integer
```

=== readInt64(binary, offset) → number #text(size: 8pt, fill: gray)[(Bin)]

Read a 64-bit integer from binary data at the given offset (big endian).

- `binary` (required): binary data
- `offset` (required): byte offset

```utlx
readInt64($input.data, 0)                // 64-bit signed integer
```

=== readJarEntry(jar, entryName) → binary #text(size: 8pt, fill: gray)[(Bin)]

Read a single entry from a JAR file by name.

- `jar` (required): JAR binary data
- `entryName` (required): path inside the JAR (e.g. `"META-INF/MANIFEST.MF"`)

```utlx
let manifest = readJarEntry($input.jarData, "META-INF/MANIFEST.MF")
{
  content: binaryToString(manifest, "UTF-8")
}
```

=== readJarManifest(jar) → object #text(size: 8pt, fill: gray)[(Bin)]

Read and parse the JAR manifest into an object of key-value pairs.

- `jar` (required): JAR binary data

```utlx
let mf = readJarManifest($input.jarData)
{
  version: mf."Implementation-Version"
}
```

=== readZipEntry(zip, entryName) → binary #text(size: 8pt, fill: gray)[(Bin)]

Read a single entry from a zip archive by name.

- `zip` (required): zip binary data
- `entryName` (required): path inside the archive

```utlx
let content = readZipEntry($input.archive, "data.json")
{
  parsed: parseJson(binaryToString(content, "UTF-8"))
}
```

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

=== reduceEntries(object, initial, accumulator) → value #text(size: 8pt, fill: gray)[(Obj)]

Reduce all entries in an object to a single value.

- `object` (required): the source object
- `initial` (required): starting accumulator value
- `accumulator` (required): lambda `(acc, key, value) -> newAcc`

```utlx
reduceEntries({a: 1, b: 2, c: 3}, 0, (acc, k, v) -> acc + v)  // 6
```

=== regexGroups(string, pattern) → array #text(size: 8pt, fill: gray)[(Str)]

Extract all capture groups from the first match of a regex pattern.

- `string` (required): the string to search
- `pattern` (required): regex with capture groups

```utlx
regexGroups("2026-05-01", "(\\d{4})-(\\d{2})-(\\d{2})")
// ["2026", "05", "01"]
```

=== regexNamedGroups(string, pattern) → object #text(size: 8pt, fill: gray)[(Str)]

Extract named capture groups from the first match of a regex pattern.

- `string` (required): the string to search
- `pattern` (required): regex with named groups `(?<name>...)`

```utlx
regexNamedGroups("2026-05-01", "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})")
// {"year": "2026", "month": "05", "day": "01"}
```

=== remove(string, search) → string #text(size: 8pt, fill: gray)[(Str)]

Remove all occurrences of a substring from a string.

- `string` (required): the source string
- `search` (required): substring to remove

```utlx
remove("Hello World", " ")              // "HelloWorld"
remove("$1,234.56", ",")                // "$1234.56"
```

=== removeBOM(data) → data #text(size: 8pt, fill: gray)[(XML)]

Remove the Byte Order Mark (BOM) if present at the start of the data.

- `data` (required): binary or string data

```utlx
let clean = removeBOM($input.fileContent)
```

=== removeQueryParam(url, param) → string #text(size: 8pt, fill: gray)[(URL)]

Remove a query parameter from a URL string.

- `url` (required): the URL string
- `param` (required): parameter name to remove

```utlx
removeQueryParam("https://example.com?page=1&size=10", "page")
// "https://example.com?size=10"
```

=== removeTax(totalWithTax, rate) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate the original amount from a total that includes tax.

- `totalWithTax` (required): the total including tax
- `rate` (required): tax rate (e.g. 0.21 for 21%)

```utlx
removeTax(121, 0.21)                     // 100.0 (remove 21% VAT from 121)
```

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

=== renderCsv(value) → string #text(size: 8pt, fill: gray)[(CSV)]

Render a UDM array as a CSV string.

- `value` (required): array of objects (each object becomes a row)

```utlx
renderCsv([{name: "Alice", age: 30}, {name: "Bob", age: 25}])
// "name,age\nAlice,30\nBob,25"
```

=== renderXml(value) → string #text(size: 8pt, fill: gray)[(XML)]

Render a UDM object as an XML string.

- `value` (required): UDM value to serialize

```utlx
renderXml({Order: {Id: "1", Item: "Widget"}})
// "<Order><Id>1</Id><Item>Widget</Item></Order>"
```

=== renderYaml(value) → string #text(size: 8pt, fill: gray)[(YAML)]

Render a UDM object as a YAML string.

- `value` (required): UDM value to serialize

```utlx
renderYaml({database: {host: "localhost", port: 5432}})
// "database:\n  host: localhost\n  port: 5432"
```

=== repeat(string, n) → string #text(size: 8pt, fill: gray)[(Str)]

Repeat a string n times.

- `string` (required): the string to repeat
- `n` (required): number of repetitions

```utlx
repeat("ab", 3)                          // "ababab"
repeat("-", 40)                          // "----------------------------------------"
```

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

=== replaceWithFunction(string, pattern, fn) → string #text(size: 8pt, fill: gray)[(Str)]

Replace all matches of a pattern using a function to compute the replacement.

- `string` (required): the source string
- `pattern` (required): regex pattern
- `fn` (required): lambda `(match) -> replacement`

```utlx
replaceWithFunction("hello world", "[a-z]+", (m) -> upper(m))
// "HELLO WORLD"
```

=== resolveQName(qname, namespaces) → object #text(size: 8pt, fill: gray)[(XML)]

Resolve a QName string to a full qualified name with namespace URI.

- `qname` (required): qualified name (e.g. `"ns:Element"`)
- `namespaces` (required): namespace map object

```utlx
resolveQName("soap:Envelope", {"soap": "http://schemas.xmlsoap.org/soap/envelope/"})
// {"localName": "Envelope", "namespaceUri": "http://schemas.xmlsoap.org/soap/envelope/", "prefix": "soap"}
```

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

=== rightJoin(left, right, leftKey, rightKey) → array #text(size: 8pt, fill: gray)[(Arr)]

Right join — returns all items from the right array, with matching items from the left. Unmatched left fields are null.

- `left` (required): left array
- `right` (required): right array
- `leftKey` (required): lambda to extract join key from left elements
- `rightKey` (required): lambda to extract join key from right elements

```utlx
let orders = [{id: 1, product: "A"}, {id: 2, product: "B"}]
let shipments = [{orderId: 2, date: "2026-05-01"}, {orderId: 3, date: "2026-05-02"}]
rightJoin(orders, shipments, (o) -> o.id, (s) -> s.orderId)
// includes shipment for orderId 3 even though no matching order
```

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

=== runtimeInfo() → object #text(size: 8pt, fill: gray)[(Sys)]

Get comprehensive runtime information (JVM version, memory, OS, etc.).

```utlx
{
  runtime: runtimeInfo()
}
```

== S

=== scan(array, initial, fn) → array #text(size: 8pt, fill: gray)[(Arr)]

Like `reduce` but returns all intermediate results as an array.

- `array` (required): the array to scan
- `initial` (required): starting accumulator value
- `fn` (required): lambda `(acc, element) -> newAcc`

```utlx
scan([1, 2, 3, 4], 0, (acc, x) -> acc + x)
// [1, 3, 6, 10] (running totals)
```

=== seconds(datetime) → number #text(size: 8pt, fill: gray)[(Date)]

Extract the seconds component (0-59) from a datetime.

- `datetime` (required): a datetime value

```utlx
seconds(now())                           // e.g. 45
```

=== setConsoleLogging(enabled) → null #text(size: 8pt, fill: gray)[(Sys)]

Enable or disable console logging output.

- `enabled` (required): `true` to enable, `false` to disable

```utlx
let _ = setConsoleLogging(true)
```

=== setLogLevel(level) → null #text(size: 8pt, fill: gray)[(Sys)]

Set the minimum log level for output (`"TRACE"`, `"DEBUG"`, `"INFO"`, `"WARN"`, `"ERROR"`).

- `level` (required): log level string

```utlx
let _ = setLogLevel("DEBUG")
```

=== setPath(url, path) → string #text(size: 8pt, fill: gray)[(URL)]

Set or replace the path component of a URL.

- `url` (required): the URL string
- `path` (required): new path to set

```utlx
setPath("https://example.com/old/path", "/api/v2/resource")
// "https://example.com/api/v2/resource"
```

=== sha224(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-224 hash. Returns 56-char hex string.

- `data` (required): string to hash

```utlx
sha224("sensitive data")                 // 56-char hex string
```

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

=== sha384(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-384 hash. Returns 96-char hex string.

- `data` (required): string to hash

```utlx
sha384("sensitive data")                 // 96-char hex string
```

=== sha3_256(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA3-256 hash (if available). Returns 64-char hex string.

- `data` (required): string to hash

```utlx
sha3_256("sensitive data")               // 64-char hex string
```

=== sha3_512(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA3-512 hash (if available). Returns 128-char hex string.

- `data` (required): string to hash

```utlx
sha3_512("sensitive data")               // 128-char hex string
```

=== shiftLeft(binary, positions) → binary #text(size: 8pt, fill: gray)[(Bin)]

Shift bits left by the specified number of positions.

- `binary` (required): binary data
- `positions` (required): number of positions to shift

```utlx
shiftLeft(toBinary("A", "UTF-8"), 2)     // bits shifted left by 2
```

=== shiftRight(binary, positions) → binary #text(size: 8pt, fill: gray)[(Bin)]

Shift bits right by the specified number of positions.

- `binary` (required): binary data
- `positions` (required): number of positions to shift

```utlx
shiftRight(toBinary("A", "UTF-8"), 2)    // bits shifted right by 2
```

=== shouldUseCDATA(content, threshold?) → boolean #text(size: 8pt, fill: gray)[(XML)]

Determine if content should be wrapped in a CDATA section (based on special character count).

- `content` (required): the text content to evaluate
- `threshold` (optional): number of special chars that triggers CDATA

```utlx
shouldUseCDATA("<script>alert('hi')</script>")  // true
shouldUseCDATA("plain text")                     // false
```

=== simpleInterest(principal, rate, time) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculate simple interest: principal * rate * time.

- `principal` (required): the principal amount
- `rate` (required): annual interest rate (e.g. 0.05 for 5%)
- `time` (required): time in years

```utlx
simpleInterest(1000, 0.05, 3)            // 150.0 (interest on 1000 at 5% for 3 years)
```

=== sin(x) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the sine of an angle in radians.

- `x` (required): angle in radians

```utlx
sin(pi() / 2)                            // 1.0
```

=== singularize(word) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a plural noun to its singular form.

- `word` (required): plural noun

```utlx
singularize("children")                  // "child"
singularize("items")                     // "item"
singularize("categories")               // "category"
```

=== sinh(x) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the hyperbolic sine.

- `x` (required): a number

```utlx
sinh(1)                                  // ~1.1752
```

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

=== slugify(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to a URL-safe slug (lowercase, hyphens, no special chars).

- `string` (required): the string to slugify

```utlx
slugify("Hello World! 2026")             // "hello-world-2026"
slugify("Cafe Resume")                   // "cafe-resume"
```

=== smartCoerce(value) → value #text(size: 8pt, fill: gray)[(Type)]

Smart coercion — infers the target type from context and coerces the value.

- `value` (required): value to coerce

```utlx
smartCoerce("42")                        // 42 (number)
smartCoerce("true")                      // true (boolean)
smartCoerce("2026-05-01")                // date value
```

=== snakeCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to snake_case.

- `string` (required): the string to convert

```utlx
snakeCase("helloWorld")                  // "hello_world"
snakeCase("Hello World")                 // "hello_world"
```

=== some(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Check if any element in the array matches the predicate.

- `array` (required): the array to test
- `predicate` (required): lambda `(element) -> boolean`

```utlx
some([1, 2, 3, 4], (x) -> x > 3)        // true
some([1, 2, 3], (x) -> x > 5)           // false
```

=== someEntry(object, predicate) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Check if any entry in the object matches the predicate.

- `object` (required): the object to test
- `predicate` (required): lambda `(key, value) -> boolean`

```utlx
someEntry({a: 1, b: 5, c: 3}, (k, v) -> v > 4)  // true
```

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

=== splitWithMatches(string, pattern) → array #text(size: 8pt, fill: gray)[(Str)]

Split a string by a pattern, keeping the matched parts as separate elements in the result.

- `string` (required): the string to split
- `pattern` (required): regex pattern to split on

```utlx
splitWithMatches("hello123world456", "\\d+")
// ["hello", "123", "world", "456"]
```

=== sqrt(x) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the square root.

- `x` (required): a non-negative number

```utlx
sqrt(16)                                 // 4.0
sqrt(2)                                  // ~1.4142
```

=== startOfDay(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the start of the day (midnight 00:00:00) for the given date.

- `datetime` (required): a date or datetime value

```utlx
startOfDay(now())                        // today at 00:00:00
```

=== startOfMonth(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the first day of the month for the given date.

- `datetime` (required): a date or datetime value

```utlx
startOfMonth(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-05-01T00:00:00
```

=== startOfQuarter(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the first day of the quarter for the given date.

- `datetime` (required): a date or datetime value

```utlx
startOfQuarter(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-04-01T00:00:00
```

=== startOfWeek(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the start of the week (Monday) for the given date.

- `datetime` (required): a date or datetime value

```utlx
startOfWeek(now())                       // Monday of current week
```

=== startOfYear(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the first day of the year for the given date.

- `datetime` (required): a date or datetime value

```utlx
startOfYear(parseDate("2026-05-15", "yyyy-MM-dd"))
// 2026-01-01T00:00:00
```

=== startTimer() → null #text(size: 8pt, fill: gray)[(Sys)]

Start a timer to measure execution time. Use with `endTimer()` to log elapsed time.

```utlx
let _ = startTimer()
// ... expensive operations ...
let _ = endTimer()
```

=== stringOrDefault(value, default) → string #text(size: 8pt, fill: gray)[(Type)]

Safely convert a value to string, returning the default if null or undefined.

- `value` (required): value to convert
- `default` (required): fallback string

```utlx
stringOrDefault($input.name, "Unknown")  // "Unknown" if name is null
```

=== stripBOM(string) → string #text(size: 8pt, fill: gray)[(XML)]

Remove the BOM character (U+FEFF) from the beginning of a string.

- `string` (required): string that may start with BOM

```utlx
let clean = stripBOM($input.fileContent)
```

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

=== substringAfterLast(string, delimiter) → string #text(size: 8pt, fill: gray)[(Str)]

Return the part of a string after the LAST occurrence of a delimiter.

- `string` (required): the source string
- `delimiter` (required): the delimiter to search for

```utlx
substringAfterLast("a.b.c.d", ".")       // "d"
substringAfterLast("/usr/local/bin", "/") // "bin"
```

=== substringBeforeLast(string, delimiter) → string #text(size: 8pt, fill: gray)[(Str)]

Return the part of a string before the LAST occurrence of a delimiter.

- `string` (required): the source string
- `delimiter` (required): the delimiter to search for

```utlx
substringBeforeLast("a.b.c.d", ".")      // "a.b.c"
substringBeforeLast("file.tar.gz", ".")  // "file.tar"
```

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

=== systemPropertiesAll() → object #text(size: 8pt, fill: gray)[(Sys)]

Get all Java system properties as an object.

```utlx
{
  allProps: systemPropertiesAll()
}
```

=== systemProperty(name) → string #text(size: 8pt, fill: gray)[(Sys)]

Get a Java system property by name. Returns null if not set.

- `name` (required): property name (e.g. `"java.version"`)

```utlx
{
  javaHome: systemProperty("java.home")
}
```

=== systemPropertyOrDefault(name, default) → string #text(size: 8pt, fill: gray)[(Sys)]

Get a system property with a fallback default value.

- `name` (required): property name
- `default` (required): fallback value if property is not set

```utlx
{
  encoding: systemPropertyOrDefault("file.encoding", "UTF-8")
}
```

== T

=== tan(x) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the tangent of an angle in radians.

- `x` (required): angle in radians

```utlx
tan(pi() / 4)                            // ~1.0
```

=== tanh(x) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the hyperbolic tangent.

- `x` (required): a number

```utlx
tanh(1)                                  // ~0.7616
```

=== tempDir() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the system temporary directory path.

```utlx
{
  tmpDir: tempDir()
}
```

=== textContent(element) → string #text(size: 8pt, fill: gray)[(XML)]

Get concatenated text content from an XML element (all text nodes).

- `element` (required): an XML element

```utlx
// Given <p>Hello <b>world</b>!</p>
textContent($input.p)                    // "Hello world!"
```

=== timerCheck(name) → number #text(size: 8pt, fill: gray)[(Sys)]

Get elapsed time of a named timer without stopping it.

- `name` (required): timer name

```utlx
let elapsed = timerCheck("process")      // milliseconds since start
```

=== timerClear() → null #text(size: 8pt, fill: gray)[(Sys)]

Clear all timers and measurements.

```utlx
let _ = timerClear()
```

=== timerList() → array #text(size: 8pt, fill: gray)[(Sys)]

List all active timer names.

```utlx
let active = timerList()                 // ["process", "transform"]
```

=== timerReset(name) → null #text(size: 8pt, fill: gray)[(Sys)]

Reset a named timer to zero without stopping it.

- `name` (required): timer name

```utlx
let _ = timerReset("process")
```

=== timerStart(name) → null #text(size: 8pt, fill: gray)[(Sys)]

Start a named timer.

- `name` (required): timer name

```utlx
let _ = timerStart("transform")
```

=== timerStats(name) → object #text(size: 8pt, fill: gray)[(Sys)]

Get statistics for a named timer (min, max, avg, count).

- `name` (required): timer name

```utlx
let stats = timerStats("transform")
// {min: 12, max: 45, avg: 28, count: 100}
```

=== timerStop(name) → number #text(size: 8pt, fill: gray)[(Sys)]

Stop a named timer and return elapsed time in milliseconds.

- `name` (required): timer name

```utlx
let elapsed = timerStop("transform")     // e.g. 42
```

=== timestamp() → string #text(size: 8pt, fill: gray)[(Date)]

Get the current timestamp as an ISO 8601 string.

```utlx
{
  processedAt: timestamp()
}
```

=== titleCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to Title Case (capitalize first letter of each word).

- `string` (required): the string to convert

```utlx
titleCase("hello world")                 // "Hello World"
titleCase("the quick brown fox")         // "The Quick Brown Fox"
```

=== toArray(value) → array #text(size: 8pt, fill: gray)[(Type)]

Convert a value to an array. Wraps non-array values in a single-element array; arrays pass through.

- `value` (required): value to convert

```utlx
toArray("hello")                         // ["hello"]
toArray([1, 2, 3])                       // [1, 2, 3] (unchanged)
toArray(null)                            // []
```

=== toBase64(binary) → string #text(size: 8pt, fill: gray)[(Bin)]

Convert binary data to a Base64-encoded string.

- `binary` (required): binary data to encode

```utlx
toBase64(toBinary("Hello", "UTF-8"))     // "SGVsbG8="
```

=== toBinary(string, encoding?) → binary #text(size: 8pt, fill: gray)[(Bin)]

Create binary data from a string with the specified encoding.

- `string` (required): the string to convert
- `encoding` (optional): character encoding (default `"UTF-8"`)

```utlx
let data = toBinary("Hello World", "UTF-8")
{
  length: binaryLength(data)
}
```

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

=== toBytes(binary) → array #text(size: 8pt, fill: gray)[(Bin)]

Convert binary data to an array of byte values (0-255).

- `binary` (required): binary data

```utlx
toBytes(toBinary("AB", "UTF-8"))         // [65, 66]
```

=== toDate(string) → date #text(size: 8pt, fill: gray)[(Date)]

Convert a string to a date. Alias for `parseDate` with single argument (ISO 8601 auto-detection).

- `string` (required): date string in ISO format

```utlx
toDate("2026-05-01")                     // date value
```

=== toDegrees(radians) → number #text(size: 8pt, fill: gray)[(Num)]

Convert radians to degrees.

- `radians` (required): angle in radians

```utlx
toDegrees(pi())                          // 180.0
toDegrees(pi() / 2)                      // 90.0
```

=== toHex(binary) → string #text(size: 8pt, fill: gray)[(Bin)]

Convert binary data to a hexadecimal string.

- `binary` (required): binary data

```utlx
toHex(toBinary("AB", "UTF-8"))           // "4142"
```

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

=== toObject(value) → object #text(size: 8pt, fill: gray)[(Type)]

Try to convert a value to an object. Arrays of pairs become objects; objects pass through.

- `value` (required): value to convert

```utlx
toObject([["name", "Alice"], ["age", 30]])  // {"name": "Alice", "age": 30}
```

=== toRadians(degrees) → number #text(size: 8pt, fill: gray)[(Num)]

Convert degrees to radians.

- `degrees` (required): angle in degrees

```utlx
toRadians(180)                           // ~3.14159 (pi)
toRadians(90)                            // ~1.5708
```

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

=== toTitleCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to title case (capitalize first letter of each word). Alias for `titleCase`.

- `string` (required): the string to convert

```utlx
toTitleCase("hello world")               // "Hello World"
```

=== toUTC(datetime) → datetime #text(size: 8pt, fill: gray)[(Date)]

Convert a local datetime to UTC.

- `datetime` (required): datetime with timezone information

```utlx
{
  utcTime: toUTC($input.localTimestamp)
}
```

=== trace(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log a value with TRACE level and return it (passthrough for debugging in pipelines).

- `value` (required): value to log and pass through

```utlx
let result = trace($input.data)          // logs and passes through
```

=== translate(string, from, to) → string #text(size: 8pt, fill: gray)[(Str)]

Translate characters in a string: each character in `from` is replaced by the corresponding character in `to`.

- `string` (required): the source string
- `from` (required): characters to replace
- `to` (required): replacement characters (same length)

```utlx
translate("hello", "elo", "ELO")         // "hELLO"
translate("2026-05-01", "-", "/")        // "2026/05/01"
```

=== treeDepth(object) → number #text(size: 8pt, fill: gray)[(Obj)]

Get the maximum nesting depth of a tree structure.

- `object` (required): nested object/array structure

```utlx
treeDepth({a: {b: {c: 1}}})              // 3
```

=== treeFilter(object, predicate) → object #text(size: 8pt, fill: gray)[(Obj)]

Filter tree nodes by a predicate, removing branches that don't match.

- `object` (required): tree structure
- `predicate` (required): lambda `(node) -> boolean`

```utlx
treeFilter($input.menu, (node) -> node.visible == true)
```

=== treeFind(object, path) → value #text(size: 8pt, fill: gray)[(Obj)]

Find a node by path in a tree structure.

- `object` (required): tree structure
- `path` (required): path to search for

```utlx
treeFind($input.org, "engineering.backend")
```

=== treeFlatten(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Flatten a tree to an array of leaf nodes.

- `object` (required): tree structure

```utlx
treeFlatten({a: {b: 1, c: 2}, d: 3})    // [1, 2, 3]
```

=== treeMap(object, fn) → object #text(size: 8pt, fill: gray)[(Obj)]

Recursively transform all values in a nested structure.

- `object` (required): tree structure
- `fn` (required): lambda `(value) -> newValue`

```utlx
treeMap($input, (v) -> if (isString(v)) trim(v) else v)
```

=== treePaths(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Get all paths in a tree structure as an array of path strings.

- `object` (required): tree structure

```utlx
treePaths({a: {b: 1}, c: 2})             // ["a.b", "c"]
```

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

=== truncate(string, maxLength, suffix?) → string #text(size: 8pt, fill: gray)[(Str)]

Truncate a string to a maximum length, appending a suffix (default `"..."`).

- `string` (required): the string to truncate
- `maxLength` (required): maximum total length (including suffix)
- `suffix` (optional): suffix to append (default `"..."`)

```utlx
truncate("Hello World, this is a long string", 15)
// "Hello World,..."

truncate("Hello World", 20)              // "Hello World" (no truncation needed)
```

=== tryCoerce(value, type) → value #text(size: 8pt, fill: gray)[(Type)]

Try to coerce a value to the target type, returning null on failure (instead of throwing).

- `value` (required): value to coerce
- `type` (required): target type string (e.g. `"number"`, `"boolean"`)

```utlx
tryCoerce("42", "number")                // 42
tryCoerce("not-a-number", "number")      // null (no error)
```

=== typeOf(value) → string #text(size: 8pt, fill: gray)[(Type)]

Return the type of a value as a string (`"string"`, `"number"`, `"boolean"`, `"array"`, `"object"`, `"null"`).

- `value` (required): any value

```utlx
typeOf("hello")                          // "string"
typeOf(42)                               // "number"
typeOf([1, 2])                           // "array"
typeOf({a: 1})                           // "object"
```

=== typeof(value) → string #text(size: 8pt, fill: gray)[(Type)]

Alias for `typeOf` — JavaScript/TypeScript naming compatibility.

- `value` (required): any value

```utlx
typeof(null)                             // "null"
typeof(true)                             // "boolean"
```

== U

=== udmToJSON(value, pretty?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a UDM object as JSON.

- `value` (required): UDM value to format
- `pretty` (optional): pretty-print with indentation

```utlx
udmToJSON($input, true)                  // formatted JSON string
```

=== udmToXML(value, rootName?, options?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a UDM object as XML.

- `value` (required): UDM value to format
- `rootName` (optional): root element name
- `options` (optional): formatting options

```utlx
udmToXML($input, "Order")               // XML string with <Order> root
```

=== udmToYAML(value, options?) → string #text(size: 8pt, fill: gray)[(Fmt)]

Pretty-print a UDM object as YAML.

- `value` (required): UDM value to format
- `options` (optional): formatting options

```utlx
udmToYAML($input)                        // YAML string
```

=== uncamelize(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from camelCase to separate words. Legacy naming — prefer `fromCamelCase`.

- `string` (required): camelCase string

```utlx
uncamelize("helloWorld")                 // "hello world"
uncamelize("firstName")                  // "first name"
```

=== unescapeXML(string) → string #text(size: 8pt, fill: gray)[(XML)]

Unescape XML entities (`&lt;`, `&gt;`, `&amp;`, `&quot;`, `&apos;`) back to their characters.

- `string` (required): string with XML entities

```utlx
unescapeXML("price &lt; 100 &amp; qty &gt; 0")
// "price < 100 & qty > 0"
```

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

=== unnest(array, childKey) → array #text(size: 8pt, fill: gray)[(Arr)]

Flatten nested children alongside parent fields. The reverse of `nestBy` — converts hierarchical data to flat rows.

- `array` (required): array of parent objects containing nested children
- `childKey` (required): the key holding the children array

```bash
echo '{"orders": [{"customer": "Alice", "items": [{"sku": "A1", "qty": 2}, {"sku": "B2", "qty": 1}]}, {"customer": "Bob", "items": [{"sku": "C3", "qty": 5}]}]}' | utlx -e 'unnest($input.orders, "items")'
# [{"customer":"Alice","sku":"A1","qty":2},{"customer":"Alice","sku":"B2","qty":1},{"customer":"Bob","sku":"C3","qty":5}]
```

```utlx
// Typical use: denormalize order/line-items for flat CSV export
let flat = unnest($input.orders, "items")
map(flat, (row) -> {
  customer: row.customer,
  sku: row.sku,
  quantity: row.qty
})
```

=== unwrapCDATA(string) → string #text(size: 8pt, fill: gray)[(XML)]

Unwrap CDATA section if present, otherwise return the original string.

- `string` (required): string that may be wrapped in `<![CDATA[...]]>`

```utlx
unwrapCDATA("<![CDATA[Hello <World>]]>") // "Hello <World>"
unwrapCDATA("plain text")               // "plain text"
```

=== unzip(pairs) → [array, array] #text(size: 8pt, fill: gray)[(Bin)]

Unzip an array of pairs into two separate arrays (inverse of `zip`).

- `pairs` (required): array of 2-element arrays

```utlx
unzip([[1, "a"], [2, "b"], [3, "c"]])    // [[1, 2, 3], ["a", "b", "c"]]
```

=== unzipArchive(zip, options?) → object #text(size: 8pt, fill: gray)[(Bin)]

Extract all files from a zip archive into an object keyed by entry name.

- `zip` (required): zip binary data
- `options` (optional): extraction options

```utlx
let files = unzipArchive($input.archive)
{
  readme: binaryToString(files."README.md", "UTF-8")
}
```

=== unzipN(tuples) → array of arrays #text(size: 8pt, fill: gray)[(Bin)]

Unzip an array of N-tuples into N separate arrays (generalized `unzip`).

- `tuples` (required): array of N-element arrays

```utlx
unzipN([[1, "a", true], [2, "b", false]])
// [[1, 2], ["a", "b"], [true, false]]
```

=== updateXMLEncoding(xml, encoding) → string #text(size: 8pt, fill: gray)[(XML)]

Update the encoding declaration in an XML processing instruction.

- `xml` (required): XML string
- `encoding` (required): new encoding name (e.g. `"UTF-16"`)

```utlx
updateXMLEncoding($input.xmlData, "UTF-8")
```

=== upper(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to uppercase. Legacy naming — prefer `upperCase`.

- `string` (required): the string to convert

```utlx
upper("hello")                           // "HELLO"
```

=== uptime() → number #text(size: 8pt, fill: gray)[(Sys)]

Get JVM uptime in milliseconds.

```utlx
{
  uptimeMs: uptime()
}
```

=== urlDecodeComponent(string) → string #text(size: 8pt, fill: gray)[(URL)]

URL-decode a component string (RFC 3986) — decodes `%20` as spaces.

- `string` (required): the encoded string

```utlx
urlDecodeComponent("hello%20world")      // "hello world"
```

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

=== urlEncodeComponent(string) → string #text(size: 8pt, fill: gray)[(URL)]

URL-encode a component string (RFC 3986) — encodes spaces as `%20` for URI paths.

- `string` (required): the string to encode

```utlx
urlEncodeComponent("hello world/path")   // "hello%20world%2Fpath"
```

=== username() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the current system username.

```utlx
{
  user: username()
}
```

== V-W

=== validateDate(string, pattern?) → boolean #text(size: 8pt, fill: gray)[(Date)]

Validate whether a string is a valid date (ISO 8601 or custom pattern).

- `string` (required): the date string to validate
- `pattern` (optional): format pattern to validate against

```utlx
validateDate("2026-05-01")               // true
validateDate("2026-13-01")               // false (month 13 invalid)
validateDate("01/05/2026", "dd/MM/yyyy") // true
```

=== validateDigest(xml, expectedDigest) → boolean #text(size: 8pt, fill: gray)[(Sec)]

Validate that an XML digest matches the expected value.

- `xml` (required): XML string to validate
- `expectedDigest` (required): expected hash value

```utlx
validateDigest($input.signedXml, $input.expectedHash)
// true if digest matches
```

=== validateEncoding(encoding) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if an encoding name is valid and supported.

- `encoding` (required): encoding name string

```utlx
validateEncoding("UTF-8")                // true
validateEncoding("INVALID-ENC")          // false
```

=== values(object) → array #text(size: 8pt, fill: gray)[(Obj)]

See `keys` above. Returns all property values as an array.

```utlx
values({name: "Alice", age: 30})         // ["Alice", 30]
```

=== version() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the UTL-X engine version.

```utlx
{
  engineVersion: version()
}
```

=== warn(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log a value with WARN level and return it (passthrough).

- `value` (required): value to log and pass through

```utlx
let result = warn("Deprecated field used")
```

=== weekOfYear(date) → number #text(size: 8pt, fill: gray)[(Date)]

Get the ISO week number (1-53) for a date.

- `date` (required): a date or datetime value

```utlx
weekOfYear(parseDate("2026-05-01", "yyyy-MM-dd"))  // 18
```

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

=== wordCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to word case (capitalize first letter, rest lowercase).

- `string` (required): the string to convert

```utlx
wordCase("HELLO WORLD")                  // "Hello world"
wordCase("hello")                        // "Hello"
```

=== wrapIfNeeded(content, threshold?, tag?) → string #text(size: 8pt, fill: gray)[(XML)]

Automatically wrap content in CDATA if it contains enough special characters to benefit.

- `content` (required): the text content
- `threshold` (optional): special char count threshold
- `tag` (optional): wrapper format

```utlx
wrapIfNeeded("<script>alert('hi')</script>")
// "<![CDATA[<script>alert('hi')</script>]]>"

wrapIfNeeded("plain text")              // "plain text" (no wrapping needed)
```

=== writeByte(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a single byte value to binary.

- `value` (required): byte value (0-255)

```utlx
writeByte(65)                            // binary containing byte 0x41
```

=== writeDouble(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a 64-bit double to binary (big endian).

- `value` (required): double value

```utlx
writeDouble(3.14159)                     // 8 bytes of binary data
```

=== writeFloat(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a 32-bit float to binary (big endian).

- `value` (required): float value

```utlx
writeFloat(3.14)                         // 4 bytes of binary data
```

=== writeInt16(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a 16-bit integer to binary (big endian).

- `value` (required): integer value

```utlx
writeInt16(256)                          // 2 bytes: [0x01, 0x00]
```

=== writeInt32(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a 32-bit integer to binary (big endian).

- `value` (required): integer value

```utlx
writeInt32(65536)                        // 4 bytes of binary data
```

=== writeInt64(value) → binary #text(size: 8pt, fill: gray)[(Bin)]

Write a 64-bit integer to binary (big endian).

- `value` (required): integer value

```utlx
writeInt64(1000000000)                   // 8 bytes of binary data
```

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

=== xnor(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical XNOR (exclusive NOR / equivalence) — returns true when both operands are the same.

- `a` (required): first boolean
- `b` (required): second boolean

```utlx
xnor(true, true)                         // true
xnor(false, false)                       // true
xnor(true, false)                        // false
```

=== xor(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical XOR (exclusive OR) — returns true when exactly one operand is true.

- `a` (required): first boolean
- `b` (required): second boolean

```utlx
xor(true, false)                         // true
xor(true, true)                          // false
xor(false, false)                        // false
```

=== yamlEntries(object) → array #text(size: 8pt, fill: gray)[(YAML)]

Get entries (key-value pairs) from a YAML object as an array of `[key, value]` pairs.

- `object` (required): YAML object

```utlx
yamlEntries({host: "localhost", port: 5432})
// [["host", "localhost"], ["port", 5432]]
```

=== yamlExists(yaml, path) → boolean #text(size: 8pt, fill: gray)[(YAML)]

Check if a path exists in a YAML structure.

- `yaml` (required): YAML value
- `path` (required): dot-separated path

```utlx
yamlExists($input, "database.host")      // true or false
```

=== yamlFilterByKeyPattern(object, pattern) → object #text(size: 8pt, fill: gray)[(YAML)]

Filter a YAML object, keeping only keys that match a pattern.

- `object` (required): YAML object
- `pattern` (required): regex pattern for keys

```utlx
yamlFilterByKeyPattern($input, "^db_.*") // keys starting with "db_"
```

=== yamlFindByField(yaml, fieldName) → array #text(size: 8pt, fill: gray)[(YAML)]

Find all values in a YAML structure by field name (recursive search).

- `yaml` (required): YAML value
- `fieldName` (required): field name to search for

```utlx
yamlFindByField($input, "version")       // all "version" values in the tree
```

=== yamlFindObjectsWithField(yaml, fieldName) → array #text(size: 8pt, fill: gray)[(YAML)]

Find all objects containing a specific field (recursive search).

- `yaml` (required): YAML value
- `fieldName` (required): field name to search for

```utlx
yamlFindObjectsWithField($input, "image")
// all objects that have an "image" field
```

=== yamlFromEntries(entries) → object #text(size: 8pt, fill: gray)[(YAML)]

Create a YAML object from an array of `[key, value]` pairs.

- `entries` (required): array of `[key, value]` pairs

```utlx
yamlFromEntries([["host", "localhost"], ["port", 5432]])
// {host: "localhost", port: 5432}
```

=== yamlGetDocument(yaml, index) → value #text(size: 8pt, fill: gray)[(YAML)]

Get a specific document from a multi-document YAML by index.

- `yaml` (required): multi-document YAML string
- `index` (required): zero-based document index

```utlx
yamlGetDocument(multiDocYaml, 0)         // first document
```

=== yamlHasRequiredFields(object, fields) → boolean #text(size: 8pt, fill: gray)[(YAML)]

Check if a YAML object has all the required fields.

- `object` (required): YAML object to check
- `fields` (required): array of required field names

```utlx
yamlHasRequiredFields($input, ["apiVersion", "kind", "metadata"])
// true if all three fields exist
```

=== yamlKeys(object) → array #text(size: 8pt, fill: gray)[(YAML)]

Get all keys from a YAML object.

- `object` (required): YAML object

```utlx
yamlKeys($input)                         // ["apiVersion", "kind", "metadata", "spec"]
```

=== yamlMerge(obj1, obj2) → object #text(size: 8pt, fill: gray)[(YAML)]

Deep merge two YAML objects. Values from `obj2` override `obj1` for matching keys.

- `obj1` (required): base object
- `obj2` (required): override object

```utlx
yamlMerge({a: 1, b: {c: 2}}, {b: {d: 3}})
// {a: 1, b: {c: 2, d: 3}}
```

=== yamlMergeAll(objects) → object #text(size: 8pt, fill: gray)[(YAML)]

Merge multiple YAML objects in order (last wins for conflicts).

- `objects` (required): array of objects to merge

```utlx
yamlMergeAll([{a: 1}, {b: 2}, {a: 3}])  // {a: 3, b: 2}
```

=== yamlOmitKeys(object, keys) → object #text(size: 8pt, fill: gray)[(YAML)]

Remove specific keys from a YAML object.

- `object` (required): YAML object
- `keys` (required): array of keys to remove

```utlx
yamlOmitKeys($input, ["password", "secret"])
```

=== yamlSelectKeys(object, keys) → object #text(size: 8pt, fill: gray)[(YAML)]

Keep only specific keys from a YAML object.

- `object` (required): YAML object
- `keys` (required): array of keys to keep

```utlx
yamlSelectKeys($input, ["name", "version"])
```

=== yamlSort(object, comparator?) → object #text(size: 8pt, fill: gray)[(YAML)]

Sort YAML object keys alphabetically.

- `object` (required): YAML object to sort
- `comparator` (optional): custom comparator function

```utlx
yamlSort({z: 1, a: 2, m: 3})            // {a: 2, m: 3, z: 1}
```

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

=== yamlValidate(yaml, rules) → object #text(size: 8pt, fill: gray)[(YAML)]

Validate YAML against a set of rules. Returns validation result with errors.

- `yaml` (required): YAML value to validate
- `rules` (required): validation rules object

```utlx
yamlValidate($input, {required: ["apiVersion", "kind"]})
// {valid: true, errors: []}
```

=== yamlValidateKeyPattern(object, pattern) → boolean #text(size: 8pt, fill: gray)[(YAML)]

Validate that all keys in a YAML object match a given pattern.

- `object` (required): YAML object to validate
- `pattern` (required): regex pattern that keys must match

```utlx
yamlValidateKeyPattern($input, "^[a-z][a-zA-Z0-9]*$")
// true if all keys are camelCase
```

=== yamlValues(object) → array #text(size: 8pt, fill: gray)[(YAML)]

Get all values from a YAML object.

- `object` (required): YAML object

```utlx
yamlValues({host: "localhost", port: 5432})
// ["localhost", 5432]
```

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

=== zipAll(arrays, pad?) → array #text(size: 8pt, fill: gray)[(Bin)]

Zip multiple arrays together, padding shorter arrays with null (or a specified value).

- `arrays` (required): array of arrays to zip
- `pad` (optional): padding value for shorter arrays

```utlx
zipAll([[1, 2, 3], ["a", "b"]])          // [[1, "a"], [2, "b"], [3, null]]
```

=== zipArchive(entries) → binary #text(size: 8pt, fill: gray)[(Bin)]

Create a zip archive from an object of entries (name -> binary content).

- `entries` (required): object mapping file names to binary content

```utlx
let archive = zipArchive({
  "data.json": toBinary(renderJson($input), "UTF-8"),
  "meta.txt": toBinary("generated", "UTF-8")
})
{
  archiveSize: binaryLength(archive)
}
```

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
