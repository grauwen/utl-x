# F28: negative array indices (`$input.items[-1]`)

**Status:** Proposed
**Priority:** Low-Medium — pure sugar over `last()`, but it is jq parity, it keeps path expressions
readable, and the language guide already promises it.
**Component:** `:core` — lexer/parser index handling and the interpreter's index evaluation, plus
the compiled path in `:modules:engine` (`ASTCompiler` / `RuntimeOps`).
**Raised:** 2026-11-14, from a `// (future)` marker in `docs/language-guide/syntax.md:386` that had
no tracking item behind it.

## The gap

`docs/language-guide/syntax.md` documents the syntax with a future-tense note:

```utlx
$input.items[-1]           // Last element (future)
```

It is not implemented, and the annotation is honest:

```
$input.items[-1]  =>  Error: Array index out of bounds: negative index -1
```

Four idioms already produce the last element, so nothing is blocked:

```utlx
last($input.items)                       // "c"   <- the intended form today
last($input.items, 1)                    // "c"   (n-arg form)
$input.items[count($input.items) - 1]    // "c"
reverse($input.items)[0]                 // "c"
```

## Why it is still worth doing

1. **jq parity.** jq supports `.[-1]`, `docs/comparison/vs-jq.md` tracks parity as a project goal,
   and this gap is not listed there — a jq user meets it immediately. (That comparison document
   should gain a row either way, whichever way this is decided.)
2. **Path fluency.** Mid-path the function form breaks the chain:
   `$input.order.lines[-1].sku` against `last($input.order.lines).sku`. For a language whose core
   idiom is the selector, staying in selector syntax matters more than saving characters.
3. **A promise with no tracking item rots.** F02 is the cautionary case: its acceptance criteria
   lived as prose examples, the top-level half was never implemented, and nothing noticed until a
   stale fixture tripped over it (B61). Either implement this or delete the line.

## Design decisions to settle first

**Out-of-range must error, not return null.** Positive indices already do:

```
$input.items[9]   =>  Error: Array index out of bounds: index 9, size 3
```

so `[-5]` on a 3-element array should fail the same way. Suggested message shape:
`Array index out of bounds: index -5 resolves to -2, size 3`.

**Literal only, or any expression?** `[-1]` is unambiguous because the `-` opens the bracket, so
nothing precedes it that the lexer could glue to. `[-n]` needs unary minus inside an index, which
the index parser does not have today. Recommendation: accept any expression and evaluate it, then
apply the `< 0` rule to the result — that gets `[-1]`, `[-n]` and `[0 - n]` from one change and
avoids a literal-only special case.

**Beware the hyphen interaction (B54).** Hyphens are identifier characters, so a *tight* computed
index is already a property lookup rather than arithmetic:

```
$input.items[$input.n - 1]  =>  "b"     (spaced — arithmetic)
$input.items[$input.n-1]    =>  Error: Index must be a number or string, got NullValue
                                        (`n-1` parsed as a property name)
```

That is pre-existing and not caused by this feature, but it is the trap a reader will hit next to
it, and the guide should say so wherever negative indices are documented.

**Sequence semantics.** After a step that projects over a sequence (B48, B57), decide whether
`[-1]` indexes the projected sequence as a whole or each member. Positive indices answer this
today; whatever they do, negative ones must match — this is exactly the class of divergence that
B48 produced between TEMPLATE and COMPILED.

## Scope

- Lexer/parser: allow a negative (or general) expression in an index position.
- Interpreter: resolve `i < 0` as `size + i` before the bounds check.
- `:modules:engine`: the same rule in the compiled path. **Semantics live in two places** — a core
  fix alone leaves COPY and COMPILED diverging, which is how B34, B35, EB03 and B48 all happened.
- Strings: out of scope. `"abc"[-1]` is a separate question; leave it erroring.

## Tests

- `F28NegativeIndexTest` in `:modules:core`: `[-1]`, `[-size]`, `[-(size+1)]` (error), `[-n]` via a
  variable, and the spaced/tight `[n - 1]` pair above as a regression guard for B54.
- A conformance fixture under `language/selectors/`, which gets all three execution strategies via
  `ConformanceStrategyParityTest` for free.
- Replace the `// (future)` line in `syntax.md:386` with a working example, and add the negative
  index row to `docs/comparison/vs-jq.md`.

## If this is declined

Delete the `[-1]` line from `syntax.md:386` and put `last($input.items)` in its place. The current
state — documented syntax that errors, with no alternative shown next to it — is the one outcome
worth avoiding.
