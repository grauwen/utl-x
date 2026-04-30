= Standard Library Overview

UTL-X ships with 652 built-in functions organized in 18 categories. This chapter provides a guided tour — what each category offers, when you'd use it, and the most important functions in each. The complete reference with signatures and examples for every function is in Part VIII (Chapter 48).

You don't need to memorize 652 functions. You need to know _what categories exist_ so you can search for the right function when you need it. The IDE's function library and `utlx functions --search` command make discovery easy.

== String Functions (83 functions)

The largest category. Text manipulation is central to data transformation — field formatting, concatenation, parsing, validation.

=== Most-Used String Functions

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Function*], [*What it does*], [*Example*],
  [concat(...)], [Join strings], [concat("Hello", " ", name)],
  [upperCase(s)], [UPPERCASE], [upperCase("hello") → "HELLO"],
  [lowerCase(s)], [lowercase], [lowerCase("HELLO") → "hello"],
  [trim(s)], [Remove whitespace], [trim("  hello  ") → "hello"],
  [contains(s, sub)], [Check substring], [contains("hello", "ell") → true],
  [startsWith(s, prefix)], [Check prefix], [startsWith("NL123", "NL") → true],
  [replace(s, old, new)], [Replace text], [replace("2026-04-30", "-", "/")],
  [split(s, delim)], [Split to array], [split("a,b,c", ",") → ["a","b","c"]],
  [substring(s, start, len)], [Extract part], [substring("hello", 0, 3) → "hel"],
  [length(s)], [Character count], [length("hello") → 5],
  [matches(s, regex)], [Regex test], [matches("abc123", "[a-z]+[0-9]+") → true],
)

=== Case Conversion

Beyond simple upper/lower, UTL-X supports naming convention conversions:

```utlx
camelCase("order_line_item")     // "orderLineItem"
snakeCase("orderLineItem")      // "order_line_item"
kebabCase("orderLineItem")      // "order-line-item"
capitalize("hello world")       // "Hello world"
```

These are essential for API mapping — converting between Java-style camelCase, Python-style snake\_case, and URL-style kebab-case.

=== Padding and Formatting

```utlx
leftPad("42", 5, "0")           // "00042"  (invoice numbers)
rightPad("EUR", 5, " ")         // "EUR  "  (fixed-width output)
truncate("Long description...", 50)  // first 50 chars
```

== Array Functions (67 functions)

The second-largest category. Arrays are everywhere — order lines, user lists, CSV rows, XML repeated elements.

=== Transform and Filter

```utlx
map(items, (i) -> i.name)                    // transform each
filter(items, (i) -> i.active)               // keep matching
flatMap(items, (i) -> i.tags)                // map + flatten
```

=== Search and Access

```utlx
find(items, (i) -> i.id == "X")              // first match or null
first(items)                                  // first element
last(items)                                   // last element
count(items)                                  // length
contains(items, "value")                      // is value in array?
```

=== Aggregate

```utlx
sum(numbers)                                  // add all
avg(numbers)                                  // average
min(numbers)                                  // smallest
max(numbers)                                  // largest
reduce(items, init, (acc, x) -> ...)          // custom aggregation
```

=== Reshape

```utlx
sort(items)                                   // natural order
sortBy(items, (i) -> i.name)                 // sort by field
reverse(items)                                // reverse order
unique(items)                                 // remove duplicates
flatten([[1,2],[3,4]])                        // [1,2,3,4]
groupBy(items, (i) -> i.category)            // group into map
take(items, 5)                                // first 5
drop(items, 5)                                // skip first 5
slice(items, 2, 7)                            // elements 2-6
chunk(items, 3)                               // groups of 3
zip(array1, array2)                           // pair elements
```

== Math Functions (37 functions)

Numeric operations for financial calculations, statistics, and data processing.

```utlx
abs(-42)              // 42
ceil(3.2)             // 4
floor(3.8)            // 3
round(3.456)          // 3
round(3.456 * 100) / 100  // 3.46 (2 decimal places)
sqrt(144)             // 12
pow(2, 10)            // 1024
min(10, 20)           // 10
max(10, 20)           // 20
```

For financial precision, use `round()` explicitly — UTL-X uses floating-point arithmetic, which can produce rounding artifacts (0.1 + 0.2 = 0.30000000000000004). Always round currency amounts: `round(amount * 100) / 100`.

== Date/Time Functions (68 functions)

The third-largest category. Date handling is critical in integration — every message has timestamps, due dates, and validity periods.

=== Current Time

```utlx
now()                 // 2026-04-30T10:30:00Z (with timezone)
today()               // 2026-04-30 (date only)
```

=== Parse and Format

```utlx
parseDate("2026-04-30", "yyyy-MM-dd")          // parse string to date
formatDate(date, "dd/MM/yyyy")                  // format date to string
parseDate("30-04-2026", "dd-MM-yyyy")           // European format
formatDate(date, "MMMM d, yyyy")                // "April 30, 2026"
```

=== Arithmetic

```utlx
addDays(date, 30)                               // 30 days later
addMonths(date, 3)                              // 3 months later
addYears(date, 1)                               // next year
dateDiff(date1, date2, "days")                  // days between dates
```

=== Extract Components

```utlx
year(date)            // 2026
month(date)           // 4
day(date)             // 30
hour(datetime)        // 10
minute(datetime)      // 30
```

== Type Functions (27 functions)

Check types and convert between them.

=== Type Checking

```utlx
isString("hello")     // true
isNumber(42)          // true
isBoolean(true)       // true
isArray([1,2])        // true
isObject({a: 1})      // true
isNull(null)          // true
getType("hello")      // "string"
```

=== Type Conversion

```utlx
toString(42)          // "42"
toNumber("42")        // 42
toBoolean("true")     // true
toNumber("not-a-num") // 0 (safe — returns 0 for unparseable input)
```

== Encoding Functions (30 functions)

Encode and decode data for transport and storage.

```utlx
base64Encode("hello")                          // "aGVsbG8="
base64Decode("aGVsbG8=")                       // "hello"
urlEncode("hello world")                       // "hello%20world"
urlDecode("hello%20world")                     // "hello world"
urlEncodeComponent("a=b&c=d")                  // "a%3Db%26c%3Dd"
hexEncode("hello")                             // "68656c6c6f"
hexDecode("68656c6c6f")                        // "hello"
```

== Security Functions (16 functions)

Hashing and UUID generation. Part of the separate `stdlib-security` module.

```utlx
md5("hello")                                   // "5d41402abc4b2a76..."
sha256("hello")                                // "2cf24dba5fb0a30e..."
hmacSha256("message", "secret-key")            // HMAC signature
uuid()                                         // random UUID v4
```

See Chapter 16 for security library details including encryption, digital signatures, and compliance considerations.

== XML Functions (60 functions)

XML-specific operations for encoding, namespace handling, and encoding detection.

```utlx
detectXMLEncoding(xmlString)                    // "UTF-8" or "ISO-8859-1"
convertXMLEncoding(xmlString, "UTF-8")          // re-encode
stripBOM(data)                                  // remove byte order mark
```

== Other Categories

=== JSON Functions (6)

Parse and render JSON within transformations:

```utlx
parseJson("{\"name\": \"Alice\"}")              // parse string to UDM
renderJson(object)                              // UDM to JSON string
```

=== CSV Functions (12)

Parse and render CSV:

```utlx
parseCsv("name,age\nAlice,30")                 // parse to array of objects
renderCsv(arrayOfObjects)                       // to CSV string
```

=== YAML Functions (22)

Parse and render YAML:

```utlx
parseYaml("name: Alice\nage: 30")              // parse to UDM
renderYaml(object)                              // to YAML string
```

=== Financial Functions (16)

Currency formatting and financial calculations:

```utlx
formatCurrency(1234.56, "EUR")                 // "EUR 1,234.56"
parseAmount("1.234,56", "european")            // 1234.56
```

=== Geospatial Functions (8)

Distance calculations:

```utlx
distance(52.37, 4.90, 48.86, 2.35)            // Amsterdam to Paris in km
```

=== Binary Functions (47)

Compression, byte manipulation:

```utlx
compress(data, "gzip")                         // compress
decompress(data, "gzip")                       // decompress
```

=== Utility Functions (27)

General-purpose helpers:

```utlx
range(1, 10)                                   // [1, 2, 3, ..., 9]
repeat("ab", 3)                                // "ababab"
coalesce(null, null, "found")                  // "found" (first non-null)
```

=== Object Functions (1)

```utlx
keys(object)                                   // ["name", "age"]
values(object)                                 // ["Alice", 30]
```

== Finding the Right Function

With 652 functions, discovery matters more than memorization.

=== In the CLI

```bash
utlx functions --search "date"        # find date-related functions
utlx functions --module string         # list all string functions
utlx functions                         # list all categories
```

=== In the IDE

The function library panel in VS Code lets you browse by category and search by name or description. Click a function to see its signature and an example.

=== In This Book

Chapter 48 (Part VIII) contains the complete reference — every function with its signature, parameter types, return type, description, and a runnable example. Use it as a desk reference.

== A Note on Naming

UTL-X uses camelCase for all function names: `upperCase`, `lowerCase`, `parseDate`, `formatDate`, `toNumber`, `groupBy`, `sortBy`. This is consistent and predictable — if you know the concept, you can guess the name.

Common mistake: `lowercase` (wrong) → `lowerCase` (correct). The IDE catches this immediately with a red underline.
