# B35: the double-based numeric model corrupts decimals and large integers

> **B = core bug** (`:core` interpreter / parser / AST), the family of B01–B34. This is **B35**.

**Status:** **FIXED — data preservation and comparison.** Numbers are now preserved as written by
every data-format parser and serialiser, and comparison is exact. **Arithmetic is still
double-based** and is tracked below as the one remaining stage, because exact computation needs a
division scale and rounding mode chosen at the language level.
**Priority:** High for the domain. UTL-X transforms invoices, orders and prices.
**Component:** `:core` (parser, AST, interpreter comparison), the five data-format modules, and the
UDM scalar accessors.

> **One-line:** every numeric literal, every arithmetic operation and every comparison went through
> IEEE-754 double, so `100.10` became `100.1`, `12345678901234567.89` became `12345678901234568`,
> and two different integers above 2⁵³ compared equal.

## What changed

| | before | after |
|---|---|---|
| `100.10` round-trip | `100.1` | **`100.10`** |
| `12345678901234567.89` round-trip | `12345678901234568` | **`12345678901234567.89`** |
| `9007199254740993` through CSV or XML | `…992` | **`…993`** |
| `9007199254740993 == 9007199254740992` | `true` | **`false`** |
| `$input.id == 9007199254740993` (id = …993) | `false` | **`true`** |
| `$input.id > 9007199254740992` | `false` | **`true`** |
| `100.10 == 100.1`, `5 == 5.0` | `true` | `true` (preserved) |
| `19.99 * 100 == 1999` | `false` | `false` — **still open** |
| `0.1 + 0.2 > 0.3` | `true` | `true` — **still open** |

## The project's own position

[`F10-decimal-type-mapping.md`](../features/F10-decimal-type-mapping.md) already stated the case,
and the interpreter failed on F10's own two examples:

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
precision)"*. So the project engineered around double-precision at the schema boundary while routing
every value through it in the parsers. Both of F10's examples now round-trip intact, and the
schema-boundary workaround is no longer compensating for a defect underneath it.

## Root cause

Three independent places, all unconditional:

1. **Each format parser chose its own numeric type.** JSON tried `Long` first and fell back to
   `Double`; CSV and XML routed *every* numeric value through `Double`; YAML delegated to its
   library, which returns `Long` for integers and `Double` for decimals. So **exactness was
   format-dependent** — the same integer survived a JSON or YAML round-trip and was corrupted by a
   CSV or XML one. For a *universal* data model, a value changing according to which parser read it
   is the failure the model exists to prevent, and a stronger objection than imprecision.

2. **Each serialiser narrowed every number back to `Double`** in order to test whether it was
   integral ("print `1`, not `1.0`"), undoing on the way out whatever a parser had preserved.

3. **Literals were `Double` from parse time, and comparison narrowed both operands to `Double`.**
   A literal above 2⁵³ *became* a different number while being read, and comparison then could not
   tell two neighbouring integers apart.

The data path was otherwise exact, which is what made the result incoherent rather than merely
imprecise: a payload integer compared to another payload integer was correct, while the same integer
compared to the literal naming it was not.

### UDM was never the problem

`UDM.Scalar` holds `Any?` and its factory is `fun number(n: Number)`. It accepts **any** `Number`,
`BigDecimal` included, so the model was designed for exact decimals from the start. The precision
was discarded by the parsers and again by the serialisers.

## The fix

Two shared definitions in `:core`, and small call-site changes everywhere else. `formats → :core`
and `:stdlib → :core` are the legal directions, so both live in `:core` and are referenced outward —
the same arrangement used for the shared validation rule kinds.

**`core.format.ExactNumber`** — the one place a written numeric form is turned into a value. The rule
is **exactness follows the written form**:

| lexeme | result | why |
|---|---|---|
| `9007199254740993` | `Long` | integral, fits |
| 400 digits | `BigInteger` | integral, does not fit (was `Infinity`) |
| `100.10` | `BigDecimal` | plain decimal — scale is part of the value |
| `1e10`, `NaN`, `0x1p3` | `Double` | exponent and special forms keep the previous path |

Two properties make this safe to drop into parsers that use it to decide whether text *is* a number:

- **Acceptance parity.** It returns null for exactly the inputs `toDoubleOrNull()` rejects, so no
  text that was a number stops being one and none that was a string becomes one. Only the
  representation improves.
- **Plain rendering.** `BigDecimal.toString()` switches to scientific notation below an adjusted
  exponent of -6, so `0.0000001` would come back as `1E-7` — exact, but not what was written.
  Rendering goes through `toPlainString()`.

Exponent forms stay on `Double` deliberately. With plain rendering the output is identical either
way, so the only thing promoting them would buy is round-trip type symmetry — against which
`1e1000000` is nine bytes that would materialise a million-digit string. For a parser treated as the
primary attack surface, that amplification is a worse trade than the asymmetry.

**`core.udm.ScalarCanonical`** — the one definition of what makes two numbers the same: an exact
`BigDecimal` with trailing zeros stripped, non-finite doubles left alone, non-numbers untouched
(the string `"50"` is not the number 50). Comparison canonicalises instead of narrowing.

This half is **not optional**. Preserving what the parser was given means the same quantity now
arrives in different boxes — `100.10` at scale 2, `100.1` at scale 1, `5` as a `Long`, `5.0` as a
`BigDecimal`. Raw boxed equality calls all of those different, so preservation alone would have
traded "two big integers compare equal" for "`5 == 5.0` is false" — a worse defect than the original.

**Literals and comparison had to land together.** `Expression.NumberLiteral` carries the exact value
of its written form alongside the `Double` reading, and `RuntimeValue.NumberValue` carries it
through evaluation. Exact comparison *without* exact literals would have been worse than no fix at
all: an author writing `$input.id == 9007199254740993` would get a literal of `…992`, an exact
comparison against it, and `false` — the right number typed, the wrong answer returned, failing in
the opposite direction.

Negation is exact too. A negative literal is lexed as a positive literal under a unary minus, so
without that every negative literal would drop back to `Double`.

### Design notes worth keeping

- `RuntimeValue.NumberValue`'s exact field is **not** a constructor property. It is a data class
  built in dozens of places; keeping the field out of the generated `equals`/`hashCode`/`copy`/
  `toString` means adding it changed nothing for code that already existed.
- The serialisers keep their **own** `Double` integrality predicates, which differ between formats
  and have fixtures behind them. Only a branch for the exact types was added in front. Unifying
  those predicates would be an improvement — JSON and CSV currently saturate `1e30` to
  `9223372036854775807` on output, which is simply wrong — but it is a separate defect, not this one.
- Numeric type classification in the type checker dispatched on the boxed primitives
  (`is Double, is Float`), so an exact type fell through to STRING. It now dispatches on `Number`.
- The UDM scalar accessors needed no change: `asNumber()` and `isNumber()` already dispatch on
  `is Number`, so they kept working when the backing type became exact. Only `asString()` was
  adjusted, for the plain-rendering reason above.

## Behaviour changes callers see

This is a **released-behaviour change**, which is why the report previously argued it needed a
version boundary. What a caller actually observes:

- **Decimal output preserves what was written.** `100.10` serialises as `100.10`, not `100.1`.
  Output bytes change for any mapping that carries a decimal with trailing zeros.
- **Integers that were silently approximated are now correct.** This changes results only where the
  previous result was wrong.
- **Equality and ordering above 2⁵³ are now correct**, which flips comparisons that previously
  returned a wrong answer.

Eight conformance fixtures were updated. All eight were money: inputs written as `150.00`,
`2500.00`, `25.50`, `22.00` whose expectations had the cents dropped.

**Those fixtures were not wrong against any specification — they agreed with the implementation of
the day, and seven of the eight were hand-authored** (one is explicitly auto-captured). This is
therefore a deliberate behaviour change that the fixtures followed, not a set of independent errors
being corrected. An earlier revision of this section claimed otherwise; the record is corrected here.

Two observations do bear on whether the change is right:

- The old rule was not a canonical numeric form. It was "render the `Double`; print it as an integer
  if it happens to be integral, otherwise `Double.toString()`". That drops trailing zeros uniformly —
  `2500.00` to `2500` and `89.50` to `89.5` alike — as a side effect of the `Double` path, not as a
  stated output policy. (An earlier revision of this report said `lookupBy_basic` *preserved* `89.50`
  while collapsing `150.00`, and cited that as evidence the old rule was arbitrary. That was wrong:
  the old renderer produced `89.5`, and the fixture only appeared to preserve the zero because the
  runner compares parsed JSON values, where `89.5` and `89.50` are the same `float`. Only the
  integer-versus-decimal distinction is visible to a JSON fixture, which is why exactly those lines
  moved.)
- **The project already has an explicit canonicaliser.** `jcs()` implements RFC 8785, which mandates
  stripping trailing zeros, and it is unaffected by this change because it narrows to `Double` before
  formatting — correctly, since RFC 8785 is defined on the IEEE-754 value rather than on the text.
  Verified: `{"b":1.50,"a":2500.00,"c":0.0000001}` still canonicalises to
  `{"a":2500,"b":1.5,"c":1e-7}`. That separation is the argument for preservation in plain output:
  if plain serialisation canonicalised implicitly, the number-normalisation half of `jcs()` would be
  redundant, and a caller who wanted the written form would have no way to get it.

Twelve unit tests that pinned the `Double`-ness of decimals were likewise updated, and three of
those were named for it (`…as Double`, `…stays Double`, `…falls back to Double`).

Unrelated finding, recorded while auditing those fixtures: `csv_regional_usa_output`,
`…_europe_output` and `…_swiss_output` set no regional number format — only a delimiter — so despite
their names and notes they never exercised the regional path, and the europe and swiss expectations
are byte-identical. The regional path itself is covered by the five `csv_output_regionalformat_*`
and `csv_render_consistency` fixtures, which are unaffected.

## Resolved: the declarative-versus-lambda divergence

The membership fix for `validate.oneOf` / `validate.unique` had made those compare exactly while the
comparison operators still narrowed to `Double`, so the same logical check gave **opposite verdicts**
depending on how it was written:

```utlx
oneOf: { field: "id", values: [9007199254740992] }   → valid:false   (exact)
check: (p) => p.id == 9007199254740992               → valid:true    (double)
```

An earlier revision of this report proposed accepting that asymmetry and documenting it as
intentional — declarative kinds are policy, lambdas are expressions. **That is no longer needed.**
The validation functions and the operators now share one canonicalisation, so both forms agree.
This matters beyond tidiness: where a validation result is consumed as an oracle for downstream
enforcement, an oracle holding two notions of numeric equality is not usable.

## Still open: exact arithmetic

`19.99 * 100 == 1999` is still false and `0.1 + 0.2 > 0.3` is still true. Operands are narrowed to
`Double` for `+ - * / %`, and the regression tests pin that so landing it later is a deliberate act
rather than a surprise.

Three things gate it, only the first of which is code:

1. **Division semantics.** `BigDecimal` division requires an explicit scale and `RoundingMode`:
   `1/3` has no exact representation. That is a *language* decision, not an implementation detail.
   XPath's `xs:decimal` rules are the precedent to copy — at least 18 digits of precision,
   implementation-defined beyond — but they have to be chosen and written down.
2. **Performance.** `BigDecimal` arithmetic is roughly an order of magnitude slower than `double`.
   On a mapping hot path that is material, and it is the strongest argument for making exact
   computation an **opt-in compute profile** rather than a flat switch. The profile mechanism
   already exists for parser strictness ([F23](../features/F23-parser-strictness-profiles.md)).
   Note that exactness could be made to cost nothing when it is not needed: falling back to `Double`
   whenever either operand is already a `Double` keeps float-style scripts on the fast path.
3. **Scope of the result change.** Every computed money total would gain the scale of its inputs
   (`2500` becoming `2500.00`), so this changes more output than the preservation work did.

The three stages were independently valuable and in increasing order of cost and risk:
**preserve the data, judge it correctly, compute it correctly.** The first two are done.

## Guard implications

The guard's own design recommends constraining the data rather than the language — *"carry money and
measures as scaled integers; compile only integer checks; confirm UTL-X number semantics"*. This
report was that confirmation. What changes now:

- **Comparison of integers above 2⁵³ is correct**, so the form-class no longer has to bound numeric
  fields to ≤ 2⁵³ for *comparison* to be trustworthy, and the subset checker no longer needs to
  reject comparisons against literals above it.
- **Arithmetic above 2⁵³ is still not safe**, so a rule that *computes* rather than compares still
  needs the bound. "Integer checks only" remains the right constraint; it is now a constraint on
  arithmetic rather than on comparison.
- A pass-through that should verify rather than rewrite now gets what it needs: the output carries
  the input's numbers as written, not a re-rendered approximation.
- The envelope's 8-byte monotonic sequence field can be compared in a rule correctly. Computing on
  it still cannot.

## Tests

- `ExactNumberTest` — the written-form rule, acceptance parity as a property over numeric and
  non-numeric samples, plain rendering, and parse-then-render round-tripping.
- `B35ExactNumericModelTest` — comparison and ordering above 2⁵³, exact literals including negation,
  scale-insensitive equality in both directions, booleans and numeric strings staying out of the
  numeric path, and the remaining arithmetic limitation pinned explicitly.
- Four conformance fixtures under `language/numeric`, written at `%utlx 1.0` because this is core
  language behaviour and so that they port unchanged: int64 through CSV, int64 through XML, exact
  comparison above 2⁵³, and decimal trailing zeros. The last one outputs **CSV on purpose** — the
  JSON comparison parses both sides to a float, where `100.10` and `100.1` are indistinguishable, so
  a JSON fixture could not have detected the defect it is there to prevent.

Verified by reverting each half independently and confirming three disjoint failure sets: removing
exact literals fails the literal and ordering tests; restoring `Double` narrowing fails the
above-2⁵³ tests; using raw boxed equality fails the scale and representation tests — which is
precisely the regression that preserving precision alone would have caused.

Full JVM test suite green; conformance **554/554 on both JVM and GraalVM native**. The native run
matters here because the fix touches every serialiser and introduces a subclass of the YAML
library's constructor, and an undeclarable reflective boundary has broken this project's native
image before (B19/B25).

## Porting to another line

The change touches 25 existing files (+257/-133) and adds 8 — two `:core` sources, two test
classes, four conformance fixtures. Nothing in it depends on version-gated machinery, so it applies
to any line carrying the same components:

| group | what |
|---|---|
| new, `:core` | `ExactNumber`, `ScalarCanonical` and their tests |
| format parsers | one call each in JSON, CSV, XML; a constructor subclass in YAML. OData inherits the JSON fix — it delegates to the JSON parser |
| format serialisers | one branch each in JSON, CSV, XML. YAML needed none |
| `:core` | exact field on the number literal node and on the runtime number value; the parser passing the lexeme's exact value; canonical `valuesEqual` / `compareValues`; exact unary minus; plain rendering in the scalar `asString()` |
| elsewhere | numeric classification in the type checker; the validation canonicaliser delegating to the shared one |

Schema-describing formats (avro, protobuf, osch, jsch, tsch, xsd) are **out of scope**: their
numeric parsing is on schema fields such as `maxLength`, not on payload data.

Two follow-ups are deliberately not included: exact arithmetic, above, and the pattern-match literal,
which still holds a `Double`. The latter affects `match` against a literal above 2⁵³ and is the one
remaining place a literal is read without its exact form.

## Related

- [F10](../features/F10-decimal-type-mapping.md) — the project's own statement of the problem, at the
  schema boundary; both of its failing examples are now correct.
- [VB03](VB03-validate-oneOf-numeric-membership-fails.md) — made `oneOf`/`unique` exact, creating the
  divergence that sharing the canonicaliser has now removed.
- [B34](B34-logical-operators-do-not-short-circuit.md) — the precedent for treating a long-standing,
  plausible-looking core behaviour as a bug rather than as inherited semantics.
- [F23](../features/F23-parser-strictness-profiles.md) — the profile mechanism an opt-in exact
  compute mode would reuse.
