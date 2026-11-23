# F32: the field allow-list — `validate.onlyFields` and `validate.absent`

> **F = the shared language stack** (`:core` rule kinds, `:stdlib` validators). Extends
> [F21](F21-utlx-1-1-semantic-validation.md) (the `validate.*` namespace) and
> [F25](F25-declarative-validation-rule-kinds.md) (declarative rule kinds). Hardened along the
> lines [VB01](../bugs-fixed/VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md),
> [VB04](../bugs-fixed/VB04-named-validators-fail-open-on-malformed-arguments.md) and
> [VB06](../bugs-fixed/VB06-chained-validators-fail-open.md) established.

**Status:** Implemented — 2026-11-22.
**Component:** `modules/core` (`semantic/ValidationRuleKinds.kt`), `stdlib` (`validate/ValidateFunctions.kt`, `Functions.kt`)
**Asked for:** a content-guard policy must reject a message carrying a field that is not on its
approved list, and must say *which rule* rejected it.

## The problem

The rejection was already expressible:

```utlx
count(difference(keys($input), ["orderId", "qty", "ccy"])) == 0
```

That gives the right **verdict** and an **empty `issues[]`**. Nothing is attributed: no rule id, no
field, nothing for an audit record, and nothing to prove a hardware failed-rule bitmask against —
and `issues[].rule` is exactly the oracle such a verifier is proved against. A `check` lambda is no
better: it carries an id but cannot be compiled to a verifier table, because the compilable
expression subset admits comparisons, booleans and integer arithmetic only, not `keys` or
`difference`.

So the gap was never capability. It was **attribution**.

## What ships

Two declarative rule kinds, so a policy stays data and every failure carries the author's id:

```utlx
$input |> validate.assert([
  { rule: "no-unknown-fields", onlyFields: ["orderId", "qty", "ccy"] },
  { rule: "no-internal-tags",  absent: ["trace", "internalRoutingTag"] }
])
```

```json
{"rule": "no-unknown-fields", "field": "isAdmin",
 "message": "Field 'isAdmin' is not permitted; allowed: [orderId, qty, ccy]", "severity": "error"}
```

Both are also standalone validators (`$input.header |> validate.onlyFields([…])`), which is how a
rule is aimed at a sub-node.

### `onlyFields` — a key-set question

Only the named fields may be present. **One issue per offending field**, each naming it, so a
failed-rule table can list them rather than reporting "something was wrong".

It never looks at values. A permitted key holding `null` is permitted; an unexpected key holding
`null` is still unexpected.

Its check count is the number of fields **present**, not the length of the allow-list: the question
asked of the message is *"is this key allowed?"*, once per key. An empty object therefore passes
with zero checks — nothing crossed, which is the right verdict for an allow-list.

### `absent` — a value question

None of the named fields may be **set**. `isSet` is the exact mirror of `required`, so
`{"trace": null}` passes `absent(["trace"])` just as it fails `required(["trace"])`. One notion of
presence across the namespace matters more than either reading on its own.

When the policy is *"this key must not appear at all, whatever its value"*, that is a key-set
question — leave the field off `onlyFields` instead. The two divide along that line deliberately,
and a test pins the division rather than only documenting it.

### Fail-closed, and where it matters most

An allow-list aimed at a node that is not an object reports a **malformed rule**, not a pass:

```
validate.onlyFields: malformed rule — payload is not an object, so its field set cannot be checked
```

This is VB04's failure mode in the place it costs most. An empty property map is what a scalar or
an array yields, so without the guard a rule pointed at the wrong node would run zero checks and
report `valid: true` — silently disabling the one rule that keeps an unexpected field from
crossing. On a fail-closed policy (`maxErrors = 0`) `valid: true` **forwards the payload**.

Same treatment for a non-array field list, a non-string entry, the wrong arity, and a chained
`ValidationResult` payload (VB06).

## Why `noAdditionalProperties` is not here

Its reference set **is** the approved contract. The guard rule library writes it as a bare
`validate.noAdditionalProperties($msg)` immediately after
`validate.conformsTo($msg, "contracts/approved/…")`, with no field list of its own. Without contract
resolution there is nothing to compare against, so it waits for `conformsTo` rather than being
given an invented signature. `onlyFields` covers the same ground wherever the permitted set can be
written down.

## A finding worth recording: `validate.all` does not carry ids

`validate.all` merges `ValidationResult`s, and it **preserves** a rule id when the results it
merges have one. Bare named validators have none, so

```utlx
$input |> validate.all([$input |> validate.required([…]), $input |> validate.onlyFields([…])])
```

returns `"rule": null` on every issue — correct verdict, no attribution (conformance
`all_combinator.yaml`). That matters because the guard rule library's own §9 composing example is
written in exactly that shape. For a policy whose ids map to bits of a failed-rule table, the form
is `validate.assert` with declarative kinds; `validate.all` is for merging results that already
carry ids — one `assert` per node, for instance.

## Governance

These two come from the **guard rule library** (the allow-list and structural-cap validators
specified in `guard-rule-library.md`), whose placement was recorded as *"kept guard-owned for now"*.
Promoting them into the **core set** moves the *mechanism* — compare a key set against a list — and
leaves the *policy* private: which fields, which contracts, which classifications. That is the same
split `validate.oneOf` already has, a public mechanism over private value lists, so it sets no new
precedent. `conformsTo`, the structural caps, releasability and dirty-word stay guard-owned, on
merit rather than secrecy: each needs either contract resolution or private policy data.

## Verification

```
stdlib/src/test/kotlin/.../validate/F32FieldAllowListTest.kt                       16 tests
conformance-suite/.../language/validate/field_allow_list_rule_kinds.yaml
conformance-suite/.../language/validate/onlyfields_non_object_fails_closed.yaml
examples/utlx/advanced/validation-guard-allow-list.utlx  (+ .input.json, .reject.json)
```

The fixtures are picked up by `ConformanceStrategyParityTest`, so both kinds are exercised on
TEMPLATE, COPY and COMPILED without a hand-written parity test.

The load-bearing assertions are the negative ones:

- **`onlyFields on a non-object is a malformed rule, not a pass`** — over a scalar, a number, an
  array and null. A silent pass here forwards the field the rule exists to stop.
- **`absent treats a present-but-null field as not set, mirroring required`** — asserts all three
  of `absent`, `required` and `onlyFields` on one document, pinning the division of labour.
- **`assert carries the rule id onto an allow-list failure`** — the whole point of the feature,
  asserted on `issues[].rule` rather than on the verdict.
- **`onlyFields on an empty object passes with zero checks`** — distinguishes the legitimate
  zero-check pass from a silently disabled rule.
