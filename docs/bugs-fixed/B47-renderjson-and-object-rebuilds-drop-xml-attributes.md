# B47: `renderJson()` and object rebuilds drop XML attributes

> **B = core bug** (the shared language stack: `:core`, `formats/*`, `:stdlib`), the family of
> B01–B46. This is **B47**.

**Status:** **FIXED**
**Priority:** High. Silent data loss on a path a user is relying on in production today.
**Component:** `:stdlib` — `SerializationFunctions.renderJson`, `EnhancedObjectFunctions`;
`:core` — `Interpreter.udmToRuntimeValue`.
**Reported by:** GitHub issue **#10** (`taatuut`), against v1.3.1 (`cfc55b24`), with a reproduction,
a correct diagnosis of the likely cause, and a note that a Java step is being kept in production
solely because of this.

> **One-line:** `renderJson` had its own serialiser, which never read `attributes` — and three
> further layers dropped them too, so a cleaned XML tree lost every `@attr` with no error.

## What the reporter found, and what was underneath

The report was accurate and understated. Reproduced exactly:

```
input:  <a><b x="1">text</b></a>
        { attr_via_access: $input.a.b.@x, json_text_has_attribute: contains(renderJson($input), "\"x\"") }
output: { "attr_via_access": "1", "json_text_has_attribute": false }
```

The data is in the UDM — `.@x` reads it — and only `renderJson` leaves it out. The reporter also
suspected `mapTree` and `mapKeys`, "not verified separately". **They were right**, and measuring it
turned up two further faults they could not have seen:

| | old `renderJson` | `output json` |
|---|---|---|
| XML attribute | **dropped** | `"@x": "1"` |
| `9007199254740993` | **`9007199254740992`** | exact |
| `1.50` | **`1.5`** | `1.50` |
| `100.10` | **`100.1`** | `100.10` |
| internal `_text` marker | **leaked into output** | unwrapped |

The numeric faults are [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md)
and [B37](B37-serializers-saturate-large-doubles-to-long-max.md) **in a fourth place**: the private
conversion narrowed every `Number` through `toDouble()` before writing.

## Root cause — one pattern, four layers

Every fault came from the same habit: **rebuilding a value from its `properties` alone.**

1. **`renderJson` had a private `udmToJsonValue`.** Its object branch was
   `udm.properties.mapValues { … }` — `attributes` was never read. Being a second implementation is
   also why it narrowed numbers and leaked `_text`: it had to re-derive everything the real
   serialiser already knew.
2. **`mapKeys`, `mapValues`, `mapTree`** each returned `UDM.Object(result)`, discarding the source
   node's attributes, element name and namespace metadata.
3. **`Interpreter.udmToRuntimeValue`** decomposed a `UDM.Object` into
   `RuntimeValue.ObjectValue(properties)` — and **`ObjectValue` has no slot for attributes at all.**
   This is the one that mattered most: fixing (2) alone changed nothing, because the attributes were
   discarded *after* those functions returned, on the way back into the interpreter.

Layer 3 is why the bug looked confined to `renderJson`. Any XML-shaped object crossing the stdlib
boundary lost its shape, and `renderJson` was simply the first place anyone noticed.

## The fix

**`renderJson` now delegates to `JSONSerializer`** — the same one `output json` uses — so all four
faults go at once and the guarantee becomes simple: *`renderJson(x)` is exactly what `output json`
would write for `x`.* That is the lesson from
[B38 §1b](B38-non-finite-numbers-serialise-as-invalid-json.md),
[B45](B45-stdlib-does-not-follow-the-decimal-rule-the-operators-use.md) and
[EB14](EB14-validation-verdict-dropped-on-the-interpreter-paths.md), applied a fourth time: **two
implementations of the same thing means one of them is wrong.**

**`mapKeys` / `mapValues` / `mapTree`** carry `attributes`, `name` and `metadata` through. The shape
of a node is not the function's to discard; only the part the lambda touched should change.

**`udmToRuntimeValue`** keeps an XML-shaped object as a `UDMValue` instead of decomposing it:

```kotlin
is UDM.Object ->
    if (result.attributes.isNotEmpty() || result.name != null) RuntimeValue.UDMValue(result)
    else RuntimeValue.ObjectValue(result.properties.mapValues { udmToRuntimeValue(it.value) })
```

Deliberately conditional. A plain JSON object has no attributes and no element name, so it still
becomes an `ObjectValue` and nothing on the JSON path changes; only objects that actually carry XML
shape take the preserving branch.

### The reporter's requested option

They asked for `renderJson(data, pretty, includeAttributes)`. It exists now as the third argument,
named **`writeAttributes`** to match the option `output json` already had:

```
renderJson($input)                  {"a":{"b":"text"}}                   same as `output json`
renderJson($input, false, true)     {"a":{"b":{"@x":"1","#text":"text"}}}
output json { writeAttributes: true }   — the same switch, already supported
```

Worth stating plainly, because the report assumed otherwise: **`output json` drops the attribute too
for a leaf that has both text and an attribute** — that is the one case `writeAttributes` exists
for, and it was already available. For every other shape (a parent, an empty element, the root)
attributes were and are written as `@name`.

## Measured, after

```
$input.a                                        {"b":{"@x":"1","c":"v"}}
mapKeys($input.a, (k) -> upper(k))              {"B":{"@x":"1","c":"v"}}
mapValues($input.a, (v) -> v)                   {"b":{"@x":"1","c":"v"}}
mapTree($input.a, (v) -> v)                     {"b":{"@x":"1","c":"v"}}
renderJson(mapKeys($input.a, (k) -> upper(k)))  "{\"B\":{\"@x\":\"1\",\"c\":\"v\"}}"
renderJson({big: 9007199254740993, money: 1.50}) exact, scale preserved
```

The last two are the reporter's actual use case — clean the key names and render the whole document
as one JSON string — which now works in the language alone, which was the point of the report.

## Behaviour change

- `renderJson` no longer emits the internal `_text` marker, matching `output json`. **Two
  auto-captured fixtures were re-captured** (`renderJson_auto_39c4ad24`, `renderJson_auto_df69e6e1`):
  they had recorded the old serialiser's output, `_text` and all. Both verified text-for-text against
  the new output, not by parsed value.
- `renderJson` is exact where it used to narrow. A script that rendered a large integer or a
  money value was getting corrupted text before.
- An XML-shaped object returned from a stdlib function is now a `UDMValue`. Property access,
  indexing and serialisation all already handle that type — it is what a parsed XML document has
  always been.

**Conformance: 563/563**, and the full Gradle suite is green.

## Still open from the same report

The reporter's broader point stands: **17 sites across `:stdlib` and `:core` rebuild a
`UDM.Object` from properties alone.** Three are fixed here because the issue named them. The rest
should be swept deliberately — each needs a judgement about whether the node's shape ought to
survive that particular operation — rather than changed blindly. Layer 3 above means many of them
are now harmless, since the object arrives as a `UDMValue` and is never decomposed; that should be
confirmed per site, not assumed.

## Related

- [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) — the open item "a leaf element *with*
  attributes drops the attribute in the plain conversion" is the `writeAttributes` case, now
  documented as an existing option rather than a defect.
- [B45](B45-stdlib-does-not-follow-the-decimal-rule-the-operators-use.md),
  [B44](B44-stdlib-results-lose-the-exact-numeric-form.md) — the other conversions in this family.
