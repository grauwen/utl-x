# B43: the XML serialiser writes significant whitespace literally, so the next read drops it

> **B = core bug** (shared core and the format parsers built on it), the family of B01–B42.
> This is **B43**.

**Status:** **FIXED**
**Priority:** Medium. A silent loss, but only on the XML output path and only for values with
whitespace at the edges.
**Component:** `formats/xml` — `xml_serializer.kt`.
**Found by:** round-tripping [B41](B41-xml-trim-discards-escaped-whitespace.md) rather than assuming
the fix was complete.

> **One-line:** the writing side had no notion of significant whitespace, so a value of `"  x"` was
> emitted as `<A>  x</A>` and read back as `"x"`.

## Why B41 was only half a fix

B41 made the **reading** side faithful: whitespace the sender escaped, or wrote inside CDATA,
survives the trim. Verifying it end to end exposed that the **writing** side had no matching notion,
so an XML→XML transformation still lost the value:

| value in UDM | document written | re-read as |
|---|---|---|
| `"  indented"` | `<A>  indented</A>` | `"indented"` |
| `" "` | `<A> </A>` | *empty element* |
| `"x\t"` | `<A>x\t</A>` | `"x"` |

Measured, before and after, on the same two-pass transformation:

```
pass 0 (original XML)         { "A": "  indented", "B": " ",  "C": "x\t" }
pass 2 (XML -> XML -> read)   { "A": "indented",   "B": {},   "C": "x"   }   <- before
pass 2 (XML -> XML -> read)   { "A": "  indented", "B": " ",  "C": "x\t" }   <- after
```

The value was never lost in memory. It was lost the moment it was written, and only noticed on the
way back in — which is why nothing caught it: a test that checks the UDM, or one that checks the
serialised text, both pass. It takes a round trip.

**This is the general lesson of the pair.** A parser and a serialiser each defensible on their own
can still be lossy together. The trim rule has to be stated once and honoured on both sides.

## The fix

A separate `writeEscapedText` for element text, alongside the existing `writeEscaped` for attribute
values. In addition to the markup characters, whitespace in the **leading or trailing** run is
written as a character reference:

```kotlin
private fun writeEscapedText(writer: Writer, text: String) {
    val lead = text.indexOfFirst { !it.isWhitespace() }
    if (lead < 0) {
        // All whitespace — every character is an edge, including the empty case (writes nothing).
        for (c in text) writer.write("&#${c.code};")
        return
    }
    val trail = text.indexOfLast { !it.isWhitespace() }
    for ((i, c) in text.withIndex()) {
        if (i < lead || i > trail) writer.write("&#${c.code};") else writeEscapedChar(writer, c)
    }
}
```

```
<A>&#32;&#32;indented</A>      <A>&#32;</A>      <A>x&#9;</A>
```

The rule is deliberately narrow, and each narrowing has a reason:

- **Only the edges.** Trimming cannot reach interior whitespace, so escaping it would add noise with
  no benefit. `a\tb` stays `a\tb` in the document.
- **Only element text.** Attribute values are never trimmed on the way in, so they keep the plain
  escaper — a document that does not need protection should not be made uglier.
- **Surrogate pairs are safe.** A character reference is only ever emitted for whitespace, which is
  always in the BMP, so the per-`Char` loop cannot split a pair. (The reference it would have
  produced was also wrong until [B42](B42-xml-character-references-above-the-bmp-are-truncated.md).)

## Behaviour change

An element whose text begins or ends with whitespace now shows character references where it
previously showed literal whitespace. The document is equivalent under XML's own rules — a
conforming parser resolves the reference to the same character — and it is the only form that
survives a re-read.

Values without edge whitespace, markup escaping, interior whitespace and attribute values are all
byte-for-byte unchanged, which is why no fixture moved.

**No conformance fixture changed:** 563/563 on the JVM and 563/563 on the GraalVM native binary, and
the full Gradle suite is green.

## Tests

`B43XmlTextRoundTripTest` (`formats/xml`), 11 tests, built around an actual round trip —
serialise, re-parse, compare — rather than asserting on the text alone, because asserting on the
text is exactly what missed this:

- round trip — leading, trailing, whitespace-only, ordinary text, markup characters, interior
  whitespace, and an astral character both bare and surrounded by spaces;
- document form — edge whitespace appears as `&#32;` / `&#9;`, markup escaping is unchanged, an empty
  value writes no references at all, and attribute values keep their literal whitespace.

Revert-verified: emitting text with no edge protection fails **6 of the 11**. The five that still
pass are the unchanged-behaviour guards.

## Related

- [B41](B41-xml-trim-discards-escaped-whitespace.md) — the reading half. The two are a pair: neither
  gives a faithful round trip alone, and B41's tests and these pin the rule from both ends.
- [B42](B42-xml-character-references-above-the-bmp-are-truncated.md) — found in the same reading
  pass, on the resolution of the references this fix now emits.
- [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) — the field report the whole thread
  started from. Its `trimText: false` request is answered by B41 + B43 together.
