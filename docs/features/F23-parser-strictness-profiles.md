# F23: Parser strictness profiles — per-reader policy enforcement + operator selection (`ParseProfile`)

> **Status:** **IN PROGRESS** (Phase 1 complete; Phase 2 next). The policy model is designed and stable;
> the plumbing landed reader-by-reader. **JSON, XML, YAML, CSV are all threaded** (Phase 1 ✅); the
> CLI/engine *selection* (Phase 2) remains. Patch-releasable in increments (no behaviour change until a profile is
> actually *selected* — the engine applies the `standard` default throughout, so every increment is
> regression-free: **conformance 522/522**, harness **standard 24/24** at each step).
>
> **This doc is a delivery tracker.** The **normative design** — the model, the dimension × profile
> matrix, the depth rule (§4.1), the parse/serialize-independence decision (§6.1), fail-closed semantics,
> and where it lives (§5/§9) — is `docs/architecture/parser-strictness-profiles.md`. This file does **not**
> restate it; it records *scope, phased plan, and current status*.
>
> **Depends on / pairs with:** **B29** (XML DOCTYPE / undefined-entity), **B31** (duplicate-key policy),
> **B32** (deep-nesting crash — per-profile `maxDepth` is the bridge; **F22** is the permanent fix),
> `core/format/ParseOptions`, the readers `formats/{json,xml,yaml,csv}`, the CLI
> (`modules:cli` `TransformationService`), and the engine/daemon deployment config.
>
> **This is utlx-code / `formats/*` + `core` + `cli` (an `F##`, like B29/B31/B32 are `B##`) — not an
> engine (`EF`) feature.** Parsers are utlx code.

## Summary

A **deployment-set strictness policy** — `lenient | standard | strict` — that governs how the format
readers recognise untrusted input, distinct from `validate.*` (schema conformance) and orthogonal to the
always-on resource bounds. It is the security-critical dial behind "**guard = strict, locked**": at
`strict` the readers reject anything malformed/ambiguous with a reason code (fail-closed); `standard` is
the general-purpose default; `lenient` tolerates/normalises benign real-world deviations.

The feature is one `ParseOptions`/`ParseProfile` in `:core` (`com.glomidco.utlx.core.format`), threaded
into every reader so the policy is enforced **identically across formats** (one depth number, one
duplicate-key rule, one entity/DOCTYPE posture — §4.1), then **selected** by the operator
(CLI flag / engine config), never by data or the mapping (§5).

## Problem (why this feature exists)

The Phase-0 `test-corpora` baseline surfaced that the readers were **inconsistent and under-specified**
at the trust boundary:

- **B31** — duplicate keys handled differently per reader (JSON silently last-wins; YAML rejects).
- **B29** — XML silently *skipped* a DOCTYPE and passed *undefined* entity references through as literal.
- **B32** — JSON/XML recursive descent *crashed* (StackOverflow) on deep input instead of rejecting.

These are not independent bugs to patch one-by-one; they are symptoms of **having no single policy layer**.
F23 is that layer: fix the behaviour *once*, per profile, enforced uniformly — and make strictness a
first-class, operator-selectable, guard-lockable policy rather than per-reader happenstance.

## Design (phased) — with status

**Phase 0 — foundation** ✅
`ParseOptions` + `ParseProfile{LENIENT,STANDARD,STRICT}` in `:core`, with `strict()/standard()/lenient()`
factories, `of(profile)`, `DEFAULT = standard()`, and `ParseOptionsTest`. Depth 64 / 256 / 256.

**Phase 1 — thread the readers** ✅ (JSON/XML/YAML/CSV all threaded)
Each reader takes `ParseOptions` and enforces the policy it governs; defaults remain `standard`, so no
behaviour change until a profile is selected (Phase 2).

| reader | depth (`maxDepth`) | duplicate keys (B31) | DOCTYPE / undefined entity (B29) | status |
|---|---|---|---|---|
| **JSON** | counter vs `maxDepth` | reject (std/strict) / last-wins (lenient) | — | ✅ threaded + tested |
| **XML** | `min(maxDepth, XML_MAX_SAFE_DEPTH=1024)` §4.1 | — | strict rejects DOCTYPE; std/strict reject undefined entity; lenient keeps literal | ✅ threaded + tested |
| **YAML** | → SnakeYAML `nestingDepthLimit` (raises default 50) | via `fromProfile` (SnakeYAML `isAllowDuplicateKeys` + check) | n/a | ✅ threaded + tested |
| **CSV** | n/a — flat records (§4.1) | n/a | n/a | ✅ inert `options` param (uniform dispatch; grammar = dialect, limits = bounds) |

**Phase 2 — operator selection** (next)
- **CLI:** `--parse-profile strict|standard|lenient` → `ParseOptions.of(profile)` threaded into
  `TransformationService.parseInput / parseInputBytes`. Named to reserve a future `--serialize-profile`
  (§6.1). Default `standard`.
- **Engine / daemon:** deployment-config key (e.g. `engine.yaml: parse.profile`, optionally per-format)
  → `ParseProfile`. **Guard = `strict`, locked**; a config-lock **wins over any flag/request** and the
  flag is **ignored** on a guard (fail-safe) — a stray `--parse-profile lenient` can never weaken it (§5/§9).
- **Engine/UTLXE API:** embedders (e.g. Open-M) select a profile at embed time; a guard/UTLXS build pins it.

## Scope / non-goals

- **In scope:** the **parse** axis only — `ParseProfile` + per-reader enforcement + operator selection.
- **Reserved (separate future `F##`):** **`SerializeProfile`** — serialize strictness is a *different*
  knob-set (canonical order, escaping, quoting, compact/pretty, charset/BOM), **not** `ParseProfile`
  reused on the output path (architecture doc §6.1). Not built here.
- **Not** schema validation (`validate.*`), **not** the always-on resource bounds (`bounds.yaml`) — those
  are orthogonal axes that compose with the profile.
- **Not** language syntax — never in the `%utlx` header, never data-reachable (§5).

## Relation to the bugs it closes

- **B31** → duplicate-key policy now uniform (JSON + YAML profile-driven; reject by default, lenient
  last-wins). Remaining: engine profile *selection*.
- **B29** → XML DOCTYPE + undefined-entity now profile-driven (strict rejects DOCTYPE; std/strict reject
  undefined entity; lenient tolerates). Remaining: engine profile *selection*.
- **B32** → per-profile `maxDepth` replaces the recursive-depth stop-gap; **F22** (iterative parsing) is
  the permanent fix that lets the depth ceiling rise safely. F23 provides the *policy*; F22 the *mechanism*.

## Open questions

Tracked in the architecture doc §12 (toggle granularity, per-format defaults, reason-code taxonomy,
`strict`-requires-canonical-input, the serializer canonical spec, profile naming). Feature-level:

1. **CSV toggles** — does CSV need any profile knob (e.g. ragged-row tolerance under lenient) or is it
   purely bounds + dialect? (Leaning: dialect + bounds; no leniency dial.)
2. **Config shape** — one `parse.profile` key vs per-format map; precedence when both are set.
3. **`SerializeProfile`** — when a concrete need appears, spin a separate `F##` (not a phase here).

## Sources / related

- **Normative design:** `docs/architecture/parser-strictness-profiles.md` (model, §4.1 depth rule,
  §5 policy/lock, §6.1 parse/serialize independence, §9 operator selection).
- `core/format/ParseOptions` + `ParseOptionsTest`; readers `formats/{json,xml,yaml,csv}`.
- `test-corpora/` — harness + per-profile oracles (`expectations/profiles/{strict,standard,lenient}/`)
  + `expectations/bounds.yaml`; the Phase-0b baseline that surfaced B29/B31/B32.
- Bugs `docs/bugs-fixed/B29-*`, `B31-*`, `B32-*`.
- Sibling feature **F22** (iterative / indexed parsing — the permanent depth-crash fix).
