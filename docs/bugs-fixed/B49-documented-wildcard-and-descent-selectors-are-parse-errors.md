# B49 — `[*]` and `..` selectors are documented but do not parse

**Status:** **Closed — 2026-11-13.** `[*]` fixed 2026-11-11. `..` **will not be implemented**;
the token now answers for itself with an error naming the alternatives. Reasoning below.
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

---

## Decision: `..` will not be implemented (2026-11-13)

Descent is a **query** convenience, and UTL-X is a **mapping** language. Asking what it would
actually buy here, against what already exists:

| the case | already served by |
|---|---|
| flat records linked by a key — SAP IDoc segments, EDI, CSV with foreign keys | **`nestBy()`** (F03), which names IDocs in its first line |
| "every child's X", across repeated elements | **`.*` + projection**, which B48 made compose |
| a value at a known depth | naming the steps |

What is left is document-wide extraction — every `@id` for an audit, every field to redact.
That is real, but it is a query over a document, and it wants to be explicit about its reach.
**0** of 1442 UTL-X blocks in the corpus and documentation need it today.

Set against that:

- **It matches what you did not mean.** `..id` finds the order id *and* the metadata id *and*
  the audit-trail id. In a query you notice; in a mapping that runs nightly, you ship wrong
  data.
- **Breaking loudly is usually the feature.** A mapping is a contract. When a source adds a
  wrapper level, an explicit path *should* fail so somebody looks — the same reasoning as
  [B24](B24-input-silently-aliased-to-first-input.md) (never substitute an input) and
  [B38](B38-non-finite-numbers-serialise-as-invalid-json.md) (refuse rather than emit garbage).
  Descent trades that away.
- **It would need the compiled path too.** A selector implemented in the interpreter alone
  diverges silently across strategies — exactly what B48 shipped with and the parity suite
  caught.
- **`..` reads as *parent* to the audience this project names.** The spec lists XSLT under
  Influences for *selector syntax* specifically, and in XPath `..` is the parent axis.
  Leaving it unspent keeps it available where those readers would look for it.

If document-wide search is ever genuinely wanted, the better shape is a **function** —
`descendants($input)` or `selectDeep($input, "name")`. A function takes the semantics as named
arguments (traversal order, attributes or not, self-inclusive, depth cap) where an operator can
only hard-code them; it needs no grammar change, no `ASTCompiler` work, and no
strategy-divergence risk; and it reads honestly at the call site — `descendants($input, …)`
says "I am walking your whole document", where `..name` hides it.

### The token was kept, and answers for itself

Deleting `DOTDOT` would have left `$input..name` lexing as two dots and failing with a generic
`Expected property name` — still a puzzle. Keeping it and giving `parsePostfix` a branch costs
a few lines and turns the dead end into a signpost:

```
$input..name
  Recursive descent ('..') is not supported in UTL-X. To reach a value at a known depth,
  name the steps: $input.order.lines.line.sku — a step over repeated elements already
  projects across them. To build nested data from flat records linked by a key (SAP IDoc
  segments, EDI, CSV exports), use nestBy().
```

Reported at the `..` itself (line 5, column 12 for `{ r: $input..name }`), not wherever the
parser previously gave up. Same principle as B38's refusal naming `isFinite`: if the answer is
no, say what to do instead.

`B49RecursiveDescentErrorTest` (5 tests) pins the message, both alternatives being named, the
location, and — most importantly — that ordinary `.`, `.*`, `.@attr` and the `...` spread
operator are untouched. That last one guards the lexer's three-way longest-match split: if
`...` ever stopped winning over `..`, a spread would start reporting this error.

### The other half of the same idea

`parent()` — structural navigation *upward* — was removed the same day for related reasons; it
never worked and could not without making the UDM cyclic or mutable. See
[B58](B58-parent-function-never-worked-and-cannot-without-changing-the-udm.md). Both are
fossils of the original XSLT-template design, which was never built; the language went to
expressions and functions over relational data instead, and neither pointer has a customer
there.
