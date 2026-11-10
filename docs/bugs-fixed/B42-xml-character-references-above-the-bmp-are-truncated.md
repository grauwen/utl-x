# B42: a character reference above the BMP is truncated into a different character

> **B = core bug** (shared core and the format parsers built on it), the family of B01–B41.
> This is **B42**.

**Status:** **FIXED**
**Priority:** High. Silent corruption of document content, with no error and no workaround from
inside a transformation.
**Component:** `formats/xml` — `xml_parser.kt`, `parseEntityReference`.
**Found by:** reviewing the function [B41](B41-xml-trim-discards-escaped-whitespace.md) had just
touched, and asking what else resolved a character reference incorrectly.

> **One-line:** numeric character references were resolved with `Int.toChar()`, which keeps only the
> low 16 bits, so `&#128512;` (U+1F600 😀) became U+F600 — a private-use character.

## Measured

| input | was | now |
|---|---|---|
| `<A>&#128512;</A>` (U+1F600 😀) | `U+F600` | **U+1F600** |
| `<A>&#x1F600;</A>` (U+1F600 😀) | `U+F600` | **U+1F600** |
| `<A>&#x10FFFF;</A>` (last legal code point) | `U+FFFF` | **U+10FFFF** |
| `<A>😀</A>` (literal) | U+1F600 | U+1F600 — never affected |
| `<A>&#233;</A>` (é) | `é` | `é` — unchanged |
| `<A>&#0;</A>` | `U+0000` | **rejected** (lenient: literal `&#0;`) |
| `<A>&#xD800;</A>` (lone surrogate) | invalid UTF-16 | **rejected** (lenient: literal) |

## Why it went unnoticed

The result of the truncation is a **perfectly valid string** — just not the one the document named.
There is no exception, no replacement character, and no shape change: a mapping sees one character
where the document asked for one character. The only way to notice is to compare code points, which
nothing did.

A literal astral character was never affected. It arrives from the reader as a surrogate pair and is
copied through unchanged, so the defect only reached documents whose producer **escapes** non-ASCII
on the way out — common in exports, and exactly the kind of producer whose output nobody reads by
eye.

Reach, in rough order of likelihood: emoji, CJK Unified Ideographs extensions B–F, mathematical
alphanumeric symbols, musical notation, and the historic scripts. All of plane 1 and above.

## Root cause

```kotlin
hex.toInt(16).toChar().toString()      // and the decimal equivalent
```

`Int.toChar()` is a narrowing conversion to 16 bits. `0x1F600.toChar()` is `0xF600`. Above the BMP a
code point needs a **surrogate pair**, which is two `Char`s, so no single `Char` can carry it.

The same truncation is why an *illegal* reference produced a corrupt result rather than an error:
`&#0;` became NUL (never legal in XML content), `&#xD800;` became a lone surrogate (not valid
UTF-16 at all, so the output document was ill-formed), and `&#x110000;` wrapped into an arbitrary
BMP character.

## The fix

Resolve by code point, and reject what XML does not allow:

```kotlin
private fun codePointText(cp: Int?, entity: String): String {
    if (cp != null && isLegalXmlChar(cp)) return String(Character.toChars(cp))
    if (options.allowUndefinedEntities) return "&$entity;"
    throw XMLParseException("Invalid character reference: &$entity;", line, column)
}

/** XML 1.0 §2.2 `Char`: `#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]`. */
private fun isLegalXmlChar(cp: Int): Boolean =
    cp == 0x9 || cp == 0xA || cp == 0xD ||
        cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
```

`Character.toChars` builds the surrogate pair when one is needed.

**The rejection rides an axis that already exists.** An undefined entity reference is already a
well-formedness error under standard and strict, kept as literal text under lenient
(`allowUndefinedEntities`, B29). A reference to an illegal character is the same kind of error, so it
gets the same treatment rather than a new switch. `toIntOrNull` also replaced the `try`/`catch`,
which folds the unparseable case (`&#xZZ;`, an overflowing decimal) into the same path.

Note the previous code returned unparseable references as literal text under *every* profile, which
quietly bypassed B29's rule. That is now consistent.

## Behaviour change

Two, and both replace a corrupt result:

1. A reference above U+FFFF now yields the character the document named. Any mapping that compared,
   hashed, or re-serialised such text was working on wrong data before.
2. A reference to an illegal character is a located parse error under standard and strict
   (`Invalid character reference: &#0;`) instead of silently producing an illegal character. Under
   lenient it is preserved as source text, which loses nothing.

BMP references, named entities (`&lt;` and friends), literal astral characters and attribute values
are untouched.

**No conformance fixture changed:** 563/563 on the JVM and 563/563 on the GraalVM native binary,
before and after; the full Gradle suite is green. The native run matters here because the fix touches
string and charset handling, which is where GraalVM has previously diverged in this repo.

## Tests

`B42XmlCharacterReferenceTest` (`formats/xml`), 9 tests:

- above the BMP — decimal and hex forms resolve to U+1F600, `&#x10FFFF;` resolves to the last legal
  code point, and a literal astral character still works;
- unchanged — BMP references (`&#65;`, `&#233;`, `&#xE9;`, `&#xFFFD;`), named entities, and the
  whitespace references B41 depends on (`&#9;`, `&#x20;`);
- illegal — `&#0;`, `&#xD800;`, `&#x110000;`, `&#x7FFFFFFF;` and the unparseable forms are rejected
  under standard, and kept as literal text under lenient while a legal reference still resolves
  there.

Revert-verified: restoring `Int.toChar()` fails **5 of the 9**. The four that still pass are the
unchanged-behaviour guards, which is what makes the check meaningful — the fix moved only what it
claims to.

## Related

- [B41](B41-xml-trim-discards-escaped-whitespace.md) — the same function, found in the same reading.
  B41 made escaped whitespace survive the trim; B42 makes escaped characters resolve correctly in the
  first place. B41's tests depend on `&#9;` and `&#x20;`, so the two are pinned together.
- B29 — established that an undefined entity is a well-formedness error, gated on
  `allowUndefinedEntities`. B42 puts illegal character references on that same axis.
