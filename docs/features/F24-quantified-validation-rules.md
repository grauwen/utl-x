# F24: Quantified Validation Rules (`every` / `some`)

**Status:** **Implemented** (2026-10-24, pending commit)
**Priority:** High — the largest gap in the `validate.*` vocabulary against Schematron and FEEL, and the prerequisite for per-node reporting
**Created:** October 2026
**Depends on:** [F21](F21-utlx-1-1-semantic-validation.md) (`validate.*`), VB01/VB02 (lambda `check` and `when`)
**Closes gap #1 of:** [`../proposals/utlx-validate-vocabulary-coverage.md`](../proposals/utlx-validate-vocabulary-coverage.md)

---

## Summary

A rule in `validate.assert` can now quantify over a named array:

```utlx
$input |> validate.assert([
  { rule: "line-qty-positive", every: "lines", check: (l) => l.qty > 0,
    message: "line qty must be positive", field: "qty" }
])
```

The `check` receives **each element**, not the payload. `every` expands into one check per element,
so each failing element becomes its own issue **and its own entry in the counts**:

```json
{ "valid": false, "passed": 1, "failed": 2,
  "issues": [
    {"rule": "line-qty-positive", "field": "qty", "severity": "error"},
    {"rule": "line-qty-positive", "field": "qty", "severity": "error"}
  ] }
```

The report shape is unchanged — no new key. See §Location for why there is no element locator.

## Why this was the priority

The vocabulary audit named it: *"Quantification — `every(array, rule)` / `some(array, rule)`. The
biggest gap: Schematron's per-context firing **is** universal quantification ('every line has a
positive amount'); FEEL has `every`/`some`. Today only raw `assert` + higher-order. High priority."*

It is also a **reporting** fix, not only an expressiveness one. Before F24 the same constraint had to
be written as a whole-payload lambda:

```utlx
{ check: (p) => count(filter(p.lines, l => l.qty <= 0)) == 0, field: "lines" }
```

which collapsed any number of bad elements into **one** issue with `field: "lines"`, so even the
*number* of failures was lost. Per-element issues and per-element counts are the gain.

## Design

### `every` expands; `some` collapses

| | Semantics | Issues |
|---|---|---|
| `every: "<field>"` | all elements must satisfy the check | **one per failing element** |
| `some: "<field>"` | at least one element must satisfy it | one, for the whole array |

`every` is the Schematron context-rule analogue: `every: "lines"` plus a `check` is
`<rule context="lines/item">` plus `<assert test="…">`. Expansion happens in the interpreter, so the
`:stdlib` `assert` still receives only boolean checks and its aggregation is unchanged.

### Edge semantics, chosen deliberately

| Case | Behaviour | Why |
|---|---|---|
| `every` over `[]` | passes, and counts **one** check | vacuous truth; keeping one check makes the rule visible in `passed` rather than vanishing |
| `some` over `[]` | fails | nothing satisfies the check |
| element check throws | error issue **for that element** | VB01's rule applied per element — one bad element is reported, not an aborted validation |
| all elements evaluated | always — no short-circuit | the same rule the multi-rule case follows, so one failure never hides another |
| field absent / not an array | malformed rule, `error` | fail-closed, consistent with `validate.unique`: a quantifier over a non-array checked nothing |
| `every` **and** `some` on one rule | malformed rule | ambiguous intent |
| quantifier with a non-lambda `check` | malformed rule | see the fail-open note below |
| `when` guard false | rule skipped; **no expansion** | the guard decides applicability before the quantifier runs |

### A fail-open hole found during implementation

The resolver's legacy-form early return — *"neither `check` nor `when` is a lambda, so there is
nothing to defer"* — sent `{ every: "lines", check: true }` straight to the stdlib as an ordinary
passing rule. A malformed quantifier therefore failed **open**. The bail now also tests for the
presence of `every`/`some`, so a quantified rule always routes through the branch that can reject it.
There is a regression test for exactly this.

## Location — deliberately absent

An earlier draft of this feature added `path: String?` to `ValidationIssue`, set to a JSON Pointer
(`/lines/1`) by the quantifier. **It was removed before release.** Three reasons, in order of weight:

1. **It makes a format-neutral layer JSON-aware.** JSON Pointer is JSON's convention (RFC 6901).
   `ValidationIssue` is a `:stdlib` type over the UDM, which exists precisely so XML, JSON, CSV and
   YAML share one model. There is no JSON Pointer or XPath anywhere else in `:core` or `:stdlib` —
   verified — so the field would not have followed a precedent, it would have established one. This
   is the same objection that correctly killed `.text()` (XML-specific) and the `[condition]`
   predicate selector (XPath-shaped): a serialization's conventions do not belong in the universal
   layer. For a CSV payload the natural answer is not a pointer at all, it is *row 2*.
2. **The mechanism was a smell.** `path` reached the issue by being stuffed into a copy of the
   author's rule record (`out["path"] = …`) and read back out in `:stdlib`
   (`asString(p["path"])`) — the rule object used as an internal transport channel for an
   engine-computed value. It was also inconsistent with its neighbours: `every` and `some` are
   *removed* after expansion (there is a test for that), while `path` was injected. Two keys
   consumed, one synthetic key added, same channel. Nothing stopped an author writing
   `path: "anything"` on an ordinary rule and having it appear in a report, unvalidated.
3. **It was only half a location.** `/lines/1` is the element, not `/lines/1/qty`. The check is a
   lambda over the whole element, so the interpreter genuinely cannot know which leaf failed;
   `field: "qty"` is the author's hint. A consumer would have had to read `path` *and* `field` and
   splice them — a key that looks like a complete answer and is not.

What remains available: the issue count and the `passed`/`failed` split tell an operator **how many**
elements failed, and `payload` carries the record for inspection. What is not available: which one.

**Open question, to be settled with the SVRL projection** (see
`../architecture/utlxe-validation-enforcement-and-observability.md`). The candidates were a plain
`index: Int?` (neutral — an array index is a UDM concept, with each consumer rendering it as
`/lines/1`, `lines[2]` or `row 2`), a structured `at: {array, index, field}`, or identifying the
element by a business key the author designates (`every: "lines", key: "sku"` → *"the line with sku
B"*), which is both more neutral and more useful than a position, since indices shift between
retries. Deciding this together with SVRL means the report shape changes once rather than twice.

## Not in scope

- **A named `validate.every(...)` function.** A lambda cannot live in a `UDM.Object`, so a standalone
  named validator cannot take one; the rule-key form reuses the existing interpreter interception and
  the rule record's `severity` / `when` / `message` for free. The proposal's `every(array, rule)`
  spelling is satisfied in substance.
- **Nested quantification** (`every` over `lines`, then over `lines[].taxes`). Expressible today only
  by a lambda inside the element check; a nested form would need a path-composing design.
- **Any element locator** — see §Location.
- **An SVRL projection.** Per-element issues are the structural prerequisite and now exist, but
  SVRL's `location` attribute needs the locator decision first.

## Tests

- `conformance .../validate/assert_every_per_element.yaml` — three lines, two failing, two issues,
  `passed:1 failed:2`. ✅
- `conformance .../validate/assert_some_quantifier.yaml` — `some` with no satisfying element. ✅
- `F24QuantifiedRulesTest` (`:core`, 13 tests) — per-element expansion in array order,
  all-elements evaluation, both empty-array semantics, a per-element unevaluable check, all four
  malformed-quantifier cases, both `when` interactions, and that the `every`/`some` keys do not leak
  to the stdlib **and no synthetic key is injected into the rule record** (the §Location smell).
  Verified as a regression guard: against the pre-F24 interpreter, **10 of the 13 fail**.
- Full conformance **546/546**, `language/validate` 17/17.

### A test-infrastructure note

`:core` tests capture what the interpreter hands to `validate.assert` via a new shared
`ValidateAssertCapture`. It is shared **because** `tryRegisterFromLookup` writes into the global
`StandardLibraryImpl.nativeFunctions` and skips a name already present — so two test classes each
registering their own `validate.assert` would silently share whichever ran first, and the loser would
assert on nothing. `VB02WhenGuardTest` was refactored onto the same holder. This is the third time
that global map has produced a vacuous-test hazard (see also B34's recorder-name collision).
