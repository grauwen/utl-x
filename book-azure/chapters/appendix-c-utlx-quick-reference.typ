= Appendix C: UTL-X Quick Reference

_Syntax cheat sheet for the most common transformation patterns. For the complete language reference, see "UTL-X: One Language, All Formats."_

== File Structure

// %utlx 1.0
// input json
// output json
// ---
// { transformation body }

== Format Headers

// input json                      JSON input
// input xml                       XML input
// input csv {header: true}        CSV with header row
// input yaml                      YAML input
// output xml                      XML output
// input json {schema: "f.xsd"}   With schema validation

== Property Access

// $input.name                     Dot notation
// $input.items[0]                 Array index (0-based)
// $input.@id                      XML attribute
// $input..name                    Recursive descent

== Object Construction

// {key: value, other: value}
// {name: $input.n, ...$input}    Spread operator

== Let Bindings

// let x = expression
// Multiple: let a = 1, let b = 2 (or newlines)

== Conditionals

// if (cond) a else b
// match value { "A" -> x, "B" -> y, _ -> z }

== Array Operations

// map(arr, (x) -> expr)
// filter(arr, (x) -> bool)
// reduce(arr, init, (acc, x) -> expr)
// find(arr, (x) -> bool)
// sortBy(arr, (x) -> key)
// groupBy(arr, (x) -> key)
// flatMap(arr, (x) -> arr)

== String Functions

// concat(a, b, c)               Concatenate
// upperCase(s) / lowerCase(s)   Case conversion
// trim(s)                        Remove whitespace
// replace(s, old, new)          Replace substring
// contains(s, sub)              Check substring
// substring(s, start, end)      Extract substring
// length(s)                      String length
// startsWith(s, prefix)         Check prefix
// split(s, delimiter)           Split to array

== Math Functions

// round(n, decimals)            Round
// floor(n) / ceil(n)            Floor / ceiling
// abs(n)                         Absolute value
// min(a, b) / max(a, b)        Minimum / maximum

== Type Functions

// toString(v) / toNumber(v)     Convert types
// isNull(v)                      Null check
// typeOf(v)                      Type name

== Date Functions

// now()                          Current timestamp
// formatDate(d, pattern)        Format date
// parseDate(s, pattern)         Parse date string

== Security Functions

// sha256(s) / sha512(s)        Hash
// hmacSHA256(s, key)            HMAC
// encryptAES(data, key)         Encrypt
// decryptAES(data, key)         Decrypt
// base64Encode(s)               Base64
// mask(s, start, end, char)     Mask sensitive data

== User-Defined Functions

// function name(param1, param2) {
//   expression
// }

== Pipe Operator

// $input.items | filter(_, (x) -> x.active) | map(_, (x) -> x.name)
