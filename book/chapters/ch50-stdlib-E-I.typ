== E

=== e() → number #text(size: 8pt, fill: gray)[(Num)]

Returns Euler's number (approximately 2.71828182845904523536).

```utlx
e()                                      // 2.718281828459045
{
  euler: e(),
  expGrowth: e() ** $input.rate
}
```

=== elementPath(element) → string #text(size: 8pt, fill: gray)[(XML)]

Get the XPath-like path of an XML element within its document tree. See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  path: elementPath($input.Invoice.Lines.Line),
  debug: elementPath($input.root.child)
}
```

=== encryptAES(data, key, iv) → string #text(size: 8pt, fill: gray)[(Sec)]

Encrypts data using AES-128-CBC. Returns Base64-encoded ciphertext. See Chapter 38.

- `data` (required): plaintext string to encrypt
- `key` (required): 16-byte encryption key (hex or Base64)
- `iv` (required): initialization vector

```utlx
let key = generateKey(128)
let iv = generateIV()
{
  encrypted: encryptAES($input.secret, key, iv),
  key: key,
  iv: iv
}
```

=== encryptAES256(data, key, iv) → string #text(size: 8pt, fill: gray)[(Sec)]

Encrypts data using AES-256-CBC. Requires a 32-byte key. See Chapter 38.

- `data` (required): plaintext string to encrypt
- `key` (required): 32-byte encryption key
- `iv` (required): initialization vector

```utlx
let key = generateKey(256)
let iv = generateIV()
{
  encrypted: encryptAES256($input.payload, key, iv),
  key: key,
  iv: iv
}
```

=== endOfDay(date) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the end of day (23:59:59.999) for a given date.

- `date` (required): date or datetime

```bash
echo '{"date": "2026-05-01"}' | utlx -e 'endOfDay(parseDate($input.date, "yyyy-MM-dd"))'
# "2026-05-01T23:59:59.999"
```

```utlx
{
  dayEnd: endOfDay(now()),
  deadline: endOfDay(parseDate($input.dueDate, "yyyy-MM-dd"))
}
```

=== endOfMonth(date) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the last moment of the last day of the month.

- `date` (required): date or datetime

```utlx
{
  monthEnd: endOfMonth(now()),
  invoiceDue: endOfMonth(parseDate($input.invoiceDate, "yyyy-MM-dd"))
}
```

=== endOfQuarter(date) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the last moment of the last day of the quarter.

- `date` (required): date or datetime

```utlx
{
  quarterEnd: endOfQuarter(now()),
  reportDeadline: endOfQuarter(parseDate($input.date, "yyyy-MM-dd"))
}
```

=== endOfWeek(date) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the end of the week (Sunday 23:59:59.999).

- `date` (required): date or datetime

```utlx
{
  weekEnd: endOfWeek(now())
}
```

=== endOfYear(date) → datetime #text(size: 8pt, fill: gray)[(Date)]

Get the last moment of December 31 for the given date's year.

- `date` (required): date or datetime

```utlx
{
  yearEnd: endOfYear(now()),
  fiscalEnd: endOfYear(parseDate($input.startDate, "yyyy-MM-dd"))
}
```

=== endsWith(string, suffix) → boolean #text(size: 8pt, fill: gray)[(Str)]

Check if a string ends with a given substring.

- `string` (required): the string to test
- `suffix` (required): the substring to check for

```bash
echo '{"filename": "invoice-2026.xml"}' | utlx -e 'endsWith($input.filename, ".xml")'
# true
```

```utlx
{
  isXml: endsWith($input.filename, ".xml"),
  isJson: endsWith($input.filename, ".json"),
  utlxFiles: filter($input.files, (f) -> endsWith(f.name, ".utlx"))
}
```

=== startsWith(string, prefix) → boolean #text(size: 8pt, fill: gray)[(Str)]

Check if a string starts with a given substring.

- `string` (required): the string to test
- `prefix` (required): the substring to check for

```bash
echo '{"orderId": "ORD-001"}' | utlx -e 'startsWith($input.orderId, "ORD-")'
# true
```

```utlx
if (!startsWith($input.id, "ORD-")) error("Invalid order ID format")
{
  validFormat: startsWith($input.orderId, "ORD-"),
  orderId: $input.orderId
}
```

=== endTimer(label) → number #text(size: 8pt, fill: gray)[(Sys)]

Logs elapsed time since `startTimer()` was called with the same label. Returns elapsed milliseconds.

- `label` (required): timer label (must match a previous `startTimer` call)

```utlx
startTimer("transform")
let result = map($input.items, (i) -> { name: upperCase(i.name) })
endTimer("transform")
result
```

=== enforceNamespacePrefixes(xml, prefixMap) → string #text(size: 8pt, fill: gray)[(XML)]

Enforces specific namespace prefixes on an XML string, renaming prefixes to match the given mapping. See Chapter 22.

- `xml` (required): XML string to process
- `prefixMap` (optional): object mapping namespace URIs to desired prefixes

```utlx
{
  normalized: enforceNamespacePrefixes($input.xml)
}
```

=== entries(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Decompose an object into an array of `[key, value]` pairs. Essential for dynamic key processing.

- `object` (required): the object to decompose

```bash
echo '{"servers": {"prod": {"host": "prod-db"}, "staging": {"host": "stg-db"}}}' \
  | utlx -e 'entries($input.servers)'
# [["prod", {"host": "prod-db"}], ["staging", {"host": "stg-db"}]]
```

```utlx
{
  environments: entries($input.servers) |> map((entry) -> {
    environment: entry[0],
    host: entry[1].host
  })
}
```

=== fromEntries(pairs) → object #text(size: 8pt, fill: gray)[(Obj)]

Build an object from an array of `[key, value]` pairs. The inverse of `entries()`.

- `pairs` (required): array of `[key, value]` arrays

```bash
echo '{"items": [{"id": "A", "name": "Widget"}, {"id": "B", "name": "Gadget"}]}' \
  | utlx -e 'fromEntries(map($input.items, (i) -> [i.id, i.name]))'
# {"A": "Widget", "B": "Gadget"}
```

```utlx
{
  lookup: fromEntries(map($input.items, (i) -> [i.id, i.name]))
}
```

=== env(name) → string #text(size: 8pt, fill: gray)[(Sys)]

Read an environment variable from the host system. Returns `null` if not set.

- `name` (required): environment variable name

```utlx
{
  home: env("HOME"),                     // "/Users/alice"
  dbHost: env("DB_HOST") ?? "localhost"  // fallback if not set
}
```

Also: `hasEnv(name)` → boolean, `envAll()` → object with all environment variables.

*Security note:* `env()` is unrestricted in the CLI and IDE. In the UTLXe engine, environment variable access can be disabled or restricted via the security policy configuration (Chapter 38) to prevent transformations from reading host secrets in multi-tenant deployments.

=== envOrDefault(name, default) → string #text(size: 8pt, fill: gray)[(Sys)]

Read an environment variable, returning a default value if not set.

- `name` (required): environment variable name
- `default` (required): fallback value if the variable is not set

```utlx
envOrDefault("LOG_LEVEL", "INFO")        // "INFO" if LOG_LEVEL not set
{
  logLevel: envOrDefault("LOG_LEVEL", "INFO"),
  dbUrl: envOrDefault("DATABASE_URL", "postgres://localhost:5432/mydb")
}
```

=== envAll() → object #text(size: 8pt, fill: gray)[(Sys)]

Get all environment variables as a key-value object.

```utlx
let allEnv = envAll()
{
  home: allEnv.HOME,
  path: allEnv.PATH,
  count: count(entries(allEnv))
}
```

*Security note:* can be restricted in UTLXe via security policy (Chapter 38) to prevent exfiltration of host secrets in multi-tenant deployments.

=== environment() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the current environment name (e.g., "development", "production").

```utlx
{
  env: environment(),
  isProduction: environment() == "production"
}
```

=== equals(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Deep equality comparison of two values. Works for all types including objects and arrays.

- `a` (required): first value
- `b` (required): second value

```bash
echo '{"a": [1,2,3], "b": [1,2,3]}' | utlx -e 'equals($input.a, $input.b)'
# true
```

```utlx
{
  same: equals($input.expected, $input.actual),
  match: equals($input.config, $input.defaults)
}
```

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

=== escapeXML(text) → string #text(size: 8pt, fill: gray)[(XML)]

Escapes special XML characters (`<`, `>`, `&`, `"`, `'`) in text without using CDATA. See Chapter 22.

- `text` (required): string to escape

```bash
echo '{"text": "price < 100 & qty > 0"}' | utlx -e 'escapeXML($input.text)'
# "price &lt; 100 &amp; qty &gt; 0"
```

```utlx
{
  safeText: escapeXML($input.userInput)
}
```

=== every(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Check if ALL elements in an array satisfy a predicate. Returns `true` for empty arrays.

- `array` (required): the array to test
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"scores": [85, 92, 78, 95]}' | utlx -e 'every($input.scores, (s) -> s >= 70)'
# true
```

```utlx
{
  allPassing: every($input.scores, (s) -> s >= 70),
  allActive: every($input.users, (u) -> u.active),
  allPositive: every($input.amounts, (a) -> a > 0)
}
```

=== everyEntry(object, predicate) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Check if ALL entries in an object satisfy a predicate. The predicate receives key and value.

- `object` (required): the object to test
- `predicate` (required): lambda `(key, value) -> boolean`

```bash
echo '{"a": 1, "b": 2, "c": 3}' | utlx -e 'everyEntry($input, (k, v) -> v > 0)'
# true
```

```utlx
{
  allPositive: everyEntry($input.metrics, (k, v) -> v > 0),
  allNonNull: everyEntry($input, (k, v) -> v != null)
}
```

=== excC14n(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalizes XML using Exclusive Canonical XML (without comments). Used for XML digital signatures. See Chapter 22.

- `xml` (required): XML string to canonicalize

```utlx
{
  canonical: excC14n($input.xml)
}
```

=== excC14nWithComments(xml) → string #text(size: 8pt, fill: gray)[(XML)]

Canonicalizes XML using Exclusive Canonical XML (with comments preserved). See Chapter 22.

- `xml` (required): XML string to canonicalize

```utlx
{
  canonical: excC14nWithComments($input.xml)
}
```

=== exp(x) → number #text(size: 8pt, fill: gray)[(Num)]

Returns e raised to the power of x (e^x).

- `x` (required): the exponent

```bash
echo '{"rate": 0.05}' | utlx -e 'exp($input.rate)'
# 1.0512710963760241
```

```utlx
{
  growth: exp($input.rate * $input.years),
  eSquared: exp(2)
}
```

=== extractBetween(string, start, end) → string #text(size: 8pt, fill: gray)[(Str)]

Extract the substring between two delimiter strings.

- `string` (required): the source string
- `start` (required): the start delimiter
- `end` (required): the end delimiter

```bash
echo '{"msg": "Hello [world] today"}' | utlx -e 'extractBetween($input.msg, "[", "]")'
# "world"
```

```utlx
{
  tag: extractBetween($input.xml, "<name>", "</name>"),
  token: extractBetween($input.header, "Bearer ", " ")
}
```

=== extractCDATA(text) → string #text(size: 8pt, fill: gray)[(XML)]

Extracts the content from a CDATA section, stripping the `<![CDATA[` and `]]>` wrappers. See Chapter 22.

- `text` (required): string containing CDATA section

```bash
echo '{"xml": "<![CDATA[Hello & World]]>"}' | utlx -e 'extractCDATA($input.xml)'
# "Hello & World"
```

```utlx
{
  content: extractCDATA($input.rawField)
}
```

=== extractTimestampFromUuidV7(uuid) → datetime #text(size: 8pt, fill: gray)[(Sec)]

Extract the embedded timestamp from a UUID v7 value. See Chapter 38.

- `uuid` (required): UUID v7 string

```bash
echo '{"id": "018f6c30-a2b0-7000-8000-000000000001"}' | utlx -e 'extractTimestampFromUuidV7($input.id)'
# "2024-05-21T..."
```

```utlx
{
  createdAt: extractTimestampFromUuidV7($input.messageId),
  age: diffHours(now(), extractTimestampFromUuidV7($input.messageId))
}
```

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
{
  activeProducts: filter($input.products, (p) -> p.active),
  premiumActive: filter($input.products, (p) -> p.price > 100 && p.active),
  overBudget: filter($input.products, (p) -> p.price > 1000)
}
```

*Anti-pattern:* `$input.products[price > 10]` — bracket predicate syntax does NOT work in UTL-X. Always use `filter()`. See Chapter 8.

*Anti-pattern:* `filter()` when you want ONE result — use `find()` instead (returns the element, not an array).

=== filterEntries(object, predicate) → object #text(size: 8pt, fill: gray)[(Obj)]

Filter object properties by key and/or value. Returns a new object with only matching entries.

- `object` (required): the object to filter
- `predicate` (required): lambda `(key, value) -> boolean`

```bash
echo '{"name": "Alice", "email": "alice@example.com", "password": "secret", "temp": null}' \
  | utlx -e 'filterEntries(., (key, value) -> value != null)'
# {"name": "Alice", "email": "alice@example.com", "password": "secret"}
```

```utlx
{
  nonNull: filterEntries($input, (key, value) -> value != null),
  safe: filterEntries($input, (key, value) -> key != "password" && key != "temp")
}
```

Also: `someEntry(obj, pred)` → true if any entry matches, `everyEntry(obj, pred)` → true if all match, `countEntries(obj, pred)` → count of matching entries.

=== find(array, predicate) → element or null #text(size: 8pt, fill: gray)[(Arr)]

Returns the FIRST element matching a predicate, or `null` if no match.

- `array` (required): the array to search
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"users": [{"id": 1, "email": "alice@example.com"}, {"id": 2, "email": "bob@example.com"}]}' \
  | utlx -e 'find($input.users, (u) -> u.email == "bob@example.com")'
# {"id": 2, "email": "bob@example.com"}
```

```utlx
{
  bob: find($input.users, (u) -> u.email == "bob@example.com"),
  unknown: find($input.users, (u) -> u.email == "unknown@example.com")
}
```

*Anti-pattern:* `filter($input.users, ...)[0]` — use `find()`. It's cleaner and returns `null` instead of an index-out-of-bounds error on empty results.

=== findIndex(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

Returns the zero-based index of the FIRST matching element, or `-1` if not found.

- `array` (required): the array to search
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"users": [{"id": 1}, {"id": 2}, {"id": 3}]}' \
  | utlx -e 'findIndex($input.users, (u) -> u.id == 2)'
# 1
```

```utlx
{
  position: findIndex($input.users, (u) -> u.id == 2),
  missing: findIndex($input.users, (u) -> u.id == 99)
}
```

Also: `findLastIndex(array, predicate)` — searches from the end.

=== findAllMatches(string, pattern) → array #text(size: 8pt, fill: gray)[(Str)]

Finds all matches of a regex pattern and returns them with positions.

- `string` (required): the string to search
- `pattern` (required): regex pattern

```bash
echo '{"text": "Order ORD-001 and ORD-002 shipped"}' | utlx -e 'findAllMatches($input.text, "ORD-\\d+")'
# [{"match": "ORD-001", "start": 6}, {"match": "ORD-002", "start": 18}]
```

```utlx
{
  orderIds: findAllMatches($input.text, "ORD-\\d+") |> map((m) -> m.match)
}
```

=== findLastIndex(array, predicate) → number #text(size: 8pt, fill: gray)[(Arr)]

Find the index of the LAST element matching a predicate, or `-1` if not found.

- `array` (required): the array to search
- `predicate` (required): lambda `(element) -> boolean`

```bash
echo '{"items": ["A", "B", "A", "C"]}' | utlx -e 'findLastIndex($input.items, (x) -> x == "A")'
# 2
```

```utlx
{
  lastActive: findLastIndex($input.users, (u) -> u.active)
}
```

=== first(array) → element or null #text(size: 8pt, fill: gray)[(Arr)]

Returns the first element of an array, or `null` if the array is empty.

- `array` (required): the source array

```utlx
first(["Apple", "Banana", "Cherry"])     // "Apple"
first([42])                              // 42
first([])                                // null

// Use case: get the cheapest product
{
  cheapest: first(sortBy($input.products, (p) -> p.price))
}
```

=== formatCurrency(amount, currency?, locale?) → string #text(size: 8pt, fill: gray)[(Fin)]

Formats a number as currency with locale-specific formatting.

- `amount` (required): numeric amount
- `currency` (optional): ISO 4217 currency code (e.g., "USD", "EUR")
- `locale` (optional): locale for formatting (e.g., "en-US", "de-DE")

```bash
echo '{"amount": 1234.5}' | utlx -e 'formatCurrency($input.amount, "USD", "en-US")'
# "$1,234.50"
```

```utlx
{
  price: formatCurrency($input.total, "EUR", "de-DE"),
  usd: formatCurrency($input.amount, "USD", "en-US")
}
```

=== formatDateTimeInTimezone(datetime, timezone) → string #text(size: 8pt, fill: gray)[(Date)]

Format a datetime value displayed in a specific timezone.

- `datetime` (required): datetime value
- `timezone` (required): timezone ID (e.g., "America/New_York")

```bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'formatDateTimeInTimezone(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "America/New_York")'
# "2026-05-01T10:30:00-04:00"
```

```utlx
{
  localTime: formatDateTimeInTimezone(now(), "Europe/Amsterdam"),
  userTime: formatDateTimeInTimezone(now(), $input.userTimezone)
}
```

=== formatEmptyElements(xml, style?) → string #text(size: 8pt, fill: gray)[(XML)]

Formats empty XML elements according to the specified style (self-closing `<br/>` or expanded `<br></br>`). See Chapter 22.

- `xml` (required): XML string
- `style` (optional): "self-closing" or "expanded"

```utlx
{
  selfClosing: formatEmptyElements($input.xml, "self-closing"),
  expanded: formatEmptyElements($input.xml, "expanded")
}
```

=== formatNumber(number, pattern?) → string #text(size: 8pt, fill: gray)[(Num)]

Format a number using a pattern string.

- `number` (required): number to format
- `pattern` (optional): format pattern (e.g., `"#,##0.00"`)

```bash
echo '{"amount": 1234567.891}' | utlx -e 'formatNumber($input.amount, "#,##0.00")'
# "1,234,567.89"
```

```utlx
{
  formatted: formatNumber($input.price, "#,##0.00"),
  integer: formatNumber($input.qty, "#,##0"),
  percent: formatNumber($input.rate * 100, "0.0")
}
```

=== formatPlural(count, word) → string #text(size: 8pt, fill: gray)[(Str)]

Creates a formatted string with count and correctly pluralized word.

- `count` (required): numeric count
- `word` (required): singular form of the word

```bash
echo '{"n": 3}' | utlx -e 'formatPlural($input.n, "item")'
# "3 items"
```

```utlx
{
  summary: formatPlural(count($input.errors), "error"),
  files: formatPlural($input.fileCount, "file")
}
```

=== fromBase64(encoded) → binary #text(size: 8pt, fill: gray)[(Sec)]

Create binary data from a Base64-encoded string. See Chapter 38.

- `encoded` (required): Base64-encoded string

```bash
echo '{"data": "SGVsbG8gV29ybGQ="}' | utlx -e 'toString(fromBase64($input.data))'
# "Hello World"
```

```utlx
{
  decoded: toString(fromBase64($input.encodedPayload))
}
```

=== fromBytes(byteArray) → binary #text(size: 8pt, fill: gray)[(Bin)]

Create binary data from a byte array (array of integers 0-255).

- `byteArray` (required): array of byte values

```utlx
{
  binary: fromBytes([72, 101, 108, 108, 111])
}
```

=== fromCamelCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from camelCase to separate words.

- `string` (required): camelCase string

```bash
echo '{"name": "myVariableName"}' | utlx -e 'fromCamelCase($input.name)'
# "my variable name"
```

```utlx
{
  words: fromCamelCase($input.fieldName)
}
```

=== fromCharCode(code) → string #text(size: 8pt, fill: gray)[(Str)]

Create a string from a Unicode character code (code point).

- `code` (required): integer code point

```utlx
fromCharCode(65)                         // "A"
fromCharCode(8364)                       // "€"
{
  char: fromCharCode($input.code)
}
```

=== fromConstantCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from CONSTANT_CASE to separate words.

- `string` (required): CONSTANT_CASE string

```bash
echo '{"name": "MAX_RETRY_COUNT"}' | utlx -e 'fromConstantCase($input.name)'
# "max retry count"
```

```utlx
{
  readable: fromConstantCase($input.envVar)
}
```

=== fromDotCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from dot.case to separate words.

- `string` (required): dot.case string

```utlx
fromDotCase("config.max.retries")        // "config max retries"
{
  words: fromDotCase($input.propertyPath)
}
```

=== fromHex(hexString) → binary #text(size: 8pt, fill: gray)[(Str)]

Create binary data from a hexadecimal string.

- `hexString` (required): hex-encoded string

```utlx
{
  data: fromHex($input.hexPayload),
  text: toString(fromHex("48656c6c6f"))
}
```

=== fromKebabCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from kebab-case to separate words.

- `string` (required): kebab-case string

```bash
echo '{"slug": "my-page-title"}' | utlx -e 'fromKebabCase($input.slug)'
# "my page title"
```

```utlx
{
  title: titleCase(fromKebabCase($input.slug))
}
```

=== fromPascalCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from PascalCase to separate words.

- `string` (required): PascalCase string

```utlx
fromPascalCase("MyClassName")            // "my class name"
{
  label: fromPascalCase($input.className)
}
```

=== fromPathCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from path/case to separate words.

- `string` (required): path/case string

```utlx
fromPathCase("my/path/name")             // "my path name"
{
  label: fromPathCase($input.route)
}
```

=== fromSnakeCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from snake_case to separate words.

- `string` (required): snake_case string

```bash
echo '{"col": "first_name"}' | utlx -e 'fromSnakeCase($input.col)'
# "first name"
```

```utlx
{
  label: titleCase(fromSnakeCase($input.columnName))
}
```

=== fromTitleCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert from Title Case to separate lowercase words.

- `string` (required): Title Case string

```utlx
fromTitleCase("My Title Case")           // "my title case"
{
  normalized: fromTitleCase($input.heading)
}
```

=== fromUTC(datetime, timezone) → datetime #text(size: 8pt, fill: gray)[(Date)]

Convert a UTC datetime to a local datetime in the specified timezone.

- `datetime` (required): UTC datetime
- `timezone` (required): target timezone ID (e.g., "America/New_York")

```bash
echo '{"utc": "2026-05-01T14:00:00Z"}' | utlx -e 'fromUTC(parseDate($input.utc, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "Europe/Amsterdam")'
# "2026-05-01T16:00:00+02:00"
```

```utlx
{
  localTime: fromUTC(now(), "Asia/Tokyo")
}
```

=== fullOuterJoin(left, right, leftKey, rightKey) → array #text(size: 8pt, fill: gray)[(Arr)]

Full outer join -- returns all items from both arrays, with `null` for non-matching sides.

- `left` (required): left array
- `right` (required): right array
- `leftKey` (required): lambda to extract join key from left elements
- `rightKey` (required): lambda to extract join key from right elements

```utlx
let employees = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let salaries = [{empId: 1, amount: 5000}, {empId: 3, amount: 6000}]
{
  joined: fullOuterJoin(employees, salaries, (e) -> e.id, (s) -> s.empId)
}
```

=== futureValue(presentValue, rate, periods) → number #text(size: 8pt, fill: gray)[(Fin)]

Calculates the future value of a present amount given compound interest.

- `presentValue` (required): current amount
- `rate` (required): interest rate per period (e.g., 0.05 for 5%)
- `periods` (required): number of compounding periods

```bash
echo '{"pv": 1000, "rate": 0.05, "years": 10}' | utlx -e 'futureValue($input.pv, $input.rate, $input.years)'
# 1628.89
```

```utlx
{
  futureAmount: futureValue($input.principal, $input.annualRate, $input.years)
}
```

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
{
  latest: last(sortBy($input.events, (e) -> e.timestamp))
}
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
{ rows: dataRows }
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
{
  allLines: flatMap($input.orders, (o) -> o.lines)
}
```

=== flatten(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove one level of array nesting. Each element that is an array is unwrapped; non-array elements are kept as-is.

- `array` (required): the nested array to flatten

```utlx
{
  flat: flatten([[1, 2], [3, 4], [5]]),
  // [1, 2, 3, 4, 5]
  mixed: flatten([[1, 2], 3, [4, 5]]),
  // [1, 2, 3, 4, 5]
  oneLevel: flatten([[[1, 2]], [[3]]])
  // [[1, 2], [3]]  (only ONE level removed)
}
```

=== flattenDeep(array) → array #text(size: 8pt, fill: gray)[(Arr)]

Remove ALL levels of array nesting, recursively. Produces a completely flat array.

- `array` (required): the deeply nested array to flatten

```utlx
{
  deep: flattenDeep([[1, [2, [3, [4]]]]]),
  // [1, 2, 3, 4]
  nested: flattenDeep([[[["deep"]]]]),
  // ["deep"]
  flat: flattenDeep([1, 2, 3])
  // [1, 2, 3]  (already flat — no change)
}
```

=== formatDate(date, pattern) → string #text(size: 8pt, fill: gray)[(Date)]

Format a date or datetime as a string using a pattern. See the Date Format Patterns section later in this chapter.

- `date` (required): date or datetime value
- `pattern` (required): format pattern string

Pattern tokens: `yyyy` (year), `MM` (month 01-12), `dd` (day 01-31), `HH` (hour 00-23), `mm` (minute 00-59), `ss` (second 00-59), `EEEE` (day name), `MMMM` (month name), `EEE` (short day), `MMM` (short month).

```bash
echo '{"timestamp": "2026-05-01T14:30:00Z"}' \
  | utlx -e 'formatDate(parseDate($input.timestamp, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"), "dd/MM/yyyy")'
# "01/05/2026"
```

```utlx
let dt = parseDate($input.timestamp, "yyyy-MM-dd'T'HH:mm:ss'Z'")
{
  isoDate: formatDate(dt, "yyyy-MM-dd"),
  european: formatDate(dt, "dd/MM/yyyy"),
  withTime: formatDate(dt, "dd-MM-yyyy HH:mm"),
  human: formatDate(dt, "EEEE, MMMM d, yyyy"),
  invoiceDate: formatDate(now(), "yyyy-MM-dd")
}
```

== G

=== generateIV() → string #text(size: 8pt, fill: gray)[(Sec)]

Generates a random initialization vector (IV) for use with AES encryption. See Chapter 38.

```utlx
let iv = generateIV()
{
  iv: iv,
  encrypted: encryptAES($input.data, $input.key, iv)
}
```

=== generateKey(bits?) → string #text(size: 8pt, fill: gray)[(Sec)]

Generates a random encryption key of the specified bit length. See Chapter 38.

- `bits` (optional): key length in bits (128 or 256, default 128)

```utlx
let key128 = generateKey(128)
let key256 = generateKey(256)
{
  aes128key: key128,
  aes256key: key256
}
```

=== generateUuid() → string #text(size: 8pt, fill: gray)[(Sys)]

Generate a random UUID (v4). Alias for `generateUuidV4()`.

```utlx
generateUuid()        // "550e8400-e29b-41d4-a716-446655440000" (v4 random)
```

Also: `isValidUuid(string)`, `getUuidVersion(string)`.

=== generateUuidV4() → string #text(size: 8pt, fill: gray)[(Sys)]

Generate a random UUID version 4.

```utlx
generateUuidV4()      // "550e8400-e29b-41d4-a716-446655440000" (random)
```

=== generateUuidV7() → string #text(size: 8pt, fill: gray)[(Sys)]

Generate a time-ordered UUID version 7 (sortable by creation time).

```utlx
generateUuidV7()      // "018f6c30-a2b0-7000-8000-000000000001" (time-ordered)

// Use case: generate correlation IDs for messages
{
  messageId: generateUuidV7(),
  timestamp: now(),
  payload: $input
}
```

Also: `isUuidV7(string)`.

=== generateUuidV7Batch(count) → array #text(size: 8pt, fill: gray)[(Sec)]

Generate a batch of UUID v7s with monotonic guarantee (each is greater than the previous). See Chapter 38.

- `count` (required): number of UUIDs to generate

```bash
echo '{"n": 3}' | utlx -e 'generateUuidV7Batch($input.n)'
# ["018f6c30-a2b0-7000-...", "018f6c30-a2b0-7001-...", "018f6c30-a2b0-7002-..."]
```

```utlx
{
  batchIds: generateUuidV7Batch(10)
}
```

=== get(object, path) → any #text(size: 8pt, fill: gray)[(Obj)]

Gets a value from an object or array by key or index.

- `object` (required): object or array
- `path` (required): key name (string) or index (number)

```bash
echo '{"items": ["a", "b", "c"]}' | utlx -e 'get($input.items, 1)'
# "b"
```

```utlx
{
  second: get($input.items, 1),
  name: get($input, "name")
}
```

=== getBaseURL(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the base URL (protocol + host + port) from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://api.example.com:8080/v1/users?page=1"}' | utlx -e 'getBaseURL($input.url)'
# "https://api.example.com:8080"
```

```utlx
{
  base: getBaseURL($input.endpoint)
}
```

=== getBOMBytes(encoding) → binary #text(size: 8pt, fill: gray)[(XML)]

Get the BOM (Byte Order Mark) bytes for a given encoding. See Chapter 22.

- `encoding` (required): encoding name (e.g., "UTF-8", "UTF-16LE")

```utlx
{
  bom: getBOMBytes("UTF-8")
}
```

=== getCurrencyDecimals(currency) → number #text(size: 8pt, fill: gray)[(Fin)]

Gets the number of decimal places for a currency code (ISO 4217).

- `currency` (required): ISO 4217 currency code

```bash
echo '{"cur": "JPY"}' | utlx -e 'getCurrencyDecimals($input.cur)'
# 0
```

```utlx
{
  usdDecimals: getCurrencyDecimals("USD"),   // 2
  jpyDecimals: getCurrencyDecimals("JPY")    // 0
}
```

=== getFragment(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the fragment (hash) portion from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://example.com/page#section2"}' | utlx -e 'getFragment($input.url)'
# "section2"
```

```utlx
{
  fragment: getFragment($input.url)
}
```

=== getHost(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the host from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://api.example.com/v1/users"}' | utlx -e 'getHost($input.url)'
# "api.example.com"
```

```utlx
{
  host: getHost($input.endpoint)
}
```

=== getJWSAlgorithm(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the algorithm (alg) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  algorithm: getJWSAlgorithm($input.token)   // "RS256"
}
```

=== getJWSHeader(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Extracts the complete header from a JWS token as an object. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  header: getJWSHeader($input.token)
  // {"alg": "RS256", "typ": "JWT", "kid": "key-1"}
}
```

=== getJWSInfo(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Gets information about the JWS token structure (header, payload size, etc.). See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  info: getJWSInfo($input.token)
}
```

=== getJWSKeyId(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the Key ID (kid) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  keyId: getJWSKeyId($input.token)
}
```

=== getJWSPayload(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Extracts the payload from a JWS token WITHOUT signature verification. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  payload: getJWSPayload($input.token)
}
```

=== getJWSSigningInput(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Extracts the signing input (header.payload) from a JWS token for manual verification. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  signingInput: getJWSSigningInput($input.token)
}
```

=== getJWSTokenType(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the token type (typ) from a JWS token header. See Chapter 38.

- `token` (required): JWS/JWT token string

```utlx
{
  type: getJWSTokenType($input.token)        // "JWT"
}
```

=== getJWTAudience(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the audience (aud) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

```utlx
{
  audience: getJWTAudience($input.token)
}
```

=== getJWTClaim(token, claim) → any #text(size: 8pt, fill: gray)[(Sec)]

Gets a specific claim from a JWT token payload. See Chapter 38.

- `token` (required): JWT token string
- `claim` (required): claim name (e.g., "sub", "email", custom claims)

```utlx
{
  email: getJWTClaim($input.token, "email"),
  role: getJWTClaim($input.token, "role")
}
```

=== getJWTClaims(token) → object #text(size: 8pt, fill: gray)[(Sec)]

Extracts all claims from a JWT payload WITHOUT verification. See Chapter 38.

- `token` (required): JWT token string

```utlx
let claims = getJWTClaims($input.token)
{
  subject: claims.sub,
  issuer: claims.iss,
  expiry: claims.exp
}
```

=== getJWTIssuer(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the issuer (iss) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

```utlx
{
  issuer: getJWTIssuer($input.token)
}
```

=== getJWTSubject(token) → string #text(size: 8pt, fill: gray)[(Sec)]

Gets the subject (sub) claim from a JWT token. See Chapter 38.

- `token` (required): JWT token string

```utlx
{
  subject: getJWTSubject($input.token)
}
```

=== getLogs() → array #text(size: 8pt, fill: gray)[(Sys)]

Retrieves all log entries that have been recorded during the current transformation.

```utlx
info("Processing started")
let result = map($input.items, (i) -> i.name)
info("Processing complete")
{
  result: result,
  logs: getLogs()
}
```

=== getNamespaces(element) → object #text(size: 8pt, fill: gray)[(XML)]

Get all namespace declarations from an XML element as a prefix-to-URI map. See Chapter 22.

- `element` (required): XML UDM element

```utlx
let ns = getNamespaces($input.Invoice)
{
  namespaces: ns,
  isSoap: hasKey(ns, "soap"),
  hasCommonBasic: hasKey(ns, "cbc")
}
```

=== getPath(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the path component from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://api.example.com/v1/users/123"}' | utlx -e 'getPath($input.url)'
# "/v1/users/123"
```

```utlx
{
  path: getPath($input.endpoint)
}
```

=== getPort(url) → number #text(size: 8pt, fill: gray)[(URL)]

Get the port number from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://api.example.com:8443/v1"}' | utlx -e 'getPort($input.url)'
# 8443
```

```utlx
{
  port: getPort($input.url)
}
```

=== getProtocol(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the protocol/scheme from a URL string.

- `url` (required): URL string

```bash
echo '{"url": "https://example.com"}' | utlx -e 'getProtocol($input.url)'
# "https"
```

```utlx
{
  protocol: getProtocol($input.url),
  isSecure: getProtocol($input.url) == "https"
}
```

=== getQuery(url) → string #text(size: 8pt, fill: gray)[(URL)]

Get the query string from a URL (without the leading `?`).

- `url` (required): URL string

```bash
echo '{"url": "https://example.com/search?q=hello&page=2"}' | utlx -e 'getQuery($input.url)'
# "q=hello&page=2"
```

```utlx
{
  queryString: getQuery($input.url)
}
```

=== getQueryParams(url) → object #text(size: 8pt, fill: gray)[(URL)]

Get query parameters from a URL as a key-value object.

- `url` (required): URL string

```bash
echo '{"url": "https://example.com/search?q=hello&page=2"}' | utlx -e 'getQueryParams($input.url)'
# {"q": "hello", "page": "2"}
```

```utlx
{
  params: getQueryParams($input.url),
  searchTerm: getQueryParams($input.url).q
}
```

=== getTimezone(datetime) → string #text(size: 8pt, fill: gray)[(Date)]

Get the timezone offset string from a datetime value.

- `datetime` (required): datetime with timezone

```utlx
{
  offset: getTimezone(now())              // "+02:00"
}
```

=== getTimezoneName() → string #text(size: 8pt, fill: gray)[(Date)]

Get the timezone name/ID for the current system.

```utlx
{
  tz: getTimezoneName()                  // "Europe/Amsterdam"
}
```

=== getTimezoneOffsetHours(datetime, timezone?) → number #text(size: 8pt, fill: gray)[(Date)]

Get the timezone offset in hours for a given datetime or timezone.

- `datetime` (required): datetime value
- `timezone` (optional): timezone ID

```utlx
{
  offsetHours: getTimezoneOffsetHours(now(), "America/New_York")  // -4 or -5
}
```

=== getTimezoneOffsetSeconds(datetime) → number #text(size: 8pt, fill: gray)[(Date)]

Get the timezone offset in seconds for a datetime value.

- `datetime` (required): datetime with timezone

```utlx
{
  offsetSecs: getTimezoneOffsetSeconds(now())   // 7200 for +02:00
}
```

=== getType(value) → string #text(size: 8pt, fill: gray)[(Type)]

Returns the type of a value as a string ("string", "number", "boolean", "array", "object", "null", "date", "datetime").

- `value` (required): the value to inspect

```bash
echo '{"name": "Alice", "age": 30}' | utlx -e 'getType($input.name)'
# "string"
```

```utlx
{
  nameType: getType($input.name),         // "string"
  ageType: getType($input.age),           // "number"
  itemsType: getType($input.items)        // "array"
}
```

=== getUuidVersion(uuid) → number #text(size: 8pt, fill: gray)[(Sec)]

Get the version number from a UUID string. See Chapter 38.

- `uuid` (required): UUID string

```bash
echo '{"id": "550e8400-e29b-41d4-a716-446655440000"}' | utlx -e 'getUuidVersion($input.id)'
# 4
```

```utlx
{
  version: getUuidVersion($input.correlationId)
}
```

=== goldenRatio() → number #text(size: 8pt, fill: gray)[(Num)]

Returns the golden ratio (approximately 1.61803398874989484820).

```utlx
goldenRatio()                            // 1.618033988749895
{
  ratio: goldenRatio(),
  scaled: $input.width * goldenRatio()
}
```

=== groupBy(array, keyFn) → object #text(size: 8pt, fill: gray)[(Arr)]

Group array elements by a computed key. Returns an object where keys are the group values and values are arrays of matching elements.

- `array` (required): the array to group
- `keyFn` (required): lambda `(element) -> groupKey`

```bash
echo '{"employees": [{"name": "Alice", "dept": "Eng"}, {"name": "Bob", "dept": "Sales"}]}' \
  | utlx -e 'groupBy($input.employees, (e) -> e.dept)'
# {"Eng": [{"name": "Alice", "dept": "Eng"}], "Sales": [{"name": "Bob", "dept": "Sales"}]}
```

```utlx
let groups = groupBy($input.orders, (o) -> o.status)
{
  byStatus: groups,
  summary: entries(groups) |> map((entry) -> {
    status: entry[0],
    count: count(entry[1]),
    total: sum(map(entry[1], (o) -> o.amount))
  })
}
```

=== gunzip(data) → binary #text(size: 8pt, fill: gray)[(Bin)]

Decompress gzip-compressed binary data.

- `data` (required): gzip-compressed binary

```utlx
{
  decompressed: gunzip($input.compressedPayload)
}
```

=== gzip(data) → binary #text(size: 8pt, fill: gray)[(Bin)]

Compress binary data using gzip.

- `data` (required): binary data to compress

```utlx
{
  compressed: gzip(toBinary($input.largeText)),
  encoded: base64Encode(gzip(toBinary($input.payload)))
}
```

== H

=== hasAlpha(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains at least one alphabetic character.

- `string` (required): string to check

```bash
echo '{"code": "ABC123"}' | utlx -e 'hasAlpha($input.code)'
# true
```

```utlx
{
  hasLetters: hasAlpha($input.value),
  numericOnly: !hasAlpha($input.code)
}
```

=== hasAttribute(element, name) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if an XML element has a specific attribute. See Chapter 22.

- `element` (required): XML UDM element
- `name` (required): attribute name to check

```utlx
{
  hasId: hasAttribute($input.element, "id"),
  hasClass: hasAttribute($input.element, "class")
}
```

=== hasBOM(data) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if binary data starts with a BOM (Byte Order Mark). See Chapter 22.

- `data` (required): binary data to check

```utlx
{
  hasBom: hasBOM($input.fileData),
  clean: if (hasBOM($input.fileData)) removeBOM($input.fileData) else $input.fileData
}
```

=== hasContent(element) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if an XML element has any content (child elements or text). See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  hasBody: hasContent($input.element),
  emptyNodes: filter($input.nodes, (n) -> !hasContent(n))
}
```

=== hasEnv(name) → boolean #text(size: 8pt, fill: gray)[(Sys)]

Check if an environment variable exists (is set, even if empty).

- `name` (required): environment variable name

```utlx
{
  hasDatabase: hasEnv("DATABASE_URL"),
  hasSecret: hasEnv("API_SECRET")
}
```

*Security note:* can be restricted in UTLXe via security policy (Chapter 38) to prevent probing for host secrets.

=== hasKey(object, key) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Check if an object has a property with the given key name.

- `object` (required): the object to check
- `key` (required): property name as string

```bash
echo '{"name": "Alice", "email": "alice@example.com"}' | utlx -e 'hasKey($input, "email")'
# true
```

```utlx
if (hasKey($input, "shippingAddress")) {
  address: $input.shippingAddress
} else {
  address: $input.billingAddress
}
```

Also: `containsValue(object, value)` — check if any property has a specific value.

=== containsKey(object, key) → boolean #text(size: 8pt, fill: gray)[(Obj)]

Alias for `hasKey()`. Check if an object has a property with the given key name.

- `object` (required): the object to check
- `key` (required): property name as string

```utlx
containsKey($input, "email")             // true
containsKey($input, "phone")             // false
{
  hasEmail: containsKey($input, "email")
}
```

=== hash(data, algorithm?) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a cryptographic hash with an explicit algorithm. Returns hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash
- `algorithm` (optional, default `"SHA-256"`): algorithm name (`"MD5"`, `"SHA-1"`, `"SHA-256"`, `"SHA-384"`, `"SHA-512"`, `"SHA3-256"`, `"SHA3-512"`)

```utlx
hash("hello", "SHA3-256")               // "3338be694f50c5f338..."
hash("hello", "SHA-256")                // "2cf24dba5fb0a30e..."
{
  digest: hash($input.payload, "SHA-256")
}
```

Also: `sha1(data)`, `sha224(data)`, `sha384(data)`, `sha3_256(data)`, `sha3_512(data)`.

=== md5(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an MD5 hash. Returns hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash

```utlx
md5("hello")                             // "5d41402abc4b2a76b9719d911017c592"
{
  checksum: md5($input.content)
}
```

*Anti-pattern:* `md5()` for security — MD5 is cryptographically broken. Use `sha256()` minimum. MD5 is acceptable only for non-security checksums (file deduplication, cache keys).

=== sha256(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-256 hash. Returns hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash

```utlx
sha256("hello")                          // "2cf24dba5fb0a30e..."

// Use case: content-addressed caching
let contentHash = sha256(renderJson($input))
{...$input, hash: contentHash}
```

=== sha512(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-512 hash. Returns hex-encoded digest string. See Chapter 38.

- `data` (required): string to hash

```utlx
sha512("hello")                          // "9b71d224bd62f378..."
{
  digest: sha512($input.document)
}
```

=== hasNamespace(element, uri) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if an XML element has a specific namespace URI declared. See Chapter 22.

- `element` (required): XML UDM element
- `uri` (required): namespace URI to check

```utlx
{
  hasSoap: hasNamespace($input.root, "http://schemas.xmlsoap.org/soap/envelope/"),
  hasUBL: hasNamespace($input.Invoice, "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2")
}
```

=== hasNumeric(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains at least one numeric digit.

- `string` (required): string to check

```bash
echo '{"code": "ABC123"}' | utlx -e 'hasNumeric($input.code)'
# true
```

```utlx
{
  hasDigits: hasNumeric($input.password),
  alphaOnly: !hasNumeric($input.name)
}
```

=== hexDecode(hex) → binary #text(size: 8pt, fill: gray)[(Bin)]

Decode a hex-encoded string to binary data.

- `hex` (required): hex string to decode

```utlx
{
  data: hexDecode($input.hexPayload)
}
```

=== hexEncode(data) → string #text(size: 8pt, fill: gray)[(Bin)]

Encode binary data as a hexadecimal string.

- `data` (required): binary data to encode

```utlx
{
  hex: hexEncode(toBinary($input.text))
}
```

=== hmac(data, key, algorithm) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an HMAC (Hash-based Message Authentication Code) with an explicit algorithm. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message to authenticate
- `key` (required): the secret key
- `algorithm` (required): hash algorithm (e.g., `"SHA-256"`, `"SHA-512"`)

```utlx
hmac("message", "key", "SHA-512")        // "..." (HMAC-SHA512 hex)
{
  signature: hmac($input.body, env("SECRET"), "SHA-256")
}
```

Also: `hmacSHA512(data, key)`, `hmacSHA1(data, key)`, `hmacMD5(data, key)`, `hmacBase64(data, key, algorithm)` (returns Base64 instead of hex).

=== hmacSHA256(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an HMAC-SHA256 for verifying message integrity and authenticity. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message to authenticate
- `key` (required): the secret key

```utlx
hmacSHA256("message-to-verify", "my-secret-key")  // "4a8f3d..."

// Use case: verify webhook signature
let expectedSig = hmacSHA256($input.body, env("WEBHOOK_SECRET"))
if (expectedSig != $input.headers.signature) error("Invalid signature")
{ verified: true, payload: $input.body }
```

=== hmacBase64(data, key, algorithm) → string #text(size: 8pt, fill: gray)[(Sec)]

Computes HMAC and returns the result as Base64-encoded string. See Chapter 38.

- `data` (required): the message to authenticate
- `key` (required): the secret key
- `algorithm` (required): hash algorithm (e.g., "SHA-256")

```utlx
{
  signature: hmacBase64($input.payload, env("SECRET"), "SHA-256")
}
```

=== hmacMD5(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Computes HMAC-MD5 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message
- `key` (required): the secret key

```utlx
{
  hash: hmacMD5($input.message, $input.key)
}
```

=== hmacSHA1(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Computes HMAC-SHA1 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message
- `key` (required): the secret key

```utlx
{
  hash: hmacSHA1($input.message, $input.key)
}
```

=== hmacSHA384(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Computes HMAC-SHA384 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message
- `key` (required): the secret key

```utlx
{
  hash: hmacSHA384($input.message, $input.key)
}
```

=== hmacSHA512(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Computes HMAC-SHA512 hash. Returns hex-encoded string. See Chapter 38.

- `data` (required): the message
- `key` (required): the secret key

```utlx
{
  hash: hmacSHA512($input.message, $input.key)
}
```

=== homeDir() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the current user's home directory path.

```utlx
homeDir()                                // "/Users/alice" or "/home/alice"
{
  home: homeDir()
}
```

=== hours(datetime) → number #text(size: 8pt, fill: gray)[(Date)]

Extract the hours component (0-23) from a datetime value.

- `datetime` (required): datetime value

```bash
echo '{"ts": "2026-05-01T14:30:00Z"}' | utlx -e 'hours(parseDate($input.ts, "yyyy-MM-dd'\''T'\''HH:mm:ss'\''Z'\''"))'
# 14
```

```utlx
{
  hour: hours(now()),
  isBusinessHours: hours(now()) >= 9 && hours(now()) < 17
}
```

== I

=== ifThenElse(condition, thenValue, elseValue) → any #text(size: 8pt, fill: gray)[(Type)]

Inline if-then-else conditional expression. Returns `thenValue` if condition is true, otherwise `elseValue`.

- `condition` (required): boolean condition
- `thenValue` (required): value if true
- `elseValue` (required): value if false

```bash
echo '{"age": 20}' | utlx -e 'ifThenElse($input.age >= 18, "adult", "minor")'
# "adult"
```

```utlx
{
  status: ifThenElse($input.active, "ACTIVE", "INACTIVE"),
  label: ifThenElse($input.count > 0, concat(toString($input.count), " items"), "empty")
}
```

=== implies(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical implication (material conditional). Returns `false` only when `a` is true and `b` is false.

- `a` (required): antecedent boolean
- `b` (required): consequent boolean

```utlx
implies(true, true)                      // true
implies(true, false)                     // false
implies(false, true)                     // true
implies(false, false)                    // true

{
  valid: implies($input.isPremium, $input.hasSubscription)
}
```

=== includes(array, value) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Check if an array contains a specific value (strict equality).

- `array` (required): array to search
- `value` (required): value to find

```bash
echo '{"tags": ["urgent", "billing", "support"]}' | utlx -e 'includes($input.tags, "urgent")'
# true
```

```utlx
{
  isUrgent: includes($input.tags, "urgent"),
  isVIP: includes($input.roles, "VIP")
}
```

=== indexOf(array, value) → number #text(size: 8pt, fill: gray)[(Arr)]

Find the 0-based position of the FIRST occurrence of a value in an array. Returns -1 if not found.

- `array` (required): array to search in
- `value` (required): value to find

```bash
echo '{"items": ["Apple", "Banana", "Cherry"]}' \
  | utlx -e 'indexOf($input.items, "Banana")'
# 1 (0-based: Apple=0, Banana=1, Cherry=2)
```

```utlx
{
  pos: indexOf($input.items, "Banana"),   // 1 (0-based index)
  missing: indexOf($input.items, "Grape") // -1 (not found)
}
```

=== inflate(data) → binary #text(size: 8pt, fill: gray)[(Bin)]

Decompress Deflate-compressed binary data.

- `data` (required): deflate-compressed binary

```utlx
{
  decompressed: inflate($input.compressedData)
}
```

=== info(message) → null #text(size: 8pt, fill: gray)[(Sys)]

Log a message at INFO level. Returns null (passthrough for pipeline usage).

- `message` (required): message to log

```utlx
info("Starting transformation")
let result = map($input.items, (i) -> i.name)
info(concat("Processed ", toString(count(result)), " items"))
{
  items: result
}
// Logs: "Starting transformation", "Processed 2 items"
// Output: {"items": ["A", "B"]}
```

=== insertAfter(array, index, element) → array #text(size: 8pt, fill: gray)[(Arr)]

Insert an element after the specified index in an array.

- `array` (required): source array
- `index` (required): position after which to insert
- `element` (required): element to insert

```bash
echo '{"items": ["a", "b", "d"]}' | utlx -e 'insertAfter($input.items, 1, "c")'
# ["a", "b", "c", "d"]
```

```utlx
{
  updated: insertAfter($input.steps, 2, {name: "validation", order: 3})
}
```

=== insertBefore(array, index, element) → array #text(size: 8pt, fill: gray)[(Arr)]

Insert an element before the specified index in an array.

- `array` (required): source array
- `index` (required): position before which to insert
- `element` (required): element to insert

```bash
echo '{"items": ["a", "c", "d"]}' | utlx -e 'insertBefore($input.items, 1, "b")'
# ["a", "b", "c", "d"]
```

```utlx
{
  updated: insertBefore($input.items, 0, "first")
}
```

=== invert(object) → object #text(size: 8pt, fill: gray)[(Obj)]

Invert an object by swapping keys and values. Values become keys and keys become values.

- `object` (required): object to invert

```bash
echo '{"US": "United States", "GB": "United Kingdom"}' | utlx -e 'invert($input)'
# {"United States": "US", "United Kingdom": "GB"}
```

```utlx
{
  countryByName: invert($input.codeToName)
}
```

=== iqr(numbers) → number #text(size: 8pt, fill: gray)[(Num)]

Calculate the interquartile range (IQR = Q3 - Q1) of a numeric array.

- `numbers` (required): array of numbers

```bash
echo '{"data": [1, 3, 5, 7, 9, 11, 13]}' | utlx -e 'iqr($input.data)'
# 8
```

```utlx
{
  spread: iqr($input.values),
  outlierThreshold: iqr($input.values) * 1.5
}
```

=== isAfter(date1, date2) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if the first date is after the second date.

- `date1` (required): date to compare
- `date2` (required): reference date

```utlx
{
  isOverdue: isAfter(now(), parseDate($input.dueDate, "yyyy-MM-dd")),
  isExpired: isAfter(now(), $input.expiresAt)
}
```

=== isAlpha(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only alphabetic characters (A-Z, a-z, Unicode letters).

- `string` (required): string to test

```bash
echo '{"name": "Alice"}' | utlx -e 'isAlpha($input.name)'
# true
```

```utlx
{
  validName: isAlpha($input.firstName),
  hasSpecialChars: !isAlpha($input.input)
}
```

=== isAlphanumeric(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only alphanumeric characters (A-Z, a-z, 0-9, Unicode letters).

- `string` (required): string to test

```utlx
isAlphanumeric("Hello123")               // true
isAlphanumeric("Hello 123")              // false (contains space)
{
  validCode: isAlphanumeric($input.productCode)
}
```

=== isAscii(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only ASCII characters (code points 0-127).

- `string` (required): string to test

```utlx
isAscii("Hello!")                        // true
isAscii("cafe\u0301")                    // false (contains accent)
{
  asciiSafe: isAscii($input.text)
}
```

=== isBefore(date1, date2) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if the first date is before the second date.

- `date1` (required): date to compare
- `date2` (required): reference date

```utlx
{
  notYetDue: isBefore(now(), parseDate($input.dueDate, "yyyy-MM-dd")),
  isPast: isBefore($input.eventDate, now())
}
```

=== isBetween(date, start, end) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if a date is between two other dates (inclusive).

- `date` (required): date to test
- `start` (required): range start date
- `end` (required): range end date

```utlx
{
  inRange: isBetween(now(), $input.startDate, $input.endDate),
  inQ1: isBetween($input.date, parseDate("2026-01-01", "yyyy-MM-dd"), parseDate("2026-03-31", "yyyy-MM-dd"))
}
```

=== isCanonicalJSON(json) → boolean #text(size: 8pt, fill: gray)[(JSON)]

Validates that a JSON string is in canonical form per RFC 8785 (JSON Canonicalization Scheme). See Chapter 24.

- `json` (required): JSON string to validate

```bash
echo '{"json": "{\"a\":1,\"b\":2}"}' | utlx -e 'isCanonicalJSON($input.json)'
# true
```

```utlx
{
  isCanonical: isCanonicalJSON(renderJson($input))
}
```

=== isCDATA(text) → boolean #text(size: 8pt, fill: gray)[(XML)]

Checks if a string is a CDATA section (wrapped in `<![CDATA[...]]>`). See Chapter 22.

- `text` (required): string to check

```utlx
{
  isCdata: isCDATA($input.xmlField),
  content: if (isCDATA($input.field)) extractCDATA($input.field) else $input.field
}
```

=== isDebugMode() → boolean #text(size: 8pt, fill: gray)[(Sys)]

Check if the current transformation is running in debug mode.

```utlx
{
  debug: isDebugMode(),
  extra: if (isDebugMode()) { logs: getLogs() } else null
}
```

=== isEmptyElement(element) → boolean #text(size: 8pt, fill: gray)[(XML)]

Check if an XML element is empty (no children, no text content). See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  isEmpty: isEmptyElement($input.node),
  nonEmpty: filter($input.elements, (e) -> !isEmptyElement(e))
}
```

=== isGzipped(data) → boolean #text(size: 8pt, fill: gray)[(Bin)]

Check if binary data is gzip-compressed (by checking magic bytes).

- `data` (required): binary data to check

```utlx
{
  compressed: isGzipped($input.payload),
  content: if (isGzipped($input.data)) gunzip($input.data) else $input.data
}
```

=== isHexadecimal(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only valid hexadecimal characters (0-9, A-F, a-f).

- `string` (required): string to test

```utlx
isHexadecimal("1a2b3c")                  // true
isHexadecimal("xyz")                     // false
{
  validHex: isHexadecimal($input.colorCode)
}
```

=== isJarFile(data) → boolean #text(size: 8pt, fill: gray)[(Bin)]

Check if binary data is a JAR file.

- `data` (required): binary data to check

```utlx
{
  isJar: isJarFile($input.fileData)
}
```

=== isJWSFormat(token) → boolean #text(size: 8pt, fill: gray)[(Sec)]

Checks if a string is in valid JWS format (three Base64URL segments separated by dots). See Chapter 38.

- `token` (required): string to validate

```utlx
{
  validFormat: isJWSFormat($input.token),
  canDecode: isJWSFormat($input.authHeader)
}
```

=== isJWTExpired(token) → boolean #text(size: 8pt, fill: gray)[(Sec)]

Checks if a JWT is expired based on its `exp` claim. See Chapter 38.

- `token` (required): JWT token string

```utlx
{
  expired: isJWTExpired($input.token),
  needsRefresh: isJWTExpired($input.accessToken)
}
```

=== isLeapYearFunc(year) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if a year is a leap year.

- `year` (required): year number

```utlx
isLeapYearFunc(2024)                     // true
isLeapYearFunc(2026)                     // false
{
  leap: isLeapYearFunc(year(now()))
}
```

=== isLocalDateTime(value) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if a value is a local datetime (datetime without timezone information).

- `value` (required): value to test

```utlx
{
  isLocal: isLocalDateTime($input.timestamp)
}
```

=== isLowerCase(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if all alphabetic characters in the string are lowercase. Non-alphabetic characters are ignored.

- `string` (required): string to test

```utlx
isLowerCase("hello")                     // true
isLowerCase("Hello")                     // false
isLowerCase("hello123")                  // true (digits ignored)
{
  isLower: isLowerCase($input.code)
}
```

=== isNotEmpty(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Checks if a value is not empty (inverse of isEmpty). Returns true for non-null, non-empty values.

- `value` (required): value to test

```utlx
isNotEmpty("hello")                      // true
isNotEmpty("")                           // false
isNotEmpty(null)                         // false
isNotEmpty([1, 2])                       // true
{
  hasName: isNotEmpty($input.name),
  hasItems: isNotEmpty($input.items)
}
```

=== isNumeric(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only numeric digits (0-9).

- `string` (required): string to test

```bash
echo '{"zip": "12345"}' | utlx -e 'isNumeric($input.zip)'
# true
```

```utlx
{
  validZip: isNumeric($input.zipCode),
  numericId: isNumeric($input.id)
}
```

=== isPlural(word) → boolean #text(size: 8pt, fill: gray)[(Str)]

Checks if a word is in plural form.

- `word` (required): word to check

```utlx
isPlural("cats")                         // true
isPlural("cat")                          // false
isPlural("children")                     // true
```

=== isPointInCircle(lat, lon, centerLat, centerLon, radiusKm) → boolean #text(size: 8pt, fill: gray)[(Geo)]

Checks if a point is within a circular radius from a center point.

- `lat` (required): point latitude
- `lon` (required): point longitude
- `centerLat` (required): circle center latitude
- `centerLon` (required): circle center longitude
- `radiusKm` (required): radius in kilometers

```utlx
{
  inRange: isPointInCircle($input.lat, $input.lon, 52.3676, 4.9041, 10),
  nearStore: isPointInCircle($input.userLat, $input.userLon, $input.storeLat, $input.storeLon, 5)
}
```

=== isPointInPolygon(lat, lon, polygon) → boolean #text(size: 8pt, fill: gray)[(Geo)]

Checks if a point is inside a polygon using the ray casting algorithm.

- `lat` (required): point latitude
- `lon` (required): point longitude
- `polygon` (required): array of [lat, lon] coordinate pairs

```utlx
let zone = [[52.0, 4.0], [52.5, 4.0], [52.5, 5.0], [52.0, 5.0]]
{
  inZone: isPointInPolygon($input.lat, $input.lon, zone)
}
```

=== isPrintable(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only printable characters (letters, digits, punctuation, spaces).

- `string` (required): string to test

```utlx
isPrintable("Hello, World!")             // true
isPrintable("Hello\x00World")            // false (contains null byte)
{
  safe: isPrintable($input.userInput)
}
```

=== isSameDay(date1, date2) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if two dates fall on the same calendar day (ignoring time).

- `date1` (required): first date
- `date2` (required): second date

```utlx
{
  sameDay: isSameDay($input.created, $input.modified),
  createdToday: isSameDay($input.created, now())
}
```

=== isSingular(word) → boolean #text(size: 8pt, fill: gray)[(Str)]

Checks if a word is in singular form.

- `word` (required): word to check

```utlx
isSingular("cat")                        // true
isSingular("cats")                       // false
isSingular("child")                      // true
```

=== isToday(date) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if a date is today.

- `date` (required): date to check

```utlx
{
  isNew: isToday($input.createdAt),
  todayOrders: filter($input.orders, (o) -> isToday(o.date))
}
```

=== isUpperCase(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if all alphabetic characters in the string are uppercase. Non-alphabetic characters are ignored.

- `string` (required): string to test

```utlx
isUpperCase("HELLO")                     // true
isUpperCase("Hello")                     // false
isUpperCase("ABC123")                    // true (digits ignored)
{
  isUpper: isUpperCase($input.countryCode)
}
```

=== isUuidV7(uuid) → boolean #text(size: 8pt, fill: gray)[(Sec)]

Check if a UUID string is specifically version 7 (time-ordered). See Chapter 38.

- `uuid` (required): UUID string to check

```utlx
{
  isV7: isUuidV7($input.messageId),
  canExtractTime: isUuidV7($input.id)
}
```

=== isValidAmount(amount) → boolean #text(size: 8pt, fill: gray)[(Fin)]

Validates if an amount is within acceptable range (finite, not NaN).

- `amount` (required): numeric value to validate

```utlx
{
  valid: isValidAmount($input.total),
  canProcess: isValidAmount($input.payment) && $input.payment > 0
}
```

=== isValidCoordinates(lat, lon) → boolean #text(size: 8pt, fill: gray)[(Geo)]

Checks if coordinates are valid (latitude -90 to 90, longitude -180 to 180).

- `lat` (required): latitude value
- `lon` (required): longitude value

```utlx
{
  valid: isValidCoordinates($input.lat, $input.lon)
}
```

=== isValidCurrency(code) → boolean #text(size: 8pt, fill: gray)[(Fin)]

Validates if a currency code is valid according to ISO 4217.

- `code` (required): currency code string (e.g., "USD", "EUR")

```bash
echo '{"currency": "USD"}' | utlx -e 'isValidCurrency($input.currency)'
# true
```

```utlx
{
  valid: isValidCurrency($input.currencyCode),
  error: if (!isValidCurrency($input.currency)) "Invalid currency" else null
}
```

=== isValidTimezone(timezone) → boolean #text(size: 8pt, fill: gray)[(Date)]

Check if a timezone ID string is valid.

- `timezone` (required): timezone ID to validate

```utlx
isValidTimezone("America/New_York")      // true
isValidTimezone("Invalid/Zone")          // false
{
  valid: isValidTimezone($input.userTimezone)
}
```

=== isValidURL(url) → boolean #text(size: 8pt, fill: gray)[(URL)]

Validate if a string is a well-formed URL.

- `url` (required): string to validate

```bash
echo '{"url": "https://example.com/path"}' | utlx -e 'isValidURL($input.url)'
# true
```

```utlx
{
  valid: isValidURL($input.website),
  links: filter($input.urls, (u) -> isValidURL(u))
}
```

=== isValidUuid(uuid) → boolean #text(size: 8pt, fill: gray)[(Sec)]

Validate if a string is a properly formatted UUID (any version). See Chapter 38.

- `uuid` (required): string to validate

```bash
echo '{"id": "550e8400-e29b-41d4-a716-446655440000"}' | utlx -e 'isValidUuid($input.id)'
# true
```

```utlx
{
  valid: isValidUuid($input.correlationId),
  error: if (!isValidUuid($input.id)) "Invalid UUID format" else null
}
```

=== isWhitespace(string) → boolean #text(size: 8pt, fill: gray)[(Str)]

Returns true if the string contains only whitespace characters (spaces, tabs, newlines).

- `string` (required): string to test

```utlx
isWhitespace("   ")                      // true
isWhitespace(" \t\n ")                   // true
isWhitespace("  a  ")                    // false
{
  blank: isWhitespace($input.field)
}
```

=== isZipArchive(data) → boolean #text(size: 8pt, fill: gray)[(Bin)]

Check if binary data is a zip archive (by checking magic bytes).

- `data` (required): binary data to check

```utlx
{
  isZip: isZipArchive($input.fileData),
  entries: if (isZipArchive($input.data)) listZipEntries($input.data) else []
}
```

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

=== isDate(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a date (not a string representation of a date).

- `value` (required): the value to test

```utlx
isDate(parseDate("2026-05-01", "yyyy-MM-dd"))       // true
isDate("2026-05-01")                                  // false (string, not date)
```

=== isDateTime(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a datetime.

- `value` (required): the value to test

```utlx
isDateTime(now())                                     // true
isDateTime("2026-05-01T14:30:00Z")                    // false (string, not datetime)
```

=== isTime(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is a time value.

- `value` (required): the value to test

```utlx
isTime(parseTime("14:30:00", "HH:mm:ss"))            // true
isTime("14:30:00")                                    // false (string, not time)
```

=== isBlank(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is null, empty string, or whitespace-only.

- `value` (required): the value to test

```utlx
isBlank(null)                            // true
isBlank("")                              // true
isBlank("  ")                            // true (whitespace-only)
isBlank("hello")                         // false
```

=== isEmpty(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is null, empty string, or empty array.

- `value` (required): the value to test

```utlx
isEmpty(null)                            // true
isEmpty("")                              // true
isEmpty([])                              // true (empty array)
isEmpty("hello")                         // false
isEmpty([1])                             // false
```

=== isDefined(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Returns true if the value is not null. Empty strings and zero are considered defined.

- `value` (required): the value to test

```utlx
isDefined(null)                          // false
isDefined("")                            // true (empty string IS defined)
isDefined(0)                             // true (zero IS defined)
isDefined($input.name)                   // true if field exists and is not null
```

=== isLeapYear(year) → boolean #text(size: 8pt, fill: gray)[(Date)]

Returns true if the given year is a leap year.

- `year` (required): year number

```utlx
isLeapYear(2024)                         // true (divisible by 4, not by 100, or by 400)
isLeapYear(2026)                         // false
```

=== isWeekday(date) → boolean #text(size: 8pt, fill: gray)[(Date)]

Returns true if the date falls on a weekday (Monday through Friday).

- `date` (required): date or datetime

```utlx
isWeekday(parseDate("2026-05-01", "yyyy-MM-dd"))  // true (Thursday)

// Use case: calculate business days
let startDate = parseDate($input.start, "yyyy-MM-dd")
let workDays = filter(
  map(range(0, 30), (i) -> addDays(startDate, i)),
  (d) -> isWeekday(d)
)
{ businessDays: count(workDays) }
```

=== isWeekend(date) → boolean #text(size: 8pt, fill: gray)[(Date)]

Returns true if the date falls on a weekend (Saturday or Sunday).

- `date` (required): date or datetime

```utlx
isWeekend(parseDate("2026-05-03", "yyyy-MM-dd"))  // true (Sunday)
```
