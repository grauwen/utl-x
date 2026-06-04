== J

=== javaVersion() → string #text(size: 8pt, fill: gray)[(Sys)]

Get the Java/JVM version string of the runtime.

```utlx
{
  runtime: javaVersion()              // "21.0.2"
}
```

=== jcs(json) → string #text(size: 8pt, fill: gray)[(JSON)]

JSON Canonicalization Scheme (RFC 8785). Produces deterministic JSON — identical output regardless of key order or whitespace. See Chapter 24.

- `json` (required): JSON UDM value to canonicalize

```bash
echo '{"z": 3, "a": 1, "m": 2}' | utlx -e 'jcs($input)'
# {"a":1,"m":2,"z":3}  (keys sorted, no whitespace)
```

Also: `canonicalJSONHash(json, algorithm?)` (hash the canonical form), `canonicalJSONSize(json)` (byte size), `isCanonicalJSON(string)`.

=== canonicalizeJSON(json) → string #text(size: 8pt, fill: gray)[(JSON)]

Alias for `jcs()`. Produces deterministic JSON using RFC 8785.

- `json` (required): JSON UDM value to canonicalize

```bash
echo '{"z": 3, "a": 1, "m": 2}' | utlx -e 'canonicalizeJSON($input)'
# {"a":1,"m":2,"z":3}  (keys sorted, no whitespace)
```

=== jsonEquals(json1, json2) → boolean #text(size: 8pt, fill: gray)[(JSON)]

Compare two JSON values semantically, ignoring key order and whitespace. See Chapter 24.

- `json1` (required): first value to compare
- `json2` (required): second value to compare

```utlx
{
  same: jsonEquals({b: 2, a: 1}, {a: 1, b: 2}),  // true (same content, different key order)
  diff: jsonEquals({a: 1}, {a: 2})                // false
}
```

```utlx
// Use case: detect changes between two API responses. See Chapter 24.
if (!jsonEquals($input.previous, $input.current)) {
  changed: true,
  hash: canonicalJSONHash($input.current)
}
```

=== join(array, separator) → string #text(size: 8pt, fill: gray)[(Str)]

Join array elements into a single string with a separator. This is the *string* join — for data restructuring (nesting children under parents), see `nestBy()` in Chapter 20.

- `array` (required): array of values to join (non-strings are converted automatically)
- `separator` (required): string to insert between elements

```bash
echo '{"tags": ["urgent", "billing", "review"]}' | utlx -e 'join($input.tags, ", ")'
# urgent, billing, review
```

```utlx
{
  label: join($input.tags, " | "),
  csvLine: join([$input.name, toString($input.age), $input.city], ";"),
  path: join(["usr", "local", "bin"], "/")
}
```

*Anti-pattern:* `reduce(arr, "", (acc, x) -> concat(acc, x, ", "))` — creates N intermediate strings. `join()` builds the result in one pass.

=== joinToString(array, separator?) → string #text(size: 8pt, fill: gray)[(Str)]

Join array elements into a string with optional separator (defaults to `","`). Alias-style alternative to `join()`.

- `array` (required): array of values to join
- `separator` (optional): delimiter string, defaults to `","`

```bash
echo '["a", "b", "c"]' | utlx -e 'joinToString($input, " - ")'
# a - b - c
```

=== joinWith(left, right, leftKeyFn, rightKeyFn, combinerFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Inner join two arrays by key with a custom combiner function. Unlike `join()` which returns `{l, r}` pairs, `joinWith` lets you shape the output.

- `left` (required): left array
- `right` (required): right array
- `leftKeyFn` (required): lambda extracting key from left items
- `rightKeyFn` (required): lambda extracting key from right items
- `combinerFn` (required): lambda `(leftItem, rightItem) -> result`

```utlx
let customers = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let orders = [{customerId: 1, product: "Widget"}]

{
  joined: joinWith(customers, orders,
    (c) -> c.id, (o) -> o.customerId,
    (c, o) -> {name: c.name, product: o.product}
  )
  // [{name: "Alice", product: "Widget"}]
}
```

== K

=== kebabCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to kebab-case (lowercase words separated by hyphens).

- `string` (required): the string to convert

```bash
echo '"orderLineItem"' | utlx -e 'kebabCase($input)'
# order-line-item
```

```utlx
{
  fromCamel: kebabCase("OrderLineItem"),     // "order-line-item"
  fromSnake: kebabCase("some_snake_case")    // "some-snake-case"
}
```

=== keys(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Get all property names (keys) from an object. Key order is preserved (insertion order).

- `object` (required): the object to inspect

```bash
echo '{"name": "Alice", "age": 30, "city": "Amsterdam"}' | utlx -e 'keys($input)'
# ["name", "age", "city"]
```

```utlx
// Iterate over dynamic keys
{
  servers: map(keys($input.servers), (env) -> {
    environment: env,
    host: $input.servers[env].host
  })
}
```

=== values(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Get all property values from an object. Order matches `keys()`.

- `object` (required): the object to inspect

```bash
echo '{"name": "Alice", "age": 30, "city": "Amsterdam"}' | utlx -e 'values($input)'
# ["Alice", 30, "Amsterdam"]
```

== L

=== leftJoin(left, right, leftKeyFn, rightKeyFn) → array #text(size: 8pt, fill: gray)[(Arr)]

Left join -- returns all items from the left array, with matching items from the right (or `null` for non-matches).

- `left` (required): left array (all items preserved)
- `right` (required): right array
- `leftKeyFn` (required): lambda extracting key from left items
- `rightKeyFn` (required): lambda extracting key from right items

```utlx
let customers = [{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]
let orders = [{customerId: 1, product: "Widget"}]

{
  result: leftJoin(customers, orders, (c) -> c.id, (o) -> o.customerId)
  // [{l: {id: 1, name: "Alice"}, r: {customerId: 1, product: "Widget"}},
  //  {l: {id: 2, name: "Bob"}, r: null}]
}
```

=== length(value) → number #text(size: 8pt, fill: gray)[(Str/Arr)]

Length of a string (character count) or array (element count). Alias for `count()` on arrays.

- `value` (required): string or array

```utlx
{
  str: length("hello"),                      // 5
  unicode: length("日本語"),                  // 3 (Unicode characters, not bytes)
  arr: length([1, 2, 3]),                    // 3
  emptyStr: length(""),                      // 0
  emptyArr: length([])                       // 0
}
```

=== listJarEntries(jarData) → array #text(size: 8pt, fill: gray)[(Bin)]

List all entries (file paths) in a JAR file.

- `jarData` (required): binary JAR data

```utlx
{
  entries: listJarEntries($input.jar)
  // ["META-INF/MANIFEST.MF", "com/example/Main.class", ...]
}
```

=== listZipEntries(zipData) → array #text(size: 8pt, fill: gray)[(Bin)]

List all entries (file paths) in a ZIP archive.

- `zipData` (required): binary ZIP data

```utlx
{
  files: listZipEntries($input.archive)
  // ["readme.txt", "data/orders.csv", "data/customers.csv"]
}
```

=== ln(number) → number #text(size: 8pt, fill: gray)[(Num)]

Natural logarithm (base _e_).

- `number` (required): positive number

```utlx
ln(1)                                    // 0
ln(e())                                  // 1
ln(10)                                   // ~2.302585
```

=== localName(element) → string #text(size: 8pt, fill: gray)[(XML)]

Extract the local name (without prefix) from an XML qualified name. See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  local: localName($input)                   // "InvoiceTypeCode"
}
```

=== namespaceUri(element) → string #text(size: 8pt, fill: gray)[(XML)]

Extract the namespace URI from an XML element. See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  ns: namespaceUri($input)                   // "urn:oasis:names:specification:ubl:..."
}
```

```utlx
// Use case: filter XML elements by namespace (XBRL taxonomy)
let usgaapFacts = filter($input.*, (elem) ->
  namespaceUri(elem) == "http://fasb.org/us-gaap/2024"
)
{
  facts: usgaapFacts
}
```

=== namespacePrefix(element) → string #text(size: 8pt, fill: gray)[(XML)]

Extract the namespace prefix from an XML element. See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  prefix: namespacePrefix($input)            // "cbc"
}
```

=== qualifiedName(element) → string #text(size: 8pt, fill: gray)[(XML)]

Get the full qualified name (prefix:localName) of an XML element. See Chapter 22.

- `element` (required): XML UDM element

```utlx
{
  qname: qualifiedName($input)               // "cbc:InvoiceTypeCode"
}
```

Also: `resolveQname(string, context)`, `matchesQname(element, pattern)`, `hasNamespace(element)`.

=== log(number, base?) → number #text(size: 8pt, fill: gray)[(Num)]

Logarithm with optional base. Without base, defaults to natural logarithm (base _e_).

- `number` (required): positive number
- `base` (optional): logarithm base (default: _e_)

```utlx
log(100, 10)                             // 2
log(8, 2)                                // 3
log(e())                                 // 1 (natural log)
```

=== log10(number) → number #text(size: 8pt, fill: gray)[(Num)]

Base-10 logarithm.

- `number` (required): positive number

```utlx
log10(1000)                              // 3
log10(1)                                 // 0
```

=== log2(number) → number #text(size: 8pt, fill: gray)[(Num)]

Base-2 logarithm.

- `number` (required): positive number

```utlx
log2(8)                                  // 3
log2(1024)                               // 10
```

=== logCount(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log the count/length of a value to stderr and pass the value through unchanged. Useful for debugging pipelines.

- `value` (required): array, string, or object to count

```utlx
{
  items: logCount($input.orders)         // logs "count: 42" to stderr, passes array through
}
```

=== logPretty(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log a pretty-printed representation of a value to stderr and pass the value through unchanged.

- `value` (required): any value to inspect

```utlx
{
  result: logPretty($input.payload)      // logs formatted JSON to stderr, passes value through
}
```

=== logSize(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log the byte size of a value to stderr and pass the value through unchanged.

- `value` (required): any value to measure

```utlx
{
  output: logSize($input.document)       // logs "size: 4096 bytes" to stderr, passes value through
}
```

=== logType(value) → value #text(size: 8pt, fill: gray)[(Sys)]

Log the type of a value to stderr and pass the value through unchanged.

- `value` (required): any value to inspect

```utlx
{
  data: logType($input.field)            // logs "type: string" to stderr, passes value through
}
```

=== lookupBy(searchValue, referenceArray, keyFn) → element | null #text(size: 8pt, fill: gray)[(Arr)]

Find one matching record from a reference array by key. Returns the first match or `null`. The go-to function for 1:1 enrichment -- adding data from a lookup table to each record. See Chapter 20.

- `searchValue` (required): the value to search for (e.g., a customer ID)
- `referenceArray` (required): the array to search in (e.g., all customers)
- `keyFn` (required): lambda extracting the comparison key from each reference record

```utlx
// Enrich order lines with product names from a product catalog
let products = $input.products           // [{sku: "W-01", name: "Widget"}, ...]

{
  orders: map($input.orders, (order) -> {
    ...order,
    productName: lookupBy(order.sku, products, (p) -> p.sku).name
  })
}
```

```bash
echo '{"id": "C-42", "customers": [{"id": "C-42", "name": "Acme"}]}' | utlx -e 'lookupBy($input.id, $input.customers, (c) -> c.id)'
# {"id": "C-42", "name": "Acme"}
```

*Choosing the right function:* `lookupBy` for 1:1 enrichment, `groupBy` for O(1) keyed map, `nestBy` for 1:N parent-child nesting.

=== lower(string) → string #text(size: 8pt, fill: gray)[(Str)]

Alias for `lowerCase()`. Convert a string to all lowercase.

- `string` (required): the string to convert

```utlx
{
  result: lower("Hello World")               // "hello world"
}
```

=== lowerCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to all lowercase.

- `string` (required): the string to convert

```utlx
{
  result: lowerCase("Hello World")           // "hello world"
}
```

Also: `camelCase`, `snakeCase`, `kebabCase`, `pascalCase`, `titleCase`, `dotCase`, `pathCase`, `constantCase`, `slugify`.

=== upperCase(string) → string #text(size: 8pt, fill: gray)[(Str)]

Convert a string to all uppercase.

- `string` (required): the string to convert

```utlx
{
  result: upperCase("Hello World"),          // "HELLO WORLD"

  // Use case: normalize API field names
  normalized: mapKeys($input, (key) -> camelCase(key))
  // {"first_name": "Alice"} -> {"firstName": "Alice"}
}
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
{
  lines: map($input.items, (item) -> {
    product: item.name,
    priceWithTax: item.price * 1.21
  }),

  // With index (second parameter):
  numbered: map($input.items, (item, index) -> {
    lineNumber: index + 1,
    product: item.name
  })
}
```

*Anti-pattern:* using `map()` to filter — `map(arr, (x) -> if (x.active) x else null)` produces nulls. Use `filter()` to remove, then `map()` to transform.

=== mapEntries(object, fn) → object #text(size: 8pt, fill: gray)[(Obj)]

Transform both keys and values of an object.

- `object` (required): the object to transform
- `fn` (required): lambda `(key, value) -> {key: newKey, value: newValue}`

```bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapEntries($input, (key, value) -> {key: upperCase(key), value: if (isString(value)) upperCase(value) else value})'
# {"FIRST_NAME": "ALICE", "LAST_NAME": "JOHNSON", "AGE": 30}
```

=== mapKeys(object, fn) → object #text(size: 8pt, fill: gray)[(Obj)]

Transform the keys of an object, keeping values unchanged.

- `object` (required): the object to transform
- `fn` (required): lambda `(key) -> newKey`

```bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapKeys($input, (key) -> camelCase(key))'
# {"firstName": "Alice", "lastName": "Johnson", "age": 30}
```

=== mapValues(object, fn) → object #text(size: 8pt, fill: gray)[(Obj)]

Transform the values of an object, keeping keys unchanged.

- `object` (required): the object to transform
- `fn` (required): lambda `(value) -> newValue`

```bash
echo '{"first_name": "Alice", "last_name": "Johnson", "age": 30}' | utlx -e 'mapValues($input, (value) -> toString(value))'
# {"first_name": "Alice", "last_name": "Johnson", "age": "30"}
```

=== mapGroups(array, keySelector, transform) → array #text(size: 8pt, fill: gray)[(Arr)]

Group array elements by key and transform each group. Returns an array of transformed results -- ideal for reporting and aggregation. See Chapter 20.

- `array` (required): input array to group
- `keySelector` (required): lambda `(item) -> key` or string property name
- `transform` (required): lambda receiving `{key, value}` group object

```utlx
// Input: flat array of orders with region field
let sales = $input.orders
// [{region: "EU", amount: 100}, {region: "US", amount: 200}, ...]

{
  summary: mapGroups(sales, "region", (group) -> {
    region: group.key,
    count: count(group.value),
    total: sum(map(group.value, (o) -> o.amount))
  })
  // Output: [{region: "EU", count: 3, total: 450},
  //          {region: "US", count: 2, total: 380}]
}
```

```bash
echo '[{"dept":"Eng","name":"Alice"},{"dept":"Eng","name":"Bob"},{"dept":"Sales","name":"Carol"}]' | utlx -e 'mapGroups($input, "dept", (g) -> {dept: g.key, headcount: count(g.value)})'
# [{"dept":"Eng","headcount":2},{"dept":"Sales","headcount":1}]
```

*Difference from `groupBy`:* `groupBy` returns an Object (keyed map for O(1) lookup); `mapGroups` returns an Array (for iteration/reporting).

=== mapTree(data, transformer) → object | array #text(size: 8pt, fill: gray)[(Obj)]

Recursively transform all values in a nested structure (objects and arrays) by applying a transformer function. Walks the entire tree depth-first.

- `data` (required): nested object or array to traverse
- `transformer` (required): lambda `(value, path) -> newValue`

```utlx
// Input: {a: "hello", b: {c: "world"}}
// Uppercase all string values, no matter how deeply nested
{
  result: mapTree($input, (v, p) ->
    if (typeOf(v) == "string") upperCase(v) else v
  )
  // {a: "HELLO", b: {c: "WORLD"}}
}
```

=== mask(string, visibleChars) → string #text(size: 8pt, fill: gray)[(Sec)]

Mask a string, keeping only the first N characters visible. For PII protection in logs and reports. See Chapter 38.

- `string` (required): the value to mask
- `visibleChars` (required): number of characters to leave visible

```utlx
{
  name: mask("Alice Johnson", 3),            // "Ali***"
  card: mask("4111111111111111", 4),         // "4111************"
  email: mask("alice@example.com", 5),       // "alice***********"
  vatId: mask("NL123456789B01", 2)           // "NL************"
}
```

```utlx
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
{
  order: matches("ORD-001", "^ORD-[0-9]+$"),         // true
  wrong: matches("INV-001", "^ORD-[0-9]+$"),         // false
  email: matches("alice@example.com",
    "^[^@]+@[^@]+\\.[^@]+$")                         // true
}
```

```utlx
// Use case: validate field formats
if (!matches($input.vatId, "^[A-Z]{2}[0-9]{9}B[0-9]{2}$"))
  error("Invalid Dutch VAT ID format")
```

Also: `matchesQname(element, pattern)` for XML QName matching (Chapter 22).

=== matchesWhole(string, pattern) → boolean #text(size: 8pt, fill: gray)[(Str)]

Test if a string matches a pattern completely (entire string must match, not just a substring). Equivalent to anchoring with `^...$`.

- `string` (required): the string to test
- `pattern` (required): regular expression pattern

```utlx
{
  full: matchesWhole("abc123", "[a-z]+[0-9]+"),      // true (whole string matches)
  partial: matchesWhole("abc123xyz", "[a-z]+[0-9]+") // false (trailing "xyz" prevents full match)
}
```

=== max(array) → number #text(size: 8pt, fill: gray)[(Num/Arr)]

Find the maximum value in a numeric array.

- `array` (required): array of numbers

```utlx
{
  highest: max([3, 1, 4, 1, 5, 9])          // 9
}
```

=== min(array) → number #text(size: 8pt, fill: gray)[(Num/Arr)]

Find the minimum value in a numeric array.

- `array` (required): array of numbers

```utlx
{
  lowest: min([3, 1, 4, 1, 5, 9])           // 1
}
```

=== maxBy(array, fn) → element #text(size: 8pt, fill: gray)[(Num/Arr)]

Find the element with the maximum value of a key extractor. Returns the entire element, not just the value.

- `array` (required): array to search
- `fn` (required): lambda `(element) -> comparable`

```bash
echo '{"products": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}, {"name": "Gizmo", "price": 10}]}' | utlx -e 'maxBy($input.products, (p) -> p.price)'
# {"name": "Gadget", "price": 150}  (the ENTIRE object, not just 150)
```

=== minBy(array, fn) → element #text(size: 8pt, fill: gray)[(Num/Arr)]

Find the element with the minimum value of a key extractor. Returns the entire element, not just the value.

- `array` (required): array to search
- `fn` (required): lambda `(element) -> comparable`

```bash
echo '{"products": [{"name": "Widget", "price": 25}, {"name": "Gadget", "price": 150}, {"name": "Gizmo", "price": 10}]}' | utlx -e 'minBy($input.products, (p) -> p.price)'
# {"name": "Gizmo", "price": 10}
```

=== measure(fn) → object #text(size: 8pt, fill: gray)[(Sys)]

Measure execution time of an expression. Returns an object with the result and elapsed time.

- `fn` (required): lambda `() -> value` to measure

```utlx
let m = measure(() -> map($input.items, (x) -> x * 2))
// m = {result: [...], elapsed: 12.5, unit: "ms"}

{
  data: m.result,
  timing: m.elapsed
}
```

=== median(array) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the median (middle value) of a numeric array.

- `array` (required): array of numbers

```bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'median($input.scores)'
# 88
```

=== mode(array) → number #text(size: 8pt, fill: gray)[(Num)]

Find the most frequently occurring value in a numeric array.

- `array` (required): array of numbers

```utlx
{
  result: mode([1, 2, 2, 3, 3, 3])          // 3 (most frequent)
}
```

=== stdDev(array) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the standard deviation of a numeric array.

- `array` (required): array of numbers

```bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'stdDev($input.scores)'
# ~8.5
```

=== variance(array) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the variance of a numeric array.

- `array` (required): array of numbers

```bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'variance($input.scores)'
# ~72.2
```

=== percentile(array, p) → number #text(size: 8pt, fill: gray)[(Num)]

Compute the p-th percentile of a numeric array.

- `array` (required): array of numbers
- `p` (required): percentile value 0-100

```bash
echo '{"scores": [72, 85, 90, 95, 88, 76, 92]}' | utlx -e 'percentile($input.scores, 90)'
# ~94.2 (90th percentile)
```

Also: `iqr(array)` — interquartile range, `quartiles(array)` — [Q1, Q2, Q3].

=== memoryInfo() → object #text(size: 8pt, fill: gray)[(Sys)]

Get JVM memory information in bytes. Returns an object with `maxMemory`, `totalMemory`, `freeMemory`, and `usedMemory`.

```utlx
{
  mem: memoryInfo()
  // {maxMemory: 4294967296, totalMemory: 2147483648, freeMemory: 1073741824, usedMemory: 1073741824}
}
```

=== merge(obj1, obj2, ...) → object #text(size: 8pt, fill: gray)[(Obj)]

Shallow merge of objects. Later arguments override earlier ones. Same as spread but as a function.

- `obj1, obj2, ...` (variadic): objects to merge

```utlx
{
  two: merge({a: 1, b: 2}, {b: 3, c: 4}),   // {a: 1, b: 3, c: 4}
  three: merge({a: 1}, {b: 2}, {c: 3})      // {a: 1, b: 2, c: 3}
}
```

*Note:* for deep (recursive) merge, use `deepMerge(obj1, obj2)`.

=== midpoint(lat1, lon1, lat2, lon2) → object #text(size: 8pt, fill: gray)[(Geo)]

Calculate the geographic midpoint between two coordinates using the Haversine formula.

- `lat1` (required): latitude of first point
- `lon1` (required): longitude of first point
- `lat2` (required): latitude of second point
- `lon2` (required): longitude of second point

```utlx
{
  center: midpoint(37.7749, -122.4194, 34.0522, -118.2437)
  // {lat: 35.9135, lon: -120.3315}
}
```

=== minutes(datetime) → number #text(size: 8pt, fill: gray)[(Date)]

Extract the minutes component (0-59) from a datetime or time value.

- `datetime` (required): datetime or time value

```utlx
{
  mins: minutes(parseDate("2026-05-01T14:35:00Z"))  // 35
}
```

=== month(date) → number #text(size: 8pt, fill: gray)[(Date)]

Extract the month component (1-12) from a date or datetime value.

- `date` (required): date or datetime value

```utlx
{
  m: month(parseDate("2026-05-01"))          // 5
}
```

=== monthName(date) → string #text(size: 8pt, fill: gray)[(Date)]

Get the full month name from a date or datetime value.

- `date` (required): date or datetime value

```utlx
{
  current: monthName(parseDate("2026-05-01")),   // "May"
  xmas: monthName(parseDate("2026-12-25"))       // "December"
}
```

== N

=== nand(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical NAND (NOT AND). Returns `true` unless both arguments are `true`.

- `a` (required): first boolean
- `b` (required): second boolean

```utlx
{
  bothTrue: nand(true, true),                // false
  mixed: nand(true, false),                  // true
  bothFalse: nand(false, false)              // true
}
```

=== nestBy(parents, children, parentKeyFn, childKeyFn, childProperty) → array #text(size: 8pt, fill: gray)[(Arr)]

Nest children under parents by matching keys. Creates a 1:N parent-child hierarchy -- the most common flat-to-hierarchical integration pattern. Performance is O(N+M) using a hash index. See Chapter 20.

- `parents` (required): array of parent records
- `children` (required): array of child records
- `parentKeyFn` (required): lambda extracting the join key from each parent
- `childKeyFn` (required): lambda extracting the join key from each child
- `childProperty` (required): string name for the new property on each parent

```utlx
// Input: flat orders and lines from two separate sources
let orders = $input.orders
// [{orderId: "O-1", customer: "Alice"}, {orderId: "O-2", customer: "Bob"}]
let lines = $input.lines
// [{orderId: "O-1", sku: "W-01"}, {orderId: "O-1", sku: "G-02"},
//  {orderId: "O-2", sku: "W-01"}]

{
  nested: nestBy(orders, lines, (o) -> o.orderId, (l) -> l.orderId, "lines")
  // Output:
  // [{orderId: "O-1", customer: "Alice",
  //   lines: [{orderId: "O-1", sku: "W-01"}, {orderId: "O-1", sku: "G-02"}]},
  //  {orderId: "O-2", customer: "Bob",
  //   lines: [{orderId: "O-2", sku: "W-01"}]}]
}
```

```bash
echo '{"depts":[{"id":"D1","name":"Eng"}],"emps":[{"dept":"D1","name":"Alice"},{"dept":"D1","name":"Bob"}]}' | utlx -e 'nestBy($input.depts, $input.emps, (d) -> d.id, (e) -> e.dept, "members")'
# [{"id":"D1","name":"Eng","members":[{"dept":"D1","name":"Alice"},{"dept":"D1","name":"Bob"}]}]
```

*Inverse:* `unnest(array, "children")` flattens the hierarchy back to a flat array.

=== nodeType(node) → string #text(size: 8pt, fill: gray)[(XML)]

Get the node type of an XML UDM node (element, attribute, text, etc.). See Chapter 22.

- `node` (required): XML UDM node

```utlx
{
  type: nodeType($input)                     // "element"
}
```

=== none(array, predicate) → boolean #text(size: 8pt, fill: gray)[(Arr)]

Check if no elements in the array match the predicate (all return `false`). Opposite of `some()`.

- `array` (required): array to test
- `predicate` (required): lambda `(element) -> boolean`

```utlx
{
  allSmall: none([1, 2, 3], (x) -> x > 10),     // true (no element > 10)
  hasLarge: none([1, 2, 3], (x) -> x > 2)       // false (3 > 2)
}
```

=== nor(a, b) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical NOR (NOT OR). Returns `true` only when both arguments are `false`.

- `a` (required): first boolean
- `b` (required): second boolean

```utlx
{
  bothFalse: nor(false, false),              // true
  oneTrue: nor(true, false),                 // false
  otherTrue: nor(false, true)                // false
}
```

=== normalizeBOM(data, targetEncoding, addBom) → binary #text(size: 8pt, fill: gray)[(XML)]

Convert binary data to a target encoding with BOM (Byte Order Mark) handling. See Chapter 22.

- `data` (required): binary data to convert
- `targetEncoding` (required): target encoding (e.g., `"UTF-8"`, `"UTF-16"`)
- `addBom` (required): boolean -- whether to add BOM to the output

```utlx
{
  output: normalizeBOM($input.xmlBytes, "UTF-8", false)
}
```

=== normalizeXMLEncoding(xml, targetEncoding) → string #text(size: 8pt, fill: gray)[(XML)]

Auto-detect the encoding of an XML string and convert it to a target encoding. Also updates the XML declaration. See Chapter 22.

- `xml` (required): XML string to re-encode
- `targetEncoding` (required): target encoding (e.g., `"UTF-8"`)

```utlx
{
  normalized: normalizeXMLEncoding($input.xmlPayload, "UTF-8")
}
```

=== not(value) → boolean #text(size: 8pt, fill: gray)[(Type)]

Logical NOT. Negates a boolean value.

- `value` (required): boolean value

```utlx
{
  negTrue: not(true),                        // false
  negFalse: not(false),                      // true
  hasName: not(isEmpty($input.name))         // true if name is non-empty
}
```

=== now() → datetime #text(size: 8pt, fill: gray)[(Date)]

Return the current UTC datetime.

```utlx
// Use case: add timestamp to output
{
  ...$input,
  timestamp: now(),                          // 2026-05-01T14:30:00Z (UTC datetime)
  processedAt: formatDate(now(), "yyyy-MM-dd'T'HH:mm:ss'Z'")
}
```

=== currentDate() → date #text(size: 8pt, fill: gray)[(Date)]

Return the current date (no time component).

```utlx
{
  today: currentDate(),                      // 2026-05-01 (date only, no time)
  formatted: formatDate(currentDate(), "yyyy-MM-dd")  // "2026-05-01"
}
```

=== currentTime() → time #text(size: 8pt, fill: gray)[(Date)]

Return the current time (no date component).

```utlx
{
  time: currentTime()                        // 14:30:00 (time only, no date)
}
```

=== normalizeSpace(string) → string #text(size: 8pt, fill: gray)[(Str)]

Collapse all whitespace sequences (spaces, tabs, newlines) to single spaces. Trim leading/trailing.

- `string` (required): the string to normalize

```utlx
{
  spaces: normalizeSpace("  hello   world  "),       // "hello world"
  mixed: normalizeSpace("line1\n  line2\t\tline3"),  // "line1 line2 line3"
  empty: normalizeSpace("")                          // ""
}
```

=== numberOrDefault(value, default) → number #text(size: 8pt, fill: gray)[(Type)]

Safely convert a value to a number, returning the default if conversion fails or the value is null.

- `value` (required): value to convert
- `default` (required): fallback number if conversion fails

```bash
echo '{"qty": "abc", "price": "19.99"}' | utlx -e '{qty: numberOrDefault($input.qty, 0), price: numberOrDefault($input.price, 0)}'
# {"qty": 0, "price": 19.99}
```

```utlx
{
  valid: numberOrDefault("42", 0),           // 42
  missing: numberOrDefault(null, -1),        // -1
  invalid: numberOrDefault("not-a-number", 0) // 0
}
```

