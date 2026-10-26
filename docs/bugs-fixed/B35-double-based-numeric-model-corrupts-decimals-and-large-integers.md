# B35: the double-based numeric model corrupts decimals and large integers

> **B = core bug** (`:core` interpreter / parser / AST), the family of B01–B34. This is **B35**.

**Status:** **OPEN — cannot be fixed in `%utlx 1.1`.** The behaviour is identical in 1.0 and is
therefore released; correcting it changes results for existing scripts. See §Why this cannot be a 1.1
fix.
**Priority:** **High for the domain.** UTL-X transforms invoices, orders and prices. `19.99 * 100`
not equalling `1999` is wrong by any business standard, and the project already documents
double-precision as corruption worth engineering around (§The project's own position).
**Component:** `:core` — `ast_nodes.kt` `Expression.NumberLiteral(val value: Double)`, and
`interpreter.kt` `extractNumber` / `compareValues` / `valuesEqual`, all of which route through
`Double`.

> **One-line:** every numeric literal, every arithmetic operation and every comparison against a
> literal goes through IEEE-754 double, so `100.10` becomes `100.1`, `12345678901234567.89` becomes
> `12345678901234568`, and two different integers above 2⁵³ compare equal.

## Evidence (via the built CLI)

```
0.1 + 0.2 > 0.3                        → true              (exact arithmetic: false)
19.99 * 100 == 1999                    → false             (money)
100.10                                 → 100.1             (trailing zero lost)
12345678901234567.89                   → 12345678901234568 (last digits corrupted)
9007199254740993                       → 9007199254740992  (literal, at parse time)
9007199254740993 == 9007199254740992   → true              (two different integers)
9007199254740992 + 1                   → 9007199254740992
9007199254740993 - 9007199254740992    → 0
```

Identical under `%utlx 1.0` and `%utlx 1.1`, and with the engine pinned via `--language-mode 1.0`.
The `%utlx` version gates *namespaces*, not arithmetic, so there is no version-dependent numeric
behaviour.

## The project's own position

[`F10-decimal-type-mapping.md`](../features/F10-decimal-type-mapping.md) already states the case,
and the interpreter fails on F10's own two examples:

> `xs:decimal` is arbitrary-precision (like Java's `BigDecimal`). JSON Schema `"type": "number"`
> maps to IEEE 754 double-precision floating point — max ~15-17 significant digits.
>
> ```
> xs:decimal value:     12345678901234567.89
> JSON number value:    12345678901234568       ← last digits corrupted
> ```
>
> In financial contexts, trailing zeros carry meaning. `100.10` means "one hundred euros and ten
> cents" — the trailing zero confirms the precision.

F10 therefore maps `xs:decimal` to **string** in JSON Schema, calling that *"safe (preserves
precision)"*. So the project engineers around double-precision at the schema boundary while routing
every value through it in the interpreter — including straight off the payload, where
`{"total": 100.10}` is read back as `100.1`.

## Root cause

Two places, both unconditional:

```kotlin
// ast_nodes.kt — a literal is a Double before evaluation begins
data class NumberLiteral(val value: Double, val location: Location) : Expression()

// interpreter.kt — operands are narrowed to Double for arithmetic and comparison
is Number -> value.toDouble()
```

So the loss happens at **parse time** for literals, and again in every operator. The data path is
otherwise exact, which is what makes the result incoherent rather than merely imprecise:

| Path | Exact? |
|---|---|
| payload → output (pass-through) | ✅ `{"id": 9007199254740993}` round-trips |
| payload compared to payload | ✅ `$input.id == $input.other` → `false`, correct |
| `validate.oneOf` / `validate.unique` membership | ✅ since [VB03](VB03-validate-oneOf-numeric-membership-fails.md) canonicalises via `BigDecimal` |
| **numeric literal in a script** | ❌ `Double` at parse time |
| **arithmetic** (`+ - * / %`) | ❌ |
| **comparison against a literal** | ❌ |

## Why this is a bug, and not "just IEEE-754"

Three arguments, the first being the one that settles it:

1. **The project already calls it corruption.** F10 is explicit, and the interpreter reproduces
   F10's exact failing values. An implementation contradicting a documented design position is a
   defect, whatever the mechanism.
2. **UTL-X's own lineage disagrees.** In XPath 2.0+ a literal such as `19.99` is an `xs:decimal`, so
   `19.99 * 100 = 1999` is **true**. UTL-X positions itself against XSLT/XPath
   (`docs/comparison/vs-xslt.md`) and diverges from it on the arithmetic that matters most in its
   domain. "Every general-purpose language does this" explains the mechanism; it does not justify it
   for a data-mapping language.
3. **Precedent inside this repo.** [B34](B34-logical-operators-do-not-short-circuit.md) — `&&` not
   short-circuiting — was equally long-standing, equally present in 1.0, and equally
   plausible-looking, and was treated as a bug. The distinction being drawn here is cost, not
   correctness: B34's fix changed no result anyone relied on, and this one would.

**"Expensive to fix without breaking compatibility" is not the same as "not a bug."** The first
assessment of this issue conflated the two; this report corrects that.

## Why this cannot be a 1.1 fix

The behaviour is identical in `%utlx 1.0`, so it is released. Making arithmetic exact would change
results for any existing script that computes money or compares doubles — silently, and in the
"more correct" direction, which is still a behaviour change callers may depend on.

That makes it a **version-boundary fix**, not a patch. Two shapes fit:

- **a language version** — exact literals and arithmetic at `%utlx 2.0`, where the break can be
  declared; or
- **an opt-in compute profile** — the same shape as the parser strictness profiles
  ([F23](../features/F23-parser-strictness-profiles.md)) and `--language-mode`: a script or
  deployment asks for exact semantics and 1.0 scripts keep theirs. This is the less disruptive route
  and reuses machinery that already exists.

Either way the fix spans the parser, the AST, every arithmetic operator, comparison, and the format
serialisers — it is a numeric-model change, not a localised one. Note in particular that an
**exact-comparison-only** fix would be worse than nothing: with literals still `Double`, an author
writing `$input.id == 9007199254740993` would get a literal of `…992`, an exact comparison, and
`false` — the right number typed, the wrong answer returned, failing in the opposite direction.

## The one part that *is* a 1.1 issue

[VB03](VB03-validate-oneOf-numeric-membership-fails.md) made `validate.oneOf` and `validate.unique`
compare exactly, this week. Comparison operators did not change. So within `%utlx 1.1` the same
logical check now gives **opposite verdicts** depending on how it is written:

```utlx
oneOf: { field: "id", values: [9007199254740992] }   → valid:false   (exact)
check: (p) => p.id == 9007199254740992               → valid:true    (double)
```

Before VB03 both were uniformly lossy; now they disagree with each other. For the guard this matters
directly, because the JVM `ValidationResult` is the **oracle** for the FPGA and an oracle that
contradicts itself is unusable.

**Proposed resolution — accept the asymmetry as intended, and document it:** declarative rule kinds
([F25](../features/F25-declarative-validation-rule-kinds.md)) compare **exactly** because they are
policy; `check` lambdas compare as doubles because they are expressions. That keeps VB03's fix, and
it gives the guard a crisp rule it already leans towards — **compile declarative kinds, not
lambdas**. The alternatives are reverting VB03 (restoring a real bug) or leaving the divergence
undocumented.

## Guard implications

Until the numeric model changes, the guard's safety rests on constraining the data rather than the
language — which is what its own design already recommends
(`utlx-guard/docs/proposals/utlx-guard-validate-subset-and-compiler.md` §5: *"carry money and
measures as scaled integers; compile only integer checks; confirm UTL-X number semantics"*). This
report is that confirmation, with one addition the guard design does not yet account for:

- **"Integer checks only" is not sufficient on its own.** Integers above 2⁵³ are already compared
  incorrectly, with no floats involved. The form-class must therefore bound numeric fields to **≤ 2⁵³**,
  and the subset checker should reject comparisons against literals above it.
- The BINF envelope's `sequence` field is **8 bytes** (64-bit, monotonic, anti-replay). If sequence
  checks ever move into UTL-X rules rather than staying in the verifier, this stops being theoretical.

## Tests

None added. A characterisation test pinning the current values would lock in the defect; the useful
moment to add tests is with the version-gated fix, where they assert the new semantics and the
old ones move behind the profile. The conformance suite has no fixture asserting a lossy numeric
result today, so nothing currently depends on the behaviour.

## Related

- [F10](../features/F10-decimal-type-mapping.md) — the project's own statement of the problem, at the
  schema boundary.
- [VB03](VB03-validate-oneOf-numeric-membership-fails.md) — made `oneOf`/`unique` exact, creating the
  1.1 asymmetry above.
- [B34](B34-logical-operators-do-not-short-circuit.md) — the precedent for treating a long-standing,
  plausible-looking core behaviour as a bug.
- [F23](../features/F23-parser-strictness-profiles.md) — the profile mechanism an opt-in exact mode
  could reuse.
