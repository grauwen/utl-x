# UTL-X — Language Versioning and Validation Extensions

## Status: Design Document

| Field | Value |
|---|---|
| Document | utlx-language-versioning-validation |
| Status | Active design — 1.0 current; 1.1 and 2.0 proposed |
| Scope | UTL-X language specification — version boundaries, UDM extensions, new stdlib namespaces |
| Repo | `github.com/grauwen/utl-x` (1.0 + 1.1 core language) |
| Related | `utlx-1_1-semantic-validation.md` (same repo); `utl-x-infer/docs/proposals/UTLX-2-tabular-foundation-models.md` (2.0) |

---

## 1. UTL-X 1.0 — the current contract

UTL-X 1.0 is a pure, stateless, format-agnostic functional transformation
language. Every script is governed by four guarantees:

| Guarantee | Definition |
|---|---|
| **Pure** | Same input always produces same output |
| **Stateless** | No external dependencies, no side effects, no model weights |
| **Deterministic** | No probability, no uncertainty, no randomness |
| **Single-pass** | One input (or N named inputs from the same correlation chain) → one output |

These guarantees are what make UTL-X 1.0 safe to run **inline on the connection
arrow** in the receiving component's wrapper — in-process, without a dedicated
pod, without GPU resources, without error isolation. They are not incidental
properties of the language. They are its defining contract.

### UTL-X 1.0 script structure

```
%utlx 1.0                          ← version declaration (required)
input: current json                ← named input(s) with format
output json                        ← output format
---                                ← separator
{                                  ← body: pure functional expression
  orderId:   $current.id,
  amount:    $current.lines |> map(l => l.price * l.qty) |> sum(),
  currency:  $current.currency |> upper()
}
```

### UDM 1.0 — the Universal Data Model

All inputs are parsed into the UDM before any expression executes.
All outputs are serialised from the UDM to the declared output format.

**UDM 1.0 node types:**

```
UDM Node (1.0)
├── Scalar
│   ├── StringValue
│   ├── NumberValue
│   ├── BooleanValue
│   └── NullValue
├── Composite
│   ├── ObjectNode    (named children — JSON object, XML element)
│   └── ArrayNode     (indexed children — JSON array, XML repeating)
└── Metadata
    ├── AttributeNode  (XML @attribute)
    └── NamespaceNode  (XML namespace)
```

All nodes carry observed, deterministic values. No uncertainty. No provenance
metadata. No probabilistic variants.

---

## 2. Version strategy

UTL-X uses **semver** with a strict interpretation:

| Bump | Meaning | Example |
|---|---|---|
| **PATCH** | No language change. Specification clarification, error message improvements, bug fixes. Scripts unchanged. | 1.0.1 |
| **MINOR** | Additive capabilities. New stdlib functions or namespaces. New UDM node types that are additive (old engines can passthrough or skip). Old scripts run unchanged on new engine. | 1.1 |
| **MAJOR** | Breaking change to the language contract, execution model, or UDM semantics. Old engines must refuse new scripts. | 2.0 |

### The version gate

The first line of every UTL-X script is the version declaration:

```
%utlx 1.0
%utlx 1.1
%utlx 2.0
```

This is not optional. A script without a version declaration is rejected by all
engine versions. The engine reads the version before executing any other line
and applies the corresponding contract:

- `%utlx 1.0` on a 2.0 engine → runs under strict 1.0 contract
- `%utlx 2.0` on a 1.0 engine → hard rejection, informative error message
- `%utlx 1.1` on a 1.0 engine → hard rejection (engine too old)
- `%utlx 1.1` on a 2.0 engine → runs under 1.1 contract

**Backward compatibility rule:** Every UTL-X N.0 engine can execute all scripts
with a lower major version. Breaking compatibility within a major version is
never permitted.

### 2.1 Release & packaging — engine version vs language version

Two version axes must be kept separate, or packaging decisions go wrong:

| Axis | Example | What it is |
|---|---|---|
| **Engine release version** | `v1.3.1 → v1.4.0 …` | the binary (UTLXe) you ship — bug fixes, new formats, performance |
| **Language version** | `%utlx 1.0`, `%utlx 1.1` | what a *script* declares in its header (the version gate, §2) |

**One engine supports multiple language versions.** A single UTLXe binary that implements `%utlx 1.1`
runs both 1.0 and 1.1 scripts; a 1.0 script behaves identically whether or not the engine also knows
`validate.*` — the header gates it. "Download both" is therefore satisfied by **one binary**: it *is*
both. Publish an explicit **engine → language support matrix** in the release notes (e.g. "UTLXe v1.4
supports `%utlx 1.0` and `%utlx 1.1`").

**`1.1` ships in the same binary — do not split it out.** `validate.*` is **pure Kotlin with zero
heavyweight dependencies** (§6; contrast `ai.*`, which pulls DJL/ONNX/TabPFN/GPU). Its native-image
footprint is a handful of stdlib functions plus the `ValidationResult` node — kilobytes against a
multi-MB image. Splitting it into a separate "validation" delivery would shave a rounding error of
size while permanently doubling the build/test/sign/distribute matrix and creating a "I downloaded
the small one and my `%utlx 1.1` script fails" support channel. The purity/dependency line — not the
version number — decides what becomes a separate delivery; by that test `1.1` stays in, `2.0` stays
out (a separate repo, `utl-x-infer`).

**Maintaining 1.0 alongside 1.1 (one repo, no binary split):**

1. Freeze 1.0 as a **tag / maintenance branch** (`v1.0.x` / `release/1.0`, the book's citable
   baseline); develop 1.1 on `main`. Git gives a pristine 1.0 *and* a home for 1.1 — no repo split.
2. Engine releases from `main` support `%utlx 1.0` **and** `1.1`; 1.0-only patches, if ever needed,
   ship from the maintenance line.
3. **CI runs the full 1.0 conformance suite on every build** (plus a 1.1 suite). This conformance
   wall — not a separate binary — is the real guarantee that bundling 1.1 never disturbs 1.0.
   (See `docs/architecture/utlx-test-corpora.md`.)

**Where binary minimalism actually pays off — not `validate.*`.** If a small image / small TCB is a
real goal (e.g. the MIL content guard), the levers that move the needle are **optional format
modules** (a JSON-only mapper should not compile in the EDIFACT/BINF readers) and **excluding `ai.*`**
(already achieved by the repo split) — applied via a build-time flag (Gradle property / GraalVM
feature), producing a reduced artifact from the *same* codebase. Note the guard is the tell: it wants
a minimal engine yet **needs `validate.*`** (it is the rule engine) and drops unused format readers and
anything probabilistic. So `validate.*` belongs in the base; the modularity knob belongs on formats
and `ai.*`.

**When a separate `1.1` artifact *is* justified** — only for reasons other than size: a **commercial**
model (validation sold as a closed add-on) or a **hard assurance policy** (a deployment that must be
*incapable* of running `validate.*`). Enforce those with a **build-time flag** that excludes the
`validate.*` module, not with a repo split — and measure the size delta first; for pure `validate.*`
it will be small.

---

## 3. UTL-X 1.1 — validate.* stdlib (proposed)

### Motivation

Cross-field semantic validation — asserting that field combinations are
internally consistent — is currently not a first-class UTL-X concern. Developers
work around this by:

- Writing UTL-X expressions that return a boolean and routing on it (awkward —
  no error detail)
- Writing SDK components in Java/Go that re-implement validation logic that
  belongs in the pipeline configuration
- Using CELL expressions that quickly become unreadable for multi-field rules

JSONtron-style assertions are **pure and deterministic** — they fit the 1.0
contract and do not require probabilistic UDM extensions. But they need a new
UDM output node type (`ValidationResult`) and a new stdlib namespace. Since a
1.0 engine would produce wrong results if it silently ignored `validate.*` calls,
a minor version bump is required.

### Why 1.1 not 2.0

The changes are **additive**:

- New stdlib namespace `validate.*` — no existing function changes
- New UDM node type `ValidationResult` — no existing node type changes
- No change to the execution model — `validate.*` is valid in `mode: inline`
  and `mode: ref`
- No change to purity, statelessness, or determinism guarantees
- All existing 1.0 scripts run unchanged on a 1.1 engine

A 1.0 engine encountering `validate.*` fails loudly — the minor version in the
header tells it to reject the script before execution begins.

### New header fields in 1.1

```
%utlx 1.1
input: current json
output json
                    ← no new required header fields
---
```

No new header fields are required for 1.1. The version declaration alone signals
availability of `validate.*`.

### validate.* namespace

```
%utlx 1.1
input: current json
output json
---
$current |> validate.assert([

  // Simple field check
  { rule:    "price-positive",
    check:   $.unitPrice > 0,
    message: "unitPrice must be positive",
    field:   "unitPrice" },

  // Conditional cross-field check
  { rule:    "vatRate-eur",
    when:    $.currency == "EUR",
    check:   [0, 7, 19] |> contains($.vatRate),
    message: "vatRate must be 0, 7, or 19 for EUR invoices",
    field:   "vatRate" },

  // Computed consistency check
  { rule:    "line-total-consistent",
    check:   $.lineTotal == $.unitPrice * $.quantity,
    message: "lineTotal does not match unitPrice × quantity",
    fields:  ["lineTotal", "unitPrice", "quantity"] },

  // Date validity
  { rule:    "date-not-future",
    check:   $.orderDate |> toDate("yyyyMMdd") <= now(),
    message: "orderDate cannot be in the future",
    field:   "orderDate" },

  // Codelist check
  { rule:    "valid-incoterm",
    check:   ["EXW","FCA","CPT","CIP","DAP","DPU","DDP"]
               |> contains($.incoterm),
    message: "incoterm is not a valid ICC 2020 Incoterm",
    field:   "incoterm" }

])
```

### validate.* function reference

| Function | Arguments | Description |
|---|---|---|
| `validate.assert(rules[])` | Array of rule objects | Evaluate all rules, return ValidationResult |
| `validate.required(fields[])` | Array of field names | Assert all named fields are non-null |
| `validate.range(field, min, max)` | Field path, min, max | Assert numeric field within range (inclusive) |
| `validate.codelist(field, values[])` | Field path, allowed values | Assert field value in controlled list |
| `validate.matches(field, pattern)` | Field path, regex | Assert field matches regex pattern |
| `validate.iso_date(field, format)` | Field path, format string | Assert field is a valid date in declared format |
| `validate.unique(arrayField, keyField)` | Array path, key field | Assert no duplicate key values in array |
| `validate.mutually_exclusive(fields[])` | Array of field names | Assert at most one of the named fields is non-null |
| `validate.at_least_one(fields[])` | Array of field names | Assert at least one of the named fields is non-null |

### ValidationResult UDM node (new in 1.1)

`validate.assert()` returns a new UDM node type — `ValidationResult`:

```
ValidationResult {
  valid:    boolean          // true if all rules passed
  passed:   integer          // count of passed rules
  failed:   integer          // count of failed rules
  errors: [
    {
      rule:    string        // rule identifier
      message: string        // human-readable failure message
      field:   string?       // optional — which field failed
      fields:  string[]?     // optional — multiple fields involved
      actual:  any?          // optional — the actual value that failed
    }
  ]
  payload:  UDM Node         // the original input — passthrough
}
```

`ValidationResult` behaves as the `payload` field in most downstream
expressions — backward compatible with mappings that don't inspect validation
metadata. The validation metadata is accessible explicitly:

```
result.valid           // true / false
result.errors[0].rule  // "vatRate-eur"
result.payload.orderId // original field access via payload
```

### Chaining validate.* with FEEL routing

The natural downstream use of `ValidationResult` is FEEL content-based routing:

```yaml
# Pipeline YAML — validation then FEEL routing

# Step 1: UTL-X 1.1 semantic validation inline on arrow
connections:
  - id: conn-inbound-to-validator
    transform:
      type: utlx
      mode: inline
      mapping: |
        %utlx 1.1
        input: current json
        output json
        ---
        $current |> validate.assert([
          { rule: "price-positive", check: $.unitPrice > 0,
            message: "unitPrice must be positive" }
        ])

# Step 2: FEEL routing on the ValidationResult
  - id: conn-validator-to-router
    routing:
      language: feel
      expression: |
        if valid = true
        then "erp-loader"
        else "validation-error-handler"
      targets:
        erp-loader:               { component: erp-loader,    port: input }
        validation-error-handler: { component: error-handler, port: input }
```

---

## 4. UTL-X 2.0 — ai.* stdlib and probabilistic UDM

### What changes — summary

UTL-X 2.0 introduces TFM (Tabular Foundation Model) support. This requires:

1. A new **execution mode** — `mode: component` (declared in the script header)
2. A new **UDM node type** — `ProbabilisticCell`
3. A new **stdlib namespace** — `ai.*`
4. New **header fields** — `mode:` and `model:`

These changes break the purity and determinism contract of 1.0. A major version
bump is required. See **`utl-x-infer/docs/proposals/UTLX-2-tabular-foundation-models.md`**
(the `utl-x-infer` repo) for the full specification.

### Why 2.0 not 1.2

| Change | Additive? | Engine can ignore? | 1.x compatible? |
|---|---|---|---|
| `ProbabilisticCell` UDM node | No — new node semantics | No — produces wrong results if ignored | ✗ |
| `ai.*` stdlib namespace | No — breaks purity contract | No — side effects | ✗ |
| `mode: component` header | Yes — new field | No — changes execution model | ✗ |
| Breaks determinism guarantee | N/A — contract change | N/A | ✗ |

All four changes require a 1.x engine to **refuse the script** rather than
execute it incorrectly. A 1.x engine can only do this reliably if the version
in the header is a new major version — `%utlx 2.0`.

### Execution mode contract — all versions

| | 1.0 | 1.1 | 2.0 mode:inline | 2.0 mode:component |
|---|---|---|---|---|
| Purity required | ✓ | ✓ | ✓ | ✗ |
| Stateless | ✓ | ✓ | ✓ | ✗ |
| Deterministic | ✓ | ✓ | ✓ | ✗ |
| `validate.*` | ✗ | ✓ | ✓ | ✓ |
| `ai.*` | ✗ | ✗ | ✗ | ✓ |
| ProbabilisticCell | ✗ | ✗ | passthrough | ✓ full |
| Inline in wrapper | ✓ | ✓ | ✓ | ✗ |
| Dedicated pod | ✗ | ✗ | ✗ | ✓ |
| GPU nodeSelector | ✗ | ✗ | ✗ | ✓ |

`2.0 mode: inline` preserves the full 1.0/1.1 contract — the version declaration
allows new UDM node types to flow through as passthroughs, but `ai.*` remains
a parse-time error. Existing scripts using `%utlx 1.0` or `%utlx 1.1` run
unchanged on a 2.0 engine.

---

## 5. UDM versioning

The UDM versions in lockstep with the language but is separately specified:

| UDM version | Ships with | New node types |
|---|---|---|
| UDM 1.0 | UTL-X 1.0 | Scalar, Composite, Metadata nodes |
| UDM 1.1 | UTL-X 1.1 | `ValidationResult` |
| UDM 2.0 | UTL-X 2.0 | `ProbabilisticCell`, `ColumnAnnotation`, `TableMeta` |

UDM node types from lower versions are always present in higher versions —
`StringValue` exists in UDM 1.0, 1.1, and 2.0 unchanged.

---

## 6. stdlib namespace allocation

Each major or minor version reserves stdlib namespaces. A namespace introduced
in a version is never reused or repurposed in a later version:

| Namespace | Available from | Purpose |
|---|---|---|
| *(no prefix)* | 1.0 | Core functions — `map`, `filter`, `reduce`, `sum`, `upper`, `toDate`, etc. |
| `string.*` | 1.0 | String operations — `string.split`, `string.pad`, `string.encode` |
| `date.*` | 1.0 | Date/time — `date.format`, `date.diff`, `date.truncate` |
| `math.*` | 1.0 | Numeric — `math.round`, `math.ceil`, `math.abs` |
| `schema.*` | 1.0 | Schema-aware — `schema.typeof`, `schema.coerce` |
| `validate.*` | **1.1** | Cross-field assertion — `validate.assert`, `validate.required`, etc. |
| `ai.*` | **2.0** | TFM inference — `ai.impute`, `ai.classify`, `ai.score`, `ai.anomaly` |

Namespaces are **reserved at the version they are introduced**. A 1.0 engine
encountering `validate.*` fails; a 1.1 engine encountering `ai.*` fails. The
version in the script header is the authoritative signal for which namespaces
are available.

---

## 7. Script header — all versions

```
# UTL-X 1.0
%utlx 1.0
input: {alias} {format}[, {alias} {format}]*
output {format}
---

# UTL-X 1.1 — no new required header fields
%utlx 1.1
input: {alias} {format}[, {alias} {format}]*
output {format}
---

# UTL-X 2.0 — new optional and required fields
%utlx 2.0
input:  {alias} {format}[, {alias} {format}]*
output  {format}
mode:   inline | ref | component       ← new (default: inline)
model:  {namespace}.models.{name}:{version}  ← required if ai.* used
---
```

### Format tokens (all versions)

| Token | Format |
|---|---|
| `json` | JSON (RFC 8259) |
| `xml` | XML 1.0 / 1.1 |
| `csv` | Delimited text (separator from TSCH) |
| `yaml` | YAML 1.2 |
| `avro` | Apache Avro binary |
| `proto` | Protocol Buffers |
| `auto` | Auto-detect from MPPM envelope `content_type` |

### N-input header syntax (all versions)

```
input: current json, previous json        // two steps, same pipe
input: order json, pricing json           // two independent inputs
input: orderHeader xml, orderLines csv    // mixed formats
input: sapIdoc xml, sfOpportunity json, customerProfile json  // three inputs
```

---

## 8. Engine implementation requirements

An Open-M UTL-X engine implementation MUST:

1. **Read the `%utlx` version declaration before executing any expression.**
   Do not parse the body before confirming the version is supported.

2. **Reject unsupported versions with an informative error.**
   Error format: `UTL-X engine 1.0 cannot execute %utlx 1.1 script.
   Upgrade to UTL-X engine 1.1 or later.`

3. **Enforce namespace availability at parse time, not runtime.**
   A `validate.*` call in a `%utlx 1.0` script is a parse error, not a
   runtime error. The script never executes.

4. **Enforce `mode:` constraints at parse time.**
   An `ai.*` call without `mode: component` is a parse error.

5. **Treat all 1.x scripts as valid on a 2.x engine.**
   A 2.0 engine applies the strict 1.0 contract to `%utlx 1.0` scripts —
   including rejecting `validate.*` and `ai.*` if somehow present.

6. **Never silently ignore unknown stdlib calls.**
   An unknown function is always a parse error. Silent passthrough would
   produce wrong results and hide configuration errors.

---

## 9. Version compatibility matrix — complete

| Script version | Engine 1.0 | Engine 1.1 | Engine 2.0 |
|---|---|---|---|
| `%utlx 1.0` | ✓ runs | ✓ runs (1.0 contract) | ✓ runs (1.0 contract) |
| `%utlx 1.1` | ✗ rejected | ✓ runs | ✓ runs (1.1 contract) |
| `%utlx 2.0 mode:inline` | ✗ rejected | ✗ rejected | ✓ runs (1.1 contract + passthrough) |
| `%utlx 2.0 mode:component` | ✗ rejected | ✗ rejected | ✓ runs (full 2.0 contract) |

---

## 10. Open questions

- **Should `validate.*` also be available in Mode 3 mapping components?**
  Proposal: yes — it is pure and deterministic regardless of execution context.
  A Mode 3 component can use both `validate.*` and `ai.*` in a 2.0 script,
  combining validation and inference in a single mapping.

- **Should the `validate.*` error format be extensible?**
  Current proposal: fixed schema with `rule`, `message`, `field`, `actual`.
  Alternative: open `context` object for domain-specific error metadata.

- **Should `%utlx auto` be a valid version declaration?**
  Would allow the engine to execute the highest version it supports.
  Risk: non-deterministic behaviour across engine versions. Proposal: no —
  the version must always be explicit.

- **Conformance suite versioning.**
  UTL-X 1.0 has 465 passing conformance tests. UTL-X 1.1 must pass all 465
  plus new tests for `validate.*` and `ValidationResult`. UTL-X 2.0 must
  pass all 1.1 tests plus new tests for `ai.*`, `ProbabilisticCell`, and
  `mode: component` enforcement.

- **LSP (Language Server Protocol) daemon versioning.**
  The UTL-X LSP daemon currently supports 1.0. It must be extended to:
  - Offer `validate.*` autocomplete for 1.1 scripts
  - Offer `ai.*` autocomplete for 2.0 scripts in `mode: component` only
  - Show a warning when `ai.*` is used in `mode: inline`
  - Resolve model refs from the Model Registry for hover documentation
