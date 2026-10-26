# F25: Declarative Validation Rule Kinds

**Status:** **Implemented** (2026-10-25, pending commit)
**Priority:** High — it is the policy form the guard needs, and it should land **before** any further validator vocabulary
**Created:** October 2026
**Depends on:** [F21](F21-utlx-1-1-semantic-validation.md) (`validate.*`), VB01 (lambda `check`), VB07 (rule-id discipline)
**Related:** [F24](F24-quantified-validation-rules.md) (`every`/`some`), [VB06](../bugs-fixed/VB06-chained-validators-fail-open.md) (chaining rejected)

---

## Summary

A `validate.assert` rule may now declare its check as a **named kind** instead of a lambda:

```utlx
$input |> validate.assert([
  { rule: "order-fields-present", required: ["orderId", "customer"] },
  { rule: "qty-in-range",         inRange: { field: "qty", min: 1, max: 100 } },
  { rule: "ccy-allowed",          oneOf: { field: "ccy", values: ["EUR","USD"] }, severity: "warning" },
  { rule: "wholesale-min",        inRange: { field: "qty", min: 50, max: 10000 },
                                  when: (p) => p.channel == "WHOLESALE" },
  { rule: "price-positive",       check: (p) => p.price > 0, message: "price must be positive" }
])
```

Exactly **one kind per rule**: `check` (the lambda escape hatch) or one of `required`, `inRange`,
`oneOf`, `matches`, `isoDate`, `unique`, `mutuallyExclusive`, `atLeastOne`.

## Why — the problem it replaces

The previous way to apply several named validators was to compose them with `validate.all`:

```utlx
$input |> validate.all([
  $input |> validate.required(["orderId", "customer"]),
  $input |> validate.inRange("qty", 1, 100),
  $input |> validate.oneOf("ccy", ["EUR", "USD"])
])
```

Three problems, which surfaced when the shape was questioned the day it shipped:

1. **The issues report `rule: null`.** A named validator has no id to report, so none of the failures
   is attributable. VB07 had just made `rule` mandatory on `assert` rules precisely because a
   downstream consumer indexes on it — a guard maps each id to one bit of a bounded failed-rule
   table and uses the set of failing ids as its audit record. The combinator form produced exactly
   the report that discipline exists to prevent. (See `all_combinator.yaml`, whose expected output
   is three `rule: null` entries.)
2. **It is policy-as-code, not policy-as-data.** `validate.assert([...])` was chosen (VB01 Option A)
   because *"the policy is then data: a rule list that can be inspected, audited and generated"*. An
   `all([...])` expression is three function applications, visible only to the interpreter — it
   cannot be inspected, hashed for a rule-set version, or compiled to a verifier table, which is the
   premise of the guard-compiler.
3. **Repeating `$input` is a correctness hazard.** Nothing stops `$input.header |> validate.required([…])`
   inside `all($input, …)`: the verdict about a sub-object merges with verdicts about the whole, the
   merged `payload` is `$input`, and an issue says `field: "orderId"` with no indication it meant
   `header.orderId`.

Declarative kinds fix all three: one `$input`, every failure attributable, policy still data.

## Design

### Kinds delegate; they do not reimplement

`assert` dispatches each declarative kind to the named validator of the same name and stamps the
rule's id and severity onto whatever it reported. So the declarative and standalone forms share one
implementation and cannot drift, and the declarative form inherits the existing hardening for free —
`unique` over a non-array is a malformed rule ([VB04](../bugs-fixed/VB04-named-validators-fail-open-on-malformed-arguments.md))
in both forms, and `oneOf`'s numeric canonicalisation ([VB03](../bugs-fixed/VB03-validate-oneOf-numeric-membership-fails.md))
applies to both.

This also resolves a tension that existed before F25: the robust Tier-1 validators carried **no rule
ids**, while the id-carrying `assert` rules could only express checks as raw interpreter arithmetic.
You had to choose. Now a rule is both robust and attributable.

### Semantics

| | |
|---|---|
| **Check count** | a kind contributes the validator's **own** count — `required` over two fields is two checks — so `passed` means the same in both forms |
| **Message** | the rule's `message` overrides the validator's generic wording; absent, the validator's wording is kept |
| **Severity** | the rule's severity applies to every issue the kind produces |
| **`when` guard** | unchanged — a false guard skips the rule and counts it as passed, without running the kind |
| **Zero or two kinds** | an error issue naming the rule and listing the kinds found |
| **Malformed kind spec** | an error issue naming the shape expected (`inRange` → `{ field, min, max }`) |
| **Quantifier + kind** | rejected: `every`/`some` apply a *lambda* per element, a kind operates on the payload |
| **Numeric exactness** | a kind compares **exactly** (VB03 canonicalisation); a `check` lambda compares as IEEE-754 double. Deliberate — see [B35](../bugs-fixed/B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — and a reason to prefer kinds for numeric constraints |

### Both call forms stay

The named validators remain callable standalone (`$input |> validate.required(["a"])`). The
enforcement point for "a policy must be data" is the **guard-compiler**, not the language — its §1
states *"anything outside is a build-time compile error"*, so a policy written as bare standalone
calls simply will not compile. Removing the standalone form would be a breaking change buying a
property that is already guaranteed elsewhere, while losing a useful idiom for ordinary mappings.
Documented as: declarative kinds are the **policy** form, standalone calls a mapping convenience that
is not guard-compilable.

### `validate.all` is now narrower

`all` remains, scoped to **merging policies** — a shared base `assert` plus a channel-specific one,
each already carrying ids, which is what the guard spec means by *"conjunction of results"*. The
guide warns against using it to compose bare validators.

It does work across **multiple named inputs** (`input: orders json, customers xml`), merging verdicts
from different documents and formats. Two caveats, documented rather than fixed: `field` alone is
ambiguous across inputs, and the merged `payload` is only whichever was piped in. Naming rules by
source (`orders-id-present`, `customers-vat-present`) disambiguates at no cost, and is preferred over
adding a `source` field to the report — the same reasoning that removed `path` in F24: no new report
key until the location design is settled.

## Implementation (2026-10-25)

- **`:core`** — new `semantic/ValidationRuleKinds.kt` holds the single definition of the kind list.
  It lives in `:core` because both sides need it and only `:stdlib → :core` is legal; the first
  attempt imported `ValidateFunctions` into the interpreter and did not compile, which is the exact
  `:core ↛ :stdlib` boundary whose reflective workaround caused B19/B25 on the native image.
- **`:core` `interpreter.kt`** — the quantifier branch recognises a declarative kind and reports the
  combination accurately. Its malformed-rule path also strips any declarative kind, so `:stdlib` does
  not then count an injected `check` plus that kind as "two rule kinds".
- **`:stdlib` `ValidateFunctions.kt`** — `RULE_KINDS` from the core definition, plus `runKind` and
  `kindShape`; `assert` selects the single kind and delegates. No result-shape change.

## Tests

- `conformance .../validate/assert_declarative_rule_kinds.yaml` — five rules mixing four kinds, a
  `when` guard, a non-default severity and a lambda; `passed:3 failed:2 warnings:1`, every issue
  carrying its author id. ✅
- `ValidateFunctionsTest` (`:stdlib`) — 12 added: id propagation, the per-kind check count, all eight
  kinds delegating, severity and message overrides, zero/two/malformed kinds, the non-array spec, the
  `when` interaction, inherited VB04 hardening, and lambda-plus-declarative in one policy.
- Full conformance **550/550**.

## What this unblocks

The remaining vocabulary work — guard-profile Tier 2 (`maxDepth`, `maxBytes`, `onlyFields`, `absent`,
`impliesPresent`, `isFinite`, `noneMatch`, …) and the coverage gaps (composite-key `unique`,
`count(field, min, max)`, temporal ordering) — is **14-odd new validators**. Each should be added as a
rule kind, which is why F25 came first: otherwise every one of them would have been added twice.
