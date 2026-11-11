# `validate.*` — Vocabulary Coverage vs Schematron & FEEL

| Field | Value |
|---|---|
| Document | utlx-validate-vocabulary-coverage |
| Status | Analysis — a design gate for the `%utlx 1.1` Phase-2 function set |
| Purpose | Use **Schematron** and **FEEL** (two mature, battle-tested predicate languages) as a *completeness yardstick* for the `validate.*` vocabulary — not to build a translator, but to make the vocabulary complete-by-construction |
| Related | `utlx-1_1-semantic-validation.md`, `utlx-validate-naming-and-guard-profile.md`, `utlx-1_1-build-plan.md` (Phase 2), `utlx-test-corpora.md` (§5.5, §5.6) |

> **Why this exists.** The question "is our `validate.*` vocabulary rich enough?" is best answered by
> checking it against languages that already solved this: Schematron (ISO/IEC 19757-3, XPath asserts
> with messages) and FEEL (DMN, unary tests and boolean expressions). This doc is that audit. It is a
> *design checklist* for Phase 2; it can later become a *conformance corpus* if a translator is built.

---

## 1. Two completeness questions, not one

`validate.assert` takes an arbitrary UTL-X boolean `check` (with an optional `when` guard), so it is an
**escape hatch**: any *pure* predicate the UTL-X expression language can compute is assertable. The
completeness question therefore splits in two:

1. **Expressive completeness** — can it express the predicate *at all*? → reduces to **"does the UTL-X
   expression sublanguage behind `assert.check` have the operators?"** (arithmetic, comparison, boolean,
   string/date/list functions, **aggregation**, **quantifiers**, **dynamic lookup**).
2. **Declarative completeness** — can it express it as a *named, analyzable* rule rather than an opaque
   `assert` lambda? → about the **named vocabulary**; matters for readability, IDE/engine reasoning, and
   faithful translation (a Schematron context-rule maps to a named `every` far better than to raw `assert`).

Almost every gap below is in layer 2 (and in the expression sublanguage), not a hard hole — because
`assert` backstops layer 1.

> **Accuracy note (2026-11-10).** The matrix below was written as a *design checklist*, and several
> rows carried a ✓ against a function name that was being proposed in this very document rather than
> one that existed. Each row has now been checked against the running build — `validate.noneMatch`,
> `validate.absent`, `validate.impliesPresent` and the Tier-2 structural caps all return
> `Undefined function`, and as `assert` rule kinds they are rejected with "has no rule kind". The
> rows are corrected below. What *is* implemented: the ten registered functions (`assert`, `all`,
> `required`, `inRange`, `oneOf`, `matches`, `isoDate`, `unique`, `mutuallyExclusive`, `atLeastOne`),
> eight declarative rule kinds inside `assert`, and the `every` / `some` quantifiers.

## 2. Coverage matrix

| Test category (Schematron / FEEL) | `validate.*` coverage |
|---|---|
| Value comparison (`< > = ≤ ≥ ≠`) | ✓ `inRange` / `assert` |
| Enumeration / code list | ✓ `oneOf` |
| Pattern / format (regex) | ✓ `matches`. Negative form: **not implemented** — a `noneMatch` was proposed here, never built; use `assert` with a negated check |
| Presence / absence | ✓ `required`. `absent` is **not implemented** — proposed here, never built |
| Mutual exclusion / at-least-one | ✓ `mutuallyExclusive` / `atLeastOne` |
| Conditional "if A then B" | ✓ `assert` `when` guard. `impliesPresent` is **not implemented** — proposed here, never built |
| Uniqueness (single key) | ✓ `unique` |
| Structural caps (depth / size / fields) | ✗ **not implemented** — "Tier-2" was a plan, not a shipped tier |
| Cross-field arithmetic (`a*b=c`, `sum(lines)=total`) | ⚠ `assert` only — needs **aggregation** (`sum`) in the expr language |
| **Quantify: *every / some* element satisfies P** | ✓ **implemented** as the `every` / `some` rule keys inside `validate.assert` ([F24](../features/F24-quantified-validation-rules.md)) — not as standalone functions |
| **Cardinality: count min / max / exact (filtered)** | ✗ **no named fn** |
| **Referential integrity / dynamic membership** | ✗ **no named fn** — `oneOf` is a *static* list |
| **Composite-key uniqueness** | ⚠ `unique` takes one key |
| **Temporal ordering** (`date1 < date2`, within-duration) | ⚠ `assert` only; + purity caveat (§4) |
| Half-open / exclusive ranges `[a..b)` | ⚠ `inRange` is inclusive both ends |

## 3. Gap list — named functions worth adding (prioritised)

1. ~~**Quantification — `every` / `some`.**~~ **DONE** — [F24](../features/F24-quantified-validation-rules.md)
   shipped it as rule *keys* on an `assert` rule (`{ every: "lines", check: (l) => l.qty > 0 }`)
   rather than as standalone functions. That is the better shape: a quantified rule stays one
   inspectable rule-list entry, and the check receives each **element**, which is the structural
   analogue of a Schematron context-rule. Reports one issue per failing element.
2. **Cardinality — `count(field, min, max)`** on a possibly-filtered set ("1–5 line items").
3. **Referential integrity / dynamic membership** — value in a set *derived from the document*
   (`oneOf` with a computed list, or a `references` / `memberOf` validator). Common in cross-record
   checks. **Medium-high.**
4. **Composite-key uniqueness** — extend `unique` to accept a tuple of key fields.
5. **Temporal ordering** — `before` / `after` / `dateOrder` (and optionally within-duration).

Minor: an exclusive/half-open flag on `inRange`.

## 4. Boundaries — NOT gaps; do not "fix" with functions

- **Markup-level assertions.** A Schematron rule on a *comment*, *processing instruction*, or *mixed
  content* cannot be expressed — the **UDM does not model those** (the §5.5 expressiveness frontier).
  This is the price of being format-agnostic, not a vocabulary gap. Record it; don't chase it.
- **Time-relative predicates** ("not in the future", "age ≥ 18"). FEEL/Schematron call `now()`/`today()`,
  which is **impure**. `validate.*` is pure/deterministic, so the reference time must be an **explicit
  input**, never an ambient clock. A genuine limit — and a good discipline (reproducible, auditable
  verdicts). The thing you need is *passing the clock in*, not a `today()` function.

## 5. Verdict

> **Gate result — expression-sublanguage audit RUN 2026-10-13: all four capabilities PRESENT.** The
> conditional below is satisfied. The `:stdlib` registry already provides:
> **quantifiers** `every`/`some`/`all`/`any`/`none` (+ `map`/`filter`/`reduce`/`find`);
> **aggregation** `count`/`size`/`length`/`sum`/`min`/`max`/`avg`;
> **date comparison** `toDate`/`parseDate`/`now`/`isBefore`/`isAfter`;
> **membership/lookup** `contains`/`containsKey`/`includes`/`indexOf`/`get`/`keys`/`values`/`lookupBy`.
> So `assert` can already express `every`/`count`/temporal-ordering/referential rules today — there is
> **no hard expressive hole**. The §3 named idioms (quantification, cardinality, referential,
> composite-unique, temporal-order) are therefore **optional *sugar* over existing functions** (sugar for
> faithful/analyzable translation), not expressiveness blockers — add them if/when a translator or
> analyzability need arises; otherwise defer. *(Caveat: confirm the exact `every`/`some` semantics —
> `(array, predicate) → bool` — when wiring `assert`.)*

- **Expressively: yes — conditional on the expression sublanguage** *(now confirmed, see gate result above)*.
  `assert` backstops everything, so the real risk is not `validate.*` the namespace but whether UTL-X's
  **expression/stdlib** has **quantifiers** (`all`/`any` over collections), **aggregation**
  (`sum`/`count`), **`DateTime` comparison**, and **dynamic lookup** — **all present**.
- **Declaratively: ~5 named idioms short** (quantification, cardinality, referential, composite-unique,
  temporal-order). Closing them makes Schematron/FEEL tests translate to *analyzable* rules, protecting
  the "readable, auditable, IDE-reasoned" value.
- **Two boundaries** (markup; purity-vs-clock) are permanent and correct.

## 6. How to use this

- **Now (Phase 2 design gate):** fold the §3 named functions into the candidate `validate.*` set, or
  explicitly defer each with a reason. Before finalising the function list, run the §5 action: confirm
  the expression sublanguage has quantifiers / aggregation / date comparison / dynamic lookup.
- **Later (if a translator is built):** turn Schematron and FEEL rule sets (e.g. EN 16931 / Peppol BIS
  Schematron) into a **conformance corpus** — run the reference processor and the translated
  `validate.*`, assert the verdicts agree (the XSLT-style cross-check, §5.6). That converts "rich
  enough?" from a hunch into a measured coverage percentage.

## 7. Scope note

This is a *vocabulary completeness* analysis. Actually building a **Schematron → `validate.*`** (or
FEEL-unary-test → `validate.*`) compiler is a *separate, undecided* feature — a sibling to
`F12-xslt-to-utlx-migration`. It is not required to benefit from this audit; the audit's job is to make
the `validate.*` vocabulary complete-by-construction regardless.
