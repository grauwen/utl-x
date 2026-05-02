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

