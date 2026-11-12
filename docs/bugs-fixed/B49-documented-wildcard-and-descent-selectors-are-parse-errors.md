# B49 — `[*]` and `..` selectors are documented but do not parse

**Status:** `[*]` fixed 2026-11-11; `..` still unimplemented (documented as Future)
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
