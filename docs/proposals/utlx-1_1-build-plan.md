# Build Plan — `%utlx 1.1` (semantic validation)

| Field | Value |
|---|---|
| Document | utlx-1_1-build-plan |
| Status | Plan — ready to execute after the spike |
| Scope | Implement `%utlx 1.1`: the `validate.*` core (Tier 1) + `ValidationResult`, as a separate `:validate` module, delivered as the **UTLXS** profile alongside **UTLXe** |
| Spec | `utlx-1_1-semantic-validation.md` (the what); this doc is the how/when |
| Related | `utlx-language-versioning-validation.md` §2.1/§3 (packaging, version gate); `utlx-validate-naming-and-guard-profile.md` (names, two tiers); `docs/architecture/validate-module-spike.md`; `docs/architecture/utlx-test-corpora.md` (§5.5, §5.7) |

## Decisions this plan rests on (already made — not reopened)

- `validate.*` is a **stdlib namespace in its own module** `:validate`; **`:core` never depends on
  `:validate`** (architecture-tested). → UTLXe is byte-invariant to 1.1.
- Two build profiles from one codebase/repo: **UTLXe** (`%utlx 1.0`, mapping, N instances) and **UTLXS**
  (`+1.1`, the single semantic component). The build flag *is* the version gate.
- `ValidationResult` lives in `:validate`, modelled as a UDM **`Object`** (pending spike confirmation).
- **No per-format parser/serializer change** for `validate.*` (UDM-layer). Hold this as an invariant.
- Naming: descriptive camelCase. **Tier 1 (core)** here; **Tier 2 (guard profile)** is a *later, separate*
  workstream in the security library.
- Engine release version ≠ language version (engine `v1.4` supports languages `1.0` + `1.1`).

---

## Phase 0 — Spike (de-risk) · ~0.5 day

Run `docs/architecture/validate-module-spike.md`.
**Exit:** "no per-parser change" confirmed; size measured; `ValidationResult` shape chosen; the
`:core ↛ :validate` architecture test is in place and proven to fail-then-pass.

## Phase 1 — Foundation: module, version gate, profiles · ~1 week

- `:validate` module (depends on `:core`); the dependency-direction test wired into CI.
- **Version-header handling:** engine parses `%utlx 1.1`; UTLXe (core profile) **rejects** it with a clear
  "engine too old / validation not in this build" error; UTLXS **accepts** it (spec §2 gate).
  *(One language parser, not two — the header reads a version value, `validate.*` is function calls (no
  new body syntax), and the 1.0/1.1 difference is decided at semantic analysis: version gate + name
  resolution. The language front-end stays in `:core`, version-aware but not `validate`-aware. See
  `utlx-language-versioning-validation.md` "Two kinds of parser".)*
- `ValidationResult` data model (per Phase 0).
- **Two build targets + CI**: `main` emits **UTLXe** and **UTLXS** from one codebase; both publish.
- A no-op `%utlx 1.1` script runs on UTLXS, is rejected on UTLXe.

**Exit:** both artifacts build in CI; the gate behaves correctly both ways.

## Phase 2 — Tier-1 `validate.*` functions · ~2 weeks

Implement, per spec §5, all nine — each **pure / stateless / deterministic**:

`assert` · `required` · `inRange` · `oneOf` · `matches` · `isoDate` · `unique` ·
`mutuallyExclusive` · `atLeastOne`

Plus the result semantics: **chaining via `|>`** (all rules evaluated, no short-circuit), aggregation
(`valid` / `passed` / `failed` / `warnings` / `errors[]` with `field`/`fields` + `severity`), and
**implicit payload passthrough**.

**Design gate — vocabulary completeness.** Before finalising the function list, run
`utlx-validate-vocabulary-coverage.md`: decide whether to add the named idioms it flags as gaps
(`every`/`some` quantification, `count`/cardinality, referential/dynamic membership, composite-key
`unique`, temporal ordering) or defer each with a reason — and **confirm the UTL-X expression
sublanguage has quantifiers / aggregation / `DateTime` comparison / dynamic lookup** (that is where any
hard expressive hole would be, not in `validate.*` itself).

**Exit:** every function matches the spec; chaining produces one merged `ValidationResult`; the
vocabulary-coverage gate is signed off (added or explicitly deferred).

## Phase 3 — The conformance wall (runs from here on, every build) · ~1 week to stand up

- **New 1.1 conformance suite** (spec §10): per-function accept/reject + boundary; chaining; result
  shape; severity (`error` vs `warning`); payload passthrough; purity/determinism (same input → same
  result, no I/O).
- **Full 1.0 suite (the 465 tests) runs against BOTH profiles** — the real guarantee that 1.1 never
  disturbs 1.0. CI gates on both.
- Hook into the `test-corpora/` conformance tier; add the `:core ↛ :validate` test and a
  parser/serializer-untouched check (diff-stat guard on `:core` readers/writers).

**Exit:** 1.1 suite green; 1.0 suite green on UTLXe *and* UTLXS; CI red if either breaks or if `:core`
grows a `:validate` dependency.

## Phase 4 — Output & routing (the UTLXS component) · ~1.5 weeks

- **Serialization:** confirm happy-path payload serialization is unchanged across formats; serialize the
  `ValidationResult` wrapper (as `Object`) where a verdict report is emitted — verify, don't rewrite,
  each writer.
- **Routing on validity:** `valid` → payload/next; invalid → **dead-letter + errors** (fail-closed),
  with an audit record. Wire UTLXS as an **Open-M `mode: component`** semantic node; FEEL routing
  integration per spec.

**Exit:** UTLXS runs end-to-end as an Open-M semantic component; valid passes, invalid dead-letters with
structured errors.

## Phase 5 — Tooling & docs · ~1 week

- **LSP/IDE** (spec §9): `validate.*` completion, `ValidationResult` field access, diagnostics that
  highlight the offending field.
- **Docs:** `validate.*` language reference; the **engine → language support matrix**; a migration note
  ("1.0 scripts run unchanged on a 1.1 engine"); update the books' appendices if the API shifts.

**Exit:** authors can write and debug 1.1 in the IDE; docs published.

## Phase 6 — Tier-2 guard profile · LATER, SEPARATE WORKSTREAM

Structural caps, `conformsTo`, `onlyFields`, `noControlChars`, the `all(...)` combinator, etc. — go in
the **security library**, not `:validate` core; depend on the per-format **parse-time hardening**
(`guard-parser-profile.md`, a different axis). Gated on MIL/guard priorities. **Out of scope for base
1.1.**

---

## Sequencing & critical path

```
P0 spike → P1 foundation → P2 functions → P4 output/routing → release
                            └→ P3 conformance (stand up early, then continuous)
                                             P5 tooling/docs (parallel from P2)
P6 guard profile ── separate track, after base 1.1 ──────────────────────────▶
```

Critical path: **P0 → P1 → P2 → P4 → release.** P3 starts during P2 and runs continuously (test-first
where practical). P5 parallels P2–P4. Rough base-1.1 estimate: **~6–7 weeks** of focused work.

## Invariants that must hold throughout (CI-enforced)

1. `:core` has **no** dependency on `:validate` (architecture test).
2. The full **1.0 conformance suite passes on both profiles**, every build.
3. **No `:core` parser/serializer edits** attributable to `validate.*` (diff-stat guard). If one is
   needed, stop — it falsifies the UDM-layer claim and the §5.5 proof.
4. Every `validate.*` function is **pure / stateless / deterministic** (property tests).

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| `validate.*` leaks into a parser → breaks format-independence | Phase 0 spike + the diff-stat guard; escalate immediately if hit |
| Bundling 1.1 regresses 1.0 | Full 1.0 suite on both profiles, every build |
| `ValidationResult` node-type churns the core UDM | Model as `Object` (Phase 0 decision) — keep core frozen |
| Cadence coupling (1.1 churn forces UTLXe re-release) | Module + profile split; UTLXe byte-invariant to 1.1 |
| Scope creep into Tier-2 / `ai.*` | Hard line: Tier 2 → security lib (P6); `ai.*` → `utl-x-infer` |

## Definition of done (base 1.1)

UTLXS runs all nine Tier-1 `validate.*` with correct `ValidationResult` semantics and fail-closed
routing; UTLXe and UTLXS both ship from `main`; the 1.0 suite is green on both; the four invariants are
CI-enforced; IDE + docs support 1.1; engine released as `v1.4.x` with a published support matrix and a
1.0 maintenance tag/branch.
