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

=== endTimer #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== enforceNamespacePrefixes #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== entries(object) → array #text(size: 8pt, fill: gray)[(Obj)]

Decompose an object into an array of `[key, value]` pairs. Essential for dynamic key processing. See Chapter 26.

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
env("HOME")                              // "/Users/alice"
env("UNDEFINED_VAR")                     // null
```

Also: `hasEnv(name)` → boolean, `envAll()` → object with all environment variables.

=== envOrDefault(name, default) → string #text(size: 8pt, fill: gray)[(Sys)]

Read an environment variable, returning a default value if not set.

- `name` (required): environment variable name
- `default` (required): fallback value if the variable is not set

```utlx
envOrDefault("LOG_LEVEL", "INFO")        // "INFO" if LOG_LEVEL not set
envOrDefault("DATABASE_URL", "postgres://localhost:5432/mydb")
```

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
{
  allLines: flatMap($input.orders, (o) -> o.lines)
}
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

=== generateIV #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== generateKey #text(size: 8pt, fill: gray)[(TODO)]

// TODO

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

// v7 is sortable by creation time — useful for database primary keys
generateUuidV7Batch(5)  // generate 5 sequential v7 UUIDs
```

Also: `isUuidV7(string)`.

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
let ns = getNamespaces($input.Invoice)
{
  namespaces: ns,
  isSoap: hasKey(ns, "soap"),
  hasCommonBasic: hasKey(ns, "cbc")
}
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
```

=== hash(data, algorithm?) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a cryptographic hash with an explicit algorithm. Returns hex-encoded digest string.

- `data` (required): string to hash
- `algorithm` (optional, default `"SHA-256"`): algorithm name (`"MD5"`, `"SHA-1"`, `"SHA-256"`, `"SHA-384"`, `"SHA-512"`, `"SHA3-256"`, `"SHA3-512"`)

```utlx
hash("hello", "SHA3-256")
// Output: "3338be694f50c5f338814986cdf0686453a888b84f424d792af4b9202398f392"

hash("hello", "SHA-256")
// Output: "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
```

Also: `sha1(data)`, `sha224(data)`, `sha384(data)`, `sha3_256(data)`, `sha3_512(data)`.

=== md5(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an MD5 hash. Returns hex-encoded digest string.

- `data` (required): string to hash

```utlx
md5("hello")
// Output: "5d41402abc4b2a76b9719d911017c592"
```

*Anti-pattern:* `md5()` for security — MD5 is cryptographically broken. Use `sha256()` minimum. MD5 is acceptable only for non-security checksums (file deduplication, cache keys).

=== sha256(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-256 hash. Returns hex-encoded digest string.

- `data` (required): string to hash

```utlx
sha256("hello")
// Output: "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

// Use case: content-addressed caching
let contentHash = sha256(renderJson($input))
{...$input, hash: contentHash}
```

=== sha512(data) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute a SHA-512 hash. Returns hex-encoded digest string.

- `data` (required): string to hash

```utlx
sha512("hello")
// Output: "9b71d224bd62f3785d96d46ad3ea3d73..."
```

=== hasNamespace #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hasNumeric #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hexDecode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hexEncode #text(size: 8pt, fill: gray)[(TODO)]

// TODO

=== hmac(data, key, algorithm) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an HMAC (Hash-based Message Authentication Code) with an explicit algorithm. Returns hex-encoded string.

- `data` (required): the message to authenticate
- `key` (required): the secret key
- `algorithm` (required): hash algorithm (e.g., `"SHA-256"`, `"SHA-512"`)

```utlx
hmac("message", "key", "SHA-512")
// Output: "..." (HMAC-SHA512)
```

Also: `hmacSHA512(data, key)`, `hmacSHA1(data, key)`, `hmacMD5(data, key)`, `hmacBase64(data, key, algorithm)` (returns Base64 instead of hex).

=== hmacSHA256(data, key) → string #text(size: 8pt, fill: gray)[(Sec)]

Compute an HMAC-SHA256 for verifying message integrity and authenticity. Returns hex-encoded string.

- `data` (required): the message to authenticate
- `key` (required): the secret key

```utlx
hmacSHA256("message-to-verify", "my-secret-key")
// Output: "4a8f3d..." (HMAC-SHA256 hex string)

// Use case: verify webhook signature
let expectedSig = hmacSHA256($input.body, env("WEBHOOK_SECRET"))
if (expectedSig != $input.headers.signature) error("Invalid signature")
```

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
let workDays = filter(
  map(range(0, 30), (i) -> addDays(startDate, i)),
  (d) -> isWeekday(d)
)
```

=== isWeekend(date) → boolean #text(size: 8pt, fill: gray)[(Date)]

Returns true if the date falls on a weekend (Saturday or Sunday).

- `date` (required): date or datetime

```utlx
isWeekend(parseDate("2026-05-03", "yyyy-MM-dd"))  // true (Sunday)
```

