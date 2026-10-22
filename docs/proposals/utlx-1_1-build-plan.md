# Build Plan — `%utlx 1.1` (semantic validation)

| Field | Value |
|---|---|
| Document | utlx-1_1-build-plan |
| Status | Plan — **foundation-first** (measure the parsers, then build); see Phase 0 |
| Scope | Implement `%utlx 1.1`: the `validate.*` core (Tier 1) + `ValidationResult`, as a separate `:validate` module, delivered as the **UTLXs** profile alongside **UTLXe** |
| Spec | `utlx-1_1-semantic-validation.md` (the what); this doc is the how/when |
| Related | `utlx-language-versioning-validation.md` §2.1/§3 (packaging, version gate); `utlx-validate-naming-and-guard-profile.md` (names, two tiers); `parser-strictness-profiles.md` (the `lenient\|standard\|strict` parse/serialize posture — Phase 0b measures baseline *per profile*, 0c hardens to `strict`); `validate-module-spike.md`; `utlx-test-corpora.md` (§5.5, §5.7) |

## Decisions this plan rests on (already made — not reopened)

> **Revision 2026-10-12 — packaging reversed.** `validate.*` now ships **inside `:stdlib`** (a
> `validate/` package), **not** a separate `:validate` module. Reason: a separate function module
> reintroduces the cross-module **GraalVM** native-image reflection-reachability failures that forced the
> `stdlib-security` → `stdlib/crypto/` merge (F11) and the B25 lookup bugs. The UTLXe/UTLXs split is
> enforced by the **`:core` version gate**, not by module exclusion — so separation only bought the
> low-value "byte-invariant UTLXe" (spike: native-size delta is small). In-stdlib also gets
> `@UTLXFunction` discoverability on the one proven reflect-config for free. See F21 §Packaging.

- `validate.*` is a **stdlib namespace in a `validate/` package of `:stdlib`** (reflection-safe, one
  native config). `:core` depends on **neither** stdlib-validation; the one-way rule `:core ↛ validation`
  holds as before (`:stdlib` already depends on `:core`, never the reverse).
- Two build profiles from one codebase/repo: **UTLXe** (`%utlx 1.0`, mapping, N instances) and **UTLXs**
  (`+1.1`, the single semantic component). The **`:core` version gate** *is* the split (not a module boundary).
- `ValidationResult` modelled as a UDM **`Object`** (keeps `:core` frozen; spike-confirmed clean).
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

- `validate/` **package inside `:stdlib`** (registered via `@UTLXFunction` + the existing loader /
  registry); the `:core ↛ validation` dependency-direction test wired into CI. *(2026-10-12: a stdlib
  package, not a separate module — see the Decisions revision above.)*
- **Version-header handling + the namespace gate** *(settled 2026-10-13; version-header enforcement
  **IMPLEMENTED 2026-10-16**)*: the engine reads the `%utlx` version and, before running the body, rejects
  (a) a version it doesn't understand (`LanguageVersions.SUPPORTED = {1.0, 1.1}`; e.g. `%utlx 2.0` →
  *"unsupported %utlx version"*) and (b) a version above its configured mode — `Interpreter(maxLanguageVersion)`
  defaults to the latest, and a **1.0-only engine rejects `%utlx 1.1`** (*"not enabled … 1.0-only mode"*).
  Covered by `LanguageVersionModeTest` + conformance `language/version/version_unsupported`. The **CLI mode
  flag is wired (2026-10-16)**: `utlx transform --language-mode 1.0|1.1|utlxe|utlxs` (default latest/1.1)
  flows through `TransformationService` into `Interpreter(maxLanguageVersion=…)`, so a `--language-mode 1.0`
  (`utlxe`) run rejects a `%utlx 1.1` script while still running 1.0 — verified end-to-end on the built CLI.
  The mode's real consumers are the **UTLXe / UTLXs engine deployments** (the CLI/engine), which this covers.
  **Not a daemon concern:** `utlxd` is IDE infrastructure — it should run the latest language (so authors can
  write `validate.*`), *not* a 1.0-only mode. Surfacing `validate.*` in the IDE is a separate workstream
  (completion/diagnostics version-awareness via `exportRegistry(version)` + how 1.1 is presented to authors).
  a 1.0-mode engine **rejects** `%utlx 1.1`. Within an accepted version, **name resolution gates
  namespaces by minimum language version** — a `namespace → minVersion` table (`{ "validate": "1.1" }`)
  rejects a namespaced call whose namespace needs a newer `%utlx` than declared (e.g. `validate.required`
  under `%utlx 1.0` → *"requires %utlx 1.1"*). Gate on the **namespace**, **not** `@UTLXFunction(since)` —
  `since` is an informational "added-in release" tag already set >1.0 on 1.0-valid functions (crypto is
  `since="1.2"`), so gating on it would wrongly break existing 1.0 scripts. So `validate.*` is callable
  **only under 1.1**; availability and the 1.1 semantics (passthrough + routing) arrive together.
  *(One language parser, not two — the header reads a version value, `validate.*` is function calls (no
  new body syntax); the 1.0/1.1 difference is decided at semantic analysis via the version + namespace
  gate. The front-end stays in `:core`, version-aware but not `validate`-aware. See
  `utlx-language-versioning-validation.md` "Two kinds of parser".)*
- `ValidationResult` data model (per Phase 0).
- **One engine + a mode switch (not two artifacts) + CI** *(decision 2026-10-12, see versioning doc)*:
  `main` emits **one** multi-version engine; a **deployment config flag** selects **1.0-only mode**
  (reject `%utlx 1.1`) vs **1.1 mode** (enable passthrough + enforced routing). `UTLXe`/`UTLXs` are those
  *modes*. CI tests the one binary in **both** modes. *(A stripped build omitting the 1.1-semantics code
  is reserved for mandated-absence assurance only — not produced by default.)*
- A no-op `%utlx 1.1` script runs in **1.1 mode**, is rejected in **1.0 mode**.

**Exit:** the one engine builds in CI; the gate behaves correctly in both modes.

## Phase 2 — Tier-1 `validate.*` functions · ~2 weeks

Implement, per spec §5, all nine — each **pure / stateless / deterministic**:

`assert` · `required` · `inRange` · `oneOf` · `matches` · `isoDate` · `unique` ·
`mutuallyExclusive` · `atLeastOne`

Plus the result semantics: **`assert([rules])` canonical** (Model A, spec §5.10 — shortcuts are
single-rule sugar; implicit `|>`-merge rejected), all rules evaluated (no short-circuit), aggregation
(`valid` / `passed` / `failed` / `warnings` / `notices` / `issues[]` with `field`/`fields` + three-level
`severity` `error`/`warning`/`notice`), and
**implicit payload passthrough**.

**Design gate — vocabulary completeness.** Before finalising the function list, run
`utlx-validate-vocabulary-coverage.md`: decide whether to add the named idioms it flags as gaps
(`every`/`some` quantification, `count`/cardinality, referential/dynamic membership, composite-key
`unique`, temporal ordering) or defer each with a reason — and **confirm the UTL-X expression
sublanguage has quantifiers / aggregation / `DateTime` comparison / dynamic lookup** (that is where any
hard expressive hole would be, not in `validate.*` itself).

> **Expression-sublanguage check — DONE 2026-10-13: all four present** (`every`/`some`/`all`/`any`/`none`;
> `count`/`sum`/`min`/`max`/`avg`/`size`; `toDate`/`isBefore`/`isAfter`/`now`; `contains`/`lookupBy`/`get`
> /`keys`/`values`). No hard hole → `assert` is expressively complete vs Schematron/FEEL; the §3 named
> idioms are optional sugar (expressible today), deferred unless a translator/analyzability need appears.
> See `utlx-validate-vocabulary-coverage.md` §5 gate result.

**Exit:** every function matches the spec; `assert([...])` produces one `ValidationResult` (Model A §5.10); the
vocabulary-coverage gate is signed off (added or explicitly deferred).

## Phase 3 — The conformance wall (runs from here on, every build) · ~1 week to stand up

- **New 1.1 conformance suite** (spec §10): per-function accept/reject + boundary; `assert` aggregation (Model A §5.10); result
  shape; three-level severity (`error`/`warning`/`notice`) + the warning-budget routing policy; payload passthrough; purity/determinism (same input → same
  result, no I/O).
- **Full 1.0 suite (the 465 tests) runs against BOTH profiles** — the real guarantee that 1.1 never
  disturbs 1.0. CI gates on both.
- Hook into the `test-corpora/` conformance tier; add the `:core ↛ :validate` test and a
  parser/serializer-untouched check (diff-stat guard on `:core` readers/writers).

**Exit:** 1.1 suite green; 1.0 suite green in **both modes** (1.0-only and 1.1); CI red if either breaks or if `:core`
grows a `:validate` dependency.

## Phase 4 — Output & routing (the UTLXs component) · ~1.5 weeks

- **Serialization — DONE (2026-10-22, verify-not-rewrite):** a `ValidationResult` is a plain UDM `Object`,
  so every writer already serializes it. Verified to **JSON** and **XML** (incl. nested `issues[]` and the
  `payload` wrapper); happy-path payload serialization unchanged. Conformance:
  `validate/result_serializes_xml`.
- **Routing policy (the "enforce") — DONE (2026-10-22):** the pure, transport-agnostic fail-closed
  decision landed as `engine/routing/ValidationRoutingPolicy` — `forward iff failed <= maxErrors AND
  warnings <= maxWarnings` (notices never gate); default `maxErrors=0, maxWarnings=0` (`FAIL_CLOSED`,
  guard-lockable); non-`ValidationResult` outputs (plain mappings) always forward. 11 unit tests
  (`ValidationRoutingPolicyTest`). This is the decide/**enforce**/lock "enforce" over the counts.
- **In-script routing (the "decide") — DONE:** an author can branch on the result today
  (`if (v.valid) …payload else …dead-letter`), verified (`validate/routing_in_script`). The pure
  functions decide; they never prevent output (spec §8).
- **Still to wire (infra-dependent, NOT built):** the dead-letter **transport** + **audit record** sink
  (needs the messaging/Dapr endpoints — EF-series), and wiring UTLXs as an **Open-M `mode: component`**
  node with **FEEL routing**. `ValidationRoutingPolicy` is the decision these plug into; routing a rejected
  payload to an actual dead-letter channel is deployment plumbing layered on top. Gated on Open-M/messaging
  infra availability.

**Exit (partial):** the routing *decision* (fail-closed policy) + serialization + in-script routing are
done and tested; the Open-M component + dead-letter *transport* wiring remains, pending that infra.

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
and the future UTLXs.

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

1. `:core` has **no** dependency on validation code (`validate.*` lives in `:stdlib`; `:core ↛ :stdlib`
   already holds — architecture test). *(Was: "no dependency on `:validate`" — same one-way rule, now
   that validate is a stdlib package rather than a separate module.)*
2. The full **1.0 conformance suite passes on both profiles**, every build.
3. **No `:core` parser/serializer edits** attributable to `validate.*` (diff-stat guard). If one is
   needed, stop — it falsifies the UDM-layer claim and the §5.5 proof.
4. Every `validate.*` function is **pure / stateless / deterministic** (property tests). "Prevent output"
   is NOT a function capability — it is engine/component fail-closed routing on the `ValidationResult`
   (Phase 4), locked by a guard; the functions only *decide* (see F21 "Execution model").

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

in **1.1 mode** the engine runs all nine Tier-1 `validate.*` with correct `ValidationResult` semantics
and fail-closed routing; the **one engine ships from `main`** and is exercised in **both modes**
(1.0-only and 1.1); the 1.0 suite is green in both; the four invariants are
CI-enforced; IDE + docs support 1.1; engine released as `v1.4.x` with a published support matrix and a
1.0 maintenance tag/branch.
