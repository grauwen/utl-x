= Appendix C: UTL-X Quick Reference

A syntax cheat sheet for the most common transformation patterns. For the complete language reference, see the companion book _UTL-X: One Language, All Formats_.

== File Structure

```
%utlx 1.0
input json
output json
---
{ transformation body }
```

== Format Headers

#table(
  columns: (auto, 1fr),
  [`input json`], [JSON input],
  [`input xml`], [XML input],
  [`input csv {header: true}`], [CSV with header row],
  [`input yaml`], [YAML input],
  [`output xml`], [XML output],
  [`input json {schema: "f.xsd"}`], [With schema validation],
)

== Property Access

#table(
  columns: (auto, 1fr),
  [`$input.name`], [Dot notation],
  [`$input.items[0]`], [Array index (0-based)],
  [`$input.@id`], [XML attribute],
  [`$input..name`], [Recursive descent],
)

== Object Construction

```
{key: value, other: value}
{name: $input.n, ...$input}     // spread operator
```

== Let Bindings

```
let x = expression
```

Multiple bindings separated by newlines or commas.

== Conditionals

```
if (condition) valueA else valueB

match value {
  "A" -> resultA,
  "B" -> resultB,
  _ -> defaultResult
}
```

== Array Operations

#table(
  columns: (auto, 1fr),
  [`map(arr, (x) -> expr)`], [Transform each element],
  [`filter(arr, (x) -> bool)`], [Keep matching elements],
  [`reduce(arr, init, (acc, x) -> expr)`], [Aggregate to single value],
  [`find(arr, (x) -> bool)`], [First matching element],
  [`sortBy(arr, (x) -> key)`], [Sort by key function],
  [`groupBy(arr, (x) -> key)`], [Group by key function],
  [`flatMap(arr, (x) -> arr)`], [Map and flatten],
  [`size(arr)`], [Array length],
  [`first(arr)` / `last(arr)`], [First or last element],
)

== Pipe Operator

```
$input.items
  | filter(_, (x) -> x.active)
  | map(_, (x) -> x.name)
  | sortBy(_, (x) -> x)
```

The underscore `_` is a placeholder for the piped value.

== String Functions

#table(
  columns: (auto, 1fr),
  [`concat(a, b, c)`], [Concatenate],
  [`upperCase(s)` / `lowerCase(s)`], [Case conversion],
  [`trim(s)`], [Remove whitespace],
  [`replace(s, old, new)`], [Replace substring],
  [`contains(s, sub)`], [Check substring],
  [`substring(s, start, end)`], [Extract substring],
  [`length(s)`], [String length],
  [`split(s, delimiter)`], [Split to array],
  [`startsWith(s, prefix)` / `endsWith(s, suffix)`], [Check prefix or suffix],
)

== Math Functions

#table(
  columns: (auto, 1fr),
  [`round(n, decimals)`], [Round to decimal places],
  [`floor(n)` / `ceil(n)`], [Floor or ceiling],
  [`abs(n)`], [Absolute value],
  [`min(a, b)` / `max(a, b)`], [Minimum or maximum],
)

== Type Functions

#table(
  columns: (auto, 1fr),
  [`toString(v)` / `toNumber(v)` / `toBoolean(v)`], [Type conversion],
  [`isNull(v)`], [Null check],
  [`typeOf(v)`], [Type name as string],
)

== Date Functions

#table(
  columns: (auto, 1fr),
  [`now()`], [Current timestamp],
  [`formatDate(d, pattern)`], [Format a date (e.g., `"yyyy-MM-dd"`)],
  [`parseDate(s, pattern)`], [Parse a date string],
)

== Security Functions

#table(
  columns: (auto, 1fr),
  [`sha256(s)` / `sha512(s)`], [Cryptographic hash],
  [`hmacSHA256(s, key)`], [HMAC signature],
  [`encryptAES(data, key)` / `decryptAES(data, key)`], [AES encryption],
  [`base64Encode(s)` / `base64Decode(s)`], [Base64 encoding],
  [`mask(s, start, end, char)`], [Mask sensitive data],
  [`env("VAR_NAME")`], [Read environment variable (engine only)],
)

== User-Defined Functions

```
function name(param1, param2) {
  expression
}
```

Defined before the main transformation body. Can call standard library functions and other user-defined functions.
