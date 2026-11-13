# B56 — a reserved word can be written as a property but not read as one

**Status:** Fixed — 2026-11-13
**Component:** `:core` — `parser/parser_impl.kt`, `parsePostfix`
**Found:** 2026-11-13, checking the claim that `$input.input` works when the document has an
`input` element
**Severity:** Medium — a document containing an element named `input`, `output`, `return`,
`case`, `template`, `match`, `module`, `function`, `default`, `when`, `in` or `as` cannot be
read with dotted path syntax at all

## The asymmetry

Writing one is fine. Reading the same name back is a parse error:

```bash
# { "input": { "value": 7 } }
utlx -e '{ input: 1 }'        # {"input":1}            ✓ produces the element
utlx -e '$input.input'        # Parse errors: Expected property name
utlx -e '$input.input.value'  # Parse errors: Expected property name
utlx -e '$input?.input'       # Parse errors: Expected property name after '?.'
```

Every reserved word behaves the same way in read position — `$input.if`, `$input.output`,
`$input.match`, `$input.template`, `$input.let`, `$input.apply` are all parse errors, while
`{ output: 1, template: 2 }` and `{ return: 5 }` all emit correctly.

XML is the same:

```bash
# <doc><input><value>7</value></input></doc>
$input.doc.input.value        # Parse errors: Expected property name
$input.doc["input"].value     # 7
```

## Why it matters more than the word "reserved" suggests

The list is ~40 words, and several are ordinary element names in real documents:

| word | where it shows up |
|---|---|
| `input`, `output` | workflow and pipeline definitions; `<input>` is an HTML element, so any XHTML-shaped XML has it |
| `return` | financial and tax XML (`<return>`), rate-of-return data |
| `case` | legal, healthcare and support records |
| `template`, `module`, `function` | build and deployment configuration |
| `default`, `when`, `in`, `as` | configuration and scheduling formats |

This is a format-agnostic transformation language whose whole premise is reading documents it
did not define. A name the *source system* chose cannot be a reserved word of ours.

## Root cause, and the precedent for the fix

`parsePostfix` requires an `IDENTIFIER` token:

```kotlin
match(TokenType.DOT) -> {
    …
    val property = if (match(TokenType.STAR)) "*"
                   else consume(TokenType.IDENTIFIER, "Expected property name").lexeme
```

A reserved word lexes as its own token type (`TokenType.INPUT`, `TokenType.IF`, …), so it
never matches. The `?.` branch above it has the same line.

**The object-literal parser already solves this**, in the same file, with a helper that
already exists:

```kotlin
} else if (peek().isKeyword()) {
    // Allow keywords as property names in object literals
    // e.g., { template: value, match: value, input: value }
    key = advance().lexeme
```

So the fix is to do there what is already done here: accept `peek().isKeyword()` in property
position and take its lexeme.

**It is unambiguous.** After `.` or `?.` the grammar expects exactly one thing — a property
name. The other continuations are distinct tokens handled before it (`*` wildcard, `@`
attribute, `^` metadata, `..` recursive descent). A keyword in that position has no competing
meaning, which is why the object-literal branch can do it safely and why JavaScript made the
same change in ES5 (`obj.if` is legal there).

**It is strictly additive.** Every program that parses today parses identically; only
programs that are currently rejected become accepted. Nothing changes meaning.

## The fix

One helper, used by both path-step sites, scoped to exactly what the object literal already
accepts:

```kotlin
private fun consumePropertyName(message: String): String =
    if (peek().isKeyword()) advance().lexeme
    else consume(TokenType.IDENTIFIER, message).lexeme
```

`Token.isKeyword()` already existed — the object-literal branch has used it all along. The
`.` and `?.` branches of `parsePostfix` now call the helper instead of `consume(IDENTIFIER…)`.

**The literals are deliberately out of scope.** `true`, `false` and `null` are rejected in
property position *and* as object-literal keys (`{ true: 1 }` is also a parse error), so there
is no asymmetry there to correct. Widening to them would be a separate decision, and this is
recorded by a test rather than left implicit.

## Verification

| | |
|---|---|
| `:modules:core:test` | 349, 0 failures (incl. `B56KeywordPropertyNameTest`, 7) |
| `:modules:cli:test` | 148, 0 failures |
| `:stdlib:test` | 2328, 0 failures |
| `:modules:engine:test` | **1014**, 0 failures |
| conformance (JVM) | **575**/575 |
| conformance (**native**) | 575/575 |

All 20 affected keywords now read, in both `.` and `?.` position:

```bash
$input.doc.input.value     # 7      <input> is an HTML element
$input.doc.return          # 9      tax and financial XML
$input.doc.map.filter      # "on"
$input?.input.value        # 7
```

Because the change is additive, the tests that carry the weight are the ones asserting the
neighbouring syntax is untouched — the wildcard, attribute, index and safe-navigation steps,
and the keywords still doing their job as keywords in the same programs:

```bash
$input.a.*            => [1]        wildcard step
$input.items[*].n     => ["x"]
$input.doc.@xml-lang  => "en"       attribute step
if (…) "y" else "n"   => "y"        `if`/`else` still keywords
map(xs, (i) -> i)     => […]        `map` still a function
```

`language/selectors/b56_keyword_property_names.yaml` carries it into the corpus, so
`ConformanceStrategyParityTest` checks it on TEMPLATE, COPY and COMPILED — the compiled path
builds member access from the same AST node. Engine count 1013 → 1014 confirms it was picked
up.

### Found alongside: recursive descent is not implemented

The spec documents `input..ProductCode` (§4.1), but `$input..value` is a parse error in every
spelling tried, and **zero** conformance fixtures use it — the 23 corpus matches for `..` are
all the `...` spread operator. Not touched here, and noted because it means the `..`
continuation could not have been broken by this change. Worth its own report.

## Workaround before the fix


The bracket form reads any key:

```utlx
$input["input"]["value"]      // 7
$input["input"].value         // 7
$input.doc["input"].value     // 7
```

## Verification needed with the fix

- a parser test over the **whole** reserved-word list in read position, asserting each parses
  and resolves — a table, since the defect is "one list was updated and another was not"
- the same for `?.`, which has its own `consume(IDENTIFIER, …)` line
- `.{*}`, `.@attr`, `.^meta` and `..name` must still parse as before — those are the
  continuations a looser property rule could swallow
- a conformance fixture reading `<input>` and `<return>` elements from XML, so the behaviour
  is pinned across all three execution strategies
- `./gradlew :modules:engine:test` — the compiled path builds member access from the same AST
