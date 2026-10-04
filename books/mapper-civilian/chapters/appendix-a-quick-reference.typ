= Appendix A: UTL-X Quick Reference

_A one-page reference for reading and writing the mappings in this book. For the complete
specification, see_ UTL-X: One Language, All Formats.

== Mapping skeleton

```
%utlx 1.1
input  <format>          // e.g. json, xml, ais, binf { definition: "packs/x.def" }
output <format>
---
<expression>             // the output, as one expression over $input
```

== Accessors

#table(
  columns: (auto, 1fr),
  [`.`], [data — `$input.lat`, `track.id`],
  [`@`], [attribute — `event."@type"`, an XML attribute],
  [`^`], [metadata — provenance, confidence, security label (releasability)],
)

== Core constructs

#table(
  columns: (auto, 1fr),
  [`let name = expr`], [bind a reusable sub-expression],
  [`map(x => ...)`], [transform each element of an array],
  [`filter(x => cond)`], [keep elements matching a condition],
  [`reduce(f, init)`], [fold an array to one value],
  [`a |> f |> g`], [pipe: feed a value through transforms left to right],
  [`match v { p => e, ... }`], [branch on value or shape],
  [`a ?? b`], [default: `b` when `a` is absent/null],
)

== UDM node kinds

Scalar · Object · Array · Binary · DateTime

== validate.\* (UTL-X 1.1)

#table(
  columns: (auto, 1fr),
  [`validate.assert(rules)`], [named rules: condition + message (+ field, severity)],
  [`validate.required(fields)`], [fields must be present],
  [`validate.inRange(field, min, max)`], [numeric bound],
  [`validate.oneOf(field, values)`], [field ∈ allowed set],
  [`validate.matches(field, pattern)`], [regex match],
  [`validate.isoDate(field, format)`], [well-formed date-time],
  [`validate.unique(arrayField, keyField)`], [no duplicate keys],
  [`validate.mutuallyExclusive(fields)`], [at most one present],
  [`validate.atLeastOne(fields)`], [at least one present],
)

Chain with `|>`; results merge into one `ValidationResult { valid, passed, failed, warnings,
errors[], payload }`. Downstream expressions see `payload` directly.

== The four guarantees

pure · stateless · deterministic · single-pass — held by both 1.0 (transform) and 1.1 (validate).
`ai.*` (2.0) is excluded from the mapping path because it breaks purity and determinism.
