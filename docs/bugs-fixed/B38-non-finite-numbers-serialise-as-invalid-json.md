# B38: non-finite numbers serialise as invalid JSON

> **B = core bug** (shared core and the format serialisers built on it), the family of B01–B37.
> This is **B38**.

**Status:** **PARTLY FIXED — XML done, JSON open.** Re-measured 2026-11-10; the decision turned out
to be narrower than first written (see *What measurement settled*), and the half that a standard
settles has been implemented. The JSON half still needs a decision, not an implementation.
**Priority:** Medium–High. Revised upward: the first write-up assumed the output "fails loudly", and
that is not what happens — most consumers accept it silently (below).
**Component:** the **JSON** serialiser. XML has a standards answer and needs no decision; CSV has no
type system; YAML is already correct.

> **One-line:** a payload carrying infinity or NaN serialises to JSON as the bare tokens `Infinity`
> and `NaN`, which JSON does not define.

## Evidence

```
input yaml:   x: .inf
              y: .nan
              z: -.inf
output json:  { "x": Infinity, "y": NaN, "z": -Infinity }
```

Neither token is in the JSON grammar. The RFC does not merely omit them, it **names them**:

> "Numeric values that cannot be represented in the grammar below (such as Infinity and NaN) are not
> permitted." — RFC 7159 §6, carried into RFC 8259

So this is not a gap to be filled by a reasonable extension; it is an explicit exclusion.

### The decisive measurement: UTL-X cannot read its own output

```
$ utlx transform identity.utlx nf-out.json
Error parsing input: JSON parse error at 2:8 - Unexpected character: I
```

That removes the question of whether this is a standards quibble. The product's own JSON parser
rejects the product's own JSON output, so no UTL-X pipeline can carry a non-finite value through two
hops.

### "Fails loudly" was wrong

The first version of this report argued the severity was limited because unparseable output fails at
the consumer. Measured against three common parsers:

| consumer | result |
|---|---|
| Node / V8 `JSON.parse` | **rejects** — `Unexpected token 'I'` |
| Python `json.loads` | **accepts** — non-finite literals are a documented extension, on by default |
| `jq` | **accepts** |
| UTL-X's own JSON parser | **rejects** |

So it is not a loud failure, it is **interoperability roulette**: two of the three most common tools
in a data pipeline take the value silently, and a non-finite number can travel a long way before
something refuses it. That is a stronger argument for fixing it at the producer, not a weaker one.

## What measurement settled

The original write-up framed this as one dilemma across four formats. Measuring each output format
separately shows that **three of the four have a non-negotiable answer, and only JSON needs a
policy**:

| output | today | round trip through UTL-X | verdict |
|---|---|---|---|
| **YAML** | `x: .inf`, `y: .NaN` | **number, lossless** | **already correct** — YAML has first-class non-finite syntax, nothing to decide |
| **XML** | `<x>Infinity</x>` | **string** `"Infinity"` | **wrong, and the spec says so** — see below |
| **CSV** | `Infinity` | string | acceptable — CSV has no type system, so no token is more correct than another |
| **JSON** | `Infinity` | **parse error** | **the only real decision** |

### XML is a spec answer, not a judgement call

XSD 1.0 §3.2.5 fixes the lexical space of `xs:double`: the special values are written **`INF`**,
**`-INF`** and **`NaN`** (XSD 1.1 adds `+INF`). `Infinity` is not in that lexical space, so
`<x>Infinity</x>` **fails validation against an `xs:double` element** — the same class of argument
as [B40](B40-yaml-reads-zero-padded-identifiers-as-octal.md) (JSON's number grammar) and
[B42](B42-xml-character-references-above-the-bmp-are-truncated.md) (XML 1.0 §2.2). It can therefore
be fixed independently of any profile, and without waiting for the JSON decision.

Note what that does **not** fix: `<x>INF</x>` still reads back as a *string*, because
[B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) deliberately restricted text inference to
canonical JSON number form. That asymmetry is the right trade — identifiers with leading zeros are
common in real payloads and non-finite numbers in XML text are vanishingly rare — but it should be
recorded rather than discovered.

## How a value gets there

- **YAML input.** `.inf`, `-.inf` and `.nan` are ordinary YAML floats and parse to the
  corresponding doubles. This is the easy path and needs no unusual data.
- **Arithmetic overflow.** `1e300 * 1e300` is infinity. Division by zero is caught and raised, so
  that route is already closed.
- **Text inputs.** CSV and XML accept `NaN` and `Infinity` as numeric text, because the decision
  about what counts as a number is `Double.parseDouble`'s, and it accepts them.

## Why this is not simply fixed

Three defensible answers, and they differ in kind rather than in quality:

1. **Reject** — raise on serialising a non-finite number. Fail-closed, consistent with the JSON
   canonicalisation function, which already throws for exactly these values per RFC 8785 §3.2.2.
   But it turns output that "worked" into a hard failure.
2. **Emit `null`** — valid JSON, and JSON's own convention for "no value". Silently discards the
   distinction between infinity, NaN and a genuinely absent value.
3. **Emit a string** (`"Infinity"`) — valid JSON and lossless, but changes the field's *type*, which
   breaks schema-validated consumers and is its own kind of surprise.

The right shape is almost certainly **profile-driven** — reject under a strict profile, substitute
under a lenient one — mirroring how parser strictness is already handled
([F23](../features/F23-parser-strictness-profiles.md)). That requires a serialisation profile, and
there is no shared one today: each format carries its own `SerializeOptions` with no common
strictness axis. So the work is the plumbing, and the plumbing deserves its own design rather than
being improvised inside a numeric fix.

## Recommendation

Two pieces, and they are independent. The first needs no decision and can be done at any time; the
second is the one that needs a call.

### 1. XML: emit `INF` / `-INF` / `NaN` — **DONE**

Standards-mandated, so it needed no decision and no plumbing: one branch in `formatScalarValue`.

```
before:  <x>Infinity</x>  <y>NaN</y>  <z>-Infinity</z>
after:   <x>INF</x>       <y>NaN</y>  <z>-INF</z>
```

`NaN` is unchanged — `Double.toString()` and XSD happen to agree there, which is why the revert check
below fails 4 tests and not 5.

The string-on-re-read asymmetry stands and is recorded above: `<x>INF</x>` still parses back as a
string, because B39 restricted text inference to canonical JSON number form. That is the right
trade, not an oversight.

### 1b. A second defect found in the same method: two renderers, one fixed

While making the change it turned out each of the XML and JSON serialisers has **two** number
renderers, and B37 had only fixed one of them:

| serialiser | UDM path (`serializeScalar` / `formatScalarValue`) | `RuntimeValue` path |
|---|---|---|
| JSON | B37-correct (Long round-trip) | `% 1.0 == 0.0` → `1e30` saturated to `Long.MAX_VALUE` |
| XML | B37-correct | `% 1.0 == 0.0`, and **no** non-finite handling at all |
| CSV | B37-correct | already delegated to the shared formatter — the right pattern |

B37's own note reads "this predicate ... is now shared, so the other serialisers cannot drift away
from it again". The sharing was incomplete: the `RuntimeValue` overloads were missed.

**No production caller reaches those overloads** — every call site in `:cli`, `:daemon`, `:stdlib`,
`formats/osch` and `formats/xsd` passes a `UDM` — so this was a latent trap rather than a live
defect, and it gets no bug number of its own. Both now delegate to the shared formatter, as CSV
already did. That is also why the XML fix above applies on both paths rather than one.

**The lesson worth keeping:** a "now shared" claim in a bug report is only as good as the search
behind it. Public API overloads are easy to miss, because nothing calls them.

### 2. JSON: reject

For JSON there is no representation to be correct about, so the question is which wrong thing to do.

#### What other systems do, and why they differ

This is worth setting out, because the precedents point in opposite directions and the split is not
arbitrary — it follows from what the system is *for*:

| system | behaviour | why |
|---|---|---|
| **JavaScript `JSON.stringify`** | `Infinity`, `-Infinity` and `NaN` all become **`null`** | A serialiser called from the middle of application code must never throw. Maximally lossy: the three values are indistinguishable from each other *and* from a real `null`, so the original cannot be restored. |
| **Python `json`** | emits bare `Infinity` / `NaN` — **invalid JSON** | A documented extension, on by default. This is what UTL-X does today. |
| **PostgreSQL `json`/`jsonb`** | **rejects** | Interoperability is the point of the type. |
| **Common API advice** | a **string**, by prior agreement between the parties | Lossless and valid, at the cost of the field's type. |

PostgreSQL is the closest analogue to UTL-X — a data system whose JSON representation exists to be
read by someone else — and the reasoning given there transfers almost word for word:

> "A large part of the point of the JSON datatype is to be interoperable; once you give that up you
> may as well use some not-standard-at-all representation." — Tom Lane, pgsql-hackers, 2019

> "There is not, and never has been, any claim that JSON numbers correspond to the IEEE spec."

A language runtime substitutes because it must not throw. A transformation engine is in the other
category: its whole job is to hand correct data to the next hop, and failing at the producer is the
cheapest possible place to fail. **UTL-X should reject.**

#### The options, ranked

- **Reject — the default.** The only option that does not lie. Consistent with `jcs()`, which
  already throws for exactly these values per RFC 8785 §3.2.2; with division by zero, which already
  errors; and with `ValidationRoutingPolicy`, which is fail-closed. A guard must never emit a frame
  its consumer cannot parse, and a *rejected* message is recoverable where an accepted-then-corrupted
  one is not.
- **`null` — available, not default.** What `JSON.stringify` does, so a consumer written against
  JavaScript semantics sees exactly what it expects. Its weakness is specific and severe: all three
  non-finite values collapse to the same token, and that token is also a legitimate value, so the
  consumer cannot tell infinity from NaN from absent.
- **A string (`"Infinity"`) — available, not default.** Lossless and valid, and the usual API advice.
  It changes the field's *type*, which breaks schema-validated consumers — the same failure mode as
  the XML round trip measured above, where a number silently became a string. Fine when both parties
  have agreed to it, which is exactly why it belongs behind an explicit option rather than in a
  default.

Both substitutions share one property that disqualifies them as the default: they are **silent**.
Every other defect fixed in this family — B39 identifiers, B40 octal, B42 character references, B43
whitespace — was silent corruption, and each was fixed by making UTL-X stop quietly changing values.
A silent default here would contradict the principle the rest of the work established.

#### Shape: a per-mapping option, not a cross-format profile

An earlier version of this report recommended a serialisation strictness profile. Having costed it,
that is over-building for this case. There is no shared serialisation profile today: only YAML has a
`SerializeOptions`, and every other serialiser takes loose constructor parameters, so a strictness
axis means a new core type threaded through four serialisers and five call-site families
(`TransformationService`, `UDMService`, `RestApiServer`, the stdlib write functions, the engine
strategies). That axis may be worth having one day; it should not be what blocks a five-line fix.

The escape hatch belongs in the **output header** instead:

```
output json { nonFinite: "null" }      // JSON.stringify-compatible
output json { nonFinite: "string" }    // lossless, by agreement with the consumer
```

Per-mapping is also the right granularity: the person writing the mapping knows whether a non-finite
value is expected, where a deployment-wide profile does not. The header already accepts arbitrary
options with no grammar work, so this costs one line to read plus a branch in the serialiser — and
it should be built when a real case appears, not speculatively.

### How much this will actually arise

Less than it looks, and shrinking. Of **558 conformance fixtures, 0** produce a non-finite value or
read one from YAML. More importantly, B35's exact arithmetic already removed most routes to creating
one: `+`, `-` and `*` are arbitrary-precision, so `99999999999999999999 *
99999999999999999999` is exact to 40 digits rather than overflowing. Measured, only two routes remain:

| route | status |
|---|---|
| exponent-form literal — `1e300 * 1e300` → `Infinity` | `ExactNumber.ofOrNull` has no exponent pattern, so it falls to `Double` (the comment at the fallback says so). **A gap, not a constraint** — `BigDecimal("1e300")` parses fine. |
| division — `1 / 3`, `123456789012345678 / 1000` | approximate by the documented B35 decision; the exact-division question is still open. |
| YAML `.inf` / `.nan` input | legitimate, and will always exist |

Close the literal gap and arithmetic can no longer produce a non-finite value from `+ - *` at all.
Finish exact division and it cannot arise from arithmetic at any point. The only remaining source is
then a value the author explicitly typed — which makes rejecting it *easier* to defend, not harder,
because there is no hidden arithmetic path left to surprise anyone.

Until then the JSON behaviour is unchanged and **pinned by a test** that says so explicitly, so the
eventual fix is a visible, deliberate change rather than a surprise — and so it is not rediscovered
as a regression of [B37](B37-serializers-saturate-large-doubles-to-long-max.md), which was found in
the same audit and fixed because it had one correct answer.

## Tests

- `B38XmlNonFiniteTest` (`formats/xml`), 10 tests — the three XSD forms for `Double` and `Float`;
  finite values untouched (exact `BigDecimal` scale, an 18-digit `Long`, and `1e30` **not** saturated
  to `Long.MAX_VALUE`); and the two entry points agreeing on non-finite values, on a large double and
  on an integral double.
- `B38JsonNumberRendererTest` (`formats/json`), 4 tests — neither path saturates, the paths agree
  across a spread of doubles, integral doubles still render without a decimal point, and one test
  **pins the open behaviour** (`Infinity` / `NaN` still emitted) with a comment saying it is a record
  and not an endorsement.

Revert-verified, separately for each change:

| reverted | failures |
|---|---|
| the XSD non-finite branch | 4 of 10 in `B38XmlNonFiniteTest` (`NaN` coincides, so it still passes) |
| the shared renderer, XML | 2 of 10 — exactly the two cross-path tests |
| the shared renderer, JSON | 2 of 4 — the saturation test and the agreement test |

The JSON row needed re-running in isolation: on the first attempt Gradle stopped at the XML failure,
never ran the JSON task, and left a stale passing result file to be read. A revert check that reads
a result file the run did not produce proves nothing.

## Related

- [B37](B37-serializers-saturate-large-doubles-to-long-max.md) — the other serialiser defect from the
  same audit; fixed, because there the right answer was not a judgement call.
- [F23](../features/F23-parser-strictness-profiles.md) — the profile mechanism this should reuse.
- [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) — why `INF` in XML text reads back as a
  string, and why that is the right trade.
- [B40](B40-yaml-reads-zero-padded-identifiers-as-octal.md),
  [B42](B42-xml-character-references-above-the-bmp-are-truncated.md) — the same "the standard already
  decided this" reasoning that settles the XML half here.
