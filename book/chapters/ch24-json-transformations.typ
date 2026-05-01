= JSON Transformations

JSON is the native language of modern APIs, microservices, and web applications. It maps almost perfectly to UTL-X's Universal Data Model: JSON objects become UDM Objects, JSON arrays become UDM Arrays, and JSON scalars (strings, numbers, booleans, null) become UDM Scalars. This near-perfect alignment makes JSON the simplest format to work with in UTL-X — and the one most transformations involve.

== JSON and UDM: A Natural Fit

Unlike XML (which has attributes, mixed content, namespaces, and text nodes) or CSV (which is flat and untyped), JSON maps to UDM without any structural translation:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*JSON*], [*UDM*], [*Notes*],
  [`{}`], [Object], [Properties map directly to UDM Object properties],
  [`[]`], [Array], [Elements map directly to UDM Array elements],
  [`"text"`], [Scalar (String)], [No wrapping, no `_text` — just the value],
  [`42`], [Scalar (Number)], [Integers preserved as Long, decimals as Double],
  [`true`/`false`], [Scalar (Boolean)], [Direct mapping],
  [`null`], [Null], [Direct mapping],
)

This means JSON input has none of the quirks described in the XML chapters — no `_text` unwrapping, no `@attribute` prefixes, no namespace handling. What you see in the JSON is exactly what you get in `$input`.

=== What You See Is What You Get

```json
{
  "order": {
    "id": "ORD-001",
    "customer": "Acme Corp",
    "items": [
      {"product": "Widget", "qty": 10, "price": 25.00},
      {"product": "Gadget", "qty": 5, "price": 49.99}
    ],
    "priority": true,
    "notes": null
  }
}
```

Every path works exactly as you'd expect:

```utlx
$input.order.id           // "ORD-001"
$input.order.customer     // "Acme Corp"
$input.order.items[0]     // {product: "Widget", qty: 10, price: 25.00}
$input.order.items[1].product  // "Gadget"
$input.order.priority     // true
$input.order.notes        // null
count($input.order.items) // 2
```

No surprises. No unwrapping. No format-specific accessors. This is why JSON is the recommended format for learning UTL-X — you can focus on the language without worrying about format quirks.

== Reading JSON

=== Property Access

Dot notation is the primary way to navigate JSON structures:

```utlx
$input.user.name                    // simple property
$input.user.address.city            // nested property
$input.user.roles[0]                // first array element
$input.user.metadata.tags[2]        // nested array element
```

Bracket notation works for dynamic keys and keys with special characters:

```utlx
let field = "name"
$input.user[field]                  // dynamic property access

$input["content-type"]              // key with hyphen
$input["2024-results"]              // key starting with number
```

=== Safe Navigation

When a property might not exist, use `?.` to avoid null reference errors:

```utlx
$input.user.address?.city           // null if address is missing (not an error)
$input.user.phone?.mobile?.number   // null if any level is missing
```

Without `?.`, accessing a property on null throws an error. With `?.`, the expression short-circuits to null. Combine with `??` for defaults:

```utlx
$input.user.address?.city ?? "Unknown"
$input.user.phone?.mobile?.number ?? $input.user.phone?.landline ?? "N/A"
```

=== Number Precision

UTL-X's JSON parser preserves integer precision. Numbers without a decimal point or exponent are parsed as 64-bit integers (Long), not floating-point:

```utlx
// Input: {"id": 9007199254740993, "price": 29.99}

$input.id     // 9007199254740993 (Long — no precision loss)
$input.price  // 29.99 (Double)
```

This matters for large IDs, timestamps, and financial identifiers that exceed JavaScript's `Number.MAX_SAFE_INTEGER`. UTL-X gets this right where many JSON tools silently lose precision.

== Writing JSON

=== Object Construction

UTL-X object literals map directly to JSON objects:

```utlx
%utlx 1.0
input json
output json
---
{
  orderId: $input.order.id,
  customerName: $input.order.customer,
  lineCount: count($input.order.items),
  total: sum(map($input.order.items, (i) -> i.qty * i.price))
}
```

Output:

```json
{
  "orderId": "ORD-001",
  "customerName": "Acme Corp",
  "lineCount": 2,
  "total": 499.95
}
```

Property names are automatically quoted in JSON output. You write `orderId:` (no quotes needed in UTL-X), and the serializer produces `"orderId":`.

=== Array Construction

Use `map()` and `filter()` to build arrays:

```utlx
// Transform each item
map($input.order.items, (item) -> {
  sku: item.product,
  quantity: item.qty,
  lineTotal: item.qty * item.price
})
```

Or construct arrays directly with bracket syntax:

```utlx
[
  $input.order.id,
  $input.order.customer,
  toString($input.order.total)
]
```

=== Spread Operator

The spread operator `...` copies all properties from one object into another — essential for JSON-to-JSON transformations where you want to keep most fields and change a few:

```utlx
// Keep everything, override one field, add one field
{
  ...$input.order,
  status: "CONFIRMED",
  confirmedAt: now()
}
```

This is more maintainable than listing every field — if the source adds new fields, they flow through automatically.

=== Conditional Fields

Include or exclude fields based on conditions:

```utlx
{
  id: $input.id,
  name: $input.name,
  // Only include email if it exists
  ...if ($input.email != null) { email: $input.email } else {},
  // Only include address if country is provided
  ...if ($input.country != null) {
    address: {
      country: $input.country,
      city: $input.city ?? "Unknown"
    }
  } else {}
}
```

=== Pretty Printing vs Compact Output

By default, UTL-X outputs pretty-printed JSON with 2-space indentation. Use `--no-pretty` on the command line for compact output:

```bash
# Pretty (default)
echo '{"x":1}' | utlx transform.utlx
# {"x": 1}   ← with whitespace

# Compact
echo '{"x":1}' | utlx transform.utlx --no-pretty
# {"x":1}    ← no whitespace
```

Pretty printing is for human readability. Compact output saves bandwidth in production pipelines.

== JSON-Specific Functions

=== parseJson and renderJson

These functions convert between JSON strings and UDM values _within_ a transformation. Use them when JSON is embedded inside another format — a JSON string inside a CSV column, or a JSON payload inside an XML element.

```utlx
// Parse a JSON string into a navigable UDM value
let config = parseJson($input.configJson)
config.database.host    // "localhost"
config.database.port    // 5432

// Render a UDM value back to a JSON string
let payload = renderJson({
  event: "order.created",
  data: $input.order
})
// payload is a string: '{"event":"order.created","data":{...}}'

// renderJson with pretty printing
let readable = renderJson($input.order, true)
```

These are NOT needed for normal JSON-to-JSON transformations — UTL-X handles the parsing and serialization automatically via the `input json` / `output json` declarations. They're for the special case where JSON is a _value_ inside your data, not the format of your data.

=== JSON Canonicalization (RFC 8785)

UTL-X implements RFC 8785 (JSON Canonicalization Scheme / JCS) for deterministic JSON serialization. This is essential for digital signatures, content hashing, and change detection — anywhere two representations of the same data must produce identical bytes.

```utlx
// Canonicalize: sorted keys, no whitespace, deterministic number format
let canonical = canonicalizeJSON($input)
// or equivalently:
let canonical = jcs($input)

// Hash the canonical form (default SHA-256)
let hash = canonicalJSONHash($input)
// "a1b2c3d4..."

// Hash with a different algorithm
let md5 = canonicalJSONHash($input, "MD5")

// Compare two JSON values semantically (ignoring key order, whitespace)
jsonEquals(json1, json2)    // true if same content

// Check if a string is already in canonical form
isCanonicalJSON(someString)  // true/false

// Get byte size of canonical form (useful for Content-Length)
canonicalJSONSize($input)    // number of UTF-8 bytes
```

JCS rules:
- Object keys sorted by Unicode code point (lexicographic)
- No whitespace between tokens
- Numbers in ECMAScript format (no unnecessary decimals, no leading zeros)
- Strings with minimal escaping (only required characters)

=== parse and render (Generic)

The generic `parse()` and `render()` functions accept a format parameter:

```utlx
let data = parse(jsonString, "json")     // same as parseJson(jsonString)
let str = render(data, "json")           // same as renderJson(data)
let str = render(data, "json", true)     // pretty-printed
```

These are useful in generic transformations that handle multiple formats dynamically.

== Common JSON Patterns

=== REST API Response Transformation

Normalize different API response shapes into a consistent internal format:

```utlx
%utlx 1.0
input json
output json
---
// API returns {data: [...], meta: {total: N, page: P}}
// Normalize to {items: [...], pagination: {total: N, page: P, hasMore: bool}}

{
  items: map($input.data, (item) -> {
    id: item.id,
    name: item.attributes?.name ?? item.name,
    createdAt: item.created_at ?? item.createdAt
  }),
  pagination: {
    total: $input.meta.total,
    page: $input.meta.page,
    pageSize: count($input.data),
    hasMore: $input.meta.page * count($input.data) < $input.meta.total
  }
}
```

=== Flattening Nested API Responses

Many APIs nest data deeply. Flatten it for downstream consumers:

```utlx
%utlx 1.0
input json
output json
---
map($input.data, (user) -> {
  id: user.id,
  name: user.attributes.name,
  email: user.attributes.email,
  city: user.relationships.address?.data?.attributes?.city,
  companyName: user.relationships.company?.data?.attributes?.name
})
```

=== Key Renaming (snake\_case to camelCase)

A common integration task — the source API uses `snake_case`, the target expects `camelCase`:

```utlx
%utlx 1.0
input json
output json
---
{
  userId: $input.user_id,
  firstName: $input.first_name,
  lastName: $input.last_name,
  emailAddress: $input.email_address,
  phoneNumber: $input.phone_number,
  createdAt: $input.created_at,
  isActive: $input.is_active
}
```

For large payloads with many fields, the spread operator plus individual overrides is more maintainable — but UTL-X does not have a built-in `camelCase()` function for dynamic key renaming. Each field must be mapped explicitly.

=== Merging Multiple JSON Objects

Combine data from different sources:

```utlx
%utlx 1.0
input json
output json
---
// $input has "user" and "preferences" from different API calls
{
  ...$input.user,
  ...$input.preferences,
  fullName: concat($input.user.firstName, " ", $input.user.lastName),
  locale: $input.preferences.language ?? "en"
}
```

=== Filtering and Reshaping Arrays

```utlx
%utlx 1.0
input json
output json
---
// Keep only active products over $10, reshape for the catalog
$input.products
  |> filter((p) -> p.active && p.price > 10)
  |> sortBy((p) -> p.price)
  |> map((p) -> {
    sku: p.id,
    title: p.name,
    priceFormatted: concat("$", toString(p.price)),
    inStock: p.inventory > 0,
    categories: p.tags ?? []
  })
```

=== Building Lookup Maps

Transform an array into a keyed object for fast access:

```utlx
%utlx 1.0
input json
output json
---
// Convert [{id: "A", name: "Alice"}, {id: "B", name: "Bob"}]
// into {"A": {name: "Alice"}, "B": {name: "Bob"}}
reduce($input.users, {}, (acc, user) ->
  {...acc, [user.id]: {name: user.name, email: user.email}}
)
```

=== Aggregation

Compute summary statistics from JSON data:

```utlx
%utlx 1.0
input json
output json
---
let items = $input.order.items
{
  orderId: $input.order.id,
  lineCount: count(items),
  subtotal: sum(map(items, (i) -> i.qty * i.price)),
  avgPrice: sum(map(items, (i) -> i.price)) / count(items),
  cheapest: sortBy(items, (i) -> i.price) |> first(),
  categories: unique(map(items, (i) -> i.category))
}
```

== JSON to Other Formats

=== JSON to XML

JSON has no concept of attributes, so the conversion is straightforward — every property becomes a child element:

```utlx
%utlx 1.0
input json
output xml {root: "Order"}
---
{
  OrderId: $input.id,
  Customer: $input.customer,
  Items: map($input.items, (item) -> {
    Product: item.product,
    Quantity: item.qty,
    Price: item.price
  })
}
```

Output:

```xml
<Order>
  <OrderId>ORD-001</OrderId>
  <Customer>Acme Corp</Customer>
  <Items>
    <Product>Widget</Product>
    <Quantity>10</Quantity>
    <Price>25.0</Price>
  </Items>
  <Items>
    <Product>Gadget</Product>
    <Quantity>5</Quantity>
    <Price>49.99</Price>
  </Items>
</Order>
```

=== JSON to CSV

Flatten nested JSON to tabular CSV:

```utlx
%utlx 1.0
input json
output csv
---
map($input.order.items, (item) -> {
  orderId: $input.order.id,
  customer: $input.order.customer,
  product: item.product,
  qty: item.qty,
  price: item.price,
  lineTotal: item.qty * item.price
})
```

Output:

```csv
orderId,customer,product,qty,price,lineTotal
ORD-001,Acme Corp,Widget,10,25.0,250.0
ORD-001,Acme Corp,Gadget,5,49.99,249.95
```

The key insight: CSV is flat, so you must denormalize — repeat parent fields (orderId, customer) for each child row. For complex hierarchies, the `unnest()` function (Chapter 21) automates this.

=== JSON to YAML

JSON to YAML is nearly trivial — the structures are equivalent:

```utlx
%utlx 1.0
input json
output yaml
---
$input
```

A pass-through transformation produces valid YAML from any JSON input. Of course, you can reshape while converting:

```utlx
%utlx 1.0
input json
output yaml
---
{
  application: $input.app.name,
  version: $input.app.version,
  database: {
    host: $input.config.db_host,
    port: $input.config.db_port,
    name: $input.config.db_name
  }
}
```

== JSON Schema

UTL-X can read and write JSON Schema as a data format — treating schema definitions as transformable data. This is not runtime validation (checking that data conforms to a schema) but schema-as-data manipulation.

=== Reading JSON Schema

```utlx
%utlx 1.0
input jsch
output yaml %usdl 1.0
---
$input
```

This reads a JSON Schema file, parses it into USDL (Universal Schema Definition Language), and outputs it as human-readable YAML. Useful for understanding complex schemas or converting between schema formats.

Supported drafts: draft-04 (best effort), draft-07, and 2020-12.

=== Writing JSON Schema

```utlx
%utlx 1.0
input xsd
output jsch
---
$input
```

Converts an XSD schema to JSON Schema 2020-12 via the USDL intermediate representation. The USDL tier system (Chapter 12) determines which constraints survive the conversion.

=== Schema Functions

```utlx
// Parse a JSON Schema string into a navigable USDL structure
let schema = parseJSONSchema(jsonSchemaString)

// Render a USDL structure as a JSON Schema string
let schemaStr = renderJSONSchema(usdlSchema)
let schemaStr = renderJSONSchema(usdlSchema, true)  // pretty-printed
```

== When JSON Isn't Simple

Despite JSON's clean mapping to UDM, there are edge cases:

=== Dates Are Strings

JSON has no date type. Dates are always strings — `"2026-04-30"` or `"2026-04-30T14:30:00Z"`. Parse them explicitly:

```utlx
let orderDate = parseDate($input.createdAt, "yyyy-MM-dd'T'HH:mm:ss'Z'")
let formattedDate = formatDate(orderDate, "dd-MM-yyyy")
```

=== Numbers Can Be Ambiguous

Is `42` an integer or a float? In JSON, both `42` and `42.0` are "numbers." UTL-X preserves the distinction (Long vs Double), but downstream systems may not. Be explicit when it matters:

```utlx
{
  quantity: toInteger($input.qty),     // ensure integer
  price: toDecimal($input.price)       // ensure decimal
}
```

=== Null vs Missing vs Empty

JSON distinguishes between `null`, missing, and empty:

```json
{"a": null, "b": "", "c": []}
// "d" is missing entirely
```

```utlx
$input.a              // null
$input.b              // "" (empty string, NOT null)
$input.c              // [] (empty array, NOT null)
$input.d              // null (missing property returns null)

// To treat all "empty-ish" values the same:
let value = $input.a
let isEmpty = value == null || value == "" || (isArray(value) && count(value) == 0)
```

=== Deeply Nested Optional Structures

Real-world JSON APIs often have deeply nested optional fields. Chain `?.` for safety:

```utlx
// GraphQL-style deeply nested response
$input.data?.viewer?.repositories?.edges[0]?.node?.name ?? "unknown"
```

Without safe navigation, any `null` in the chain would crash the transformation. With `?.`, it gracefully returns `null`, and `??` provides the fallback.
