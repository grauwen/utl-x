= Assured Validation

A converter translates. An *assured* converter refuses to emit a message it cannot certify as
correct. That refusal is the whole point of building the Mapper on UTL-X *1.1* rather than 1.0 — and
this chapter is how it works.

== Why 1.0 is not enough

UTL-X 1.0 is a transformation language: it maps, converts, restructures. It does *not* validate. If a
field is wrong, missing, or internally inconsistent, 1.0 maps it anyway — it has no first-class way to
assert a value is correct before proceeding. The workarounds (a boolean flag routed downstream, null
sentinels, a hand-written Kotlin component) all lose error detail or push the logic out of the
declarative mapping where it belongs. Cross-field correctness — that a coordinate is in range, that a
track's fields are mutually consistent, that a release caveat is a permitted value — is a natural part
of an assured mapping. It belongs *in the language*.

== The `validate.*` namespace

UTL-X 1.1 adds one thing: the `validate.*` standard-library namespace. Each function takes the current
value and returns a `ValidationResult` — a first-class UDM node recording what passed, what failed,
and why.

#table(
  columns: (auto, 1fr),
  [`validate.assert(rules)`], [the general form: a list of named rules, each a condition + message, with an optional field path and severity (`error` / `warning`)],
  [`validate.required(fields)`], [assert that named fields are present],
  [`validate.inRange(field, min, max)`], [assert a numeric field lies within bounds],
  [`validate.oneOf(field, values)`], [assert a field is one of an allowed set — the releasability/enum check],
  [`validate.matches(field, pattern)`], [assert a field matches a regular expression],
  [`validate.isoDate(field, format)`], [assert a date-time field is well-formed],
  [`validate.unique(arrayField, keyField)`], [assert no duplicate keys in a collection],
  [`validate.mutuallyExclusive(fields)`], [assert at most one of a set is present],
  [`validate.atLeastOne(fields)`], [assert at least one of a set is present],
)

They chain with the pipe, and the results merge into one `ValidationResult`:

```
%utlx 1.1
input  ais
output cise
---
let checked = $input
  |> validate.required(["mmsi", "lat", "lon"])
  |> validate.inRange("lat",  -90.0,  90.0)
  |> validate.inRange("lon", -180.0, 180.0)
  |> validate.oneOf("navStatus", [0,1,2,3,4,5,6,7,8,9,10,11,14,15])

checked.valid
  ? { vessel: { id: checked.payload.mmsi, position: {...} } }
  : dead_letter(checked.errors)
```

== ValidationResult: structured, auditable failure

The `ValidationResult` node is what turns validation from a yes/no into evidence:

#table(
  columns: (auto, 1fr),
  [`valid`], [true if no `error`-severity rule failed (warnings do not clear it)],
  [`passed` / `failed` / `warnings`], [counts, for dashboards and metrics],
  [`errors[]`], [per-failure detail: the rule id, the human message, the field path(s), and the severity],
  [`payload`], [the value under validation — passed through, so downstream expressions see the data directly],
)

Every rejected message therefore carries *why* it was rejected — which rule, which field — as a
structured record, not a log line. For an assured mapper this is decisive: a rejection is auditable
evidence, and an operator or an accreditor can see exactly which contract a feed violated.

== It inherits the four guarantees

Crucially, `validate.*` breaks *none* of the 1.0 guarantees. Every function is pure (same input →
same `ValidationResult`), stateless (no lookup, no I/O, no model weights), and deterministic (no
randomness, no probability). This is exactly what makes 1.1 a *minor* version and not a major one: it
only adds. The contrast is deliberate — the `ai.*` namespace proposed for UTL-X 2.0 *would* break
purity and determinism, which is why it needs a major version and why *AI never enters the mapping
path*. An assured verdict must be reproducible; a probabilistic one is not.

#table(
  columns: (auto, auto, auto),
  [*Guarantee*], [*1.0 transform*], [*1.1 validate*],
  [Pure], [✓], [✓],
  [Stateless], [✓], [✓],
  [Deterministic], [✓], [✓],
  [Single-pass], [✓], [✓],
)

== Validation as the contract gate

The operational pattern the Mapper is built around: *validate against the receiving contract before
emit*. A mapping does not merely produce output in the target format — it asserts the output conforms
to what the receiver will accept (its required fields, value ranges, code lists, releasability
labels), and routes a failure to a dead-letter with its `ValidationResult` instead of delivering a
bad message into a downstream system.

#align(center)[
  *parse → map to UDM → validate against the contract → emit only if valid, else dead-letter + audit*
]

The releasability check deserves emphasis: a STANAG 4774/4778 confidentiality label rides on the `^`
metadata channel (Chapter 5), and `validate.oneOf` on that label is how the Mapper enforces that a
message carries a permitted caveat for its destination — mislabelled data is refused, not translated.

Chapter 8 turns this gate into a workflow: how contracts are defined and how a mapping is authored and
proven against them.
