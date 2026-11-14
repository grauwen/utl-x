# F30: accept a trailing comma in literals, argument lists and match cases

**Status:** Proposed — **recommended**
**Priority:** Medium — small and risk-free in implementation, but it removes a whole class of bug
from every script *generator* this project ships, and it is documented as working on both branches.
**Component:** `:core` — `parser_impl.kt` (`parseObjectLiteral`, `parseArrayLiteral`, the call
argument list, and `parseMatchExpression`). **No engine change** — see "Risk".
**Raised:** 2026-11-14, from `docs/language-guide/syntax.md` claiming "Trailing comma OK" twice.

## The gap

`syntax.md` documents it for both objects (line 263) and arrays (line 338), on `development` **and**
`main`, with no caveat:

```utlx
[
  1,
  2,
  3,    // Trailing comma OK
]
```

Nothing accepts it. All four comma-separated constructs reject the trailing separator:

```
{ a: 1, b: 2, }        => Expected property name or spread operator
[1, 2, 3,]             => Expected expression
max(1, 2,)             => Expected expression
match (1) { 1 => "one", _ => "other", }
                       => Expected pattern (literal, identifier, or '_')
```

## Why support it

1. **It removes a bug class from every generator this project ships.** UTL-X scripts are written by
   machines in at least two places: the IDE's function/mapping builder, and the MCP server's
   `generate_utlx_from_prompt`. An emitter that may append `",\n"` after *every* element needs no
   last-element special case; without trailing-comma support, each generator carries its own
   "is this the last one?" branch, which is the classic place an emitter breaks. F02 made exactly
   this argument when it rated the "AI generation impact" of separator confusion **HIGH**.

2. **Diff hygiene in long-lived mappings.** A transformation is a maintained artefact that gains
   fields for years. With a trailing comma, adding a field touches one line; without it, two (the
   new line plus the previous line, to add its comma). For a language whose central construct is a
   multi-line object literal, that is a daily cost paid by every reviewer.

3. **It is the direction F02 already set.** F02 deliberately made separators lenient — newline,
   comma or semicolon, whichever the author writes. Rejecting a harmless separator because it sits
   last is inconsistent with the rule the language already chose.

4. **Habit from every neighbouring language.** JavaScript/TypeScript, Python, Rust, Kotlin and
   JSON5 all accept it; Go *requires* it in composite literals. Authors arrive with the habit, and
   nothing about UTL-X argues against it. (Note the input formats are irrelevant here: strict JSON
   forbids trailing commas, but a `.utlx` script is not JSON.)

5. **It is already promised.** Documented twice, on two branches. Either implement it or retract
   the claim.

## Risk: contained, and no new semantics

This is parse-time only and produces the **identical AST** — one fewer element appended to a list
that is otherwise built the same way. The interpreter, `ASTCompiler` and `RuntimeOps` see nothing
new, so there is no "semantics live in two places" exposure of the kind behind B34, B35, EB03 and
B48. A conformance fixture still earns three-strategy coverage for free, and if parity ever fails
on it, the desugaring is wrong.

**What must keep failing.** The allowance is strictly *one* separator immediately before the
closing token. These are errors today and must stay errors:

```
[1,,2]      => Expected expression      (double comma — a missing element)
[,1]        => Expected expression      (leading comma)
```

So the rule is "a separator directly before the closer is ignored", **not** "empty elements are
allowed". Anything laxer silently accepts a typo that drops a value.

## Implementation sketch

Both loops already consume the separator in their `while` condition; each needs a break when the
next token closes the construct.

```kotlin
// parseArrayLiteral — currently:
} while (match(TokenType.COMMA))
// becomes: after consuming the comma, stop if the array is closing
} while (match(TokenType.COMMA) && !check(TokenType.RBRACKET))

// parseObjectLiteral — currently:
} while (match(TokenType.COMMA) || (hasNewlineBefore() && !check(TokenType.RBRACE)))
// the newline arm already guards on RBRACE; the comma arm needs the same guard
} while ((match(TokenType.COMMA) && !check(TokenType.RBRACE)) ||
         (hasNewlineBefore() && !check(TokenType.RBRACE)))
```

`parseArguments()` (line 1587) and `parseMatchExpression()` (1437) need the same guard, against
`RPAREN` and `RBRACE` respectively.

**Do not apply it blindly to every comma loop.** The parser has 14 of them across 10 functions,
and they are not all the same kind of list:

| site | construct | in scope? |
|---|---|---|
| `parseObjectLiteral` (1236) | `{ a: 1, }` | **yes** |
| `parseArrayLiteral` (1259) | `[1, 2, ]` | **yes** |
| `parseArguments` / `parseArgument` (1587, 1619) | `f(a, b, )` | **yes** — same generator argument |
| `parseMatchExpression` (1437) | `match { 1 => "a", }` | **yes** |
| `parseFunctionDefinition` (1331) | `function F(a, b, )` parameter list | probably — decide with the above |
| `parseLambdaOrExpression` (653) | lambda parameters | probably — same reason |
| `parseFormatOptions` (601, 612) | `output json { writeAttributes: true, }` | **decide** — header territory |
| `parseInputsOrOutputs*` (370, 500) | `input: a xml, b json,` | **decide** — a trailing comma in a header reads like a dropped input |
| `parseObjectLiteral` (1103, 1107, 1111) | the let/function prelude inside a block | **no** — F02 owns those separators |

The body constructs are the clear win. The header ones are a judgement call: a trailing comma after
the last declared input looks more like an editing accident than an intention, and headers are not
what generators emit repeatedly.

## Decisions to settle

- **Scope.** Recommendation: the four body constructs plus the two parameter lists (see the table
  above), and **not** the header ones. A generator emitting an argument list has the same
  last-element problem as one emitting an array, and a reader who learns the rule for `[...]` will
  expect it in `f(...)` — so stopping at literals leaves an arbitrary seam. Headers are different:
  they are hand-written once, and `input: a xml, b json,` reads like a dropped input rather than a
  style choice.
- **Interaction with newline separators.** `{ a: 1,\n }` and `{ a: 1\n }` must both work; the
  object rule above covers them because each arm independently checks for the closer.
- **Spread.** `{ ...base, }` and `[...xs, ]` should be accepted on the same rule — no special case.
- **Error-message quality.** When the construct is genuinely malformed (`[1,,2]`), the message
  should keep pointing at the empty element, not at the closer.

## Tests

- `F30TrailingCommaTest` in `:modules:core`: object, array, argument list and match cases, each
  single-line and multi-line; combined with spread; combined with F02 newline separators; plus the
  negative cases `[1,,2]`, `[,1]`, `{a: 1,,b: 2}` which must still error.
- Conformance fixtures under `language/object-literals/` and `language/arrays/`, which also assert
  the AST is unchanged via `ConformanceStrategyParityTest`.
- Replace the two `// Trailing comma OK` examples in `syntax.md` with runnable ones, on **both**
  branches.

## Related

- **F29** — shorthand object properties: the other documented-but-absent object-literal form, same
  file, same two parse functions, same no-engine-change profile. If both are accepted they should
  land together.
- **F02** — the separator-leniency rule this extends.
- **B61** — F02's top-level half, never implemented; a reminder to land the fixtures with the
  feature rather than leaving the examples as prose.
