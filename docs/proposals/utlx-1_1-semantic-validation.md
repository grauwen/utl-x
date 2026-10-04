# UTL-X 1.1 — Semantic Validation Extension

## Design Proposal

| Field         | Value |
|---|---|
| Document      | utlx-1.1-semantic-validation |
| Repo          | `github.com/grauwen/utl-x` |
| Target path   | `docs/proposals/utlx-1.1-semantic-validation.md` |
| Status        | Proposal — open for review |
| Replaces      | — |
| Depends on    | UTL-X 1.0 (parser, UDM 1.0, core stdlib) |
| Language version | `%utlx 1.1` |
| Engine release | Ships in a future `v1.x.0` engine release |

---

## 1. Motivation

UTL-X 1.0 is a transformation language. It maps, converts, and restructures
data. It does not validate it. If a field is wrong, missing, or internally
inconsistent, UTL-X 1.0 maps it anyway — it has no first-class way to assert
that a value is correct before the transformation proceeds.

Validation in UTL-X 1.0 today requires one of three workarounds:

**Workaround 1 — Return a boolean and route on it externally.**
The mapping returns `{ valid: condition1 and condition2, payload: ... }` and
a downstream router checks the `valid` field. This works but produces no error
detail — the consumer cannot distinguish which rule failed or why.

**Workaround 2 — Conditional mapping with null sentinels.**
Fields that fail a check are mapped to `null` and the downstream component
treats null as a signal. This loses the original value and produces no
structured error record.

**Workaround 3 — Write an SDK component in Kotlin/Java.**
Correct but expensive — a full component for logic that belongs in a
declarative mapping script.

None of these are satisfying. Cross-field semantic validation — asserting that
`vatRate` is valid for the declared `currency`, that `lineTotal` equals
`unitPrice * quantity`, that `orderDate` is not in the future — is a natural
part of data transformation. It belongs in the transformation language.

UTL-X 1.1 introduces the `validate.*` standard library namespace to address
this gap directly.

---

## 2. Design principles

UTL-X 1.1 extends 1.0 within the same fundamental contract:

| Guarantee | UTL-X 1.0 | UTL-X 1.1 |
|---|---|---|
| **Pure** | ✓ | ✓ |
| **Stateless** | ✓ | ✓ |
| **Deterministic** | ✓ | ✓ |
| **Format-agnostic** | ✓ | ✓ |
| **Runs inline in wrapper** | ✓ | ✓ |
| **No external dependencies** | ✓ | ✓ |

`validate.*` functions are pure — same input always produces the same
`ValidationResult`. They are stateless — no external lookup, no model weights,
no I/O. They are deterministic — no randomness, no probability. Every property
that makes UTL-X 1.0 safe to run inline on a connection arrow applies equally
to UTL-X 1.1.

This is what distinguishes 1.1 from 2.0. The `ai.*` namespace proposed for
UTL-X 2.0 (see `utl-x-infer/docs/proposals/UTLX-2-tabular-foundation-models.md`) breaks the purity and
determinism guarantees — it requires a major version. The `validate.*` namespace
does not break any 1.0 guarantee — it only adds. A minor version is correct.

---

## 3. Version bump rationale — why 1.1 and not a 1.0 stdlib addition

New core stdlib functions (e.g. `string.pad`, `date.truncate`) ship in engine
patch and minor releases without a header version bump. A 1.0 script gains
access to new functions simply by running on a newer engine — no script change
required.

`validate.*` requires a header version bump to `%utlx 1.1` for one reason:
it introduces a new UDM output node type — `ValidationResult` — that does not
exist in UDM 1.0. A 1.0 engine encountering `validate.assert()` would not know
what to do with the result. Rather than silently produce wrong output, it must
reject the script before execution begins. The version declaration in the
header is the mechanism for this rejection.

A 1.0 engine encountering a `%utlx 1.1` script emits:

```
UTL-X engine v1.3.0 cannot execute %utlx 1.1 scripts.
The validate.* namespace requires UTL-X engine v1.x.0 or later.
Upgrade: https://github.com/grauwen/utl-x/releases
```

All `%utlx 1.0` scripts run unchanged on a `%utlx 1.1`-capable engine. The
header version is a **minimum language version** declaration — not an engine
release pin.

---

## 4. New header fields

UTL-X 1.1 introduces no new required header fields. The version declaration
alone signals availability of `validate.*`. All other header syntax is
unchanged from 1.0:

```
%utlx 1.1                          ← version declaration (required)
input: current json                ← unchanged from 1.0
output json                        ← unchanged from 1.0
---                                ← separator, unchanged
```

Multi-input syntax is unchanged:

```
%utlx 1.1
input: orderHeader json, orderLines csv
output json
---
```

---

## 5. The `validate.*` namespace

### 5.1 `validate.assert`

The primary function. Evaluates a list of named rules against the input and
returns a `ValidationResult` UDM node.

**Signature:**

```
validate.assert(rules: Rule[]) → ValidationResult
```

**Rule structure:**

| Field | Type | Required | Description |
|---|---|---|---|
| `rule` | string | ✓ | Stable rule identifier. Used in error reports and routing. Should be kebab-case. |
| `check` | boolean expression | ✓ | The assertion. Evaluated against `$` (current input context). Must return boolean. |
| `when` | boolean expression | — | Guard condition. Rule is only evaluated when `when` is true. Skipped rules count as passed. |
| `message` | string | ✓ | Human-readable failure message. Included in the `ValidationResult.errors` array. |
| `field` | string | — | The field path that failed. Single field. Used by IDE and ops dashboard to highlight the offending field. |
| `fields` | string[] | — | Multiple field paths involved in the failure. Use for cross-field rules. Mutually exclusive with `field`. |
| `severity` | string | — | `"error"` (default) or `"warning"`. Warnings do not set `valid: false`. |

**Example:**

```
%utlx 1.1
input: current json
output json
---
$current |> validate.assert([

  // Simple field check — unitPrice must be positive
  { rule:    "price-positive",
    check:   $.unitPrice > 0,
    message: "unitPrice must be a positive number",
    field:   "unitPrice" },

  // Conditional cross-field check — VAT rate valid for EUR invoices
  { rule:    "vatrate-eur",
    when:    $.currency = "EUR",
    check:   [0, 7, 19] |> contains($.vatRate),
    message: "vatRate must be 0, 7, or 19 for EUR invoices",
    field:   "vatRate" },

  // Computed consistency check — line total must match unit price × quantity
  { rule:    "line-total-consistent",
    check:   $.lineTotal = $.unitPrice * $.quantity,
    message: "lineTotal does not equal unitPrice × quantity",
    fields:  ["lineTotal", "unitPrice", "quantity"] },

  // Date validity — order date must not be in the future
  { rule:    "date-not-future",
    check:   $.orderDate |> toDate("yyyyMMdd") <= today(),
    message: "orderDate cannot be in the future",
    field:   "orderDate" },

  // Codelist check — warning only, does not fail validation
  { rule:     "preferred-incoterm",
    check:    ["EXW","FCA","CPT","CIP","DAP","DPU","DDP"] |> contains($.incoterm),
    message:  "incoterm is not a standard ICC 2020 Incoterm",
    field:    "incoterm",
    severity: "warning" }

])
```

### 5.2 `validate.required`

Assert that all named fields are non-null and non-empty.

```
validate.required(fields: string[]) → ValidationResult
```

```
$current |> validate.required(["orderId", "currency", "lineItems"])
```

Equivalent to `validate.assert` with a null check per field, but terser for
presence-only validation.

### 5.3 `validate.inRange`

Assert a numeric field is within a declared range (inclusive).

```
validate.inRange(field: string, min: number, max: number) → ValidationResult
```

```
$current |> validate.inRange("unitPrice", 0.01, 999999.99)
```

### 5.4 `validate.oneOf`

Assert a field value is a member of a controlled list.

```
validate.oneOf(field: string, values: any[]) → ValidationResult
```

```
$current |> validate.oneOf("currency", ["EUR", "USD", "GBP", "CHF", "JPY"])
```

### 5.5 `validate.matches`

Assert a field value matches a regular expression pattern.

```
validate.matches(field: string, pattern: string) → ValidationResult
```

```
$current |> validate.matches("iban", "^[A-Z]{2}[0-9]{2}[A-Z0-9]{4}[0-9]{7}([A-Z0-9]?){0,16}$")
```

### 5.6 `validate.isoDate`

Assert a string field is a valid date in the declared format.

```
validate.isoDate(field: string, format: string) → ValidationResult
```

```
$current |> validate.isoDate("orderDate", "yyyyMMdd")
$current |> validate.isoDate("deliveryDate", "yyyy-MM-dd")
```

### 5.7 `validate.unique`

Assert no duplicate values exist for a key field within an array column.

```
validate.unique(arrayField: string, keyField: string) → ValidationResult
```

```
$current |> validate.unique("orderLines", "lineId")
```

### 5.8 `validate.mutuallyExclusive`

Assert at most one of the named fields is non-null.

```
validate.mutuallyExclusive(fields: string[]) → ValidationResult
```

```
$current |> validate.mutuallyExclusive(["shipToId", "shipToAddress"])
```

### 5.9 `validate.atLeastOne`

Assert at least one of the named fields is non-null.

```
validate.atLeastOne(fields: string[]) → ValidationResult
```

```
$current |> validate.atLeastOne(["email", "phone", "fax"])
```

### 5.10 Chaining validate.* functions

Multiple `validate.*` calls can be chained via the pipe operator. Results are
merged into a single `ValidationResult`:

```
%utlx 1.1
input: current json
output json
---
$current
  |> validate.required(["orderId", "currency", "lineItems"])
  |> validate.inRange("unitPrice", 0.01, 999999.99)
  |> validate.oneOf("currency", ["EUR", "USD", "GBP"])
  |> validate.assert([
       { rule: "vatrate-eur",
         when: $.currency = "EUR",
         check: [0, 7, 19] |> contains($.vatRate),
         message: "Invalid VAT rate for EUR" }
     ])
```

When chained, all rules from all calls are evaluated. The final
`ValidationResult` aggregates `passed`, `failed`, `warnings`, and `errors`
from every step in the chain. Chaining is short-circuit-free — all rules are
always evaluated regardless of earlier failures.

### 5.11 Naming convention and the guard profile

The `validate.*` namespace follows one convention: **descriptive, camelCase** names
(`validate.inRange`, not `validate.range`; `validate.isoDate`, not `validate.iso_date`). A call
site should read as intent, which matters most for the content guard's reviewers and accreditors.

The functions in §5.1–5.9 are the **core tier**, carried by every edition. A separate **guard
profile** — structural caps and allow-list functions such as `validate.conformsTo`,
`validate.onlyFields`, `validate.maxDepth`, `validate.maxBytes`, `validate.noControlChars`, and the
`validate.all(...)` combinator — extends the namespace for content-guard use. These have no
equivalent in the core tier and are defined in `utlx-mil/docs/guard-rule-library.md`. The full
two-tier vocabulary and the rename that aligned this spec to the convention are recorded in
`utlx-validate-naming-and-guard-profile.md`.

---

## 6. ValidationResult — new UDM node type

`validate.*` functions return a `ValidationResult` — a new UDM node type
introduced in UDM 1.1. It does not exist in UDM 1.0.

### Structure

```
ValidationResult {
  valid:    boolean      // true if no "error" severity rules failed
  passed:   integer      // count of passed rules (including skipped guards)
  failed:   integer      // count of failed "error" severity rules
  warnings: integer      // count of failed "warning" severity rules
  errors: [
    {
      rule:     string   // rule identifier
      message:  string   // human-readable failure description
      field:    string?  // single field path, if declared
      fields:   string[] // multiple field paths, if declared
      actual:   any?     // the actual value that failed, if scalar
      severity: string   // "error" | "warning"
    }
  ]
  payload:  UDM Node     // the original input — passed through unchanged
}
```

### Accessing ValidationResult fields

```
// In a downstream expression or routing condition
result.valid                  // true / false
result.passed                 // integer
result.failed                 // integer
result.warnings               // integer
result.errors[0].rule         // "vatrate-eur"
result.errors[0].message      // "Invalid VAT rate for EUR invoices"
result.errors[0].field        // "vatRate"
result.errors[0].actual       // 21
result.payload.orderId        // original field — passthrough via .payload
```

### Implicit payload passthrough

In most downstream expressions, `ValidationResult` behaves as its `payload`
field. A downstream mapping that accesses `$input.orderId` on a
`ValidationResult` will resolve to `$input.payload.orderId` automatically.
This means existing 1.0 mappings downstream of a 1.1 validation step do not
need to be rewritten — they access the original fields as before.

Explicit `._` access is required only when inspecting validation metadata:

```
// Implicit — downstream mapping, unaware of validation
{ orderId: $input.orderId }           // resolves to $input.payload.orderId

// Explicit — routing or error reporting, validation-aware
{ valid: $input.valid,
  errorCount: $input.failed,
  firstError: $input.errors[0].message }
```

---

## 7. Complete script examples

### Example 1 — Simple field validation before mapping

```
%utlx 1.1
input: current json
output json
---
$current
  |> validate.required(["orderId", "currency", "lineItems"])
  |> validate.assert([
       { rule:    "price-positive",
         check:   $.unitPrice > 0,
         message: "unitPrice must be positive",
         field:   "unitPrice" },
       { rule:    "vatrate-eur",
         when:    $.currency = "EUR",
         check:   [0, 7, 19] |> contains($.vatRate),
         message: "vatRate must be 0, 7, or 19 for EUR invoices",
         field:   "vatRate" }
     ])
```

Produces a `ValidationResult`. If `valid = true`, the `payload` field
contains the original input ready for downstream mapping. If `valid = false`,
the `errors` array contains structured failure records for routing to an
error handler.

### Example 2 — Validation then mapping in one script

```
%utlx 1.1
input: current json
output json
---
let validated = $current |> validate.assert([
  { rule: "price-positive", check: $.unitPrice > 0,
    message: "unitPrice must be positive", field: "unitPrice" },
  { rule: "date-not-future",
    check: $.orderDate |> toDate("yyyyMMdd") <= today(),
    message: "orderDate cannot be in the future", field: "orderDate" }
])

in if validated.valid
   then {
     orderId:   validated.payload.orderId,
     amount:    validated.payload.unitPrice * validated.payload.quantity,
     currency:  validated.payload.currency,
     status:    "VALIDATED"
   }
   else {
     orderId:   validated.payload.orderId,
     status:    "REJECTED",
     errors:    validated.errors |> map(e => e.message)
   }
```

The `let ... in` binding allows using the `ValidationResult` in a conditional
expression — mapping to different output shapes depending on validation outcome.

### Example 3 — Multi-input validation

```
%utlx 1.1
input: orderHeader json, orderLines csv
output json
---
let headerResult = $orderHeader |> validate.assert([
  { rule: "header-currency",
    check: ["EUR","USD","GBP"] |> contains($.currency),
    message: "Unsupported currency", field: "currency" }
])

let linesResult = $orderLines |> validate.assert([
  { rule: "lines-not-empty",
    check: $orderLines |> count() > 0,
    message: "Order must have at least one line" },
  { rule: "line-qty-positive",
    check: $orderLines |> every(line => line.quantity > 0),
    message: "All line quantities must be positive", field: "quantity" }
])

in {
  valid:        headerResult.valid and linesResult.valid,
  headerErrors: headerResult.errors,
  lineErrors:   linesResult.errors,
  payload: {
    orderId:  $orderHeader.orderId,
    currency: $orderHeader.currency,
    lines:    $orderLines |> map(l => { sku: l.productCode, qty: l.quantity })
  }
}
```

---

## 8. Engine implementation requirements

A UTL-X 1.1-capable engine MUST:

1. **Accept `%utlx 1.1` in the script header** and apply the 1.1 contract.

2. **Run all `%utlx 1.0` scripts unchanged** under the strict 1.0 contract.
   A 1.0 script must not gain access to `validate.*` simply by running on a
   1.1 engine — the header version gates availability.

3. **Reject `validate.*` calls in `%utlx 1.0` scripts at parse time.**
   Not at runtime. Parse-time rejection produces an informative error before
   any evaluation begins.

4. **Implement `ValidationResult` as a first-class UDM node type** with the
   structure defined in section 6. The node must support implicit `payload`
   passthrough in field access expressions.

5. **Evaluate all rules regardless of earlier failures** — validation is not
   short-circuit. Every rule in a `validate.assert` call is evaluated even if
   earlier rules have already failed. This ensures the full error picture is
   always available.

6. **Treat `when` guards as pass when false** — a rule whose `when` condition
   evaluates to `false` is counted as passed, not skipped in the `passed`
   count. This makes the `passed` count meaningful as a total rule count.

7. **Support rule severity** — `"error"` severity failures set `valid: false`.
   `"warning"` severity failures increment `warnings` but do not affect `valid`.

8. **Pass the full UTL-X 1.0 conformance suite** — all 465+ existing tests
   must pass on a 1.1 engine without modification.

9. **Pass the UTL-X 1.1 conformance suite additions** — new tests covering
   every `validate.*` function and the `ValidationResult` UDM node.

---

## 9. LSP daemon — changes for 1.1

The UTL-X LSP daemon (JSON-RPC 2.0) requires the following additions to
support `%utlx 1.1` scripts:

- **Autocomplete** for `validate.*` function names when the script header
  declares `%utlx 1.1`
- **Signature help** for `validate.assert` rule objects — field names, types,
  required vs optional
- **Hover documentation** for all `validate.*` functions
- **Diagnostic** when `validate.*` is used in a `%utlx 1.0` script — error
  with a link to the upgrade guide
- **`ValidationResult` field completion** — `result.valid`, `result.errors`,
  `result.payload.*` completions when the cursor is on a `ValidationResult`
  node

No new LSP protocol messages are required. All additions are within the
existing completion, hover, and diagnostic capabilities.

---

## 10. Conformance suite additions

The following test categories are added to the conformance suite for UTL-X 1.1.
All existing 1.0 tests remain and must continue to pass.

| Category | Description | Min tests |
|---|---|---|
| `validate.assert` — basic | Single rule, pass and fail | 6 |
| `validate.assert` — when guard | Guard true, guard false, guard missing | 4 |
| `validate.assert` — severity | Error severity, warning severity, mixed | 4 |
| `validate.assert` — cross-field | Multi-field `fields` array, computed check | 4 |
| `validate.required` | All present, one missing, all missing | 4 |
| `validate.inRange` | In range, below min, above max, boundary | 4 |
| `validate.oneOf` | Value in list, value not in list | 3 |
| `validate.matches` | Pattern match, pattern no match, invalid pattern | 4 |
| `validate.isoDate` | Valid date, invalid date, wrong format | 4 |
| `validate.unique` | No duplicates, one duplicate, all duplicate | 4 |
| `validate.mutuallyExclusive` | Zero set, one set, two set | 3 |
| `validate.atLeastOne` | None set, one set, all set | 3 |
| Chaining | Two functions chained, three chained, errors merged | 4 |
| ValidationResult — implicit passthrough | Field access on result resolves via payload | 3 |
| ValidationResult — explicit access | `.valid`, `.errors`, `.failed`, `.warnings` | 4 |
| let...in with ValidationResult | Conditional mapping on valid flag | 3 |
| Multi-input validation | Two named inputs, separate results | 3 |
| Header version gate | `validate.*` in `%utlx 1.0` → parse error | 2 |
| Backward compat | All existing 1.0 tests pass on 1.1 engine | 465+ |

---

## 11. Open questions

- **Should `validate.assert` rules be orderable by priority?**
  Current proposal: all rules evaluated, no priority. Alternative: declare
  `priority: integer` on rules — higher priority rules evaluated first, failure
  stops further evaluation for dependent rules. Adds complexity; not proposed
  for 1.1. Deferred to 1.2 if a use case emerges.

- **Should `ValidationResult.payload` be omitted when `valid = true`?**
  Current proposal: `payload` always present — passthrough regardless of
  outcome. Alternative: omit `payload` on failure to reduce message size.
  Rejected — omitting `payload` on failure prevents the error handler from
  accessing the original values for logging and correction workflows.

- **Should `validate.*` functions accept a schema ref for codelist lookups?**
  `validate.oneOf("currency", ["EUR","USD","GBP"])` hardcodes the list.
  A schema-ref-based variant — `validate.oneOf("currency",
  schema: "open-m.codelists.iso-4217:1.0.0")` — would resolve the list from
  the Schema Registry at runtime. This would break the stateless guarantee
  (external call to the registry). Not proposed for 1.1. If needed, the
  codelist should be passed as a named input rather than resolved at runtime.

- **Should `when` guard failures be counted as `passed` or as a separate
  `skipped` counter?**
  Current proposal: guarded rules that do not fire are counted as `passed`.
  Alternative: add a `skipped` counter to `ValidationResult`. More precise
  but adds complexity to the result structure. Deferred pending feedback from
  early adopters.

---

## 12. Relationship to other proposals

| Proposal | Relationship |
|---|---|
| `utl-x-infer/docs/proposals/UTLX-2-tabular-foundation-models.md` | UTL-X 2.0 adds `ai.*` namespace — probabilistic, non-pure. Different repo (`utl-x-infer`). 1.1 is a prerequisite: a 2.0 engine must pass all 1.1 conformance tests. |
| `utl-x-infer/docs/proposals/utlx-repository-strategy.md` | 1.1 ships in `github.com/grauwen/utl-x` (this repo) — alongside 1.0. It is not part of `github.com/grauwen/utl-x-infer`. The `utl-x-infer` engine imports `utl-x` and therefore also supports `%utlx 1.1` scripts out of the box. |
| `open-m-validation-decision-languages.md` | Describes how Open-M uses `validate.*` — routing on `ValidationResult.valid`, integration with FEEL routing stanza. That document is Open-M-specific. This document is the language-level specification. |
