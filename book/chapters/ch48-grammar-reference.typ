= Grammar Reference

This chapter provides the formal grammar, operator precedence, reserved words, and syntax rules for UTL-X 1.0. Use it as a desk reference when writing transformations.

== File Structure

Every `.utlx` file has two sections separated by `---`:

```
%utlx 1.0              ← version directive
input <format>          ← input declaration
output <format>         ← output declaration
---                     ← separator
<expression>            ← transformation body (single expression)
```

The header is parsed as declarations. The body is parsed as a single expression that produces the output.

== Header Syntax

=== Version Directive

```
%utlx 1.0
```

Required. Must be the first non-empty line. Currently always `1.0`.

=== Input Declaration

```
input <format>                                    // single unnamed input
input <name> <format>                             // single named input
input <name> <format> {key: value, ...}           // with options
input: <name1> <format1>, <name2> <format2>       // multiple inputs (colon required)
input: <name1> <format1>,                         // multi-line (comma-separated)
       <name2> <format2> {key: value}
```

Name comes before format. The colon after `input:` signals multiple comma-separated inputs. Without a name, the default name is `input` (accessed as `$input`). Named inputs are accessed as `$name`.

=== Output Declaration

```
output <format>                                   // basic output
output <format> {key: value, ...}                 // with options
output <format> %<dialect> <version>              // with dialect annotation
```

The dialect annotation (`%name version`) is a generic mechanism. Currently no dialects have functional behavior — they are parsed and stored but do not change processing. Future dialects may include schema inference or compatibility modes.

=== Format Keywords

Data formats: `json`, `xml`, `csv`, `yaml`, `odata`, `auto`

Schema formats: `xsd`, `jsch`, `avro`, `proto`, `osch`, `tsch`

=== Format Options

Options use `{key: value}` syntax after the format keyword:

```
{delimiter: ";"}                    // string value
{headers: true}                     // boolean value
{decimals: 2}                       // number value
{regionalFormat: "european"}        // string value
{writeAttributes: true}             // boolean value
{encoding: "UTF-8"}                 // string value
{bom: true}                         // boolean value
```

== Expression Grammar (Precedence)

From lowest to highest precedence:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Level*], [*Name*], [*Operators*], [*Associativity*],
  [1], [Pipe], [`|>`], [Left],
  [2], [Conditional], [`if ... else ...`], [Right],
  [3], [Ternary], [`? :`], [Right],
  [4], [Nullish coalescing], [`??`], [Left],
  [5], [Logical OR], [`\|\|`], [Left],
  [6], [Logical AND], [`&&`], [Left],
  [7], [Equality], [`==` `!=`], [Left],
  [8], [Comparison], [`<` `<=` `>` `>=`], [Left],
  [9], [Addition], [`+` `-`], [Left],
  [10], [Multiplication], [`*` `/` `%`], [Left],
  [11], [Exponentiation], [`**`], [Right],
  [12], [Unary], [`!` `-` (prefix)], [Right],
  [13], [Postfix], [`.` `?.` `[]` `()`], [Left],
)

=== Grammar Rules (EBNF)

```
expression     → pipe
pipe           → conditional ("|>" conditional)*
conditional    → nullish ("if" nullish "else" conditional)?
nullish        → logical_or ("??" logical_or)*
logical_or     → logical_and ("||" logical_and)*
logical_and    → equality ("&&" equality)*
equality       → comparison (("==" | "!=") comparison)*
comparison     → term (("<" | "<=" | ">" | ">=") term)*
term           → factor (("+" | "-") factor)*
factor         → exponent (("*" | "/" | "%") exponent)*
exponent       → unary ("**" unary)*
unary          → ("!" | "-") unary | postfix
postfix        → primary ("." IDENTIFIER | "?." IDENTIFIER
                          | "[" expression "]"
                          | "(" arguments ")"
                          | "." "@" IDENTIFIER
                          | "." "*")*
primary        → NUMBER | STRING | BOOLEAN | NULL
               | IDENTIFIER
               | "$" IDENTIFIER
               | "(" expression ")"
               | "{" object_properties "}"
               | "[" array_elements "]"
               | lambda
               | let_binding
               | function_def
               | match_expression
               | try_catch
               | if_expression
```

== Literals

=== Numbers

```
42                  // integer
3.14                // decimal
1e10                // scientific notation
-7                  // negative (unary minus)
```

=== Strings

```
"hello world"       // double-quoted
'hello world'       // single-quoted
"line1\nline2"      // escape sequences: \n \t \\ \" \'
```

=== Booleans and Null

```
true                // boolean true
false               // boolean false
null                // null value
```

== Object Construction

Properties are separated by commas or newlines. Commas are required on the same line; newlines are sufficient across lines.

```
{
  key: value                     // identifier key (newline separates from next)
  "key-with-dashes": value       // string key (for special characters)
  [dynamicKey]: value            // computed key (expression in brackets)
  ...otherObject                 // spread operator
  ...if (cond) {k: v} else {}   // conditional spread
}
```

== Array Construction

```
[1, 2, 3]                       // literal array
[item1, item2]                   // array of expressions
```

== Let Bindings

`let` binds a value to a name. The binding is available from the point of declaration to the end of the enclosing scope.

=== Top-Level Let (Body Scope)

At the top of the transformation body, `let` creates a binding visible to everything that follows:

```utlx
let tax = 0.21
let threshold = 1000

map($input.orders, (order) -> {
  total: order.amount,
  vat: order.amount * tax,               // tax visible here
  priority: order.amount > threshold      // threshold visible here
})
```

Multiple `let` bindings are sequential — each one can reference the previous:

```utlx
let items = $input.order.items
let total = sum(map(items, (i) -> i.price * i.qty))
let average = total / count(items)

{itemCount: count(items), total: total, average: average}
```

=== Let Inside Object Construction

`let` inside an object creates a binding scoped to that object:

```utlx
{
  let customer = find($input.customers, (c) -> c.id == $input.order.customerId)

  orderId: $input.order.id,
  customerName: customer?.name ?? "Unknown",   // customer visible here
  country: customer?.country                    // and here
}
```

The `customer` binding is only available within this object — not outside it.

=== Let Inside Lambda

`let` inside a lambda is scoped to that lambda invocation:

```utlx
map($input.orders, (order) -> {
  let lineTotal = sum(map(order.lines, (l) -> l.qty * l.price))
  let discount = if (lineTotal > 500) 0.1 else 0

  orderId: order.id,
  subtotal: lineTotal,
  discount: discount,
  total: lineTotal * (1 - discount)
})
```

Each iteration of `map` gets its own `lineTotal` and `discount` — they don't leak between iterations.

=== Let Inside Function Definitions

`let` inside a function body is scoped to that function call:

```utlx
function CalculateShipping(weight, country) {
  let baseRate = if (country == "NL") 5.95 else 12.50
  let weightSurcharge = if (weight > 20) (weight - 20) * 0.50 else 0
  baseRate + weightSurcharge
}
```

=== Scoping Summary

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Where*], [*Visible to*], [*Example*],
  [Top of body], [Everything below in the body], [`let tax = 0.21`],
  [Inside object `{}`], [Properties in that object], [`{let x = ...` ↵ `a: x}`],
  [Inside lambda], [That lambda invocation only], [`(item) -> {let x = ...` ↵ `...}`],
  [Inside function body], [That function call only], [`function F(n) {let x = ...` ↵ `...}`],
  [Inside `if/else`], [That branch only], [`if (...) {let x = ...` ↵ `x} else ...`],
)

=== Separators: Commas and Newlines

UTL-X uses newline-sensitive parsing for separators. The rule is simple:

- *Same line:* commas are required between properties, let bindings, or array elements
- *Different lines:* newlines are sufficient — commas are optional

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Context*], [*Same line*], [*Different lines*],
  [Object properties], [`{a: 1, b: 2}`], [`{` ↵ `a: 1` ↵ `b: 2` ↵ `}`],
  [Let bindings in object], [`{let x = 1, y: x}`], [`{` ↵ `let x = 1` ↵ `y: x` ↵ `}`],
  [Let before array return], [N/A], [`let x = 1` ↵ `[x, x + 1]`],
  [Array elements], [`[1, 2, 3]`], [Commas required (arrays stay comma-separated)],
  [Function arguments], [`map(arr, fn)`], [Commas required],
)

The `[` bracket on a new line is treated as an *array literal*, not an index operator. This resolves the ambiguity between `let x = 20[arr]` (index) and `let x = 20` followed by `[arr]` (array):

```utlx
let x = arr[0]    // same line: index access
let x = arr
[0, 1, 2]         // new line: array literal (not index)
```

This parsing model follows Kotlin and Go — newlines act as implicit separators where syntactically unambiguous.

```utlx
// Multi-line object — no commas needed
{
  let customer = find($input.customers, (c) -> c.id == $input.customerId)
  orderId: $input.id
  customerName: customer?.name
}

// Lambda body — no commas needed
map($input.items, (item) -> {
  let total = item.qty * item.price
  name: item.name
  total: total
})

// Single-line — commas required
{orderId: $input.id, total: $input.amount}
```

Commas are always accepted — existing code with commas continues to work unchanged.

== Function Definitions

```
function Name(param1, param2) {
  expression
}

function Name(param: Type): ReturnType {
  expression
}

def Name(param1, param2) = expression    // shorthand alias
```

Function names MUST start with an uppercase letter (PascalCase). See Chapter 15.

== Lambda Expressions

UTL-X supports two arrow syntaxes for lambdas: `->` (thin arrow) and `=>` (fat arrow). Both are equivalent — use whichever you prefer.

=== Thin Arrow (`->`)

```
(x) -> x * 2                            // single parameter
(x, y) -> x + y                         // multiple parameters
(item) -> { name: item.name, age: item.age }  // object body
() -> "constant"                         // no parameters
```

Parentheses around parameters are always required with `->`.

=== Fat Arrow (`=>`)

```
(x) => x * 2                            // with parentheses — same as ->
x => x * 2                              // WITHOUT parentheses — single param only
(x, y) => x + y                         // multiple params — parentheses required
item => item.name                        // short form for single parameter
```

The fat arrow `=>` allows omitting parentheses for single-parameter lambdas. This mirrors JavaScript's arrow function syntax: `x => x * 2` instead of `(x) -> x * 2`. For multiple parameters, parentheses are required with both styles.

=== Which to Use?

Both are identical in behavior. Convention:
- `->` is the standard style used throughout this book and the conformance suite
- `=>` is accepted for developers coming from JavaScript/TypeScript who prefer it
- `x => x.name` (no parens) is the shortest form for simple single-parameter lambdas

=== In Match Expressions

The `=>` arrow is also used in match case arms:

```
match ($input.status) {
  "ACTIVE" => "green",
  "PENDING" => "yellow",
  _ => "gray"
}
```

== Conditional Expressions

```
if (condition) thenExpr else elseExpr

if (x > 0) "positive"
else if (x < 0) "negative"
else "zero"
```

`if/else` is an expression — it returns a value. The `else` branch is required.

== Match Expression

```
match (value) {
  pattern1 -> result1,
  pattern2 -> result2,
  _ -> defaultResult
}
```

== Try/Catch

```
try { riskyExpression } catch { fallbackValue }
```

If the expression in `try` throws an error, `catch` returns the fallback value.

== Safe Navigation

```
$input.order?.customer?.name      // null if any step is null
$input.items?[0]?.price           // safe index + safe property
```

`?.` short-circuits to `null` when the left side is null. Combine with `??` for defaults:

```
$input.name ?? "Unknown"
$input.order?.customer?.name ?? "No customer"
```

== Property Access

```
$input.name                      // dot notation
$input["content-type"]           // bracket notation (string key)
$input[variable]                 // bracket notation (dynamic key)
$input.*                         // wildcard (all children as array)
$input.@id                       // XML attribute
$input.@*                        // all attributes as array
$input.^metadata                 // metadata access
```

== Pipe Operator

```
$input
  |> filter((x) -> x.active)
  |> map((x) -> x.name)
  |> sortBy((x) -> x)
```

The pipe operator passes the left-hand result as the first argument to the right-hand function.

== Spread Operator

```
{...object}                      // copy all properties
{...obj1, ...obj2}               // merge (later wins)
{...obj, key: override}          // copy + override
[...array1, ...array2]           // concatenate arrays
```

== Reserved Words

=== Language Keywords

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Keyword*], [*Purpose*],
  [`if` / `else`], [Conditional expressions],
  [`let`], [Variable binding],
  [`function`], [User-defined function declaration],
  [`def`], [Alias for `function`],
  [`match`], [Pattern matching],
  [`try` / `catch`], [Error handling],
  [`in`], [Used in `let...in` expressions],
  [`import` / `as`], [Module import (reserved, not yet implemented)],
  [`template`], [Template definition],
  [`apply`], [Template application],
  [`true` / `false`], [Boolean literals],
  [`null`], [Null literal],
  [`input` / `output`], [Header declarations],
  [`auto`], [Auto-detect format],
)

=== Format Keywords

`json`, `xml`, `csv`, `yaml`, `odata`, `xsd`, `jsch`, `avro`, `proto`, `osch`, `tsch`

These are reserved in the header but can be used as identifiers in the body (though not recommended for clarity).

=== NOT Reserved

`map`, `filter`, `reduce` — these are token types for parser optimization but usable as identifiers. The 652 stdlib function names (lowercase) are not reserved — they're resolved by the interpreter at runtime. User-defined functions (PascalCase) never collide with stdlib (camelCase).

== Operators Reference

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Operator*], [*Name*], [*Example*],
  [`+`], [Addition / string concatenation], [`1 + 2`, `"a" + "b"`],
  [`-`], [Subtraction / unary negation], [`5 - 3`, `-x`],
  [`*`], [Multiplication], [`3 * 4`],
  [`/`], [Division], [`10 / 3`],
  [`%`], [Modulo], [`10 % 3` → `1`],
  [`**`], [Exponentiation], [`2 ** 10` → `1024`],
  [`==`], [Equality], [`x == 42`],
  [`!=`], [Inequality], [`x != null`],
  [`<` `<=` `>` `>=`], [Comparison], [`x > 0`],
  [`&&`], [Logical AND], [`a && b`],
  [`\|\|`], [Logical OR], [`a \|\| b`],
  [`!`], [Logical NOT], [`!active`],
  [`??`], [Nullish coalescing], [`x ?? "default"`],
  [`?.`], [Safe navigation], [`obj?.prop`],
  [`\|>`], [Pipe], [`arr \|> filter(...)`],
  [`...`], [Spread], [`{...obj}`],
  [`.`], [Property access], [`obj.name`],
  [`@`], [Attribute access], [`elem.@id`],
  [`^`], [Metadata access], [`elem.^source`],
  [`*` (postfix)], [Wildcard], [`obj.*`],
  [`->`], [Lambda arrow (standard)], [`(x) -> x * 2`],
  [`=>`], [Lambda arrow (fat arrow)], [`(x) => x * 2` or `x => x * 2`],
  [`..`], [Recursive descent (reserved)], [Not yet implemented],
)

== Identifier Rules

- *Start character:* letter (Unicode) or underscore
- *Continue characters:* letter, digit, underscore
- *Case-sensitive:* `name` and `Name` are different identifiers
- *Hyphenated names:* supported in header (input names) via bracket notation in body
- *`$` prefix:* used for input variables (`$input`, `$orders`), not part of the identifier itself
- *PascalCase requirement:* user-defined functions must start with uppercase (`function MyFunc`)
- *camelCase convention:* stdlib functions use camelCase (`map`, `filter`, `parseDate`)

== USDL Grammar (Universal Schema Definition Language)

USDL is a convention within UTL-X — not a separate language (see Chapter 12). USDL directives use the `%` prefix and represent schema information in a format-agnostic way. They are automatically added when parsing schema formats (Tier 2 input) and automatically detected by schema serializers (Tier 2 output).

=== USDL Document Structure

```
usdl_document → "{" usdl_directives "}"

usdl_directives → ("%namespace" ":" STRING)?
                   ("%version" ":" STRING)?
                   ("%types" ":" "{" type_definitions "}")?
                   ("%entityContainer" ":" "{" entity_container "}")?
```

=== Type Definitions

```
type_definitions → (TYPE_NAME ":" type_definition)*

type_definition → "{"
  ("%kind" ":" ("object" | "enum" | "union"))?
  ("%description" ":" STRING)?
  ("%fields" ":" "{" field_definitions "}")?
  ("%values" ":" "[" STRING ("," STRING)* "]")?    // enums only
  ("%key" ":" "[" STRING ("," STRING)* "]")?        // entity key
"}"
```

=== Field Definitions

```
field_definitions → (FIELD_NAME ":" field_definition)*

field_definition → "{"
  "%type" ":" TYPE_STRING
  ("%description" ":" STRING)?
  ("%required" ":" BOOLEAN)?
  ("%nullable" ":" BOOLEAN)?
  ("%array" ":" BOOLEAN)?
  ("%default" ":" VALUE)?
  ("%example" ":" VALUE)?
  // Shared: Common constraints
  ("%minLength" ":" NUMBER)?
  ("%maxLength" ":" NUMBER)?
  ("%pattern" ":" STRING)?
  ("%minimum" ":" NUMBER)?
  ("%maximum" ":" NUMBER)?
  ("%enum" ":" "[" VALUE ("," VALUE)* "]")?
  ("%format" ":" STRING)?
  // Format-native: Format-specific
  ("%fieldNumber" ":" NUMBER)?        // Protobuf
  ("%logicalType" ":" STRING)?        // Avro
  ("%precision" ":" NUMBER)?          // Avro decimal
  ("%scale" ":" NUMBER)?              // Avro decimal
  ("%primaryKey" ":" BOOLEAN)?        // Database
  ("%foreignKey" ":" STRING)?         // Database
  ("%navigation" ":" BOOLEAN)?        // OData
  ("%target" ":" STRING)?             // OData
  ("%cardinality" ":" STRING)?        // OData
"}"
```

=== USDL Directive Classification

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Level*], [*Scope*], [*Directives*], [*Applies to*],
  [Core], [Portable], [`%namespace`, `%version`, `%types`, `%kind`, `%name`, `%type`, `%description`, `%fields`, `%values`], [All formats],
  [Shared], [Widespread], [`%required`, `%nullable`, `%array`, `%default`, `%minLength`, `%maxLength`, `%pattern`, `%minimum`, `%maximum`, `%enum`, `%format`, `%example`], [Most formats],
  [Format-native], [Exclusive], [`%fieldNumber` (Proto), `%logicalType` (Avro), `%primaryKey` (DB), `%navigation` (OData), `%choice` (XSD), etc.], [One format],
  [Reserved], [Future], [Future USDL versions], [Planned],
)

=== USDL Type Strings

The `%type` directive accepts these type names:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*USDL type*], [*Maps to*],
  [`string`], [String in all formats],
  [`integer`], [int/Int32/long — whole numbers],
  [`number`], [double/float/decimal — fractional numbers],
  [`boolean`], [true/false],
  [`date`], [Date without time],
  [`time`], [Time without date],
  [`datetime`], [Date and time (ISO 8601)],
  [`binary`], [Byte array / Base64],
  [`object`], [Nested type (reference to another type name)],
  [`any`], [Untyped / dynamic],
)

For type references, use the type name directly: `%type: "CustomerType"` references the type defined in `%types`.

=== Example: Complete USDL Document

```json
{
  "%namespace": "com.example.orders",
  "%version": "1.0.0",
  "%types": {
    "Order": {
      "%kind": "object",
      "%description": "A customer order",
      "%fields": {
        "orderId": {
          "%type": "string",
          "%required": true,
          "%pattern": "^ORD-[0-9]{5}$",
          "%description": "Unique order identifier"
        },
        "customer": {
          "%type": "Customer",
          "%required": true
        },
        "total": {
          "%type": "number",
          "%minimum": 0,
          "%description": "Order total in EUR"
        },
        "lines": {
          "%type": "OrderLine",
          "%array": true,
          "%description": "Order line items"
        }
      }
    },
    "Customer": {
      "%kind": "object",
      "%fields": {
        "name": { "%type": "string", "%required": true, "%maxLength": 100 },
        "email": { "%type": "string", "%format": "email" }
      }
    },
    "OrderLine": {
      "%kind": "object",
      "%fields": {
        "product": { "%type": "string", "%required": true },
        "quantity": { "%type": "integer", "%minimum": 1 },
        "unitPrice": { "%type": "number", "%minimum": 0 }
      }
    },
    "OrderStatus": {
      "%kind": "enum",
      "%values": ["DRAFT", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"]
    }
  }
}
```

This USDL document can be converted to JSON Schema, XSD, Avro, Protobuf, or EDMX — the USDL classification system determines what survives in each target format (Chapter 12).

#block(
  fill: rgb("#FFF3E0"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Note (F08):* USDL enrichment is implemented. All 6 Tier 2 parsers produce enriched UDM with both raw structure and USDL `%` properties. Tier 2 → Tier 2 schema conversions (e.g., `input xsd` / `output jsch`) work via USDL passthrough. The stdlib functions (`parseXSDSchema`, `parseJSONSchema`, etc.) also work independently.
]
