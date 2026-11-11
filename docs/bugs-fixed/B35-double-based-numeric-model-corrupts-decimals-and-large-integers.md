# B35: the double-based numeric model corrupts decimals and large integers

> **B = core bug** (`:core` interpreter / parser / AST), the family of B01–B34. This is **B35**.

**Status:** **FIXED.** Numbers are preserved as written by every data-format parser and serialiser,
comparison is exact, and `+`, `-` and `*` are exact. **`/` and `%` remain approximate by decision,
not by omission** — exact division requires a digit count and a rounding mode that are prescribed by
law in some invoicing regimes, which makes them a business decision; see §Why division is
deliberately excluded.
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
| `19.99 * 100 == 1999` | `false` | **`true`** |
| `0.1 + 0.2 > 0.3` | `true` | **`false`** |
| `9007199254740993 - 9007199254740992` | `0` | **`1`** |
| `"total: " + 1` | `"total: 1.0"` | **`"total: 1"`** |
| `10 / 3` | `3.3333333333333335` | unchanged — **approximate by decision** |

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

## Exact arithmetic — `+`, `-`, `*`

Addition, subtraction and multiplication are now exact. This is the half of B35 that a customer
notices, and it is why the report was filed.

| | before | after |
|---|---|---|
| `19.99 * 100 == 1999` | `false` | **`true`** |
| `0.1 + 0.2 > 0.3` | `true` | **`false`** |
| `19.99 * 3` | `59.97000000000001` | **`59.97`** |
| `10.00 - 9.99` | `0.009999999999999787` | **`0.01`** |
| `9007199254740993 - 9007199254740992` | `0` | **`1`** |
| `10 / 3` | `3.3333333333333335` | unchanged — **still approximate** |

### Why division is deliberately excluded

Addition, subtraction and multiplication of two decimals **always have an exact decimal answer**.
Nothing is chosen, nothing is rounded, no information is discarded. `BigDecimal` computes them
directly and the result is simply correct.

Division is the one operator with no such guarantee. `10 / 3` is 3.3333… with no terminating decimal
form, so an exact division cannot proceed until it is told **how many digits to keep** and **which
way to round the last one**. `BigDecimal` refuses to guess and throws rather than inventing an
answer.

Those two settings are not an implementation detail. In invoicing and tax the rounding rule is
sometimes **prescribed by law** and varies by jurisdiction, which makes it a business decision rather
than an engineering one. Picking a default quietly inside a bug fix would have buried a commercial
decision in a commit.

So `/` and `%` keep the behaviour they have always had, and the author-facing rule is one sentence:

> **Division returns you to approximate arithmetic.**

An exact total that is then divided yields an ordinary double — the same answer as before this
change, so nothing regresses; it simply stops being exact from that point on. When the rounding
question is answered, the change is small and isolated: one operator, in one shared object. The
recommendation to whoever owns it would be XPath's `xs:decimal` precedent — `DECIMAL128` (34
significant digits) with `HALF_EVEN` — because it is a written-down standard rather than an invented
rule, and because `HALF_EVEN` does not drift upward across a long column of figures the way
school-style "round .5 up" does.

### When exactness applies, and why that makes it free

Only when **both** operands already carry an exact value — a number that came out of a payload or
was written as a literal. If either side is a `Double` (an exponent-form literal, or the result of an
earlier division) there is nothing exact to preserve and the operation stays on the `Double` path.
Promoting a `Double` to `BigDecimal` would be worse than useless: it would manufacture the 55-digit
value a double actually holds and present it as though the author had written it.

That rule was expected to be the mitigation for `BigDecimal`'s cost, which is roughly an order of
magnitude per operation. **Measured, the cost does not appear at all.** Summing 200,000
price×quantity pairs:

```
exact path (both operands exact)     0.49s, 0.63s      total 11994000.00
double path (one operand a Double)   0.52s, 0.52s      total 1.199400000002839E7
```

Parsing and serialising dominate so completely that the arithmetic is not measurable, which removes
the main argument for making exactness an opt-in compute profile. The second row is also worth
reading as a result in its own right: summing 200,000 money values as doubles drifts by 2.8e-6 and
renders in exponent notation.

### Scale, and the one thing to know about it

Exactness means the result's scale **follows from the inputs** rather than being chosen:

```
1.50 + 2.50   =  4.00      adding keeps the larger scale
19.99 * 1.5   =  29.985    multiplying ADDS the scales
100 * 0.08    =  8.00
```

This is the arithmetically correct answer, and it is why a computed money total now carries its
inputs' cents instead of being rounded to look tidy.

The consequence to be aware of: **a computed value that used to serialise as a JSON integer may now
serialise as a JSON decimal.** `100 * 0.08` was `8` and is now `8.00`. A consumer validating that
field against `"type": "integer"` will need updating. This is the single most likely source of
friction from this change.

It also means repeated multiplication grows the number of decimal places linearly — 200 chained
multiplications of a scale-2 value produce a 400-digit number. Measured cost stays modest (20,000
chained multiplications take 1.9s), and output size grows linearly with input size rather than
explosively, so there is **no cap**: capping would require either a rounding decision, which this
work deliberately avoids, or a silent behaviour cliff. Where a presentation value is wanted, round
explicitly — `round(x, 2)` and `roundToCents(x)` both exist and both work on exact values.

### Why `stripTrailingZeros` was considered and rejected

Stripping trailing zeros from arithmetic results would have turned `8.00` back into `8` and reduced
fixture churn from six files to one. It was rejected on consistency grounds: Stage 0 deliberately
preserves a *parsed* `8.00`, so stripping a *computed* `8.00` would leave the language with two
contradictory rules for the same value depending on where it came from. One rule — report what you
computed — is worth more than the saved churn.

> **Correction (2026-11-11).** This section previously ended: *"It is also what DataWeave and XPath
> `xs:decimal` arithmetic do, so the behaviour will not surprise anyone arriving from either."*
> **That claim was wrong on both counts**, and was never verified. It is corrected below. The
> *decision* stands — the consistency argument above is the real reason — but it is a deliberate
> departure from both products, not an alignment with them, and anyone arriving from either **will**
> be surprised.

### Where this sits relative to other products — verified

| | trailing zeros on a decimal | so `1.50` becomes |
|---|---|---|
| **UTL-X** (this decision) | **preserved** | `1.50` |
| XSD / XPath canonical `xs:decimal` | **prohibited** in canonical form, and explicitly *"not considered significant"* | `1.5` |
| **DataWeave ≤ 2.4** (Mule ≤ 4.4) | preserved | `1.50` |
| **DataWeave ≥ 2.5** (Mule ≥ 4.5) | **stripped** | `1.5` |
| JSON (RFC 8259) | no decimal type; no rule either way | `1.5` for most consumers, which discard the distinction on parse |

Three things follow, and they are worth keeping because they bear on whether this decision should
eventually become configurable:

1. **XML: valid but not canonical.** `1.50` *is* in the lexical space of `xs:decimal`, so it
   validates and nothing rejects it. But XSD's canonical form prohibits the trailing zero and the
   spec states outright that such zeros are **not significant**, so a canonicalising toolchain —
   `fn:string(xs:decimal("1.50"))` in XPath/XSLT — renders `1.5`. UTL-X's position is that a
   transformation is a *pipe* (the same principle as B39, B40, B42 and B43: do not silently alter
   what the sender wrote), whereas XSD models a *value*. Both are coherent; they are not the same.

2. **JSON: the preserved scale is largely invisible.** `JSON.parse('{"a":1.50}')` gives `1.5` in
   JavaScript, and Python and Go behave the same. The scale survives only for a consumer that reads
   the text or uses a decimal-aware parser. We violate no expectation — JSON has none — but we also
   deliver little to a typical consumer.

3. **DataWeave changed its mind, and its users pushed back.** Mule 4.5 began stripping trailing
   zeros; MuleSoft documents it as expected behaviour with a workaround, there is a standing customer
   request to restore the old behaviour, and the escape hatch shipped is a runtime flag
   (`-Dcom.mulesoft.dw.stripTrailingZeroes=false`, or pinning `minMuleVersion` to `4.4.0`).

   **That is the most useful datum here.** A comparable product tried both postures and found that
   neither satisfies everyone — so it is evidence that this will eventually need to be configurable
   in UTL-X too, rather than a question of taste. It also suggests the escape hatch belongs at the
   *output* boundary (presentation) rather than in arithmetic, since the value is identical either
   way. See [B46](B46-product-scale-reports-places-no-operand-had.md) for the sequencing argument and
   the implementation hazard that has to be solved first.

   Note separately that DataWeave does **not** change its default arithmetic to get controlled
   precision — it exposes explicit functions (`dw::util::Math::decimalMultiply` with an
   `OperationContext` carrying precision and rounding mode). That is a model worth copying for the
   still-open division decision: an explicit, opt-in rounding call rather than a silent global rule.

### Both strategies, one definition

`ExactArithmetic` lives in `:core` and the interpreter and the compiled path both call it, with the
`Double` result passed in as the fallback. That shape matters: the operand coercion, the error
messages and the string-concatenation branch of `+` are all untouched, so the only thing that
changed is which of two already-computed answers is returned.

Keeping it in one place is the direct lesson of
[EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md) — two strategies each holding
their own copy of a semantic rule drift apart, and the drift is invisible because the conformance
suite exercises only the interpreted path.

### Related fix: numbers inside strings

`"total: " + 1` produced `"total: 1.0"`, because the only value available at concatenation was the
`Double`. The literal's exact form says the author wrote `1`, so it now renders as `"total: 1"`.
This also corrected a fixture that expected a currency string of `"$108.0"` — one decimal place on a
money value.

### Fixtures updated

Six, all the same cause: a computed decimal now carries scale, so a JSON integer became a JSON
decimal. Two of the six were **recording the defect** rather than asserting intent:

- `ternary_operator` expected `93.50000000000001` for `85 * 1.1` — a pure floating-point artefact.
- `function_definition_basic` expected `"$108.0"` from a function named `FormatCurrency`.

The other four (`let_chained_dependency`, `f01_schema_with_policy`, `valid_pascalcase_function`,
`02_xml_json_to_json`) changed from `8`/`121`/`10` to `8.00`/`121.00`/`10.00`.

Worth recording why those four passed before: the old double arithmetic was correct **only when the
rounding error happened to vanish**.

```
100 * 0.08   double 8.0                 printed 8      exact 8.00
100 * 1.21   double 121.0               printed 121    exact 121.00
100 * 0.21   double 21.0                printed 21     exact 21.00
 85 * 1.1    double 93.50000000000001   printed as-is  exact 93.5
```

Four instances of the same expression form; three landed exactly on the integer by luck and one did
not. That is the clearest available argument that the old behaviour was an accident rather than a
design: it worked whenever the error cancelled and produced visible garbage when it did not.

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

- `ExactArithmeticTest` — the arithmetic contract: exact when both operands are exact, **fall back
  rather than fabricate** when either is a `Double`, and scale derived rather than chosen. One test
  asserts by reflection that **no divide method exists**, so adding exact division cannot happen
  without someone first recording the rounding decision.
- `ScalarCanonicalTest` — the canonicalisation contract directly: representation must not matter
  (scale, integral-vs-decimal, exponent-vs-plain, signed zero), type must still matter (a numeric
  string is not a number, a boolean is not 1), and non-finite doubles keep their existing semantics
  rather than being redefined.
- `B35CompiledExactNumericTest` (`:engine`) — the cross-strategy invariant: literals and comparison
  must give the same answer compiled as interpreted. Drives `ASTCompiler` directly and asserts
  `compile(...) != null` first, because `CompiledStrategy` silently falls back to the interpreter —
  a test routed through it could pass while measuring the wrong path.
- `B35CsvExactNumberTest`, `B35XmlExactNumberTest` — parser-level exactness for the two formats that
  had no integer branch at all, each also asserting that **acceptance is unchanged**: text that was
  a string stays a string.
- `B35YamlExactScalarTest` — YAML is the one format whose scalars a third-party library constructs,
  so its fix is a subclass rather than a call. The tests that matter most there are the *delegation*
  ones: `.inf`, `-.inf`, `.nan`, underscore grouping and YAML 1.1 sexagesimals must still go to
  SnakeYAML's own construct. An edit that replaced the float construct outright instead of
  delegating would otherwise break those silently.

Verified by reverting each half independently and confirming three disjoint failure sets: removing
exact literals fails the literal and ordering tests; restoring `Double` narrowing fails the
above-2⁵³ tests; using raw boxed equality fails the scale and representation tests — which is
precisely the regression that preserving precision alone would have caused.

Full JVM test suite green; conformance **556/556** through the Python runner against both the JVM
jar and the GraalVM native binary. Note that figure describes the **interpreted** path: the
conformance runner drives the CLI, which interprets, so the compiled strategy's coverage is the
`:engine` suite and the parity tests named above — the gap EB03 documents. The native run
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
| `:core` | exact field on the number literal node and on the runtime number value; the parser passing the lexeme's exact value; canonical `valuesEqual` / `compareValues`; exact unary minus; plain rendering in the scalar `asString()` and in string concatenation |
| `:core`, `:engine` | `ExactArithmetic` plus one call per operator in the interpreter and in `RuntimeOps`; exact literal emission in `ASTCompiler` (a `BigDecimal` constant has to be rebuilt from its string form in bytecode) |
| elsewhere | numeric classification in the type checker; the validation canonicaliser delegating to the shared one |

Schema-describing formats (avro, protobuf, osch, jsch, tsch, xsd) are **out of scope**: their
numeric parsing is on schema fields such as `maxLength`, not on payload data.

One thing is deliberately not included: **exact division**, and the reason is a decision rather
than an omission — see §Why division is deliberately excluded.

Everything else found while auditing coverage is in. A first pass had missed three places where a
number was still narrowed, none of which the original tests would have caught:

- **Emitting a literal.** `RuntimeValue.toUDM()` used the `Double` field, so comparison was exact
  while *constructing* a value was not — `{ id: 9007199254740993 }` still serialised as `…992` and
  `{ total: 100.10 }` as `100.1`. Preserving an incoming number but corrupting one the mapping writes
  is arguably the worse half for a guard, which constructs its output frame.
- **The COMPILED strategy's literals.** `ASTCompiler` emitted `Double.valueOf` for every numeric
  literal. COMPILED is the engine's **default**, so after the interpreter was fixed the default path
  was still lossy and the two strategies disagreed on the value of the same script. Exact types are
  now reconstructed from their string form in bytecode, which is also the only way to place a
  `BigDecimal` constant.
- **The COMPILED strategy's comparison.** `RuntimeOps.compareValues` narrowed to `Double`, so
  equality above 2⁵³ was strategy-dependent. It now shares `ScalarCanonical`.
- **Pattern literals.** `match` compared the `Double` reading of each arm, so an arm written
  `9007199254740993` never matched that value — and before the comparison fix it matched the
  *neighbouring* arm instead. Pattern literals now carry the exact form too.

A strategy-dependent number is worse than a uniformly lossy one, so the compiled path is covered by
its own parity tests rather than by inference from the interpreter's.

## Adjacent issues found while auditing, deliberately not changed

Recorded so they are not rediscovered as regressions of this fix.

- **The compiled path coerced strings and booleans in comparison; the interpreter did not** —
  `"5" == 5` was true compiled and false interpreted. Type confusion rather than imprecision, so it
  was deliberately not folded into a numeric fix. → **[EB03](EB03-compiled-comparison-coerces-types-so-strategies-disagree.md), fixed.**
  Measurement found seven diverging comparisons, not the one noted here.
- **Three serialisers saturated a large `Double` on output** — `1e30` and `1e300` both printed as
  `9223372036854775807`. YAML was affected too, which this note originally missed; XML was already
  correct. → **[B37](B37-serializers-saturate-large-doubles-to-long-max.md), fixed.**
- **Non-finite numbers serialise as invalid JSON** — `.inf` emits the bare token `Infinity`. No
  single correct answer, and the fix wants a serialisation profile that does not exist yet.
  → **[B38](B38-non-finite-numbers-serialise-as-invalid-json.md), open.**
- **YAML yields `Integer` for a small integer where JSON yields `Long`.** The formats differ in
  *type* while agreeing in *value*. Harmless now that comparison canonicalises, and pinned by a test
  so it stays a known difference rather than a surprise.
- **Three fixtures named `csv_regional_*_output` set no regional format**, only a delimiter, so they
  never exercised the regional path, and the europe and swiss expectations are byte-identical. The
  regional path itself is covered by the five `csv_output_regionalformat_*` fixtures and
  `csv_render_consistency`.

## Related

- [F10](../features/F10-decimal-type-mapping.md) — the project's own statement of the problem, at the
  schema boundary; both of its failing examples are now correct.
- [VB03](VB03-validate-oneOf-numeric-membership-fails.md) — made `oneOf`/`unique` exact, creating the
  divergence that sharing the canonicaliser has now removed.
- [B34](B34-logical-operators-do-not-short-circuit.md) — the precedent for treating a long-standing,
  plausible-looking core behaviour as a bug rather than as inherited semantics.
- [F23](../features/F23-parser-strictness-profiles.md) — the profile mechanism an opt-in exact
  compute mode would reuse.
