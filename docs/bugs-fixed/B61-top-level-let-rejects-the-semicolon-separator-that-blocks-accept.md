# B61 — a top-level `let` rejects the `;` separator that blocks accept

**Status:** Fixed — 2026-11-15
**Priority:** Medium — syntax that F02 documented as supported did not parse
**Component:** `:core` (`parser/parser_impl.kt`)
**Found:** 2026-11-14, by a stale lint fixture that happened to use the syntax

## Summary

F02 made the separator after a `let` binding optional, accepting a newline, `;` or `,`. Only
the block half of that was implemented. At the top level of a script a newline works and `;` is
a parse error — **including the example F02's own feature document gives**:

```utlx
// docs/features/F02-let-binding-separator-consistency.md, "The Target State":
// Single-line — semicolons as optional shorthand
let x = 10; let y = 20; {result: x + y}
```

```
Error: Parse errors:
  Expected expression at Location(line=5, column=12)
```

The same document's "What Stays the Same" section lists *"Semicolons as optional single-line
separators: still work"*. They do not, at the top level. So this is not merely an internal
inconsistency: it is a documented part of F02 that was never wired up, and F02 itself rates the
"AI generation impact" of separator confusion as HIGH — a model or a reader following that
document writes a script that fails to parse.

The multi-line form fails the same way:

```utlx
%utlx 1.0
input json
output json
---
let a = 1;
{ v: a }
```

```
Error: Parse errors:
  Expected expression at Location(line=5, column=10)
```

The same separator in a block is accepted:

```utlx
{ r: { let a = 1; a + 1 } }   // => { "r": 2 }
```

So the same character is a separator in one scope and a syntax error in the other, and the
message names neither the `;` nor the scope rule.

## Repro

```bash
# fails
printf '%%utlx 1.0\ninput json\noutput json\n---\nlet a = 1;\n{ v: a }\n' > /tmp/t.utlx
echo '{}' | ./utlx transform /tmp/t.utlx

# the same binding without the semicolon works
printf '%%utlx 1.0\ninput json\noutput json\n---\nlet a = 1\n{ v: a }\n' > /tmp/t.utlx
echo '{}' | ./utlx transform /tmp/t.utlx     # => { "v": 1 }

# and inside a block the semicolon is fine
printf '%%utlx 1.0\ninput json\noutput json\n---\n{ r: { let a = 1; a + 1 } }\n' > /tmp/t.utlx
echo '{}' | ./utlx transform /tmp/t.utlx     # => { "r": 2 }
```

Two consecutive top-level bindings fail the same way (`let a = 1;\nlet b = 2;` → "Expected
expression" at the first `;`).

## Root cause

The top-level body is collected by a loop with no separator handling at all:

```kotlin
// parser_impl.kt — parseProgram()
while (!isAtEnd()) {
    val expr = parseExpression()
    allExpressions.add(expr)
}
```

Expressions are simply juxtaposed. A newline therefore "works" by accident — it is whitespace
the lexer does not turn into a token the parser must consume — while a `;` is left in the
stream, the next iteration calls `parseExpression()`, meets `SEMICOLON`, and reports
`Expected expression`.

The block body loop does handle it, which is where F02 was implemented:

```kotlin
// parser_impl.kt — the block form, inside parseObjectLiteral()
while (match(TokenType.LET) || match(TokenType.FUNCTION) || match(TokenType.DEF)) {
    // ...
    // F02: Accept newline, semicolon, or comma as separator after let/function in blocks
    if (!hasNewlineBefore() && !match(TokenType.SEMICOLON) && !match(TokenType.COMMA)) {
        error("Expected newline, ';' or ',' after let binding or function definition in block expression")
    }
}
```

`parseLetBinding()` itself consumes no separator — by design, since the two call sites differ —
so the asymmetry is entirely between these two loops.

## Why it was not noticed

Nothing in the corpus writes a top-level `let` with a semicolon: the language guide's examples
and all 578 conformance fixtures use the newline form, which works. The one place it did appear
was `conformance-suite/utlx/lint-tests/clean/clean_code_example.yaml`, and that suite is not run
by any CI workflow, so its failure sat unreported. The F02 work added the separator rule where
blocks are parsed and there was no failing case to show the top level needed it too.

Worth drawing out, because it is the reusable lesson: **F02's acceptance criteria were written
as prose examples in the feature document and never became fixtures.** The block examples were
implemented and are covered; the single-line top-level example was not, and nothing noticed the
difference. A feature document that shows syntax should land a fixture per example, or the
unimplemented half is indistinguishable from the implemented half.

## The fix

One statement, at the end of `parseProgram()`'s body loop:

```kotlin
while (!isAtEnd()) {
    val expr = parseExpression()
    allExpressions.add(expr)

    // B61: accept an explicit `;` or `,` between top-level expressions, as F02 already does
    // inside a block. ...
    match(TokenType.SEMICOLON) || match(TokenType.COMMA)
}
```

**Exactly one separator is consumed, not a run of them.** `let a = 1;;` stays an error: a doubled
separator is a typo, and swallowing it would hide a dropped expression. The same reasoning keeps a
leading `;` an error.

Verified against the forms that matter:

| script | before | after |
|---|---|---|
| `let x = 10; let y = 20; {result: x + y}` (F02's own example) | parse error | `{"result": 30}` |
| `let a = 1;` / `let b = 2;` multi-line | parse error | works |
| `let a = 1,` / `let b = 2,` (comma) | parse error | works |
| `let a = 1` / `let b = 2` (newlines) | works | **unchanged** |
| `let a = 1;;` | error | **still an error** |

## Tests

`B61TopLevelLetSeparatorTest` (11 tests, `:modules:core`) covers each separator style, the
unchanged newline form, hoisted function definitions, the block rule F02 already implemented, and
the two forms that must keep failing.

`conformance-suite/utlx/tests/language/let-bindings/b61_top_level_let_separators.yaml` exercises
all four styles in one script. That also buys cross-strategy coverage: `ConformanceStrategyParityTest`
runs every fixture through TEMPLATE, COPY and COMPILED, and a parse-level change should be
invisible to all three — parity failing there would have meant it was not.

**Revert-verified:** removing the one statement fails six of the eleven — `F02's own single-line
example parses`, `a top-level let accepts a trailing semicolon`, `consecutive top-level lets accept
semicolons`, `a top-level let accepts a comma, as blocks do`, `a separator after the final
expression is accepted`, and `a function definition still follows a let`. The other five pass
either way, which is correct: they assert the behaviour this fix must not change.

Full regression after the change: **4887 JVM tests, 0 failures** (including the 1017 engine tests),
and **579/579 conformance on both the JVM and the GraalVM native binary**.

## Follow-up done

`conformance-suite/utlx/lint-tests/clean/clean_code_example.yaml` — the fixture that found this —
has been restored to the semicolon form it originally used. It had been rewritten to newlines so
the lint suite could test linting rather than this parse failure; with B61 fixed it exercises both.

## Still open

The guide does not show the single-line form anywhere, which is part of why the gap survived. When
`syntax.md` is next revised, F02's `let x = 10; let y = 20; {result: x + y}` is worth including as
a runnable example rather than prose.

## Related

- **F02** — [let binding separator consistency](../features/F02-let-binding-separator-consistency.md),
  the feature this is an unimplemented half of.
- The same investigation found a diagnostic-quality issue in the neighbouring code: an unclosed
  object literal reports `Expected property name or spread operator` at the line after the
  content, rather than naming the missing `}`. Correct detection, unhelpful wording; not filed
  separately.
