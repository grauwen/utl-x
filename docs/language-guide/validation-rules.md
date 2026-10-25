# Validation Rules

Author semantic validation rules with `validate.*` and route on the verdict.

**Requires `%utlx 1.1`.** The `validate.*` namespace is gated, and the gate is **static**: a script
declaring `%utlx 1.0` is rejected for *containing* a `validate.*` call, before any data is processed —
even if the call sits on a branch that would never be taken
([VB05](../bugs-fixed/VB05-version-gate-is-evaluation-time-not-static.md)). The error names the call
and exits non-zero. A variable that happens to be named `validate` is unaffected: a namespace is only
a namespace when the name is not bound.

---

## Overview

UTL-X separates *deciding* whether a record is acceptable from *enforcing* what happens to it.
A `validate.*` call is **pure**: it reports counts and issues, and never applies a threshold or
routes anything. The forward/reject decision is a policy layered on top.

The vocabulary has two tiers, and the order matters:

| Tier | What | When to reach for it |
|------|------|----------------------|
| **1 — named validators** | `required`, `inRange`, `oneOf`, `matches`, `isoDate`, `unique`, `mutuallyExclusive`, `atLeastOne` | **Default.** Declarative, null/type-aware, robust on missing and mistyped data. |
| **2 — `validate.assert`** | A list of rules whose conditions are **lambdas** | Only when no named validator expresses the constraint. |

Tier 1 is type-safe by construction. Tier 2 is raw interpreter arithmetic that *degrades
gracefully* — a weaker guarantee. Prefer Tier 1; grow it rather than reaching past it.

---

## Tier 1 — Named validators

Each validator takes the payload by pipe and returns a `ValidationResult`.

| Validator | Signature | Example |
|-----------|-----------|---------|
| `required` | `(fields: string[])` | `$input \|> validate.required(["orderId", "currency"])` |
| `inRange` | `(field, min, max)` — inclusive | `$input \|> validate.inRange("unitPrice", 0.01, 999999.99)` |
| `oneOf` | `(field, values[])` | `$input \|> validate.oneOf("currency", ["EUR", "USD", "GBP"])` |
| `matches` | `(field, pattern)` — regex | `$input \|> validate.matches("iban", "^[A-Z]{2}[0-9]{2}.*")` |
| `isoDate` | `(field, format)` | `$input \|> validate.isoDate("orderDate", "yyyyMMdd")` |
| `unique` | `(arrayField, keyField)` | `$input \|> validate.unique("orderLines", "lineId")` |
| `mutuallyExclusive` | `(fields: string[])` — at most one set | `$input \|> validate.mutuallyExclusive(["shipToId", "shipToAddress"])` |
| `atLeastOne` | `(fields: string[])` — at least one set | `$input \|> validate.atLeastOne(["email", "phone", "fax"])` |
| `all` | `(results: ValidationResult[])` — merge several verdicts | see [Applying several rules](#applying-several-rules) |

**Robustness.** These degrade gracefully instead of throwing:

| Input | `required(["price"])` | `inRange("price",0,100)` | `oneOf("price",[50,60])` |
|---|---|---|---|
| `{}` — missing | `valid:false` | `valid:false` | `valid:false` |
| `{"price":"abc"}` — mistyped | `valid:true` (present) | `valid:false` | `valid:false` |

`oneOf` compares numbers by value, not by how they were boxed, so a JSON `50` matches a script
literal `[50, 60]` ([VB03](../bugs-fixed/VB03-validate-oneOf-numeric-membership-fails.md) — before
that fix it did not). It does **not** coerce across kinds: the string `"50"` is not the number `50`,
which is what makes it useful as a type check as well as a value check.

### Validators do not chain

Each validator takes **the message**. Piping one into another is rejected:

```utlx
$input |> validate.required(["orderId"]) |> validate.inRange("qty", 1, 100)
  → valid:false — "validate.inRange: malformed rule — payload is a ValidationResult;
                   validators must not be chained with |>"
```

The second call would otherwise inspect the *first call's result* instead of your message, and
because a `ValidationResult` has its own `valid` and `issues` fields it could report **`valid:true`
over a real failure** ([VB06](../bugs-fixed/VB06-chained-validators-fail-open.md)).

<h4 id="applying-several-rules">Applying several rules</h4>

Use **`validate.all`**, which merges several results into one verdict:

```utlx
$input |> validate.all([
  $input |> validate.required(["orderId", "customer"]),
  $input |> validate.inRange("qty", 1, 100),
  $input |> validate.oneOf("ccy", ["EUR", "USD"])
])
```

Every validator takes `$input` — never the previous result. The merged `passed` is the sum of the
parts, and the counts are **recomputed from the merged issues**, so they can never disagree with
them. An empty list checks nothing and is vacuously valid; an element that is not a
`ValidationResult` is a malformed rule, which catches the likely mistake of passing payloads or rule
lists instead of validator outputs.

The alternative is one `validate.assert` carrying all the rules (below) — preferable when the rules
need ids, severities or `when` guards, since those are rule-record features.

### Rule ids and severities are checked

Two parts of a rule are validated before anything runs, because a report that gets them wrong is
not usable:

```utlx
{ check: (p) => p.qty > 0, message: "m" }                     // ✗ no `rule` id
{ rule: "a", … }, { rule: "a", … }                            // ✗ duplicate ids
{ rule: "r", …, severity: "banana" }                          // ✗ not a severity
{ rule: "r", …, severity: "ERROR" }                           // ✓ normalised to `error`
```

- **`rule` is required and must be unique.** It is what `issues[].rule` attributes a failure to, and
  what a downstream consumer indexes on. A missing or duplicated id rejects the whole rule list as a
  malformed policy. (A quantified `every` rule expands to one issue per element, all sharing the
  author's single id — that is not a duplicate.)
- **`severity` is a closed set** — `error`, `warning`, `notice` — matched case-insensitively and
  reported in lowercase. An unrecognised value becomes an `error` issue naming the rule. Before
  [VB07](../bugs-fixed/VB07-unknown-severity-leaves-a-failing-rule-uncounted.md) it was counted in
  *no* bucket, so a typo left `failed:0` and `valid:true` on a rule that had failed.

### When a rule itself is wrong

A validator whose **arguments** are malformed reports an `error` issue rather than passing:

```utlx
$input |> validate.required("orderId")        // brackets forgotten
  → valid:false, failed:1
    "validate.required: malformed rule — fields must be an array of field names"
```

This covers wrong arity, a non-array where a list is expected, a non-numeric bound in `inRange`, an
unparseable regex in `matches`, an unusable format in `isoDate`, and a `unique` target that is not an
array. The message names the function and the reason, so it points at your script rather than at the
payload.

It follows the same rule as the two `assert` cases: **a rule that cannot be constructed is an error,
exactly like one that cannot be evaluated.** Before
[VB04](../bugs-fixed/VB04-named-validators-fail-open-on-malformed-arguments.md) these cases passed
silently — a typo disabled the rule and forwarded the data.

An empty list is still legal and still passes: `validate.required([])` checks nothing by design,
since a computed field list may legitimately be empty.

---

## Tier 2 — `validate.assert` with lambda rules

A rule's condition is a **function**, not a value:

```utlx
%utlx 1.1
input json
output json
---
$input |> validate.assert([
  { rule: "price-positive", check: (p) => p.price > 0, message: "price must be positive", field: "price" },
  { rule: "qty-positive",   check: (p) => p.qty > 0,   message: "qty should be positive", field: "qty", severity: "warning" }
])
```

### Rule record

| Key | Required | Meaning |
|-----|----------|---------|
| `check` | **yes** | Lambda `(p) => <boolean expr>`, called with the payload |
| `rule` | **yes** | Rule id — echoed into `issues[].rule`, and what a failure is attributed to. Must be non-blank and unique within the list |
| `message` | no | Issue text (defaults to `"rule failed"`) |
| `field` / `fields` | no | Field name, or an array joined comma-separated |
| `severity` | no | `error` (default), `warning` or `notice` — a **closed set**, matched case-insensitively. Anything else is an error issue naming the rule ([VB07](../bugs-fixed/VB07-unknown-severity-leaves-a-failing-rule-uncounted.md)) |
| `when` | no | Applicability guard, lambda `(p) => <boolean>` — see [Conditional rules](#conditional-rules-when) |
| `every` / `some` | no | Quantify over a named array; `check` then takes each **element** — see [Quantified rules](#quantified-rules-every--some) |

### The lambda contract

| The `check` lambda | Outcome | Severity |
|---|---|---|
| returns `true` | passes | — |
| returns `false` | fails | as declared (`error` by default) |
| **throws** | fails | **forced to `error`** — any declared `severity` is overridden |

Two guarantees follow:

- **No short-circuit.** Every rule is evaluated. One throwing check does not stop the rest.
- **Fail-closed.** A check that *cannot be evaluated* — missing field, wrong type — becomes a
  reported `error` issue (`"check 'price-positive' could not be evaluated: …"`), never a
  process-aborting throw, and never a mere warning. Malformed data cannot slip through because
  the rule that would have caught it was declared `severity: warning`.

Worked example — payload `{"name": "ok"}` against the three rules `price-positive` (error),
`qty-positive` (declared `warning`), `name-present` (error):

```json
{ "valid": false, "passed": 1, "failed": 2, "warnings": 0, "notices": 0,
  "issues": [
    {"rule": "price-positive", "field": "price", "message": "check 'price-positive' could not be evaluated: Cannot compare these types", "severity": "error"},
    {"rule": "qty-positive",   "field": "qty",   "message": "check 'qty-positive' could not be evaluated: Cannot compare these types",   "severity": "error"}
  ] }
```

`name-present` passes; the other two checks cannot evaluate against the absent fields, so both
land as errors — including the one declared `warning`.

### Why a lambda

A deferred condition over a value has exactly **one** working form in UTL-X — a lambda.
`filter($input.items, x => x.price > 100)` works; a bare condition as an argument does not, and
neither does the `items[price > 100]` predicate form. So a lambda `check` is not a special case
for validation; it is the language's own condition idiom.

It is also what makes the rule robust. Because the condition is deferred, `assert` can invoke it
per rule under its own error boundary. An eagerly evaluated condition is computed while the rule
list is being built — *upstream* of `assert` — so a comparison against missing or mistyped data
throws there and then, and `assert` never runs to isolate anything.

### Quantified rules (`every` / `some`)

A rule can quantify over a named array. The `check` then receives **each element**, not the payload:

```utlx
$input |> validate.assert([
  { rule: "line-qty-positive", every: "lines", check: (l) => l.qty > 0,
    message: "line qty must be positive", field: "qty" }
])
```

| Form | Passes when | Issues reported |
|---|---|---|
| `every: "<field>"` | every element satisfies the check | **one per failing element** |
| `some: "<field>"` | at least one element does | one, for the whole array |

On `lines: [{qty:5}, {qty:-2}, {qty:-9}]` the `every` rule above gives `passed:1, failed:2` and two
issues. Write the same constraint as a whole-payload lambda
(`check: (p) => count(filter(p.lines, l => l.qty <= 0)) == 0`) and you get **one** issue with
`field: "lines"` — the number of failures is lost. That is the reason to prefer `every` for anything
per-element.

An issue does **not** carry an element locator. A location syntax belongs to a serialization — JSON
Pointer is JSON's, XPath is XML's — and this report is format-neutral, so the counts tell you how
many elements failed and `payload` carries the record for inspection. Expressing *which* element
neutrally is an open decision ([F24 §Location](../features/F24-quantified-validation-rules.md)).

Edge semantics worth knowing:

- `every` over an empty array **passes** (vacuously true); `some` over an empty array **fails**.
- Every element is evaluated — no short-circuit — so one bad element never hides another.
- An element whose check cannot be evaluated becomes an error issue **for that element**, naming it
  in the message (*"could not be evaluated for element 1 of 'lines'"*).
- A quantifier over a missing field or a non-array is a **malformed rule** (fail-closed), like
  `validate.unique`.
- A `when` guard is resolved first: a false guard skips the rule without quantifying at all.

See [F24](../features/F24-quantified-validation-rules.md) for the full design.

### Conditional rules (`when`)

A rule can carry an applicability guard. It is a lambda with the same contract as `check`, and it
is resolved **first**:

| The `when` lambda | Outcome |
|---|---|
| returns `true` | the rule applies; `check` is then evaluated |
| returns `false` | the rule is **skipped** and counts as passed; `check` is **never** evaluated |
| **throws**, or returns a non-boolean | its own **error** issue, `"guard '<rule>' could not be evaluated: …"` |

```utlx
$input |> validate.assert([
  { rule: "wholesale-min-qty", check: (p) => p.qty >= 100, message: "wholesale needs qty >= 100",
    field: "qty", when: (p) => p.type == "WHOLESALE" },
  { rule: "retail-max-qty",    check: (p) => p.qty <= 10,  message: "retail needs qty <= 10",
    field: "qty", when: (p) => p.type == "RETAIL" }
])
```

On a `{"type": "RETAIL", "qty": 50}` payload the wholesale rule is skipped and counts as passed;
the retail rule applies and fails.

A guard that **cannot** be evaluated fails its rule rather than skipping it — the same fail-closed
reasoning as `check`, and for the same reason: malformed data must never be able to silence the rule
meant to catch it. As with `check`, that escalates over a declared `severity: warning`.

> Lambda `when` was silently ignored before
> [VB02](../bugs-fixed/VB02-validate-assert-lambda-when-guard-silently-ignored.md) — a rule you
> believed was scoped ran against every record. If you are reading older scripts, check their
> guards actually took effect.

### Writing a defensive check

`&&` and `||` short-circuit, so a guard protects the comparison behind it — all of these report
cleanly on `{}` (no `qty`) rather than escalating to "could not be evaluated":

| Check on `{}` (no `qty`) | Result |
|---|---|
| `(p) => p.qty != null && p.qty > 0` | ✓ `false` — fails the rule with your own `message` |
| `(p) => p.qty == null \|\| p.qty > 0` | ✓ `true` — treats absent as not-applicable |
| `(p) => if (p.qty == null) true else p.qty > 0` | ✓ `true` — equivalent, more explicit |

Note that `and` / `or` are **not** keywords in UTL-X — `check: (p) => a or b` is a parse error.
Use `&&` / `||`.

Pick the guard that says what you mean: `&&` to **fail** the rule on absent data, `||` to treat
absent as **not applicable**. Leaving the check unguarded is also valid — it then reports
"could not be evaluated" at `error` severity, which is the right outcome when absent data should
itself be a finding. Prefer a Tier-1 named validator over any of them where one fits.

> Short-circuiting was fixed in
> [B34](../bugs-fixed/B34-logical-operators-do-not-short-circuit.md); before that fix, only the
> `if`/`else` form guarded correctly.

### Deprecated: the eager `check:` form

```utlx
{ rule: "legacy", check: $input.price > 0, message: "..." }    // ✗ do not write this
```

This is still accepted for backward compatibility and works on well-formed data, but it is the
anti-pattern the lambda form replaced: on `{}` or `{"price":"abc"}` it aborts the whole
transformation with `Error: Cannot compare these types` instead of reporting an issue. Migrate
any remaining occurrences to `check: (p) => …`.

---

## The ValidationResult

Every `validate.*` call returns the same shape:

```json
{ "valid": false, "passed": 0, "failed": 1, "warnings": 1, "notices": 0,
  "issues": [ {"rule": "...", "field": "...", "message": "...", "severity": "error"} ],
  "payload": { "...": "the record, unchanged" } }
```

| Field | Semantics |
|-------|-----------|
| `valid` | **Exactly** `failed == 0`. No threshold is applied here. |
| `failed` | Count of `error`-severity issues only |
| `warnings` / `notices` | Counted separately; neither affects `valid` |
| `passed` | `totalChecks - issues.size` — a warning-issuing rule is neither passed nor failed |
| `issues` | One entry per non-passing rule — and one **per failing element** for a quantified rule |
| `payload` | The input record, carried through for downstream routing |

The result serializes cleanly to both JSON and XML (verified, including on the GraalVM native
binary).

---

## Routing on the verdict

Three layers, deliberately separate:

| Layer | Owner | Responsibility |
|-------|-------|----------------|
| **decide** | the script | `validate.*` produces the result; `valid = (failed == 0)` |
| **enforce** | the engine | `forward iff failed <= maxErrors AND warnings <= maxWarnings` |
| **lock** | deployment guard | pins the budgets (default `0 / 0`) |

Thresholds are kept out of the pure functions so their determinism holds — the same design axis
as the parse profile.

**Decide, in-script.** Branching on the result is ordinary language:

```utlx
%utlx 1.1
input json
output json
---
let v = $input |> validate.required(["orderId", "lineItems"])
in if (v.valid) { status: "forward", payload: v.payload }
   else { status: "dead-letter", failed: v.failed, issues: v.issues }
```

**Enforce, in the engine.** `ValidationRoutingPolicy` applies the per-severity budgets. It
defaults to the strictest posture (`maxErrors = 0`, `maxWarnings = 0`) — any error or warning
rejects. `notices` never gate. Output that is not a `ValidationResult` forwards untouched, so
the policy never governs a plain mapping. It is transport-agnostic: *where* a rejected payload
goes is deployment plumbing on top of this decision.

> **Deployment status:** the enforce layer is built and unit-tested but **not yet wired into the
> engine**, so today a verdict is ordinary output — nothing rejects on it, and no counter or log
> records it. If you need enforcement now, branch in-script as shown above. See
> [Validation Enforcement & Observability](../architecture/utlxe-validation-enforcement-and-observability.md)
> for the current state and the target design.

---

## Known limitations

- The `[condition]` predicate selector documented in [Selectors](selectors.md) is **not
  implemented** — use `filter(…, x => …)`.

---

## Cheat sheet

```utlx
%utlx 1.1                                      // required for validate.*

$input |> validate.required(["a", "b"])        // prefer named validators
$input |> validate.inRange("price", 0, 100)
$input |> validate.oneOf("ccy", ["EUR"])       // numbers compare by value too

$input |> validate.assert([                    // escape hatch: lambda checks
  { rule: "r", check: (p) => p.x > 0, message: "m", field: "x", severity: "warning" },
  { rule: "c", check: (p) => p.x <= 10, message: "m",           // conditional rule:
    when: (p) => p.type == "RETAIL" }                           // false => skipped, counts passed
])

v.valid        // failed == 0
v.failed       // errors only
v.warnings     // never affects valid
v.issues       // [{rule, field, message, severity}]
v.payload      // the record, for routing
```

| Rule | Why |
|------|-----|
| Named validator first | Type-safe by construction; `assert` only degrades gracefully |
| `check` is always `(p) => …` | The language's only working deferred-condition form |
| Guard with `&&` / `\|\|` | They short-circuit (B34); `&&` fails on absent, `\|\|` skips it |
| A broken `when` fails its rule | It never skips it — malformed data must not silence a rule |
| A malformed rule is an error | Wrong arity or argument type reports, never passes (VB04) |
| Never rely on `when` | Eager form crashes, lambda form is silently ignored |
| Never read `valid` as "within budget" | `valid` is `failed == 0`; budgets live in the engine policy |

---

## Next Steps

- **Operators and comparison semantics:** [Operators Guide](operators.md)
- **Lambdas and higher-order functions:** [Functions Guide](functions.md)
- **Conditionals and `let`:** [Control Flow](control-flow.md)

---

**Questions?** Ask in [Discussions](https://github.com/grauwen/utl-x/discussions)

---

**Last Updated:** 2026-10-23
**Language Version:** `%utlx 1.1`
**License:** AGPL-3.0 / Commercial Dual-License
