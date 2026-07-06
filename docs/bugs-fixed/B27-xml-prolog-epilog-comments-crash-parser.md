# B27: XML prolog/epilog comments crash the core XML→UDM parse

**Status:** Root cause **CONFIRMED** by source analysis + a reproduced core-CLI failure. **Fix DESIGNED, not yet implemented.**
**Priority:** **High** — a comment before or after the root element is valid, extremely common XML (schema headers, license banners, generator stamps). Any such instance fails to reach UDM at all, so *no* transformation can run against it.
**Created:** July 2026
**Reported:** IDE — `examples/xml/00-healthcare-claim.xml` "does not parse to UDM." The IDE only surfaces the failure; the defect is in core.
**Related:** B13 / B14 (XML text-node handling), B20 (XML encoding/BOM), B16 (XSD serializer). All in the format layer.

> **Classification: this is a BUG, not a feature.** Comments are permitted anywhere in the XML
> *misc* production (before and after the root element, and between child nodes). Rejecting them is
> unintended, non-conformant behavior — not a documented limitation.

---

## TL;DR

The core XML parser knows how to skip comments **inside elements** (`parseElement`, `xml_parser.kt:189`),
but the **document-level** `parse()` function skips only whitespace between the `<?xml?>` declaration
and the root element. A `<!--` in the **prolog** (or **epilog**) is therefore handed straight to
`parseName`, which sees `!` and throws.

| # | Symptom | Root cause | File:line | Status |
|---|---|---|---|---|
| 1 | `XML parse error at 2:2 - Invalid name start: !` on a prolog comment | `parse()` skips only whitespace before the root; no misc-skip | `xml_parser.kt:85→89` | ⏳ fix designed |
| 2 | Trailing comment after root → `Content after root element` | same gap on the epilog side | `xml_parser.kt:92-93` | ⏳ fix designed |
| 3 | `<?xml-stylesheet?>` PI mis-consumed as the XML declaration | decl match `peek(5) == "<?xml"` also matches `<?xml-stylesheet` | `xml_parser.kt:69` | ⏳ fix designed (latent, related) |

The unifying fix: a **`skipMisc()`** loop (whitespace + `<!-- -->` + `<?PI?>` + `<!DOCTYPE>`) applied
in the prolog **and** the epilog, plus a tightened declaration match.

---

## Reproduction (core, no IDE)

```bash
java -jar modules/cli/build/libs/cli-1.3.0.jar --from xml --to json -e '$input' \
  < examples/xml/00-healthcare-claim.xml
# Error parsing input: XML parse error at 2:2 - Invalid name start: !
```

The offending head of the file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- Healthcare Insurance Claim Processing System -->          <!-- line 2 -->
<!-- Comprehensive medical claim with patient... -->          <!-- line 3 -->
<HealthcareClaim xmlns="http://healthcare.example.com/claim/v2" ...>
```

Because the same failure occurs through the **core CLI with no IDE in the loop**, this is
unambiguously a **core** bug. The IDE/daemon merely relays the core parse error.

---

## Root cause

`formats/xml/src/main/kotlin/com/glomidco/utlx/formats/xml/xml_parser.kt`

```kotlin
fun parse(): UDM {
    if (!isAtEnd() && peek() == '﻿') advance()   // BOM
    skipWhitespace()

    if (peek(5) == "<?xml") {                          // (bug #3: also matches <?xml-stylesheet)
        ... skipUntil("?>"); advance(); advance()
        skipWhitespace()                               // ← only whitespace is skipped
    }

    val root = parseElement(emptyMap())                // :89 — next char is '<!--' → parseName → throw

    skipWhitespace()
    if (!isAtEnd()) throw XMLParseException("Content after root element", ...)  // :93 — epilog gap
}
```

`parseName` (`:266`) rejects the `!` of `<!--`:

```kotlin
throw XMLParseException("Invalid name start: ${peek()}", line, column)   // :271
```

**The tell-tale asymmetry.** The parser *already* skips comments — but only as element children:

```kotlin
// parseElement, child loop
} else if (peek() == '<') {
    if (peek(4) == "<!--")       skipComment()        // :189-190  ← works
    else if (peek(9) == "<![CDATA[") ...
```

and `skipComment()` (`:374`) is fully implemented. The gap is purely at the **document level**
(prolog before the root, epilog after it), where nothing but whitespace is consumed. So a comment
*nested inside* `<HealthcareClaim>` would have parsed fine; the document dies on the *prolog*
comments at lines 2–3 first.

Per the XML spec the document production is `prolog element Misc*`, and `Misc ::= Comment | PI | S`.
The parser implements `element` and (via `skipWhitespace`) the `S`, but not `Comment`/`PI`/`DOCTYPE`
in the surrounding `Misc`/prolog.

---

## The fix

Add one helper and call it where only `skipWhitespace()` is called today.

```kotlin
/** Skip XML "misc" content permitted in the prolog and epilog: whitespace, comments, PIs, DOCTYPE. */
private fun skipMisc() {
    while (!isAtEnd()) {
        skipWhitespace()
        when {
            peek(4) == "<!--"      -> skipComment()            // reuse existing helper
            peek(9) == "<!DOCTYPE" -> skipDoctype()            // to matching '>', honoring [ internal subset ]
            peek(2) == "<?"        -> skipProcessingInstruction() // to "?>"
            else                   -> return
        }
    }
}
```

Wiring in `parse()`:

```kotlin
// after the <?xml?> declaration (tighten the match — see bug #3):
if (peek(5) == "<?xml" && (peekAt(5) == ' ' || peekAt(5) == '\t' || peekAt(5) == '\r'
                            || peekAt(5) == '\n' || peekAt(5) == '?')) { ... }
skipMisc()                              // ← prolog: was skipWhitespace()
val root = parseElement(emptyMap())
skipMisc()                              // ← epilog: was skipWhitespace()
if (!isAtEnd()) throw XMLParseException("Content after root element", line, column)
```

Notes:
- **`skipComment()` already exists** — no new comment logic, just call it at the document level.
- **`skipProcessingInstruction()`** = `skipUntil("?>")` + 2 × `advance()` (mirrors the declaration read).
- **`skipDoctype()`** — skip to the matching top-level `>`, but if a `[` is seen first, skip the
  internal subset to its `]` before the `>`. (A naive "to first `>`" is wrong for internal subsets;
  the bracket check keeps it correct. DOCTYPEs are rare in data instances but cheap to handle.)
- **Bug #3** — tightening the declaration match so `<?xml-stylesheet?>` is treated as a PI (skipped
  by `skipMisc`) instead of being mis-read as the XML declaration.

Comments are **skipped, not preserved** — see the Design Rationale below for why that is the correct
default and not a shortcut.

---

## Cherry-pick plan (development → main)

The fix is deliberately **self-contained**: a single production file plus a test.

- **Touched:** `formats/xml/src/main/kotlin/com/glomidco/utlx/formats/xml/xml_parser.kt` (+ new test).
- **`formats/xml` exists on `main`** (XML is a shipped format), and after the v1.3.0 namespace
  migration **both branches share the identical package path** `com.glomidco.utlx.formats.xml`.
- Therefore: implement + test on `development`, then `git cherry-pick <sha>` onto `main` — expected
  **clean** (no path/namespace divergence, no cross-module coupling).
- Suggested commit subject: `B27: XML prolog/epilog comments crash the parser — skip document-level misc`.

---

## Test plan

Add `XmlPrologEpilogCommentTest` (core, `formats/xml` test source):

1. **Prolog comment(s)** between `<?xml?>` and root → parses; UDM equals the comment-free equivalent.
2. **Epilog comment** after the root → parses; no `Content after root element`.
3. **Nested + prolog + epilog** together (the `00-healthcare-claim.xml` shape) → parses.
4. **PI in prolog** (`<?xml-stylesheet ...?>`) → skipped, not mis-read as the declaration.
5. **DOCTYPE with an internal subset** `<!DOCTYPE x [ <!ENTITY ...> ]>` → skipped.
6. **Regression:** existing comment-free and in-element-comment cases still pass byte-for-byte.
7. **End-to-end:** `--from xml --to json` on `examples/xml/00-healthcare-claim.xml` succeeds.

---

## Design Rationale — preserving comments vs. documentation

The report raised a deeper question: *if an instance has comments, do we want to preserve them? If an
XSD has `<xs:documentation>`, do we want to preserve that — in the USDL or the UDM?* These feel similar
but are **two different layers**, and separating them gives a clean, defensible answer.

### 1. Instance-document comments → **skip, do not preserve** (default)

An `<!-- … -->` in an XML *instance* is, by definition, **not data**. The XML Information Set treats a
comment as a distinct information item precisely *because* it sits outside the element/attribute content
model. Concretely, preserving instance comments in UDM is the wrong call for four reasons:

1. **UDM is a format-agnostic *data* model.** A comment is an XML-only presentation artifact with no
   equivalent in JSON, CSV, or YAML. Injecting it into UDM would leak a format-specific concept into the
   one representation whose entire purpose is to mean the same thing across all formats. (This is the same
   principle behind B13/B14: keep format-specific noise out of the semantic model.)

2. **There is nowhere faithful to put it.** UDM today is `Scalar | Array | Object`, and `Object` carries
   only `properties`, `attributes: Map<String,String>`, `name`, and `metadata: Map<String,String>`
   (`udm_core.kt:98`). A comment can appear **anywhere** — between any two child nodes, inside mixed
   content — but `metadata` is an unordered `String→String` map on an *object*, with no notion of
   *"a comment positioned between child 3 and child 4."* Faithful round-trip would require a real
   **`UDM.Comment` node inside an ordered child sequence** (as XPath/XQuery's data model has), a large
   model change that would then have to be handled — or explicitly dropped — by **every** format
   serializer. The cost is structural; the payoff is near-zero.

3. **Transformations can't address it.** UTL-X selects and maps data by path. A comment has no name,
   no key, no addressable identity a transformation could reference. Even if stashed in `metadata`, the
   first restructuring (`map`, object rebuild, format flip) would silently drop it — so "preservation"
   would be an illusion that survives only the identity transform.

4. **It matches every peer tool.** Jackson, DataWeave, and XSLT-in-practice all discard instance
   comments on the data path. Users do not expect `<!-- TODO -->` to travel with their payload.

**So: the bug fix is to *skip* comments cleanly — that is the correct end state, not a stopgap.**
If a genuine XML→XML *byte-fidelity* use-case ever appears, the right shape is an **opt-in parser flag**
(e.g. `preserveComments`) that materializes `UDM.Comment` nodes, kept off the default path. Not worth
building until a real requirement exists (documented here so the idea isn't rediscovered from scratch).

### 2. XSD/schema documentation → **preserve, in USDL (`%documentation`), not UDM**

`<xs:annotation><xs:documentation>` is a *different animal*. It is **semantic metadata about a type or
field** — the authored description of what a thing *means* — and it is **structurally attached** to a
specific declaration (this element, this type, this attribute). Unlike an instance comment, it has
identity, position, and meaning.

And UTL-X already has the home for it: **USDL's `%documentation` directive** ("Type-level documentation",
`USDL10.kt:191`). So the mapping is natural and already-modeled:

```
XSD  <xs:element name="claimId"><xs:annotation><xs:documentation>Unique claim identifier</xs:documentation>…
USDL  claimId: string  %documentation: "Unique claim identifier"
```

Preserving it is **worth doing** because, unlike an instance comment, it earns its keep:

- It rides on a schema node, so there is an exact, lossless place to store and restore it
  (XSD → USDL → XSD round-trips).
- Tooling can *use* it: IDE hover text, generated documentation, and — per the book's "AI as author,
  not executor" thesis — as **context an AI mapping-author consumes** to infer intent. Field
  documentation is precisely the human intent that makes a proposed mapping trustworthy.
- It lives in the **contract**, which is where meaning belongs — travelling with the schema, not the
  payload.

This is largely an *enrichment* task on the XSD↔USDL path (`XSDParser`/`XSDSerializer` ↔ `%documentation`),
**independent of B27** and tracked separately as a feature, not folded into this bug.

### 3. The same split across *every* format

The instance-vs-schema divide is not XML-specific — it holds for the whole format matrix, which is what
makes it a genuine principle rather than a one-off call. Verified against the current parsers:

**Instance / data layer — comments are non-semantic → skip (never preserve in UDM):**

| Format | Instance comments? | Parser today | Verdict |
|---|---|---|---|
| **XML** | yes `<!-- -->` | **crashes** in prolog/epilog | **B27 — skip** |
| **YAML** | yes `#` | already skips ✓ (full-line + inline verified) | correct, no change |
| **JSON** | no (standard) | rejects `//` correctly | N/A — not a bug |
| **CSV** | no standard concept | no comment handling | N/A |
| Avro / Protobuf *(data)* | binary wire format | no comments possible | N/A |
| OData *(payload)* | inherits JSON/XML | — | follows the carrier format |

XML is the *only* instance format that mishandles comments; YAML — the other comment-bearing instance
format — already skips them, which independently confirms "skip" is the right default.

**Schema layer — documentation is semantic → preserve as USDL `%documentation`:**

| Schema format | Documentation construct | → USDL |
|---|---|---|
| **XSD** | `<xs:annotation><xs:documentation>` | `%documentation` |
| **JSCH** (JSON Schema) | `description` / `title` / `$comment` | `%documentation` |
| **Avro** schema | `"doc"` field | `%documentation` |
| **OSCH** (OData EDMX/CSDL) | `Documentation` / `Core.Description` | `%documentation` |
| **TSCH** (Frictionless Table Schema) | `description` / `title` | `%documentation` |
| **Protobuf** (`.proto`) | leading `//` comments (doc convention) | `%documentation` |

Every schema format carries documentation in *some* construct, and every one has the same natural home
in USDL. So the XSD→`%documentation` enrichment (§2) is really the first instance of a general
**schema-doc → `%documentation`** mapping that applies across JSCH, Avro, OSCH, TSCH, and Protobuf too.

### The refinement: it's the *layer*, not the *syntax*

The tempting shortcut — "comments are throwaway, fields are worth keeping" — is **wrong**, and the format
matrix shows why. What decides preservation is the **layer the annotation lives in**, not its syntactic form:

- A **comment in an instance** (XML `<!-- -->`, YAML `#`) is non-semantic → **skip**.
- A **comment in a schema DSL** (Protobuf `.proto` leading `//`) *is* the documentation mechanism —
  `protoc` treats it as the field's doc → semantic → **preserve**.
- A **field in a JSON-based schema** (JSCH/Avro/TSCH `description`/`doc`/`title`) is explicitly
  semantic → **preserve**. And note *why* it's a field at all: JSON has no comments, so a JSON-based
  schema is **forced** to make documentation a structured, addressable field — precisely the
  preserve-worthy form.

So the same syntactic token (a `//` or `#` comment) is discarded in an instance but preserved in a
schema DSL. That is not inconsistent — it is the principle working correctly, because the two live on
different layers.

### The principle that resolves the question

> **Documentation belongs to the *schema*; comments in an *instance* are non-semantic annotations.**
> Preserve documentation in **USDL** (`%documentation`), where it has identity and consumers — for
> **every** schema format (XSD, JSCH, Avro, OSCH, TSCH, Protobuf), whatever construct each uses. Do
> **not** preserve instance comments in **UDM** — skip them (XML, YAML). What decides this is the
> **layer**, not the syntax: the same `//`/`#` token is discarded in an instance yet preserved in a
> schema DSL. "Does it make sense to preserve documentation in an *instance* doc?" No: if the content is
> worth keeping, it is documentation and belongs in the schema/USDL; if it is a throwaway human note, it
> is not data. Either way it does not belong in the instance's UDM.

B27 implements exactly half of that principle — *stop crashing, skip the comment*. The USDL
`%documentation` enrichment (across all schema formats) is the other half, filed as its own feature.

---

## Checklist

- [ ] `skipMisc()` (+ `skipProcessingInstruction`, `skipDoctype`) added; prolog & epilog call sites updated
- [ ] Declaration match tightened (bug #3)
- [ ] `XmlPrologEpilogCommentTest` added (cases 1–7 above)
- [ ] `:formats:xml:test` + `:modules:core:test` green
- [ ] `examples/xml/00-healthcare-claim.xml` parses via CLI end-to-end
- [ ] Cherry-picked to `main`; `formats:xml` tests green there
- [ ] (separate feature) schema-doc → USDL `%documentation` enrichment across all schema formats
      (XSD `<xs:documentation>`, JSCH `description`/`title`, Avro `doc`, OSCH `Documentation`,
      TSCH `description`, Protobuf leading `//`)
