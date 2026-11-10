# B41: the XML trim discards whitespace the sender escaped

> **B = core bug** (shared core and the format parsers built on it), the family of B01–B40.
> This is **B41**.

**Status:** **FIXED**
**Priority:** Medium. A silent loss of author-written data, with no workaround — but only in
documents that escape whitespace, which is uncommon.
**Component:** `formats/xml` — `xml_parser.kt`.
**Found by:** looking for an answer to the `trimText: false` request in
[B39](B39-numeric-looking-xml-text-loses-leading-zeros.md)'s field report that did not need a new
option.

> **One-line:** XML leaf text was trimmed indiscriminately, so `<A>&#32;&#32;&#32;indented</A>`
> became `"indented"` and `<A>&#32;</A>` became an empty element.

## The distinction that was missing

Trimming XML leaf text is the right default and stays the default. XML is pretty-printed, authors do
not control their serialiser's indentation, and a parser that preserved it would make ordinary
documents unusable.

But that reasoning covers *literal* whitespace only. Measured before the fix:

| input | was | now |
|---|---|---|
| `<A>\n  indented</A>` | `"indented"` | `"indented"` — unchanged |
| `<A>&#32;&#32;&#32;indented</A>` | `"indented"` | **`"   indented"`** |
| `<A><![CDATA[   indented]]></A>` | `"indented"` | **`"   indented"`** |
| `<A>&#32;</A>` | `{}` | **`" "`** |
| `<A><![CDATA[ ]]></A>` | `{}` | **`" "`** |
| `<A> </A>` | `{}` | `{}` — unchanged, see below |

A character reference is not formatting. No serialiser emits `&#32;` for indentation, no author
types one by accident, and there is no reading of XML in which it is layout — the sender went out of
their way to encode that space. The same holds for a CDATA section, which exists to say "this is
literal character data".

The fourth and fifth rows are the loss with no workaround, and the reason this is a bug rather than
a preference: nothing downstream can distinguish a **blank line from a missing one**.

## Why this instead of the `trimText: false` option that was asked for

The field report proposed a parser option. It was carefully designed, and it would have worked — but
it is the wrong shape, and this is a better answer to the same need:

- **It is per element.** A POS document holds receipt block text, where indentation is content, and
  ordinary fields, where it is formatting, *in the same file*. A document-wide flag cannot express
  that; the next request would have been per-element control.
- **The sender controls it**, and the sender is who knows which fields are layout.
- **No permanent API surface.** No option to document, version, guard-lock, or reconcile with the
  still-open `inferNumbers`. Options are forever, and they multiply against each other.
- **No second parser path.** All 563 conformance fixtures run the default; a flag's other branch
  would be covered only by its own unit tests. That is precisely the shape that let B34, B35 and B36
  each be fixed in one execution path while another diverged unnoticed.
- **Round trips stay stable.** The XML serialiser pretty-prints by default (`prettyPrint = true`,
  two-space indent) and writes `_text` verbatim, so a parser that preserved literal indentation
  would grow the document on every parse/serialise cycle.

So `trimText: false` is **declined, with this in its place**. `inferNumbers: false` remains open and
unrelated.

## Root cause

Leaf text was accumulated into a `StringBuilder` that had no memory of where each character came
from, and then trimmed in one go:

```kotlin
val content = textContent.toString().trim()
```

By that point an escaped space and an indentation space were the same character. Both the character
reference and the CDATA path appended into the same buffer, so both were reachable by the trim.

Note it is a **single** site. The `skipWhitespace()` at the top of the content loop — which also
skips indentation *between* child elements, and was the risky part of implementing `trimText` —
never sees this text: escaped text starts with `&` and CDATA with `<`, so the loop stops and hands
over. The content loop is untouched by this fix.

## The fix

A small `TextBuffer` carries the text and, per character, whether the sender escaped it. `trimmed()`
is `trim()` that steps over escaped characters:

```kotlin
fun trimmed(): String {
    var start = 0
    var end = text.length
    while (start < end && text[start].isWhitespace() && escaped[start] == LITERAL) start++
    while (end > start && text[end - 1].isWhitespace() && escaped[end - 1] == LITERAL) end--
    return text.substring(start, end)
}
```

A parallel mask rather than a sentinel character in the text: `&#0;` is a legal lexeme for this
parser and expands to NUL, so no character is safe to reserve.

Boundary behaviour falls out of ordinary trim semantics — `<A>  &#32;x&#32;  </A>` gives `" x "`:
the outer literal runs go, the escaped spaces stay, and any literal whitespace they shelter becomes
interior text.

Attribute values are unaffected; they were never trimmed.

## Deliberately not done

- **CDATA does not imply "not a number".** `<Qty><![CDATA[12]]></Qty>` is still `12`. Senders use
  CDATA to avoid escaping `&`, so reading it as a type signal would be an invention rather than a
  reading of XML. Asking for a string is what `inferNumbers` is for.
- **`<A> </A>` still collapses to `{}`.** The bare literal form is left as it was. It is the same
  "whitespace-only" question, but changing it would change the default for every document, and the
  sender now has two ways to say that a blank matters. Recorded in B39's open items.

## Behaviour change

Only documents that escape whitespace at the edges of leaf text, or use CDATA there, see any
difference — and in those the previous output had discarded author data. Ordinary pretty-printed XML
is byte-for-byte unchanged.

**No conformance fixture changed:** 563/563 before and after, and the full Gradle suite is green.

## Tests

`B41XmlEscapedWhitespaceTest` (`formats/xml`), 13 tests in three groups:

- escaped whitespace survives — decimal and hex character references, CDATA, a trailing escaped
  space, and the mixed boundary case `  &#32;x&#32;  ` → `" x "`;
- the loss is closed — an escaped or CDATA blank is a value, while a genuinely empty element
  (`<A></A>`, `<A/>`) is still empty;
- the default is unchanged — literal indentation still trimmed, `<A> </A>` still `{}` (recorded, not
  endorsed), numeric inference untouched including through CDATA, non-whitespace entities
  unaffected, attribute values still untrimmed.

Revert-verified: restoring the plain `trim()` (ignoring the mask) fails **7 of the 13** — exactly the
first two groups. The six guard tests still pass, which is what makes the revert check meaningful:
it shows the fix changed only what it claims to.

## Related

- [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) — the field report this came from. Its
  `trimText: false` item is now answered; `inferNumbers: false` and the leaf-attribute and
  empty-element notes remain open.
- [B40](B40-yaml-reads-zero-padded-identifiers-as-octal.md) — the other defect found by following
  that report outwards, in YAML.
- [B42](B42-xml-character-references-above-the-bmp-are-truncated.md) — found by reading the same
  function afterwards: the escaped characters this fix preserves were themselves being resolved
  wrongly above the BMP.
