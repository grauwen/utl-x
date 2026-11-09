# B40: YAML reads a zero-padded identifier as a number — and `0100031012` as octal

> **B = core bug** (shared core and the format parsers built on it), the family of B01–B39.
> This is **B40**.

**Status:** **FIXED**
**Priority:** High. Worse than [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md): the value is
not truncated but **replaced by an unrelated number**, and nothing downstream can tell.
**Component:** `:core` — `ExactNumber.isZeroPadded`; `formats/yaml` — `YAMLParser`.
**Found by:** asking whether the XML/CSV defect in B39 had siblings in the other parsers.

> **One-line:** the YAML parser let SnakeYAML resolve scalars, so the store id `04.6166` became
> `4.6166` and the article number `0100031012` became **`16790026`** — YAML 1.1 read it as octal.

## What was measured

The B39 report arrived about XML. The obvious next question was whether the other parsers shared
the defect, so all four were measured on the reporter's own values:

| parser | `04.6166` | `0100031012` | verdict |
|---|---|---|---|
| XML | `4.6166` | `100031012` | B39 — **fixed** |
| CSV | `4.6166` | `100031012` | B39 — **fixed** |
| JSON | *parse error* | *parse error* | **correct already** |
| OData | *parse error* | *parse error* | **correct already** (delegates to `JSONParser`) |
| YAML | `4.6166` | **`16790026`** | **this report** |

JSON needed no change and should not get one: its lexer rejects a leading zero outright, as the
grammar requires, so `{"store": 04.6166}` is a parse error rather than a silent conversion. That is
the behaviour B39 generalised to free text; JSON had it from the start.

### Why the YAML case is the serious one

`100031012` is recognisably wrong — a digit is missing, and an eye or a checksum catches it.
`16790026` is not. It is a plausible article number with no arithmetic relation to the input,
produced because YAML 1.1 resolves a leading-zero integer as **octal**:

```
0100031012₈ = 16790026₁₀
```

Truncation loses information. Octal reinterpretation *fabricates* it.

## Root cause

`YAMLParser` installed an `ExactFloatConstructor` for `Tag.FLOAT` only — the B35 work, which was
about decimal precision. Integers were left to SnakeYAML's own `Tag.INT` constructor, which applies
YAML 1.1 resolution: leading `0` → octal, `0x` → hex, `_` → digit separator, `1:30` → sexagesimal.
So the two halves of the defect arrived by different routes:

- `04.6166` — reached the float constructor, which parsed it as a number because nothing asked
  whether the *text* was meant to be one.
- `0100031012` — never reached our code at all.

## The fix, and why it is narrower than B39's

B39 could use the full JSON-number grammar, because XML and CSV leaf text has no number syntax of
its own — the parser decides. YAML is different: **SnakeYAML owns YAML's number syntax**, and
`0x1F`, `0o17`, `1_000` and sexagesimal `1:30` are deliberate spellings a user means. Applying
B39's rule here would have broken all of them.

So `ExactNumber` grew a second, deliberately narrow predicate:

```kotlin
/** A decimal number written with a leading zero: `04.6166`, `0100031012`, `007`. */
private val ZERO_PADDED = Regex("-?0\\d+(\\.\\d+)?")

fun isZeroPadded(text: String): Boolean = ZERO_PADDED.matches(text.trim())
```

and the parser overrides **both** tags, returning the raw text when — and only when — it is
zero-padded, and otherwise delegating to the library:

```kotlin
val intDelegate = yamlConstructors[Tag.INT]!!
yamlConstructors[Tag.INT] = object : AbstractConstruct() {
    override fun construct(node: Node): Any? {
        val raw = (node as? ScalarNode)?.value
        if (raw != null && ExactNumber.isZeroPadded(raw)) return raw
        return intDelegate.construct(node)
    }
}
```

Delegation is the important half. Every scalar shape this parser does not have an opinion about
still gets YAML's own semantics, including the non-finite floats and anchors it never sees.

`0` and `0.5` are **not** zero-padded — the zero is canonical, not padding — so they stay numbers.

### Measured after the fix

```
store: "04.6166"   article: "0100031012"   padded: "007"   quoted: "007"
zero: 0   half: 0.5   plain: 12   price: 1.50   neg: -7
hex: 31   underscore: 1000   sexa: 90   barcode: 123456789012345678
```

The bottom row is the point: YAML's own spellings are untouched.

## Behaviour change

A zero-padded scalar that previously became a number is now a string. In YAML that was never a
*correct* number anyway — `0100031012` meant 16790026, which no author intended — so the change is
from a wrong number to the right text. A value genuinely meant as octal must be spelled `0o...`
(YAML 1.2) and still resolves.

**No conformance fixture changed:** 563/563 before and after, and the full Gradle suite is green.

## Tests

- `ExactNumberTest` — `isZeroPadded` fires on `04.6166`, `0100031012`, `007`, `-007`; does **not**
  fire on canonical `0`, `0.5`, `0.50`, `12`, `-7`, `1.50`, `1e5`; and does not fire on `0x1F`,
  `0o17`, `1_000`, `1:30`, `0b101`, `.inf`, so the narrowness is pinned against a later "tidy-up"
  that would merge it with `inferredOrNull`.
- `B40YamlZeroPaddedTest` (`formats/yaml`) — the reported values end to end through the parser, an
  explicitly quoted identifier, ordinary numbers and decimal scale, **and** the YAML-specific
  spellings that must keep resolving (hex, underscores, sexagesimal, `.inf`, `.nan`, `1.5e3`).

Revert-verified twice, because the fix has two independent halves:

| reverted | failures |
|---|---|
| `isZeroPadded` → `false` | 3 (1 in `ExactNumberTest`, 2 in `B40YamlZeroPaddedTest`) |
| the `Tag.INT` override alone | 1 — exactly the octal test, the decimal one still passes |

The second check is the one that matters: it shows the `Tag.INT` override is doing work the
pre-existing `Tag.FLOAT` override did not.

## Related

- [B39](B39-numeric-looking-xml-text-loses-leading-zeros.md) — the same symptom in XML and CSV, from the
  field report that prompted this sweep. Different mechanism (our own inference, not a library's
  resolver) and different severity (truncation, not substitution).
- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — added the
  `Tag.FLOAT` override this extends, and the exact numeric model both rely on.
- [CB03](CB03-capture-yaml-writer-loses-data-in-three-ways.md) — the **writing** side of the same
  hazard: the capture writer had to quote `0123` so it would not be read back as octal `83`. Reading
  and writing have now met in the middle.
