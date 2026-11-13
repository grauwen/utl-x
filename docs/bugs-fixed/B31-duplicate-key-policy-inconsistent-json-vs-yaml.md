# B31: duplicate-key policy is inconsistent across readers — JSON silently last-wins; YAML rejects

**Status:** **FIXED — 2026-11-13.** JSON, YAML and XML share one parse profile, and the
profile is selectable where this report's own component reaches: the CLI and the engine. (The
daemon's inability to select one was tracked out of here as an IB defect — see the note at the
end.)
**daemon** alone cannot select a profile. See "What is actually left" below.
**last-wins under lenient** (unit-tested in `json_tests.kt`; conformance 522/522 holds). This removes the
JSON-vs-YAML *default* inconsistency (both now reject by default). **YAML is now threaded onto the same
profile** too: `YAMLParser.ParseOptions.fromProfile(core ParseOptions)` maps `allowDuplicateKeys` (and
`maxDepth`→`nestingDepthLimit`) from the shared profile, so YAML rejects duplicates under standard/strict
and is **last-wins under lenient** — the same policy, uniformly (unit-tested in `YAMLTests.kt`; harness
standard 24/24; conformance 522/522). JSON + XML + YAML now share one profile. **Still open:** wire the
engine/daemon's deployment-config → `ParseProfile` **selection** so a deployment can actually choose
lenient/strict (the engine currently applies the STANDARD default uniformly). Found 2026-10-10 (Phase 0b).
**Priority:** Medium (correctness + cross-reader consistency + strict/standard policy).
**Created:** October 2026
**Component:**
- `formats/json/src/main/kotlin/com/glomidco/utlx/formats/json/json_parser.kt` — `parseObject()`
  (L67–89): `mutableMapOf` + `properties[key] = value` → **silent last-wins, no detection, no option**.
- `formats/yaml/src/main/kotlin/com/glomidco/utlx/formats/yaml/YAMLParser.kt` — `allowDuplicateKeys`
  (L50, default **false**) + `containsKey` check that **throws** `Duplicate key found` (L160–161).

> **One-line:** the *same* logical deviation — a duplicate object/mapping key — is handled **opposite**
> ways by two readers: **JSON accepts** it (silently keeps the last value), **YAML rejects** it. A guard
> cannot reason uniformly about "duplicate keys" when the answer depends on the input format.

## Problem

Two issues, one root cause (no shared policy):

1. **JSON silently drops data.** `{"id":1,"id":2}` parses to `{"id":2}` — the first value vanishes with
   no error or warning. This is the classic "Parsing JSON is a Minefield" divergence and, per
   `parser-strictness-profiles.md` §4 (duplicate-key row), **standard and strict should reject** it as
   ambiguous (only `lenient` may last-wins). JSON has **no** knob to do so.
2. **Readers disagree.** YAML already has the right shape — a profile-like `allowDuplicateKeys` that
   defaults to reject. JSON has nothing. So the stack contains a **parser differential of its own
   making** (`§11.4`): identical intent, format-dependent behaviour.

## Reproduction

| corpus case | reader | current | strict/standard want | lenient wants |
|---|---|---|---|---|
| `malformed/json/duplicate_key.json` (`{"id":1,"id":2}`) | JSON | **accept** (last-wins) | reject | accept |
| `malformed/yaml/duplicate_key.yaml` (`id: 1 / id: 2`)   | YAML | **reject** | reject | accept |

In `test-corpora/baseline.json` these show as mirror-image GAPs: JSON dup-key fails `standard`+`strict`
(accepts where they want reject); YAML dup-key fails `lenient` (rejects where it wants accept).

## Impact

- **Silent data loss** in JSON (dropped first value) — can mask an injected/overriding key.
- **No uniform policy** — a guard's "reject ambiguous input" stance holds for YAML but not JSON today.

## Fix

Make duplicate-key handling a **single profile-driven policy applied consistently across all readers**:
- `strict` / `standard` → **detect and reject** (ambiguous).
- `lenient` → last-wins (accept), the current JSON behaviour.
- Give JSON the same knob YAML has (generalise `allowDuplicateKeys` into the shared profile), rather
  than two independent per-reader flags.

This is the **first concrete instance** of a principle the strictness design calls out: a profile is
only meaningful if it is enforced **the same way in every format reader**. Worth fixing as the template
for how all §4 toggles thread through the readers.

## Related
- `docs/architecture/parser-strictness-profiles.md` §4 (duplicate-key row), §5 (one policy, all readers), §11.4 (parser differentials).
- `docs/architecture/utlx-test-corpora.md` §5.7. `test-corpora/baseline.json`.
- **B29**, **B30** — the same growing theme: readers accept/handle malformed input **inconsistently**; profiles must unify it.

---

## Verified, 2026-11-13

The status read "Still open: wire the engine/daemon's deployment-config → `ParseProfile`
selection". Measured: for this report's subject — one duplicate-key policy across readers —
it is done.

```bash
# {"a":1,"a":2}
--parse-profile lenient    => {"a":2}                         last-wins
--parse-profile standard   => JSON parse error: Duplicate key
--parse-profile strict     => JSON parse error: Duplicate key
(no flag)                  => rejects — standard is the default
```

YAML behaves identically on the same input, which is the cross-reader consistency this report
was raised about: JSON no longer silently last-wins while YAML rejects. XML shares the same
profile object.

Selection reaches the parsers on both surfaces this component has:

| surface | selection |
|---|---|
| CLI | `--parse-profile strict\|standard\|lenient` |
| engine | `UtlxEngine.createStrategy` → `EngineConfig.effectiveParseProfile()` → every strategy |

## The daemon half was not this report's to carry

The original status named the "engine/daemon" together. The daemon could not select a profile
either — but a daemon defect is **IB**, not B, and IB reports live in `devdocs/` because the
IDE and daemon do not ship on `main`. Carrying it here put daemon material into a document
that ships, describing a component that does not.

Split out, fixed, and filed where it belongs:
`devdocs/bugs-fixed/IB08-daemon-cannot-select-a-parse-profile.md`.

This report's own `Component:` is the readers in `:core`/formats, which is what keeps it a B.
