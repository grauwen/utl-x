# F29: shorthand object properties (`{ name, age }`)

**Status:** Proposed
**Priority:** Low-Medium — pure syntactic sugar, but it is documented on **both** branches with no
warning, it is unambiguous, and it costs one parser branch with no runtime change.
**Component:** `:core` — `parser_impl.kt`, `parseObjectLiteral()` only. **No engine change:** see
"Why this is cheap" below.
**Raised:** 2026-11-14, from `docs/language-guide/syntax.md` documenting it as working syntax.

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

## Decisions to settle

**Identifiers only, not keywords.** B56 allows a reserved word as a property *name*
(`{ template: v }`, `{ input: v }`). Shorthand should **not** extend to those: `{ input }` would
desugar to `input: input`, and bare `input` still resolves to the whole input document, so
`{ input }` would silently emit the entire document under the key `input`. Require an explicit
colon for keyword keys, and keep today's error message for them.

**Not for attributes or directives.** `{ @id }` and `{ %kind }` should keep requiring a colon —
`@id: id` reads as a coincidence rather than an intention, and `%kind` is a USDL directive whose
value is never a same-named variable.

**Not for quoted keys.** `{ "name" }` has no implied variable to read; leave it an error.

**Undefined name behaviour.** `{ nope }` where `nope` is unbound should fail exactly as
`{ nope: nope }` does today (`Undefined variable: nope`), not silently produce null — no new
leniency.

**Mixed and multi-line forms must work:**

```utlx
{ name, age: age + 1, email }           // mixed with explicit
{                                        // F02 newline separators
  name
  age
}
```

## Tests

- `F29ShorthandPropertyTest` in `:modules:core`: single shorthand, several shorthand, mixed with
  explicit properties, newline-separated (F02), an unbound name, a keyword key (must still error),
  `@attr` and quoted keys (must still error), and shorthand alongside a spread.
- A conformance fixture under `language/object-literals/` — this gets all three execution
  strategies through `ConformanceStrategyParityTest`, which for a pure desugaring should pass
  without any engine work. That is also the check that the desugaring really is semantics-free: if
  parity fails, the AST produced is not the one the explicit form produces.
- Replace the `syntax.md` example with a runnable one, on **both** branches.

## If this is declined

Mark the section `NOT SUPPORTED` in `syntax.md` on both branches and show `{ name: name }`
instead. What must not persist is the current state: a documented construct, on two branches,
with no warning, that fails at the first property.

## Related

- **F28** — negative array indices, the other documented-but-absent selector form.
- **Bracket predicates** (`$input.items[price > 100]`) — also documented in the guide and also
  absent, but **declined by design** rather than pending: they would need `[...]` to open an
  implicit scope where a bare name means a field of the current element, which collides with the
  enclosing scope. Shorthand has no such ambiguity, which is why it is proposable and they are
  not. See the note in `docs/language-guide/selectors.md`.
- **B56** — reserved words as property names, which bounds what shorthand may accept.
- **F02** — the newline/comma separator rule that shorthand must respect.
