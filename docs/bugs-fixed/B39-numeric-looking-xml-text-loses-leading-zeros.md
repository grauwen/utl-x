# B39: numeric-looking text loses its leading zeros, destroying identifiers

> **B = core bug** (shared core and the format parsers built on it), the family of B01–B38.
> This is **B39**.

**Status:** **FIXED** for the leading-zero case. Two related requests from the same report are
recorded below as **open**.
**Priority:** High. It silently corrupts identifiers in production payloads, and no script can
recover them.
**Component:** `:core` — `ExactNumber`; `formats/xml` and `formats/csv` text inference.
**Reported by:** a user integrating a Centric ORS POS XML feed, against v1.3.1 (`cfc55b24`), with a
full diagnosis and a proposed patch.

> **One-line:** the XML and CSV parsers read any numeric-looking text as a number, so the store id
> `04.6166` became `4.6166` and the article number `0100031012` became `100031012`.

## Why it could not be worked around

The reporter identified the decisive part, and it is what makes this a defect rather than a
preference:

> A script can't undo it, because the text is converted before the script runs. `toString()`
> afterwards returns `"4.6166"`, so it can't recover it either.

By the time a transformation sees the value, the digits are gone. There was no option to turn the
conversion off.

## What the numeric-model work had already fixed, and what it had not

The report was written against v1.3.1. Measured against this branch, which carries
[B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md):

| reported case | v1.3.1 | before this fix | after |
|---|---|---|---|
| 18-digit barcode | last digits corrupted | **exact** | exact |
| 22-digit GTIN-style code | destroyed | **exact** | exact |
| `1.50` | `1.5` | **`1.50`** | `1.50` |
| `04.6166` store id | `4.6166` | `4.6166` | **`"04.6166"`** |
| `0100031012` article no. | `100031012` | `100031012` | **`"0100031012"`** |
| `   indented` receipt line | trimmed | trimmed | trimmed — **open** |

So B35 had already fixed the digit-destruction half without anyone asking. The leading-zero half is
a different defect: not a loss of precision, but a **type misjudgement**.

## The fix needs no option

The reporter proposed an `inferNumbers: false` parser option, carefully designed and routed through
the existing header-options path. It is a reasonable feature — recorded below — but it is not the
right fix for *this*, because it asks every user to know about the trap and opt out of it.

A leading zero is **not a number** in JSON's own grammar:

```
int = "0" / (digit1-9 *DIGIT)
```

`04.6166` and `0100031012` are not JSON numbers, and in practice a leading zero is precisely the
marker of an identifier — a store code, an article number, a padded reference. So inference from
free text now uses that grammar, and the default does the right thing with no option at all.

### Two entry points, deliberately asymmetric

`ExactNumber` now answers two different questions:

- **`ofOrNull(lexeme)`** — parse a token the grammar has *already* decided is numeric. JSON's lexer
  hands it one, so it must stay lenient: refusing a token there would turn a successful parse into
  an error.
- **`inferredOrNull(text)`** — decide whether arbitrary text is *meant* to be a number. Used by XML
  and CSV, where the text is whatever the document contained.

`ofOrNull("007")` is still `7`; `inferredOrNull("007")` is `null`, so the text stays a string. The
asymmetry is the point, and there is a test asserting it so a later "tidy-up" cannot collapse the
two.

## Behaviour change

Text that previously became a number now stays a string when it has a leading zero, a leading `+`,
underscores, hex, or `NaN`/`Infinity` spelling — in short, anything outside canonical JSON number
form. Ordinary values (`12`, `-7`, `1.50`, `0.5`, `1e5`) are unchanged.

**No conformance fixture changed:** 563/563 before and after, and the full suite is green. That is
some evidence the old behaviour was not relied on, though the corpus is not a user base.

## Still open, from the same report

Both are genuine and both are **feature requests rather than defects**, because unlike the leading
zero they have a workaround and a defensible default:

1. **`inferNumbers: false`** — keep *all* leaf text as text. Useful when a payload is entirely
   identifiers, and the reporter's design is sound: thread the flag through the parser constructors
   and read it from the `input xml { … }` options map, which already carries `arrays`. Worth taking
   largely as proposed. Note that an 18-digit barcode still arrives as a number today — exact, so
   nothing is lost, but typed as a number; this option is how you would ask for a string.
2. ~~**`trimText: false`**~~ — **answered without the option**, see
   [B41](B41-xml-trim-discards-escaped-whitespace.md): whitespace the sender *escapes* (`&#32;`) or
   wraps in CDATA is no longer trimmed, which is per-element and sender-controlled where a
   document-wide flag could not be. What follows is the original analysis, kept because the bare
   `<A> </A>` case below is still open.

   **`trimText: false`** — keep leading and trailing whitespace. The report's case is POS block text
   used to lay out a receipt, where indentation is content. The whitespace is gone before any
   numeric check, so this is independent of the above and needs its own option.

   **Two sites, not one** — worth knowing before estimating it. The `.trim()` in
   `xml_parser.kt` (`val content = textContent.toString().trim()`) removes only the **trailing**
   whitespace. The **leading** whitespace is eaten earlier, by the `skipWhitespace()` at the top of
   the content loop — which is the same call that skips the indentation *between* child elements. So
   switching it off naively gives every pretty-printed container an `_text` of `"\n  "`. The option
   needs the rule "keep text verbatim in a leaf; ignore a whitespace-only run in an element that has
   children".

   Re-measured on this branch, and there is a **sharper case than the report gave**: text that is
   *entirely* whitespace does not merely lose its indentation, it disappears, and the element
   collapses into the empty-element branch:

   ```
   <Line>   indented line</Line>   ->  "indented line"
   <Blank> </Blank>                ->  {}            // the text is gone, not trimmed
   ```

   Whoever implements the option should treat the second line as the acceptance case — it is the one
   that loses a value rather than reformatting it.

The report also notes two things outside this area, recorded so they are not lost: a leaf element
*with* attributes drops the attribute in the plain conversion (scripts must read `.@name`), and an
empty element becomes `{}` rather than `""` or `null`.

## Tests

- `ExactNumberTest` — the leading-zero rule (`04.6166`, `0100031012`, `007`, `-007`), ordinary
  numbers still inferred, a long canonical code still exact, non-numeric text untouched, and the
  deliberate asymmetry between the two entry points.
- `B39XmlIdentifierTextTest` (`formats/xml`) — the reported payload shapes end to end through the
  parser, including both long-code cases from the same report, and one test that **documents the
  open whitespace issue** rather than pretending it is fixed.

Revert-verified: accepting leading zeros again fails 3 tests.

## The other parsers

Asked whether the same trap existed elsewhere, all four were measured. JSON and OData are correct
already — the JSON lexer rejects a leading zero, which is where the grammar reasoning above came
from, and OData delegates to `JSONParser`. YAML was **worse**, and is
[B40](B40-yaml-reads-zero-padded-identifiers-as-octal.md): it read `0100031012` as octal
`16790026`.

## Related

- [B40](B40-yaml-reads-zero-padded-identifiers-as-octal.md) — the same symptom in YAML, found by
  sweeping the other parsers after this report.
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — fixed the
  digit-destruction half of this report before it was filed.
- [CB03](CB03-capture-yaml-writer-loses-data-in-three-ways.md) — the same JSON-grammar reasoning,
  applied to the capture writer: `0123` must not be read back as octal `83`.
