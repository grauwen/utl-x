# Build Plan — `%utlx 1.1` (semantic validation)

| Field | Value |
|---|---|
| Document | utlx-1_1-build-plan |
| Status | Plan — **foundation-first** (measure the parsers, then build); see Phase 0 |
| Scope | Implement `%utlx 1.1`: the `validate.*` core (Tier 1) + `ValidationResult`, as a separate `:validate` module, delivered as the **UTLXS** profile alongside **UTLXe** |
| Spec | `utlx-1_1-semantic-validation.md` (the what); this doc is the how/when |
| Related | `utlx-language-versioning-validation.md` §2.1/§3 (packaging, version gate); `utlx-validate-naming-and-guard-profile.md` (names, two tiers); `parser-strictness-profiles.md` (the `lenient\|standard\|strict` parse/serialize posture — Phase 0b measures baseline *per profile*, 0c hardens to `strict`); `validate-module-spike.md`; `utlx-test-corpora.md` (§5.5, §5.7) |

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

## Phase 0 — Foundation: test infra, parser baseline, hardening, spike · ~1–2 weeks (+ hardening as the baseline demands)

Build capability on a **verified** foundation, not on faith. `validate.*` sits *on top of* the parse —
a verdict is only as sound as the parse beneath it ("validation on a shaky parser is theater") — so the
parse is **measured and brought to a defined bar before 1.1 lands**. Crucially this is not a detour: the
test harness built here *is* the Phase-3 conformance wall, reused.

- **0a — Shared test infra.** Stand up `test-corpora/` + the conformance / roundtrip / cross-format /
  fuzz harness (`docs/architecture/utlx-test-corpora.md`). Serves both hardening *and* 1.1's conformance
  wall (Phase 3) — it is the common prerequisite, not throwaway work.
- **0b — Parser baseline.** Run it against **today's 1.0 parsers/serializers**. Record the number
  (conformance pass rate, roundtrip failures, fuzz crashes/hangs, parser-differentials). This turns
  "how much hardening?" from a guess into data.
- **0c — Harden to a bar (not a rabbit hole).** Fix what 0b surfaces, to a **defined exit bar** —
  conformance green, *N* cpu-hours of fuzz with no new crash/hang/OOM, resource bounds enforced
  (`bounds.yaml`, §5.7). **How much is required before Phase 1 depends on 1.1's first consumer:**

  | 1.1's first consumer | Input | Hardening required before 1.1 |
  |---|---|---|
  | **Content guard** | untrusted (a boundary) | **Full** — the guard's security claim *is* the hardened parser; non-negotiable |
  | **Open-M semantic component** (trusted zone) | trusted / internal | **Baseline only** — production-matured parsers are likely adequate; harden fully *before* any guard use |

- **0d — Validate spike** (parallel; code-independent): run `docs/architecture/validate-module-spike.md`
  — confirm "no per-parser change", measure size, choose the `ValidationResult` shape, land the
  `:core ↛ :validate` architecture test (proven to fail-then-pass).

**Exit:** test harness live and green on 1.0 (both would-be profiles); parser baseline recorded;
hardening at the bar its first-consumer requires; spike findings in (shape chosen, no-per-parser-change
confirmed).

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

**Foundation-first, by measurement not faith.** The one thing that is unambiguously first is the **test
harness (0a)** — it is a shared prerequisite (it *is* the Phase-3 wall), and it *measures* the parsers so
the rest of the order is set by data, not guesswork. Building 1.1 on an unverified parse is validation
theater; but `0c` is **bar-limited**, not open-ended. Because `validate.*` needs no parser change,
hardening is never wasted and never blocks 1.1 — every parser fix strengthens both UTLXe (shipping now)
and the future UTLXS.

```
P0 foundation: test-infra(0a) → measure(0b) → harden-to-bar(0c)   [spike 0d ∥]
   → P1 module/gate/profiles → P2 functions → P4 output/routing → release
                               └→ P3 conformance  (= the 0a harness, now continuous)
                                                P5 tooling/docs (∥ from P2)
P6 guard profile ── separate track, after base 1.1 ───────────────────────────────▶
```

Critical path: **P0 → P1 → P2 → P4 → release.** P3 reuses the 0a harness and runs continuously from P2.
P5 parallels P2–P4. Estimate: foundation **~1–2 weeks** (plus hardening if the 0b baseline is poor or the
guard is the first consumer), then the base-1.1 build **~6–7 weeks** on top.

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
| Building 1.1 on unverified parsers → validation theater / rework | **P0 measure-first**; harden to the bar before 1.1 (fully, if the guard is the first consumer) |
| Hardening becomes an open-ended rabbit hole | **0c is bar-limited** — conformance green + *N* cpu-hrs fuzz clean + bounds enforced, not "until perfect" |
| Bundling 1.1 regresses 1.0 | Full 1.0 suite on both profiles, every build |
| `ValidationResult` node-type churns the core UDM | Model as `Object` (Phase 0 decision) — keep core frozen |
| Cadence coupling (1.1 churn forces UTLXe re-release) | Module + profile split; UTLXe byte-invariant to 1.1 |
| Scope creep into Tier-2 / `ai.*` | Hard line: Tier 2 → security lib (P6); `ai.*` → `utl-x-infer` |

## Definition of done (base 1.1)

UTLXS runs all nine Tier-1 `validate.*` with correct `ValidationResult` semantics and fail-closed
routing; UTLXe and UTLXS both ship from `main`; the 1.0 suite is green on both; the four invariants are
CI-enforced; IDE + docs support 1.1; engine released as `v1.4.x` with a published support matrix and a
1.0 maintenance tag/branch.
