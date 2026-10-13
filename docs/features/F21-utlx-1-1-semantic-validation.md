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
Delivered as the **UTLXs** build profile alongside the mapping-only **UTLXe**.

## The Gap

UTL-X 1.0 maps and restructures data but cannot *assert* that a value is correct before proceeding.
Cross-field correctness — "latitude in range", "line total = price × qty", "release caveat is permitted"
— is a natural part of transformation and belongs **in the language**, not in bolt-on boolean routing or
hand-written SDK components.

## The Feature (Tier 1 — core)

Nine descriptive, camelCase functions, each returning a `ValidationResult`:

`assert` · `required` · `inRange` · `oneOf` · `matches` · `isoDate` · `unique` ·
`mutuallyExclusive` · `atLeastOne`

**Composition — `assert([rules])` is canonical (Model A, spec §5.10):** put all constraints in one rule
list; the shortcuts are single-rule sugar, not chained. Implicit `|>`-merge (Model B) was rejected — it
forces merge-awareness into every function and turns the policy into code rather than an inspectable rule
list (full rationale + the rejected alternative in §5.10). Plus: `ValidationResult`
(`valid`/`passed`/`failed`/`warnings`/`notices`/`issues[]`, three severities `error`/`warning`/`notice`),
implicit payload passthrough, and fail-closed routing (invalid → dead-letter + audit) with
deployment-configured, guard-lockable **per-severity budgets** (`maxErrors`/`maxWarnings` — "block after
N errors / M warnings"; a guard pins `maxErrors = 0`).

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
- Versioning / packaging (UTLXe vs UTLXs, the version gate): [`../architecture/utlx-language-versioning-validation.md`](../architecture/utlx-language-versioning-validation.md) §2.1/§3
- Build plan (phases): [`../architecture/utlx-1_1-build-plan.md`](../architecture/utlx-1_1-build-plan.md)
- Testing: [`../architecture/utlx-test-corpora.md`](../architecture/utlx-test-corpora.md); spike: [`../architecture/validate-module-spike.md`](../architecture/validate-module-spike.md)

## Packaging — **revised 2026-10-12: in `:stdlib`, not a separate module**

`validate.*` ships **inside `:stdlib`** (a `validate/` package), **not** a separate `:validate` module.
The UTLXe/UTLXs distinction is enforced by the **`:core` version gate** — a `%utlx 1.0` script cannot
resolve `validate.*`, and a UTLXe engine rejects `%utlx 1.1` — **not** by physical module exclusion.

**Why this reverses the earlier "own module" decision:**
1. **GraalVM.** A separate function module reintroduces cross-module reflection reachability in the
   closed-world native image — the exact failure class that forced the `stdlib-security` →
   `stdlib/crypto/` merge (F11) and the B25 "stdlib lookup not reaching the native image" bugs.
   In-stdlib rides the **one** proven reflect-config.
2. **The gate, not the boundary, enforces the split.** Separation only ever bought "UTLXe byte-invariant
   to 1.1"; the spike pre-concluded the native-size delta is small and not worth marketing. The real
   requirement (UTLXe can't run `validate.*`) is the **version gate's** job and works wherever the code lives.
3. **Discoverability for free.** In stdlib, `validate.*` uses `@UTLXFunction` + the existing loader /
   registry like every other function (IDE completion, `exportRegistry()`, docs) — no parallel machinery.

**Invariants preserved either way:** `:core` never depends on validation; `ValidationResult` is a plain
UDM `Object` (core frozen); **no per-format parser/serializer change** (UDM-layer feature).

## Execution model — `validate.*` *decides*, the engine *enforces* (can it prevent output?)

`validate.*` functions are **pure**: they return a `ValidationResult` and **cannot halt or prevent
output** in the language. A script always produces a value; the author branches on `.valid` (spec §7
Example 2). This is what keeps 1.1 a *minor* — every 1.0 guarantee (pure, stateless, deterministic,
single-pass) is intact.

"Prevent output" is an **engine / component** behavior one layer up: deployed as a UTLXs validation
component, the engine reads the `ValidationResult` and routes — `valid` → forward the payload; `invalid`
→ **dead-letter + audit, not forwarded** (fail-closed, build-plan Phase 4). A **guard locks** that
enforcement so it is not author-reachable.

That is the **decide / enforce / lock** split — identical to the parse-profile model
(`../architecture/parser-strictness-profiles.md` §5.2): the pure layer decides, the engine enforces, the
deployment locks. It is why the *same* `.utlx` is a harmless mapping on UTLXe and an output-gating guard
on UTLXs with no code change — and why the fail-closed routing is **not** part of the `validate.*`
functions themselves.

**How the version gates it — by NAMESPACE (settled 2026-10-13).** The engine holds a
`namespace → minimum-%utlx-version` table (`{ "validate": "1.1" }`; a future `ai` → `"2.0"`) and, at
stdlib name resolution, **rejects** a namespaced call whose namespace needs a newer `%utlx` than the
script declares. So `validate.*` is callable **only under `%utlx 1.1`** — and because you must be in 1.1
to call it, you automatically get the 1.1 engine semantics (passthrough + enforced routing); **availability
and semantics arrive together**. Gate on the **namespace**, **not** `@UTLXFunction(since)` — `since` is an
informational "added-in release" tag already set >1.0 on 1.0-valid functions (crypto is `since="1.2"`), so
gating on it would wrongly reject existing 1.0 scripts. This keeps "1.0" a fixed, portable contract.
(Spec §3 / §8 item 3.)

## Implementation status

Per the build plan: Phase 0 spike → P1 foundation (module + version gate + profiles) → P2 the nine
functions → P3 conformance wall (1.0 suite green on both profiles) → P4 routing/output (UTLXs as an
Open-M `mode: component`) → P5 tooling/docs. Rough estimate ~6–7 weeks for base 1.1.

## Scope

- **In:** Tier-1 `validate.*`, `ValidationResult`, `assert`-based composition (Model A §5.10), routing — base `%utlx 1.1`.
- **Out (separate):** Tier-2 guard profile (structural caps, hardened parse) → security library / `utlx-mil`;
  `ai.*` probabilistic validation → `%utlx 2.0` / `utl-x-infer`.
