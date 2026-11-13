# B49 — `[*]` and `..` selectors are documented but do not parse

**Status:** `[*]` fixed 2026-11-11; `..` still unimplemented — see "What `..` is today" (2026-11-13)
**Component:** `:core` — parser
**Found:** 2026-11-11, alongside [B48](B48-path-steps-do-not-project-across-a-sequence.md)
**Severity:** Medium — documented syntax, rejected at parse time

## Summary

Two selector forms described in `docs/language-guide/selectors.md` are not
accepted by the parser. They fail before evaluation, with a generic
`Expected expression` error that does not say the construct is unsupported.

## Reproduction

```bash
echo '{"items":[{"price":10.00},{"price":25.00},{"price":15.00}]}' > doc.json
```

| documented at | expression | documented result | actual |
|---|---|---|---|
| selectors.md:169 | `input.items[*]` | all items | `Parse errors: Expected expression` |
| selectors.md:170 | `input.items[*].price` | `[10.00, 25.00, 15.00]` | `Parse errors: Expected expression` |
| selectors.md:178 | `input..price` | recursive descent | `Parse errors: Expected expression` |

For contrast, these do work:

| expression | result |
|---|---|
| `input.items[0]` | `{"price":10.00}` |
| `input.Order.@*` | `["1","x"]` — the attribute wildcard is implemented |
| `map(input.items, (t) -> t.price)` | `[10,25,15]` |

## Two defects, one report

1. **The parser does not accept `[*]` or `..`.** Index access `[0]` parses, so
   the bracket path exists; `*` inside it does not.
2. **The documentation asserts they work**, with worked examples and expected
   output. `docs/language-guide/selectors.md` §"Wildcard Selection" and
   §"Recursive Descent" both need to match reality — either by implementing the
   forms or by marking them unimplemented.

Note that the same page already marks one feature honestly:
`### Negative Indexing (Future)` at line 143. The wildcard and descent sections
carry no such marker, which is what makes them misleading rather than merely
aspirational.

## Root cause for `[*]`

The machinery exists but the expression parser cannot reach it.
`udm_core.kt:482` already parses `[*]` into a `PathSegment.Wildcard` — but that
is the **string path** parser (`"a.b[*].c"`), not the expression grammar. In
`parser_impl.kt` the `.`-branch accepts `STAR` (that is how `.*` works), while
the `LBRACKET` branch only accepts an index expression, so `items[*]` fails at
parse time.

So `[*]` needs the `LBRACKET` branch to accept `STAR` and emit the same node
`.*` emits — after which it inherits B48's projection fix for free.

## Scope note

`..` (recursive descent) is a genuine language feature and a larger piece of
work. `[*]` is closer to B48 — it is the bracket spelling of the same
projection, and would likely fall out of the same fix.

## What was done

**`[*]` — implemented.** `parser_impl.kt`'s `LBRACKET` branch now accepts `STAR` and emits
the same `MemberAccess(expr, "*")` node that `.*` produces, so both spellings share one
evaluation path and inherit [B48](B48-path-steps-do-not-project-across-a-sequence.md)'s
projection. `docs/language-guide/selectors.md` §"Wildcard Selection" is now accurate, and
documents the projection rule it previously only implied.

**`..` — not implemented, and now says so.** `docs/language-guide/selectors.md`
§"Recursive Descent" is retitled `(Future)` with an explicit note that it is a parse error
in 1.4, following the `### Negative Indexing (Future)` precedent already on that page. It
points readers at chained steps, which now project.

Recursive descent remains open as its own feature: it needs a traversal order, a cycle
policy for deep structures, and a decision on whether it crosses into attributes.

---

## What `..` is today (added 2026-11-13)

Re-examined while answering whether `..` collides with the spread operator. It does not, and
the finding is narrower and more actionable than "unimplemented":

**The lexer half is finished and correct.** `TokenType.DOTDOT` is declared (`// .. (recursive
descent)`) and `lexer_impl.kt` emits it with proper maximal munch:

```kotlin
'.' -> {
    if (peek() == '.' && peekNext() == '.') { … SPREAD }   // ...
    else if (peek() == '.')                 { … DOTDOT }   // ..
    else                                    {   DOT }      // .
}
```

**No conflict with `...` is possible.** Longest-match settles the lexing, and the two
multi-dot tokens never occupy the same grammatical position — `...` is only ever a prefix
inside an object or array literal, `..` only ever a postfix path step.

**The parser never consumes `DOTDOT`.** It appears in exactly two places in the whole
codebase: the token enum and the lexer. So the token is produced and meets a parser with no
branch for it, which is why the error is a generic `Expected expression` instead of anything
naming the construct. **An emitted-but-unconsumed token is worse than no token** — it turns a
missing feature into a misleading parse error, and it is the one part of this that should be
resolved regardless of which direction the feature goes.

### There is no alternative spelling, which raises the priority

`.*` is **strictly one level**, so it cannot stand in for descent:

```bash
# {"a": {"name":"A", "deep": {"name":"D"}}, "b": {"name":"B"}}
$input.*.name            # ["A","B"]   — "D" at depth 2 is NOT found
$input.*.deep.name       # ["D"]       — only by naming the depth
$input.*.*.name          # ["A","B"]   — stacking changes nothing (see below)
```

Stacking wildcards is a **no-op**, not an error: `*` applied to a sequence is that sequence
(B48's `isPlainWildcard`), so `$input.*.*.name` is exactly `$input.*.name` and still misses the
nested `"D"`. No amount of `.*` reaches further down.

and no stdlib function fills the gap: `descendants`, `deepSelect`, `valuesDeep` and
`selectDeep` are all `Undefined function`. So "every `ProductCode` anywhere in this document"
— the ordinary XML case, and the reason XPath has `//` — cannot be written at all today.

### Operator or function?

Worth settling before implementing, because the two cost very different amounts:

- **`..` as an operator** matches DataWeave and JSONPath, and is concise. But it needs a
  parser branch *and* `ASTCompiler` support — B48 is the precedent for what happens when only
  the interpreter gets a selector — and `..` reads as **parent** to anyone coming from XPath
  or a filesystem, which is awkward given this project names XSLT selector syntax as an
  influence (spec §1.3).
- **A function** — `descendants($input)` or `selectDeep($input, "name")` — needs no grammar
  change, no compiler work, and no strategy-divergence risk. It also matches what UTL-X
  already does for the symmetric case: **going *up* is a function**, `parent()`, registered at
  `Functions.kt:726`. If "up" is a function, "down any depth" being one too is the consistent
  choice, and it leaves `..` free for a future parent axis where XPath users would expect it.

Either way the semantics need deciding — traversal order, whether attributes are included,
whether the starting node is included, and what happens on cycles — and those are easier to
state and document as named parameters than as operator syntax.

### Documentation corrected alongside

- `language-spec.md` §4.1 listed `input..ProductCode // Recursive descent`; now carries a
  "Not implemented" note pointing here.
- `language-spec.md` §4.3 claimed `input.*.Name // All Name properties at any level`. It is
  one level. Corrected, with the worked example above.
- `selectors.md` said "Not implemented" in its §Recursive Descent but still taught
  `input..name  // Find 'name' anywhere` 470 lines further down. That example now uses an
  explicit path.
- `syntax.md` §Recursive Descent retitled and marked as a parse error.
