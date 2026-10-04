= Appendix A: UTL-X Quick Reference

_A syntax cheat-sheet. For the complete reference see_ UTL-X: One Language, All Formats.

== File structure

```
%utlx 1.1            // a guard validates, so 1.1; 1.0 for a pure transform
input  json
output json
---
{ transformation body }
```

== Format headers

#table(
  columns: (auto, 1fr),
  [`input json` / `xml` / `csv` / `yaml`], [self-describing text formats],
  [`input track binf {definition: "t.def"}`], [binary via a form-class (Ch 6)],
  [`input ais`], [named alias → `binf` + registered form-class],
  [`output xml`], [output format (same as input, or different)],
)

== Access

#table(
  columns: (auto, 1fr),
  [`$in.name` (`.`)], [data],
  [`$in.El.@id` (`@`)], [attribute],
  [`$in.x^crcValid` (`^`)], [metadata about a node (incl. the label)],
)

== Validation (1.1)

```
validate.inRange($m.lat, -90, 90)
validate.oneOf($m.status, ["OK","WARN","FAIL"])
validate.conformsTo($m, "contracts/approved/x:1.0.0")
validate.assert($m.stale > $m.time, "stale after time")
```

== The guard, in one line

```
untrusted bytes → hardened parse → UDM → validate.* rules → canonical serialize → egress
                                         └──── any failure → isolation + audit + alert
```

== The non-negotiables

no pass-through · allow-list / fail-closed · pure 1.0/1.1 only (never `ai.*`) · canonical low-fidelity
output · every verdict auditable · UDM only sees what it models.

== The verdict policy

rebuild what is clean, reject what is wrong — never repair · three categories (formatting noise /
policy-strip / violation) · three verdicts (`PASS` / `PASS-STRIPPED` / `REJECT`).
