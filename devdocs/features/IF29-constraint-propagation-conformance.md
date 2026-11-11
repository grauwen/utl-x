# IF29: Constraint soundness — static constraint propagation + runtime conformance

**Status:** **Proposed.** Adds a *second completeness dimension* to MC analysis (constraint soundness
beside coverage) and a runtime conformance gate. No language/semantics change.
**Priority:** Medium-high — silent constraint violations (a concat overflowing a `maxLength`, a value out
of range) are a common, hard-to-debug production failure that the analysis can catch *before* data flows.
**Created:** June 2026
**Component:** mcp-server / analysis (a **constraint-propagation pass** over the derivation tree) +
engine/validator (a **runtime conformance check** against the output contract's constraints). IDE surface:
show the per-field soundness verdict beside coverage.
**Depends on:** IF11 (coverage — this is the orthogonal second axis), IF20 (correspondence set + inferred
derivations whose constraints are propagated), USDL (carries the constraints: `%maxLength`, `%minimum`,
`%maximum`, `%pattern`, `%values`/enum, normalized across formats).

> **One-liner:** A field can be **covered** (sourced) yet **constraint-unsound** (its value can violate the
> field's `maxLength`/range/enum/pattern). Propagate the output constraint **backward** through the
> derivation → **provably safe / provably unsafe / unprovable risk**; surface the risk, record the policy,
> and where static proof is impossible, enforce at runtime.

See the theory book *Many to One*, ch. 9 §"Constraint Soundness: A Second Completeness."

---

## Motivation

The classic example: output `address` has `maxLength: 300`; the mapping concatenates house number, street,
city, postal code, state. Most addresses fit; a real **Indonesian** address overflows 300. Coverage reports
the field **covered** (it is sourced) — and the mapping silently produces invalid output or a downstream
failure. This is the **schema-complete-but-instance-incomplete** gap (book ch. 3), and — unlike arbitrary
data validity — much of it is **statically decidable** from the metadata.

## Two axes of completeness

- **coverage** (IF11) — is every required output field *sourced*?
- **constraint soundness** (this) — does every sourced field's value provably (or by enforced policy)
  *satisfy the field's constraints*?

A mapping can be coverage-complete and constraint-unsound. The report must show both.

## Static pass — backward constraint propagation

For each constrained output field with a derivation, propagate the bound backward from the input bounds:

| Derivation | Propagation (length) |
|---|---|
| copy `a` | `len ≤ maxLen(a)` |
| `concat(a,b,…)` | `len ≤ Σ maxLen(parts) + separators` |
| `substring`/`truncate(x,n)` | `len ≤ n` (a *fix*, see policy) |
| numeric `a+b`, `sum(xs)` | range from operand ranges / cardinality × max |

Verdicts:
- **provably safe** — propagated worst case within the target bound → static guarantee, no action.
- **provably unsafe** — worst case (even modest inputs) exceeds the bound → flag at design time; the mapping
  is wrong.
- **unprovable** — an input is unbounded, or worst-case > bound but typical data fits → a **constraint risk**
  to surface (not a silent pass).

Tractability: **length and numeric range** propagate cleanly; **pattern/regex** rarely do — defer those to
the runtime check. Static pass is only as strong as the inputs' *declared* bounds (declare-don't-infer).

## The policy is an authored decision (never an engine default)

On a provable-unsafe or risk verdict, the remedy is recorded intent, surfaced for human/AI choice — the
engine must not silently truncate (data loss) or silently overflow (downstream break):

- **truncate** explicitly — `truncate(concat(...), 300)` (lossy; must be chosen, not defaulted);
- **validate & reject** — fail the instance at runtime;
- **widen the contract** — if you own the target;
- **accept rare failure** — explicit, with the runtime gate catching it.

## Runtime conformance gate (for the unprovable residue)

Where static proof is impossible, the obligation becomes an **execution-time conformance check**: validate
the produced output against the contract's constraints; a violating instance is **rejected / dead-lettered**,
not passed downstream. This is the `conformance` layer of the validation stack (cf. IF28).

## Phasing

- **Phase 1 — detect & report.** Compute per-field soundness verdicts (length + range) and show them beside
  coverage (IF11). No enforcement — just the second axis made visible.
- **Phase 2 — policy capture.** Let the author record the remedy (truncate / reject / widen / accept) per
  flagged field; generate the explicit construct.
- **Phase 3 — runtime conformance gate.** Validate output against contract constraints at execution;
  reject/dead-letter violations.

## Honest limits

- Only as strong as declared input bounds; unbounded inputs → flag, not guarantee.
- Worst-case is conservative — a *possibility* of violation, not a certainty (most addresses fit).
- Pattern/regex constraints: defer to runtime.

## Out of scope / non-goals

- Not a general theorem prover — prove the tractable constraints (length, range), surface the rest.
- Not a silent fixer — never auto-truncate/clamp without a recorded policy decision.
- Not a coverage replacement — it's the orthogonal second axis (IF11 stays).
