# B64: a processing instruction inside an element crashes the XML parser

> **B = core/formats bug** (`formats/xml`). Directly continues
> [B27](B27-xml-prolog-epilog-comments-crash-parser.md), which fixed two of the three places a
> processing instruction can appear.

**Status:** **FIXED (2026-11-22)** — one branch in the element-content loop, with a conformance
fixture covering all three strategies.
**Priority:** High — a processing instruction inside an element is ordinary, valid XML, and the
document does not reach UDM at all, so *no* transformation can run against it.
**Component:** `formats/xml` — `xml_parser.kt`, the element-content dispatch
**Found:** 2026-11-22, by a contributor writing documentation: a worked example specified as "an
XML comment, a processing instruction…" would not parse.

## Summary

```xml
<order><!-- hi --><?pi data?><orderId>A-1</orderId></order>
```

```
Error: Error parsing input: XML parse error at 1:20 - Invalid name start: ?
```

The comment is fine. The processing instruction is not. Move the same instruction into the prolog
and the document parses — which is the workaround until this ships, and also the clue to the
cause.

## Root cause

B27 taught the parser about processing instructions and gave it `skipProcessingInstruction()` plus
a `skipMisc()` that handles comments, PIs and DOCTYPE together. But `skipMisc` was wired into the
**prolog and epilog only**. Element content has its own, narrower dispatch:

```kotlin
if (peek(4) == "<!--")            { skipComment() }            // handled
else if (peek(9) == "<![CDATA[")  { … }                        // handled
else                              { parseElementChecked(…) }   // <?pi?> lands here
                                                               // → parseName → throw
```

So `<?` fell through to `parseElementChecked`, which called `parseName`, which rejected `?` as a
name start character.

B27 fixed the two positions where a PI is *most* common — `<?xml-stylesheet?>` before the root,
a trailing instruction after it — and the third position was never considered. The machinery to
handle it already existed and simply was not reached.

## Fix

```kotlin
} else if (peek(2) == "<?") {
    skipProcessingInstruction()
}
```

Tested after `"<!--"` so the two `<`-prefixed forms stay distinct. `skipMisc` itself is
deliberately **not** reused here: it also accepts DOCTYPE, which is illegal inside an element, and
silently skipping one would turn malformed XML into a quiet success.

## Verification

`conformance-suite/utlx/tests/formats/xml/processing_instruction_in_content.yaml`. Picked up by
`ConformanceStrategyParityTest`, so it runs on TEMPLATE, COPY and COMPILED.

Proven load-bearing — removing the branch takes `formats/xml` from 17/17 to 16/17, failing on
exactly this fixture.

The fixture places a processing instruction in **three** positions, because they reach different
points in the content loop: immediately after the open tag, between two children, and immediately
before the close tag. It also keeps a PI in the prolog and the epilog, as a B27 regression guard.

The assertion that carries the weight is `childCount`:

```
childCount: count($input.order.*)   expected 2
```

A processing instruction must be **skipped**, not parsed. If it were turned into a child element
the count would be 5, and every other assertion in the fixture would still pass — the ids and
text would all be correct. Without that count the test would confirm "it no longer throws" while
missing "it produces the right tree".

### The Kotlin test

`formats/xml/src/test/kotlin/com/glomidco/utlx/formats/xml/B64XmlProcessingInstructionTest.kt`
— 8 tests, driving `XMLParser` directly. The fixture proves the success path across all three
strategies but goes through a transformation, so three things stay out of its reach:

- **An unterminated PI must still fail.** `<r><?pi never closed</r>` has to raise
  "Unterminated processing instruction" — skipping must not degrade into "consume to end of input".
- **DOCTYPE inside an element must still be rejected.** This is the test that guards the design
  decision. `skipMisc()` handles comments, PIs *and* DOCTYPE together, so reusing it in the
  content branch was the shorter fix — and would have made the parser silently accept a DOCTYPE
  inside an element, which is illegal XML. Nothing else in the suite covers that, so a later
  "simplification" to `skipMisc` would have passed green.
- **A PI-like sequence inside CDATA stays literal.** `<![CDATA[<?pi x?>]]>` is character data; the
  parser must not reach into it looking for instructions.

Plus the parser-level shape assertion the fixture can only make indirectly: after three PIs in
three positions the root has exactly two children, asserted on the UDM rather than on serialized
JSON.

Both halves were proven by mutation, not by assumption:

| mutation to `xml_parser.kt` | result |
| --- | --- |
| content branch condition replaced by `false` (the pre-fix parser) | **5 of 8 fail** — the three that pass are the CDATA, DOCTYPE and B27 prolog guards, which are not meant to be sensitive to it |
| `skipProcessingInstruction()` → `skipMisc()`, condition widened to `<!DOCTYPE` | **exactly 1 fails** — "DOCTYPE inside an element is still rejected" |

The second row is the point of that test: the simplification is otherwise invisible. Widening the
branch to all `<!`-prefixed forms instead does not fail — it **hangs**, because `<![CDATA[` then
reaches `skipMisc`, which has no case for it and returns without consuming, spinning the content
loop forever. The DOCTYPE test also carries a vacuity guard: `<!DOCTYPE r><r>…` in the prolog must
still parse, so the assertion is about position and not about DOCTYPE being unsupported.

## Notes

Worth recording how this was found: not by a test, and not by review, but by someone writing a
documentation example and discovering the example did not work. The parser had three places to
handle the same construct and handled two; nothing in the suite covered the third, so the gap was
invisible for as long as nobody wrote that XML.
