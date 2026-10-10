# B29: XML parser silently *skips* a DOCTYPE and passes *undefined* entity references through as literal text — accepts malformed XML that a strict/guard profile must reject

**Status:** **OPEN** — found 2026-10-10 by the `test-corpora` per-profile baseline (Phase 0b).
**Priority:** Medium (correctness + strict/guard fail-closed posture). **Not** a critical security vuln — see [Scope](#scope-this-is-not-xxe).
**Created:** October 2026
**Component:** `formats/xml/src/main/kotlin/com/glomidco/utlx/formats/xml/xml_parser.kt`
— hand-written XML parser: `skipDoctype()` (~L445) and `parseEntityReference()` (~L328, unknown-entity branch L361).

> **One-line:** a document with a `<!DOCTYPE …>` and a reference to an **undefined** entity is
> **accepted**: the DOCTYPE (incl. its `<!ENTITY>` definitions) is **skipped**, and the undefined
> reference `&b;` is kept verbatim as the literal text `&b;`. Both should be **rejected** under the
> strict/guard profile; the undefined-entity passthrough is arguably a well-formedness bug in **any**
> profile.

---

## Scope: this is NOT XXE

The initial baseline read flagged "XXE / billion-laughs." **That was wrong** — reading the parser
corrects it. The parser is **safe** against both classic XML attacks:

- **No XXE / external fetch.** `skipDoctype()` consumes and discards the entire DOCTYPE (tracking
  `[`/`]` internal-subset depth); it never resolves `SYSTEM`/`PUBLIC` identifiers or reads any
  external resource.
- **No billion-laughs / entity expansion.** Because the DOCTYPE is skipped, custom entities
  (`<!ENTITY a …>`) are **never defined**, so they cannot be expanded. `parseEntityReference()`
  expands only the five built-ins (`lt gt amp apos quot`) and numeric char-refs (`&#…;`/`&#x…;`),
  which are bounded.

So `external_entities: false` in `expectations/bounds.yaml` already holds in practice. The residual
issue is **reject-vs-skip-vs-literal**, not entity amplification.

## Problem (two behaviours)

**A. DOCTYPE is *skipped*, not *rejected*.** `skipDoctype()` was added deliberately (B27, to not choke
on prologs). Skipping is fine for a lenient mapper, but for a **guard / strict** profile the posture is
wrong: a clean message carries no DOCTYPE, so strict should **reject it outright** — "reject, never
ignore." Silent skip = accept-and-discard, which violates fail-closed.

**B. Undefined entity reference → literal passthrough.** `parseEntityReference()` falls through for any
non-builtin, non-numeric entity to *"Unknown entity - keep as is"* and returns the literal `&entity;`.
Per XML 1.0, a reference to an **undefined** entity is a **well-formedness error** — a conformant parser
must reject. Keeping `&b;` as literal text instead:
- **accepts malformed XML**, and
- silently injects the literal string `&b;` into the UDM as data (confusing/incorrect; may be
  mis-handled on re-serialisation).

## Reproduction

Corpus case `test-corpora/corpora/security/xml/doctype_entity.xml`:
```xml
<?xml version="1.0"?>
<!DOCTYPE root [ <!ENTITY a "AAAAAAAAAA"> <!ENTITY b "&a;&a;…"> ]>
<root>&b;</root>
```
Baseline probe (identity transform) → **accepted** (exit 0); `<root>` text becomes the literal `&b;`.
Both the `strict` and `lenient` oracles expect **reject** (`reason: doctype-xxe`), so it shows as a GAP
for both profiles in `test-corpora/baseline.json`.

## Fix

- **B (correctness, profile-independent):** treat a reference to an **undefined** entity as a parse
  error (well-formedness) rather than literal passthrough — or, at minimum, make it rejectable and
  reject it under `standard`+`strict`.
- **A (strict/guard profile):** when the profile is `strict`, **reject any DOCTYPE** rather than skip it
  (lenient/standard may keep skipping). This is the `parser-strictness-profiles.md` §4 "XML DOCTYPE/DTD"
  row: *lenient* = skip, *strict* = "DOCTYPE rejected outright." Ties to `external_entities: false`
  (already honoured) in `expectations/bounds.yaml`.

Add corpus cases for the undefined-entity and DOCTYPE paths to lock the fix (and keep the "no external
fetch / no expansion" guarantees as explicit negative tests).

## Related
- `docs/architecture/parser-strictness-profiles.md` §4 (DOCTYPE row) and §11.4 (LANGSEC: reject at the boundary).
- `docs/architecture/utlx-test-corpora.md` §5.7 (malformed vs malicious; the leniency profile).
- `test-corpora/baseline.json` (the Phase-0b measurement that surfaced this); `test-corpora/scripts/HARNESS.md`.
- B27 (prolog/DOCTYPE skipping — the intended behaviour this refines for the strict profile).
