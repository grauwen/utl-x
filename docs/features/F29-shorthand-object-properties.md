# F29: shorthand object properties (`{ name, age }`)

**Status:** Implemented (2026-11-15)
**Priority:** Low-Medium — pure syntactic sugar, but it is documented on **both** branches with no
warning, it is unambiguous, and it costs one parser branch with no runtime change.
**Component:** `:core` — `parser_impl.kt`, `parseObjectLiteral()` only. **No engine change:** see
"Why this is cheap" below.
**Raised:** 2026-11-14, from `docs/language-guide/syntax.md` documenting it as working syntax —
which it now is on `development`.

## The gap

`docs/language-guide/syntax.md` (line 290 on `development`, line 224 on `main`) publishes this
under "### Shorthand Properties", with no caveat on either branch:

```utlx
let name = "Alice"
let age = 30

{
  name,      // Shorthand for name: name
  age        // Shorthand for age: age
}
```

It does not parse:

```
{ name, age }   =>  Error: Parse errors:
                      Expected ':' after property name at Location(line=8, column=7)
{ name }        =>  same
```

The explicit form works and is the only way today:

```utlx
{ name: name, age: age }     // => {"name":"Alice","age":30}
```

Everything else documented in that same section of `syntax.md` is genuine, so this is an isolated
gap rather than an aspirational section:

| Documented in the same section | Works |
|---|---|
| Computed key `[dynamicKey]: "value"` | yes — `{"k":"value"}` |
| Computed key `["prefix_" + suffix]: …` | yes — `{"prefix_a":"computed"}` |
| Spread `{ ...base, z: 3 }` | yes — `{"x":1,"y":2,"z":3}` |
| Quoted key `{ "my-key": 1 }` | yes — `{"my-key":1}` |
| Shorthand `{ name, age }` | **no** |

## Why this is cheap, and why it carries no semantic risk

`{ name }` desugars to `{ name: name }` **at parse time** — the AST it should build is exactly the
one the explicit form already builds:

```
Property(key = "name", value = Expression.Identifier("name"))
```

So nothing downstream changes: the interpreter, `ASTCompiler` and `RuntimeOps` all see a node they
already handle. That makes this unlike F28 (negative indices), which needs matching runtime
semantics in `:core` **and** `:modules:engine` — the two-places problem behind B34, B35, EB03 and
B48. Here there is one place.

The change sits at `parser_impl.kt:1231`, which currently always demands the colon:

```kotlin
consume(TokenType.COLON, "Expected ':' after property name")
val value = parseExpression()
properties.add(Property(key, value, Location.from(previous()), isAttribute))
```

Shorthand means: if the next token is **not** `:` and the property position is complete — the next
token is `,`, `}`, or a newline, per the F02 separator rule on the `do/while` just below — build
`Property(key, Expression.Identifier(key), …)` instead of erroring.

## What was implemented

Two small changes in `parseObjectLiteral()`. A flag records that the key came from the *plain
identifier* branch, and the colon became conditional:

```kotlin
val isShorthand = keyIsPlainIdentifier && !isAttribute && !check(TokenType.COLON) &&
    (check(TokenType.COMMA) || check(TokenType.RBRACE) || hasNewlineBefore())

val value = if (isShorthand) {
    Expression.Identifier(key, Location.from(previous()))   // `{ name }` == `{ name: name }`
} else {
    consume(TokenType.COLON, "Expected ':' after property name")
    parseExpression()
}
```

Requiring the property position to be *complete* (`,`, `}` or a newline — F02's separators) is what
keeps the existing error for a genuine mistake: `{ name 5 }` still reports
`Expected ':' after property name` rather than silently taking `name` as shorthand and then
failing somewhere stranger.

All the exclusions from the section above were implemented and are asserted:

| form | result |
|---|---|
| `{ name }`, `{ name, age }` | shorthand |
| `{ input }`, `{ match }` (keywords, B56) | **still `Expected ':'`** |
| `{ @id }` (attribute) | **still `Expected ':'`** |
| `{ "name" }` (quoted) | **still `Expected ':'`** |
| `{ name 5 }` | **still `Expected ':'`** |
| `{ input: 1 }`, `{ match: 2 }` | unchanged — B56 intact |
| `{ nope }` where `nope` is unbound | `Undefined variable: nope`, exactly as `{ nope: nope }` |

## Tests

`F29ShorthandPropertyTest` (14 tests, `:modules:core`). One of them is the load-bearing test:
**`shorthand produces exactly the AST the explicit form produces`** parses both forms and compares
the resulting `Property`, since "no engine change is needed" is a claim about the tree, not about
the output. If the desugaring ever stops matching, that test fails rather than some distant
strategy.

`conformance-suite/utlx/tests/language/let-bindings/f29_shorthand_properties.yaml` covers shorthand
alone, mixed with explicit properties, nested, with F02 newline separators and an F30 trailing
comma. Running identically under TEMPLATE, COPY and COMPILED via `ConformanceStrategyParityTest` is
the second piece of evidence that the change is parse-level only.

**Revert-verified:** removing the conditional fails seven of the fourteen — every positive case —
while the five exclusion tests and the two unchanged-behaviour tests pass either way, which is
correct: they assert what this feature must *not* change.

Full regression: **4915 JVM tests, 0 failures**; **581/581 conformance on the JVM and on the
GraalVM native binary**.

## Still to do

`docs/language-guide/syntax.md` publishes the example on both branches. It is now accurate on
`development`; `main` still documents a form its code rejects until this ships there.

## Related

- **F28** — negative array indices, the other documented-but-absent selector form.
- **Bracket predicates** (`$input.items[price > 100]`) — also documented in the guide and also
  absent, but **declined by design** rather than pending: they would need `[...]` to open an
  implicit scope where a bare name means a field of the current element, which collides with the
  enclosing scope. Shorthand has no such ambiguity, which is why it is proposable and they are
  not. See the note in `docs/language-guide/selectors.md`.
- **B56** — reserved words as property names, which bounds what shorthand may accept.
- **F02** — the newline/comma separator rule that shorthand must respect.
