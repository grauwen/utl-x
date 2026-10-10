# F21: `%utlx 1.1` — Semantic Validation (`validate.*`)

**Status:** Design complete — implementation planned (see build plan)
**Priority:** High (next minor of the pure language line; prerequisite for the content guard and Open-M semantic components)
**Created:** October 2026
**Updated:** October 2026

---

## Summary

`%utlx 1.1` adds the **`validate.*`** standard-library namespace and the **`ValidationResult`** UDM node
— first-class *semantic* validation (cross-field consistency, value ranges, code lists, required fields).
It keeps every 1.0 guarantee (pure, stateless, deterministic, single-pass), so it is a **minor** version.
Delivered as the **UTLXS** build profile alongside the mapping-only **UTLXe**.

## The Gap

UTL-X 1.0 maps and restructures data but cannot *assert* that a value is correct before proceeding.
Cross-field correctness — "latitude in range", "line total = price × qty", "release caveat is permitted"
— is a natural part of transformation and belongs **in the language**, not in bolt-on boolean routing or
hand-written SDK components.

## The Feature (Tier 1 — core)

Nine descriptive, camelCase functions, each returning a `ValidationResult`:

`assert` · `required` · `inRange` · `oneOf` · `matches` · `isoDate` · `unique` ·
`mutuallyExclusive` · `atLeastOne`

Plus: chaining via `|>` (all rules evaluated, merged into one result), `ValidationResult`
(`valid`/`passed`/`failed`/`warnings`/`errors[]`, `error`/`warning` severity), implicit payload
passthrough, and fail-closed routing (invalid → dead-letter + audit).

## Not to be confused with (relationship to other features)

| | Kind of validation | Tracked by |
|---|---|---|
| **F01** inline schema declaration + **EF02** engine schema validation | **schema conformance** (JSON Schema / XSD) | F01, EF02 |
| **F21** (this) `validate.*` | **semantic** validation (cross-field / value rules on the UDM) | this card |

`validate.conformsTo` (the **Tier-2 guard profile** — structural caps, allow-list) overlaps the
schema-conformance territory but is a **separate, later** workstream in the security library, not part of
base 1.1. See *Scope* below.

## Design (do not duplicate — see proposals)

- Spec: [`../architecture/utlx-1_1-semantic-validation.md`](../architecture/utlx-1_1-semantic-validation.md)
- Naming + two-tier vocabulary: [`../architecture/utlx-validate-naming-and-guard-profile.md`](../architecture/utlx-validate-naming-and-guard-profile.md)
- Vocabulary coverage (vs Schematron & FEEL): [`../architecture/utlx-validate-vocabulary-coverage.md`](../architecture/utlx-validate-vocabulary-coverage.md)
- Versioning / packaging (UTLXe vs UTLXS, the version gate): [`../architecture/utlx-language-versioning-validation.md`](../architecture/utlx-language-versioning-validation.md) §2.1/§3
- Build plan (phases): [`../architecture/utlx-1_1-build-plan.md`](../architecture/utlx-1_1-build-plan.md)
- Testing: [`../architecture/utlx-test-corpora.md`](../architecture/utlx-test-corpora.md); spike: [`../architecture/validate-module-spike.md`](../architecture/validate-module-spike.md)

## Packaging

Own module **`:validate`** (never linked by `:core`), so `validate.*` ships in the **UTLXS** profile and
is absent from **UTLXe** — UTLXe stays byte-invariant to 1.1. `ValidationResult` modelled as a UDM
`Object`. **No per-format parser/serializer change** (UDM-layer feature).

## Implementation status

Per the build plan: Phase 0 spike → P1 foundation (module + version gate + profiles) → P2 the nine
functions → P3 conformance wall (1.0 suite green on both profiles) → P4 routing/output (UTLXS as an
Open-M `mode: component`) → P5 tooling/docs. Rough estimate ~6–7 weeks for base 1.1.

## Scope

- **In:** Tier-1 `validate.*`, `ValidationResult`, chaining, routing — base `%utlx 1.1`.
- **Out (separate):** Tier-2 guard profile (structural caps, hardened parse) → security library / `utlx-mil`;
  `ai.*` probabilistic validation → `%utlx 2.0` / `utl-x-infer`.
