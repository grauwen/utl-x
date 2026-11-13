# CB04 — the `-e` dot shorthand needs whitespace, so compact jq expressions fail

**Status:** Fixed — 2026-11-13
**Component:** `modules/cli` — `commands/TransformCommand.kt`, `expandDotShorthand`
**Found:** 2026-11-12, while checking whether `docs/comparison/vs-jq.md` is still accurate
**Severity:** Low — the failure was a parse error, so nothing was silently wrong, and UTL-X is
written spaced everywhere (2513:1, measured below). Fixed because the fix turned out to carry
no risk, not because the symptom was pressing.

## Summary

`-e` expands a leading `.` to `$input.` before wrapping the expression in a script. Whether
it expands depends on **the character before the dot**, and the accepted set is missing most
of the punctuation that jq users actually type:

| expression | result |
|---|---|
| `{p:.a}` | **parse error** |
| `{p: .a}` | `{"p":1}` |
| `[.a]` | **parse error** |
| `[ .a]` | `[1]` |
| `.a==.b` | **parse error** |
| `.a == .b` | `false` |
| `.a+.b` | **parse error** |
| `.a + .b` | `3` |
| `!.a` | **parse error** |
| `-.a` | **parse error** |
| `{x:{y:.a}}` | **parse error** |
| `count(.items)+1` | `3` — works, the dot follows `(` |

Eight of nine compact forms fail, and in every case inserting one space fixes it. The error
says `Expected expression at Location(line=5, column=4)` — a line and column in a *generated*
script the user never wrote, pointing at a dot that looks fine.

## Root cause

`TransformCommand.kt`, `expandDotShorthand`:

```kotlin
val prevChar = if (i > 0) expression[i - 1] else ' '
val isPathStart = i == 0 ||
    prevChar == '(' || prevChar == ',' || prevChar == ' ' || prevChar == '\t' ||
    prevChar == '\n' || prevChar == '>' // => arrow
```

A whitelist of six characters. `:` `[` `=` `!` `+` `-` `*` `/` `{` `|` are all absent, so in
`{p:.a}` the dot is treated as "a regular dot, part of a path like `obj.name`", left alone,
and `{p:.a}` reaches the parser with a leading dot that means nothing there.

The `>` entry is the tell: it was added for `=>`, one spelling at a time, which is how a
whitelist like this ends up incomplete.

## Was the whitelist deliberate?

Partly, and the part that is deliberate is right: **when unsure, do not rewrite.** A dot left
alone produces a parse error, which is loud; a dot wrongly rewritten produces a different
program, which is silent. Erring toward the error is the correct instinct for a textual
preprocessor that runs before the parser.

But the current rule does not achieve that, and this is the argument for changing it rather
than leaving it alone:

```bash
utlx -e '1 + .5'     # Error: Expected property name
```

`.5` is a decimal literal. The dot follows a space, which *is* in the whitelist, and the next
character is a digit, which the expander accepts — so it rewrites it to `$input.5`. The
conservative rule misfires in exactly the way conservatism was meant to prevent. The `>`
entry tells the same story from the other side: it was added for `=>`, one spelling at a
time, which is how a whitelist ends up both too narrow and too wide at once.

## Three real ambiguities, found by testing rather than reasoning

A first draft of this report proposed simply inverting the rule — "a dot continues a path
only when the previous character could end an operand". That is the right shape but it is not
sufficient, and the three cases it misses are the whole risk. Two of them were found only
because the flat rule was prototyped and tested against the live CLI rather than reasoned
about:

**1. Decimal literals.** `.5`, `1.5`, `0.5`. Safe to resolve, but only because of a fact that
must be pinned: **dot-indexing is not valid UTL-X.** `.items.0` is `Expected property name`;
indexing is `.items[0]`. So a dot followed by a digit is *always* a decimal point and never a
path step. If dot-indexing were ever added, this rewriter silently breaks.

**2. `-` is an identifier character, so `.a-.b` is not subtraction.** This one was missed on
the first pass and is the most dangerous of the three, because it fails *silently* rather
than loudly. The lexer binds `-` into identifiers (`lexer_impl.kt:306`), so `$input.a-b` is
the key `"a-b"` — on a document holding both, it returns `99` where `a - b` would give `6`.
Subtraction therefore requires whitespace, and a dot after a `-` that follows an identifier
character **must not** expand. Unary minus at the start of an expression (`-.a`) is a
different case and expands safely. See
[B54](B54-hyphens-in-identifiers-implementation-and-docs-disagree.md) — the language guide
currently denies that hyphens are legal at all, which is why this was missed, and CB04
cannot land until that is settled.

**3. `*` is both a wildcard step and multiplication**, and the two want opposite answers from
the same preceding character:

| expression | the `*` is | the following dot must |
|---|---|---|
| `.items.*.n` | a wildcard step | **not** expand — it continues the path |
| `.a*.b` | multiplication | **expand** — it starts an operand |

A flat character class cannot separate these; classifying `*` as an operand-ender leaves
`.a*.b` broken, and classifying it the other way breaks `.items.*.n`, which *works today* and
is the B48/B49 projection syntax. It is decidable with one more character of lookback: a
wildcard `*` is always immediately preceded by `.`, a multiplication `*` never is. That fact
is load-bearing and belongs in a test, not in a comment.

## The rule, stated so it can be tested

> A dot **continues** a path when the previous character is an identifier character, `)`,
> `]`, `}`, `"`, `'`, a `*` that is itself preceded by `.`, or a `-` that is itself preceded
> by an identifier character. A dot followed by a digit is a decimal point. Otherwise the dot
> **starts** a path and expands to `$input.`.

The two lookback clauses are not decoration: `*` and `-` are each two different things
depending on what precedes them, and a flat character class gets one of the two readings
wrong in every case.

## Measured risk: zero on a 42-expression corpus

Both rules were transcribed into a prototype and diffed over 42 expressions — the failing
forms, the ones that must survive untouched, eighteen taken from this repo's own docs and
`--help` examples, and six covering the hyphen family added after that defect was found:

| | |
|---|---|
| identical under both rules | **30** |
| differ | **12** |
| of those 12, broken today | **12** |

Every expression whose meaning changes is one that does not work today — parse errors, plus
`1 + .5` (currently mis-rewritten) and `$input.a + .b` (see below). No currently-working
expression changes meaning. In particular `.a-.b`, `.a-b`, `.a - .b` and `.my-key` come out
**byte-identical** under both rules, which is the check that the hyphen clause actually
works rather than merely being present. That is the number worth re-measuring before the fix lands, with
the corpus kept as the test.

## The single-input assumption, which is the real design constraint

The shorthand expands to `$input` specifically, so it presumes **exactly one unnamed input**.
That holds for `-e` today, and not by accident — `-e` has no named-input syntax at all:

```bash
utlx -e '.a' -i one.json -i two.json    # the second -i silently wins
```

So `.x` is unambiguous in every expression `-e` can currently express, and the fix does not
touch that. What it does is make the coupling explicit and worth recording: **if `-e` ever
gains named inputs** (`--input orders=…`, which the script form already supports), `.x` has no
defensible meaning and the shorthand needs a rule — most likely "shorthand is only legal when
there is exactly one input", enforced rather than assumed. Two `-i` flags silently discarding
the first is a smaller wart of the same family.

Note also that `--arg`/`--argjson` already put *other* bindings in scope alongside `$input`
(`$min`, `$cfg`), and those coexist fine because the shorthand only ever targets `$input`.
What does not coexist is the guard below.

## A second defect in the same function

```kotlin
if (expression.contains("\$input")) return expression // already explicit
```

One literal `$input` anywhere disables expansion for the **whole** expression, so a mixed
expression silently leaves the rest unexpanded:

```bash
utlx -e '$input.a + .b'    # Error: Expected expression — `.b` was never expanded
```

This matters more now that `--arg` and `--argjson` encourage `$name` references, and it is
the tenth row in the corpus above. The guard should be per-dot (a dot already preceded by
`$input` needs no expansion), not per-expression.

Also, the doc comment lists the accepted set as "`(`, `=>`, `,` or whitespace", which already
disagrees with the code it describes.

### The same shape, elsewhere in the CLI

Not `expandDotShorthand`, but the identical defect — a whitelist that grew one entry at a
time — in subcommand inference:

```bash
utlx -e '1+1' -n            # 2
utlx transform -n -e '1+1'  # 2
utlx -n -e '1+1'            # Unknown command: -n
```

A leading `-e` makes the CLI infer `transform`; a leading `-n` does not. Any flag that
`transform` accepts should imply it, or none should. Worth fixing in the same pass, since a
`jq -n …` line ported across hits it immediately.

## How much UTL-X is actually written this way: 2513 to 1

Measured across the whole corpus — 1690 UTL-X blocks from the conformance fixtures, the
```utlx blocks in `docs/`, and the standalone `.utlx` files — with strings, comments, `#`
prose lines, exponent notation, lambda arrows and wildcard steps excluded:

| | |
|---|---|
| operators written **with** surrounding spaces | **2513** |
| operators written tight | 32 |
| …of those, genuine tight **arithmetic** | **1** |

The one is `cubeRoot: 8 ** (1/3)` in `examples/intermediate/exponentiation.yaml`. The other
31 are identifier examples (`user-name`, `let my-var = 10`), grammar placeholders
(`<transformation-body>`), XPath-style paths (`Items/Item/($price * $quantity)`), prose, and
a single `{let x=100,let y=x*0.08,total:x+y}` in `docs/language-guide/syntax.md` that is
labelled **`// ❌ Bad`** — an anti-example in a section headed "Format for Readability".

### And the IDE enforces it

`theia-extension/.../function-builder/operator-insertion-generator.ts:94`:

```ts
// Insert operator with spaces
return ` ${operator.symbol} `;
```

Every operator the Function Builder inserts is surrounded by spaces, deliberately — which is
the same defence the language needs against `*` and `-` binding into names.

### What that means for this report

The premise — "the most idiomatic spellings fail" — is true of **jq habits**, not of UTL-X
usage. Nobody writing UTL-X hits this; someone porting a jq one-liner does, once. Set against
a rewriter that needs three lookback clauses (digit, `*`, `-`), is blocked on
[B54](B54-hyphens-in-identifiers-implementation-and-docs-disagree.md), and whose `-` clause
exists only to preserve behaviour that is *already correct*, the trade is poor.

## Rejected: inserting whitespace around operators

The obvious shortcut — have `-e` normalise `a+b` into `a + b` before calling the parser — is
**not safe**, for the same reason the three ambiguities exist. It rewrites expressions that
work today into expressions that mean something else:

```bash
# input: {"a":10, "b":4, "a-b":99}
utlx -e '.a-b'        # 99  — the key "a-b"
#          ^ eager spacing would make this '.a - b'  =>  6
```

That is the one outcome worse than a parse error: a silent change of meaning in a working
expression. `*` fails the same way — spacing `.items.*.n` into `.items. * .n` breaks the
wildcard. (`+`, `/` and `==` would be safe: `/` is division, not a path separator — the
`Items/Item/(...)` spelling in `docs/language-guide/syntax.md` is legacy XPath syntax that
does not parse. But a rule that is safe for three operators and corrupting for two is not a
rule.)

## The fix: parse, and fall back

The same idea restructured as a **fallback** rather than a guess, which changes its safety
completely:

1. expand with the **current, conservative** rule; hand it to the parser;
2. **only if that is a parse error**, expand again with the aggressive rule and parse that;
3. if the second parses, run it; otherwise report the error.

The ordering is the whole guarantee. An expression that parses today never reaches step 2,
so **no working expression can change meaning** — which is exactly what eager spacing cannot
promise. The ambiguities stop being blockers: `.a-b` parses in step 1 and is served as the
key, `.items.*.n` parses in step 1 and keeps its wildcard, and neither ever meets the
aggressive rule. **This is why CB04 no longer waits on B54** — the fallback does not need to
know which way the hyphen question is settled.

### Measured, end to end

Both expansions were generated for 21 expressions and each was executed as a real script
through the CLI:

| | |
|---|---|
| fixed by the fallback | **8** — `{p:.a}` `[.a]` `.a+.b` `.a==.b` `!.a` `-.a` `{x:{y:.a}}` `.a*.b` |
| working expressions whose meaning changed | **0** |
| still failing, correctly | `.a-.b`, `1 + .5` |

`.a-b` still returns `99`, `.items.*.n` still returns `["x","y"]`, `.a - .b` still returns
`6`.

### Design points that are not optional

- **Conservative attempt first.** Reversing the order reintroduces every risk above.
- **Fall back on a parse error only — never on a runtime error.** `.a-.b` parses (as the key
  `a-`) and fails at *runtime*; re-running on runtime failure would re-execute a
  transformation and could mask a genuine data error as a syntax one.
- **Report the second attempt's error when it ran.** Its expansion is textually closest to
  what the user typed. For `1 + .5` the first attempt says `Expected property name` (about
  `$input.5`, which the user never wrote); the second says `Expected expression` at column 5,
  pointing at the `.5` itself. Note that `.5` is **not** valid UTL-X in any spelling —
  `{ r: .5 }` is a parse error — so the honest answer there is "write `0.5`", and no rewriter
  can do better.
- **Cost is one extra parse, on the failure path only.** Nothing successful pays for it.
- **Consider surfacing it.** When step 2 is used, the executed script differs from the
  literal expansion of what was typed; `--verbose` should probably say so.

### Implemented as

`TransformCommand.expandDotShorthandAggressive` holds the wider rule; `parses()` decides
whether it is needed. In expression mode the body is wrapped, parsed, and only re-expanded on
failure — the conservative expansion is still what runs whenever it parses.

Three further things landed with it:

- **the `$input` guard is gone from the wider pass.** The conservative one bails out entirely
  on seeing a literal `$input`, so `-e '$input.a + .b'` left `.b` unexpanded and failed. A dot
  already preceded by an identifier character is left alone by the rule itself, so no guard is
  needed.
- **`-n` may lead.** `Main.kt`'s subcommand inference listed `-e`/`--expression` alone, so
  `utlx -n -e '1+1'` failed with `Unknown command: -n` while `utlx -e '1+1' -n` worked. The
  transform-only F27 flags (`-n`, `--null-input`, `--arg`, `--argjson`, `--exit-status`) now
  imply `transform` too. `--to`/`--from` stay with identity mode, since they drive both.
- **a hint for the one case no rule can rescue:**

```
$ utlx -e '1 + .5'
hint: a leading decimal point is not valid UTL-X — write '0.5' rather than '.5' (jq accepts both).
Error: Parse errors:
  Expected expression at Location(line=5, column=5)
```

  It fires only when the expression fails to parse *and* contains a dot followed by a digit in
  operand position — not on `1 + 0.5`, and not on an unrelated failure like `nosuchfn(.a)`.

## Verification

| | |
|---|---|
| `:modules:cli:test` | 148, 0 failures (incl. `CB04DotShorthandFallbackTest`, 12) |
| `:modules:core:test` | 342, 0 failures |
| `:stdlib:test` | 2328, 0 failures |
| `:modules:engine:test` | 1013, 0 failures |
| conformance (JVM) | 574/574 |
| conformance (**native**) | 574/574 |

Fixed, and asserted by test:

```
utlx -e '{p:.a}'        => {"p":10}
utlx -e '[.a]'          => [10]
utlx -e '.a+.b'         => 12
utlx -e '.a*.b'         => 20
utlx -e '.a==.b'        => false
utlx -e '!.a'           => false
utlx -e '-.a'           => -10
utlx -e '{x:{y:.a}}'    => {"x":{"y":10}}
utlx -e '$input.a + .b' => 12
```

Unchanged, and asserted by test — these parse conservatively and never meet the wider rule:

```
utlx -e '.a-b'            => 99          the key "a-b", not a minus b
utlx -e '.items.*.n'      => ["x","y"]   wildcard intact
utlx -e '.a - .b'         => 8
utlx -e 'count(.items)+1' => 3
utlx -e '"a.b"'           => "a.b"       dots in strings untouched
```

The `.a-b` assertion runs against a document holding **both** `"a-b": 99` and `a`/`b` — so if
the wider rule ever ran on an expression that already parses, it returns 8 and the test fails
on the value rather than on an error. That is the one regression this change could cause, so
it is pinned by value, not by exit code.

Re-verified on a freshly built GraalVM binary: identical on all of the above, including
`utlx -n -e '1+1'`.

## Reproduction

```bash
echo '{"a":1,"b":2}' > j.json

utlx -e '{p:.a}'  -i j.json     # Error: Parse errors: Expected expression …
utlx -e '{p: .a}' -i j.json     # {"p":1}
```

## Not a core parser bug

`-e` is the only thing that expands dots. In a script file a leading dot is **not** valid at
all — `{ p: .price }` in a `.utlx` file is a parse error by design, and `$input.price` is the
spelling. So this is entirely the CLI's preprocessor, which is why it is `CB` and not `B`.

## Verification needed with the fix

- the 36-expression corpus as a table test, asserting the expansion *string* — not just that
  it parses — so a rule that happens to produce a working-but-different program is caught
- both halves of the `*` ambiguity in the same test (`.items.*.n` and `.a*.b`), since they
  are the pair a future change is most likely to break
- `.items.0` asserted as still invalid, because the digit guard depends on it
- the hyphen family (`.a-.b`, `.a-b`, `.my-key`) asserted unchanged, against a document that
  contains both `a-b` and `a` — so a regression returns the wrong value rather than erroring,
  and only a value assertion catches it
- the mixed `$input` case, which is a second defect in the same function
- `docs/comparison/vs-jq.md` claims "`-e` expressions … matches jq's shell scripting
  ergonomics"; that line is only honest once this is fixed
