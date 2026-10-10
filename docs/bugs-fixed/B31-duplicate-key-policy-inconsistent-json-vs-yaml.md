# B31: duplicate-key policy is inconsistent across readers — JSON silently last-wins; YAML rejects

**Status:** **OPEN** — found 2026-10-10 by the `test-corpora` per-profile baseline (Phase 0b).
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
