# EB08: compiled object literals drop XML attributes

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB07. This is **EB08**.

**Status:** **FIXED.**
**Priority:** High. Wrong output, silently, for an everyday XML mapping — on both compiled
strategies, one of which is the engine's default.
**Component:** `:engine` — `ASTCompiler.compileObjectLiteral`, `RuntimeOps.buildObject`.

> **One-line:** `Property.isAttribute` was ignored, so `@number:` became a child element instead of
> an attribute.

```
TEMPLATE   <Invoice number="INV-2025"><Status>ok</Status></Invoice>
COMPILED   <Invoice><number>INV-2025</number><Status>ok</Status></Invoice>
```

## Root cause

The parser marks `@key:` with `isAttribute` on the `Property` node, and the interpreter uses it to
split the object:

```kotlin
if (prop.isAttribute) attributes[key] = attrValue else properties[key] = value
…
if (attributes.isNotEmpty()) UDM.Object(udmProperties, attributes) else ObjectValue(properties)
```

`compileObjectLiteral` filtered on `key != null && !isSpread` and passed everything to
`RuntimeOps.buildObject(keys, values)`, which builds properties only. The flag was available and
simply unused.

## Not only an XML concern

This is why the fix has to be the attribute **map** and not a naming convention: `UDM.Object` keeps
attributes separately and each serialiser re-presents them its own way. The divergence was therefore
visible in JSON and YAML output too, not just XML:

```
xml    <Invoice number="INV-2025" count="42">
json   { "@number": "INV-2025", "@count": "42" }
yaml   '@number': INV-2025
```

Attributes are strings by construction, and the stringification **mirrors the interpreter
deliberately** — including that an integral number loses its decimal point, so `@count: 42` is
`"42"` even in JSON output. Routing it through the exact-number rendering from the numeric work
would have been an improvement that made the strategies disagree, which is the opposite of the
point. That both of them render `100.10` as `100.1` in an attribute is a separate gap, recorded
below.

## How it was found — and a flaw in the harness it exposed

The cross-strategy run had this filed as **"COPY emits XML attributes as child elements"**, the
largest class in the inventory, and I had reported that **"COMPILED never produced different
output"**.

Both statements were wrong, and for the same reason: `describeDivergence` compared COPY first and
**returned on the first difference**. On any fixture where both compiled strategies were wrong, the
COPY entry masked the COMPILED one. The harness now reports both and labels the case `BOTH_OUTPUT`,
which immediately changed the picture:

| | before the harness fix | after |
|---|---|---|
| `COPY_OUTPUT` | 39 | **1** |
| `BOTH_OUTPUT` | — | **38** |
| `COMPILED_OUTPUT` | 0 | 0 |

So the dominant class was never COPY's own machinery — it was the **shared `ASTCompiler`**, which is
far better news: a single fix moves many fixtures, and `BOTH_OUTPUT` is now the class to work
through. Measuring one strategy and generalising was the mistake; a differential test has to report
every axis it compares, or it quietly becomes a single-axis test.

## Effect

| | before | after |
|---|---|---|
| known divergences | 47 | **35** |
| `BOTH_OUTPUT` | 38 | 26 |

12 fixtures, not the 38 the class size suggested — the remaining 26 are heterogeneous, with no
single large cause left. One visible group is an extra `<Metadata1>` element on the compiled path in
the xml-encoding fixtures, which looks like separate `^metadata` handling.

## Tests

`EB08XmlAttributeTest` (`:engine`) drives the three **strategies** rather than `ASTCompiler` directly,
because the defect was in what reached the *serialiser* and that is where the paths have to agree.
Each case asserts all three agree *and* checks the result actually carries an attribute — agreement
alone would be satisfied by all three being wrong together.

Cases: an attribute versus a child element (asserting `<number>` does **not** appear), several
attributes alongside several children, attributes with no children, an object with no attributes at
all (the common case must stay byte-identical), the stringification rules, JSON output, YAML output,
and an attribute nested below the root.

Revert-verified: ignoring the flag again fails 7 of the 8 — the eighth is the no-attributes case,
which is the one that must *not* change.

## Known gap, recorded rather than fixed

An attribute's value is stringified through `Double`, so `@price: 100.10` renders as `100.1` on
**every** strategy. That is consistent and therefore not a parity defect, but it is the same
preservation failure the numeric work removed everywhere else. Fixing it means changing the
interpreter and the compiled path together, and it belongs with that work rather than here.

## Related

- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
  — the harness, and the masking flaw described above.
- [EB07](EB07-compiled-index-access-ignores-string-keys.md) — the previous largest class, also a
  shared-compiler defect, also mis-diagnosed at first.
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the numeric
  preservation work the attribute stringification gap belongs to.
