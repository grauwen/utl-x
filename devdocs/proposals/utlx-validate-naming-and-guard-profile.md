# `validate.*` — Naming Convention & the Guard Profile

| Field | Value |
|---|---|
| Document | utlx-validate-naming-and-guard-profile |
| Repo | `github.com/grauwen/utl-x` |
| Status | Accepted — reconciliation of `validate.*` across the 1.1 spec and the MIL books |
| Depends on | `utlx-1_1-semantic-validation.md` (the 1.1 `validate.*` namespace) |
| Related | `utlx-mil/docs/guard-rule-library.md` (the guard rule library); MIL books _The Content Guard_, _The Mapper_ |

> **One line.** The canonical `validate.*` style is **descriptive, camelCase** (`validate.inRange`,
> not `validate.range`). The namespace has two parts: a **core set** every edition carries, and the
> **guard rule library** — a structural/allow-list extension used by the content guard — defined in
> `guard-rule-library.md`. This doc records the convention, the rename that aligned the 1.1 spec and
> the Mapper book to it, and the full vocabulary table.

---

## 1. Decision

1. **Convention: descriptive, camelCase.** A call site should read as intent
   (`validate.inRange(lat, -90, 90)`), which matters most for the guard's reviewers and
   accreditors. Terse (`range`) and snake_case (`iso_date`) names are rejected.
2. **The guard's vocabulary is the model.** The content guard is the most demanding consumer, so its
   names (already descriptive) set the standard; the 1.1 spec and the Mapper book were brought up to
   it, not the other way round.
3. **Two parts, one namespace.** The **core set** ships in every edition. The **guard rule
   library** is a structural/allow-list extension — the caps and allow-list functions that only a
   guard needs — defined in `guard-rule-library.md`. It is not required of a non-guard engine.
4. **Not "Tier 1 / Tier 2".** Those words are taken, and by the more established meaning:
   **Tier 1 = the data formats** (JSON, XML, CSV, YAML, OData) and **Tier 2 = the schema /
   metadata formats** (XSD, JSCH, Avro, Protobuf, OSCH, TSCH) — `civilian-guard.md` §Formats and
   the Guard book ch. 4. Numbering the `validate.*` split the same way produced two meanings for
   one phrase inside one repository, with `civilian-guard.md` writing "guard-profile parsers
   (Tier 1/2)" about *formats* while this document wrote "Tier 2 (guard profile)" about
   *functions*. "Tier" now means formats everywhere. The two parts of `validate.*` are named after
   the documents that define them: **the core set** (the 1.1 spec §5.1–5.9) and **the guard rule
   library** (`guard-rule-library.md`). Note "guard profile" bare is also avoided — that is the
   *parser* side, `guard-parser-profile.md`.

## 2. Rename applied (terse/snake_case → canonical)

| Was (1.1 spec draft) | Now (canonical) | Reason |
|---|---|---|
| `validate.range` | **`validate.inRange`** | descriptive; guard already used it |
| `validate.codelist` | **`validate.oneOf`** | descriptive; guard already used it |
| `validate.iso_date` | **`validate.isoDate`** | camelCase |
| `validate.mutually_exclusive` | **`validate.mutuallyExclusive`** | camelCase |
| `validate.at_least_one` | **`validate.atLeastOne`** | camelCase |

Applied in: `utlx-1_1-semantic-validation.md` (§5.3–5.9 and examples) and the Mapper book
(`07-military-grade-validation.typ`, `appendix-a-quick-reference.typ`). The Guard book needed no
change — it was already the model.

## 3. The core set — core validation (every edition)

| Function | Purpose |
|---|---|
| `validate.assert(rules)` | general form: named rules, each a condition + message (+ field, severity) |
| `validate.required(fields)` | named fields must be present |
| `validate.inRange(field, min, max)` | numeric field within bounds |
| `validate.oneOf(field, values)` | field is one of an allowed set (enum / releasability) |
| `validate.matches(field, pattern)` | field matches a regular expression |
| `validate.isoDate(field, format)` | field is a well-formed date-time |
| `validate.unique(arrayField, keyField)` | no duplicate keys in a collection |
| `validate.mutuallyExclusive(fields)` | at most one of a set is present |
| `validate.atLeastOne(fields)` | at least one of a set is present |

All core-set functions return a `ValidationResult`, chain with `|>`, and keep every 1.0 guarantee
(pure, stateless, deterministic, single-pass). See `utlx-1_1-semantic-validation.md`.

## 4. The guard rule library (structural / allow-list extension)

Defined in `utlx-mil/docs/guard-rule-library.md`. These enforce the guard's allow-list and
anti-bomb posture and have no equivalent in the base spec, so they keep their own descriptive names.

| Function | Category |
|---|---|
| `validate.conformsTo(node, contract)` | schema allow-list |
| `validate.onlyFields(node, fields)` | field allow-list |
| `validate.noAdditionalProperties(node)` | field allow-list |
| `validate.maxDepth(node, n)` | structural cap |
| `validate.maxBytes(node, n)` | structural cap |
| `validate.maxNodes(node, n)` | structural cap |
| `validate.maxStringLength(node, n)` | structural cap |
| `validate.maxArrayLength(node, n)` | structural cap |
| `validate.noControlChars(field)` | keyword / dirty-word |
| `validate.noEmbeddedMarkup(field)` | keyword / dirty-word |
| `validate.noneMatch(field, patterns)` | keyword / dirty-word |
| `validate.absent(fields)` | field allow-list (negative) |
| `validate.impliesPresent(a, b)` | cross-field semantics |
| `validate.isFinite(field)` | value constraint |
| `validate.all(results)` | combinator — merge many results into one `ValidationResult` |

## 5. Rationale

- **Readability at the call site** is the deciding factor; the guard's audience (reviewers,
  accreditors) reads the rules as documentation.
- **The hardest consumer sets the vocabulary**, so the reference case is the demanding one.
- **No loss of power**: the structural caps that only a guard needs live in a named profile rather
  than being forced into, or omitted from, the base spec.

## 6. Open questions

- Should the guard rule library eventually be promoted into the base spec as an optional module, or
  stay guard-owned in `guard-rule-library.md`? **Partly settled, 2026-11-22**: the field allow-list
  — `onlyFields` and `absent` — was promoted into the core set and ships in `utl-x`
  (`docs/features/F32-field-allow-list-validators.md`). The reasoning, for the rest of the library:

  - What moves is the **mechanism** (compare a key set against a list). What stays guard-owned is
    the **policy** — which fields, which contracts, which classifications. Same split
    `validate.oneOf` already has: a public mechanism over private value lists, so this sets no new
    precedent.
  - `conformsTo` and `noAdditionalProperties` stay on merit, not secrecy: their reference set *is*
    the approved contract, so they need USDL contract resolution and Message Contract mode as a
    gate. `noAdditionalProperties` in particular has no field list of its own — it is written as a
    bare call after `conformsTo` — so it cannot be implemented ahead of it without inventing a
    signature.
  - The structural caps overlap what the parse profile already enforces during read (depth is
    bounded at 64 under `strict`), so as UDM rules they are defence in depth rather than the only
    line.
  - Releasability and dirty-word need private policy data (classification lists, keyword packs) and
    `^` metadata semantics that are not settled.

- **`validate.all` does not carry rule ids.** It preserves an id when the results it merges have
  one, but a bare named validator has none, so `validate.all([$msg |> validate.required([…]), …])`
  returns `rule: null` on every issue (conformance `all_combinator.yaml`). The §9 composing example
  in `guard-rule-library.md` is written in exactly that shape, so as written it produces a correct
  verdict with **no attribution** — nothing to map onto a failed-rule bitmask. A policy whose ids
  matter must use `validate.assert` with declarative rule kinds; `validate.all` is for merging
  results that already carry ids, one `assert` per node. Worth correcting in §9.
- A schema-ref variant of `validate.oneOf` (list sourced from a contract enum rather than inline) —
  tracked in `utlx-1_1-semantic-validation.md` §11.
- Conformance: one behaviour suite that both the engine (core set) and the guard edition (core set
  plus guard rule library) must pass, proving parity of names and semantics.
